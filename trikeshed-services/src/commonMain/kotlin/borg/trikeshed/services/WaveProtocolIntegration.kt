@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wave Protocol Integration for Real-time Collaboration
 * 
 * Implements Wave's operational transformation patterns:
 * - Document operations (insert, delete, annotate)
 * - Operational transformation for conflict resolution
 * - Wavelet-based document structure
 * - Real-time synchronization
 */
class WaveProtocolIntegration(
    internal val sessionManager: WaveSessionManager = WaveSessionManager(),
    internal val otEngine: OperationalTransformEngine = OperationalTransformEngine()
) {
    
    // Active collaboration sessions
    internal val activeSessions = mutableMapOf<String, WaveSession>()
    
    // Document wavelets
    internal val documentWavelets = mutableMapOf<String, Wavelet>()
    
    // Participant management
    internal val participants = mutableMapOf<String, WaveParticipant>()
    
    // Operation history for replay
    internal val operationHistory = mutableMapOf<String, MutableList<WaveOperation>>()

    /**
     * Create a new Wave collaboration session
     */
    fun createSession(sessionId: String, documentId: String): WaveSession {
        val session = WaveSession(sessionId, documentId, otEngine)
        activeSessions[sessionId] = session
        
        // Initialize document wavelet
        val wavelet = Wavelet(documentId, sessionId)
        documentWavelets[documentId] = wavelet
        
        return session
    }

    /**
     * Join an existing collaboration session
     */
    fun joinSession(sessionId: String, participantId: String): WaveParticipant {
        val session = activeSessions[sessionId] 
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val participant = WaveParticipant(participantId, sessionId)
        participants[participantId] = participant
        
        session.addParticipant(participant)
        
        // Send initial state
        val initialState = session.getDocumentState()
        participant.sendState(initialState)
        
        return participant
    }

    /**
     * Apply a Wave operation with operational transformation
     */
    suspend fun applyOperation(
        sessionId: String, 
        participantId: String, 
        operation: WaveOperation
    ): WaveOperationResult {
        val session = activeSessions[sessionId]
            ?: return WaveOperationResult.Failure("Session not found")
        
        val participant = participants[participantId]
            ?: return WaveOperationResult.Failure("Participant not found")
        
        // Transform operation based on current state
        val transformedOp = otEngine.transform(operation, session.getOperationHistory())
        
        // Apply to session
        val result = session.applyOperation(transformedOp)
        
        // Broadcast to other participants
        broadcastOperation(sessionId, participantId, transformedOp)
        
        // Store in history
        operationHistory.getOrPut(sessionId) { mutableListOf() }.add(transformedOp)
        
        return result
    }

    /**
     * Get document state for a session
     */
    fun getDocumentState(sessionId: String): WaveDocumentState? {
        val session = activeSessions[sessionId] ?: return null
        return session.getDocumentState()
    }

    /**
     * Create a document operation (insert, delete, annotate)
     */
    fun createDocumentOperation(
        type: DocumentOperationType,
        position: Int,
        content: String = "",
        annotations: Map<String, String> = emptyMap()
    ): WaveOperation.DocumentOp {
        return WaveOperation.DocumentOp(
            operationId = generateOperationId(),
            type = type,
            position = position,
            content = content,
            annotations = annotations,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Create a participant operation (join, leave, cursor move)
     */
    fun createParticipantOperation(
        type: ParticipantOperationType,
        participantId: String,
        data: Map<String, Any?> = emptyMap()
    ): WaveOperation.ParticipantOp {
        return WaveOperation.ParticipantOp(
            operationId = generateOperationId(),
            type = type,
            participantId = participantId,
            data = data,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Subscribe to real-time updates
     */
    fun subscribeToUpdates(sessionId: String): Flow<WaveUpdate> {
        val session = activeSessions[sessionId]
            ?: return flow { emit(WaveUpdate.Error("Session not found")) }
        
        return session.updateFlow
    }

    // Private methods

    internal suspend fun broadcastOperation(
        sessionId: String, 
        senderId: String, 
        operation: WaveOperation
    ) {
        val session = activeSessions[sessionId] ?: return
        
        session.getParticipants().forEach { participant ->
            if (participant.id != senderId) {
                participant.sendOperation(operation)
            }
        }
    }

    internal fun generateOperationId(): String = 
        "wave-op-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
}

/**
 * Wave Session for managing collaboration
 */
class WaveSession(
    val id: String,
    val documentId: String,
    internal val otEngine: OperationalTransformEngine
) {
    internal val participants = mutableSetOf<WaveParticipant>()
    internal val operationHistory = mutableListOf<WaveOperation>()
    internal val documentState = WaveDocumentState(documentId, "")
    
    internal val _updateFlow = MutableSharedFlow<WaveUpdate>()
    val updateFlow: Flow<WaveUpdate> = _updateFlow.asSharedFlow()

    fun addParticipant(participant: WaveParticipant) {
        participants.add(participant)
        _updateFlow.tryEmit(WaveUpdate.ParticipantJoined(participant.id))
    }

    fun removeParticipant(participantId: String) {
        participants.removeIf { it.id == participantId }
        _updateFlow.tryEmit(WaveUpdate.ParticipantLeft(participantId))
    }

    fun getParticipants(): Set<WaveParticipant> = participants.toSet()

    fun getOperationHistory(): List<WaveOperation> = operationHistory.toList()

    fun getDocumentState(): WaveDocumentState = documentState.copy()

    suspend fun applyOperation(operation: WaveOperation): WaveOperationResult {
        return when (operation) {
            is WaveOperation.DocumentOp -> applyDocumentOperation(operation)
            is WaveOperation.ParticipantOp -> applyParticipantOperation(operation)
        }
    }

    internal suspend fun applyDocumentOperation(operation: WaveOperation.DocumentOp): WaveOperationResult {
        return when (operation.type) {
            DocumentOperationType.INSERT -> {
                val newContent = documentState.content.substring(0, operation.position) +
                               operation.content +
                               documentState.content.substring(operation.position)
                documentState.content = newContent
                operationHistory.add(operation)
                _updateFlow.emit(WaveUpdate.DocumentChanged(operation))
                WaveOperationResult.Success(operation)
            }
            DocumentOperationType.DELETE -> {
                val newContent = documentState.content.substring(0, operation.position) +
                               documentState.content.substring(operation.position + operation.content.length)
                documentState.content = newContent
                operationHistory.add(operation)
                _updateFlow.emit(WaveUpdate.DocumentChanged(operation))
                WaveOperationResult.Success(operation)
            }
            DocumentOperationType.ANNOTATE -> {
                // Apply annotations
                operationHistory.add(operation)
                _updateFlow.emit(WaveUpdate.DocumentChanged(operation))
                WaveOperationResult.Success(operation)
            }
        }
    }

    internal suspend fun applyParticipantOperation(operation: WaveOperation.ParticipantOp): WaveOperationResult {
        operationHistory.add(operation)
        _updateFlow.emit(WaveUpdate.ParticipantAction(operation))
        return WaveOperationResult.Success(operation)
    }
}

/**
 * Wave Participant for managing individual users
 */
class WaveParticipant(
    val id: String,
    val sessionId: String
) {
    internal val operationQueue = mutableListOf<WaveOperation>()
    internal val _operationFlow = MutableSharedFlow<WaveOperation>()
    val operationFlow: Flow<WaveOperation> = _operationFlow.asSharedFlow()

    fun sendOperation(operation: WaveOperation) {
        operationQueue.add(operation)
        _operationFlow.tryEmit(operation)
    }

    fun sendState(state: WaveDocumentState) {
        // Send initial state to participant
    }

    fun getOperationQueue(): List<WaveOperation> = operationQueue.toList()
}

/**
 * Operational Transform Engine for conflict resolution
 */
class OperationalTransformEngine {
    
    fun transform(operation: WaveOperation, history: List<WaveOperation>): WaveOperation {
        // Apply operational transformation based on operation history
        var transformedOp = operation
        
        history.forEach { historicalOp ->
            transformedOp = transformPair(transformedOp, historicalOp)
        }
        
        return transformedOp
    }
    
    internal fun transformPair(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        // Simplified transformation - in real implementation this would be more complex
        return when {
            op1 is WaveOperation.DocumentOp && op2 is WaveOperation.DocumentOp -> {
                transformDocumentOps(op1, op2)
            }
            else -> op1
        }
    }
    
    internal fun transformDocumentOps(op1: WaveOperation.DocumentOp, op2: WaveOperation.DocumentOp): WaveOperation.DocumentOp {
        // Adjust position based on previous operations
        val adjustedPosition = when {
            op2.type == DocumentOperationType.INSERT && op2.position < op1.position -> {
                op1.position + op2.content.length
            }
            op2.type == DocumentOperationType.DELETE && op2.position < op1.position -> {
                maxOf(0, op1.position - op2.content.length)
            }
            else -> op1.position
        }
        
        return op1.copy(position = adjustedPosition)
    }
}

/**
 * Wave Session Manager for coordinating multiple sessions
 */
class WaveSessionManager {
    internal val sessions = mutableMapOf<String, WaveSession>()
    
    fun createSession(sessionId: String, documentId: String): WaveSession {
        // Implementation would create and manage sessions
        return WaveSession(sessionId, documentId, OperationalTransformEngine())
    }
}

/**
 * Wavelet - Wave's document structure unit
 */
class Wavelet(
    val documentId: String,
    val sessionId: String
) {
    val operations = mutableListOf<WaveOperation>()
    val participants = mutableSetOf<String>()
    
    fun addOperation(operation: WaveOperation) {
        operations.add(operation)
    }
    
    fun addParticipant(participantId: String) {
        participants.add(participantId)
    }
}

// Core types

sealed class WaveOperation(val operationId: String, val timestamp: Long) {
    data class DocumentOp(
        override val operationId: String,
        val type: DocumentOperationType,
        val position: Int,
        val content: String,
        val annotations: Map<String, String>,
        override val timestamp: Long
    ) : WaveOperation(operationId, timestamp)
    
    data class ParticipantOp(
        override val operationId: String,
        val type: ParticipantOperationType,
        val participantId: String,
        val data: Map<String, Any?>,
        override val timestamp: Long
    ) : WaveOperation(operationId, timestamp)
}

enum class DocumentOperationType {
    INSERT, DELETE, ANNOTATE
}

enum class ParticipantOperationType {
    JOIN, LEAVE, CURSOR_MOVE, SELECTION_CHANGE
}

sealed class WaveOperationResult {
    data class Success(val operation: WaveOperation) : WaveOperationResult()
    data class Failure(val error: String) : WaveOperationResult()
}

sealed class WaveUpdate {
    data class DocumentChanged(val operation: WaveOperation.DocumentOp) : WaveUpdate()
    data class ParticipantJoined(val participantId: String) : WaveUpdate()
    data class ParticipantLeft(val participantId: String) : WaveUpdate()
    data class ParticipantAction(val operation: WaveOperation.ParticipantOp) : WaveUpdate()
    data class Error(val message: String) : WaveUpdate()
}

data class WaveDocumentState(
    val documentId: String,
    var content: String
) 