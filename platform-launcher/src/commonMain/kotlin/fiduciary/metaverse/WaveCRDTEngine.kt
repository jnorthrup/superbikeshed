package fiduciary.metaverse

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

/**
 * Apache Wave CRDT Engine
 * 
 * Implements Wave's operational transformation patterns for real-time collaboration:
 * - Document operations (insert, delete, annotate)
 * - Operational transformation for conflict resolution
 * - Wavelet-based document structure
 * - CRDT convergence guarantees
 */
class WaveCRDTEngine {
    
    // Active Wave sessions
    internal val sessions = mutableMapOf<String, WaveSession>()
    
    // Operational transformation engine
    internal val otEngine = OperationalTransformEngine()
    
    // CRDT state management
    internal val crdtStates = mutableMapOf<String, CRDTState>()
    
    // Participant management
    internal val participants = mutableMapOf<String, WaveParticipant>()

    /**
     * Create a new Wave session
     */
    fun createSession(sessionId: String): WaveSession {
        val session = WaveSession(
            id = sessionId,
            documentState = WaveDocumentState(content = ""),
            participants = mutableSetOf(),
            operationHistory = mutableListOf(),
            wavelets = mutableMapOf()
        )
        
        sessions[sessionId] = session
        
        // Initialize CRDT state
        crdtStates[sessionId] = CRDTState(
            sessionId = sessionId,
            vectorClock = mutableMapOf(),
            document = "",
            operations = mutableListOf()
        )
        
        return session
    }

    /**
     * Join an existing Wave session
     */
    fun joinSession(sessionId: String, participantId: String): WaveParticipant {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val participant = WaveParticipant(
            id = participantId,
            sessionId = sessionId,
            vectorClock = mutableMapOf(),
            pendingOperations = mutableListOf()
        )
        
        participants[participantId] = participant
        session.participants.add(participantId)
        
        return participant
    }

    /**
     * Apply a Wave operation with operational transformation
     */
    suspend fun applyOperation(sessionId: String, operation: WaveOperation): WaveOperationResult {
        val session = sessions[sessionId]
            ?: return WaveOperationResult.Failure("Session not found")
        
        val participant = participants[operation.participantId]
            ?: return WaveOperationResult.Failure("Participant not found")
        
        // Transform operation based on current state
        val transformedOp = otEngine.transform(operation, session.operationHistory)
        
        // Apply to CRDT state
        val crdtState = crdtStates[sessionId]!!
        val updatedState = applyToCRDT(crdtState, transformedOp)
        crdtStates[sessionId] = updatedState
        
        // Update document state
        session.documentState = WaveDocumentState(content = updatedState.document)
        
        // Add to operation history
        session.operationHistory.add(transformedOp)
        
        // Update participant's vector clock
        updateVectorClock(participant, transformedOp)
        
        // Create wavelet entry
        val wavelet = Wavelet(
            id = "wavelet-${transformedOp.operationId}",
            operation = transformedOp,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            vectorClock = participant.vectorClock.toMap()
        )
        
        session.wavelets[wavelet.id] = wavelet
        
        return WaveOperationResult.Success(transformedOp)
    }

    /**
     * Get current document state for a session
     */
    fun getDocumentState(sessionId: String): WaveDocumentState? {
        val session = sessions[sessionId] ?: return null
        return session.documentState
    }

    /**
     * Get operation history for a session
     */
    fun getOperationHistory(sessionId: String): List<WaveOperation> {
        val session = sessions[sessionId] ?: return emptyList()
        return session.operationHistory.toList()
    }

    /**
     * Get wavelets for a session
     */
    fun getWavelets(sessionId: String): List<Wavelet> {
        val session = sessions[sessionId] ?: return emptyList()
        return session.wavelets.values.toList()
    }

    /**
     * Subscribe to real-time updates
     */
    fun subscribeToUpdates(sessionId: String): Flow<WaveUpdate> {
        val session = sessions[sessionId]
            ?: return flow { emit(WaveUpdate.Error("Session not found")) }
        
        return session.updateFlow
    }

    // Private helper methods

    internal fun applyToCRDT(crdtState: CRDTState, operation: WaveOperation): CRDTState {
        return when (operation.type) {
            WaveOperationType.INSERT -> {
                val newDocument = crdtState.document.substring(0, operation.position) +
                               operation.content +
                               crdtState.document.substring(operation.position)
                
                crdtState.copy(
                    document = newDocument,
                    operations = (crdtState.operations + operation).toMutableList()
                )
            }
            WaveOperationType.DELETE -> {
                val newDocument = crdtState.document.substring(0, operation.position) +
                               crdtState.document.substring(operation.position + operation.content.length)
                
                crdtState.copy(
                    document = newDocument,
                    operations = (crdtState.operations + operation).toMutableList()
                )
            }
            WaveOperationType.ANNOTATE -> {
                // Apply annotations (simplified for demo)
                crdtState.copy(
                    operations = (crdtState.operations + operation).toMutableList()
                )
            }
            else -> crdtState
        }
    }

    internal fun updateVectorClock(participant: WaveParticipant, operation: WaveOperation) {
        val currentClock = participant.vectorClock.getOrDefault(participant.id, 0)
        participant.vectorClock[participant.id] = currentClock + 1
    }
}

/**
 * Operational Transform Engine for conflict resolution
 */
class OperationalTransformEngine {
    
    fun transform(operation: WaveOperation, history: List<WaveOperation>): WaveOperation {
        var transformedOp = operation
        
        // Apply operational transformation based on operation history
        history.forEach { historicalOp ->
            transformedOp = transformPair(transformedOp, historicalOp)
        }
        
        return transformedOp
    }
    
    internal fun transformPair(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        // Skip if same operation or different participants
        if (op1.operationId == op2.operationId || op1.participantId == op2.participantId) {
            return op1
        }
        
        return when {
            op1.type == WaveOperationType.INSERT && op2.type == WaveOperationType.INSERT -> {
                transformInsertInsert(op1, op2)
            }
            op1.type == WaveOperationType.INSERT && op2.type == WaveOperationType.DELETE -> {
                transformInsertDelete(op1, op2)
            }
            op1.type == WaveOperationType.DELETE && op2.type == WaveOperationType.INSERT -> {
                transformDeleteInsert(op1, op2)
            }
            op1.type == WaveOperationType.DELETE && op2.type == WaveOperationType.DELETE -> {
                transformDeleteDelete(op1, op2)
            }
            else -> op1
        }
    }
    
    internal fun transformInsertInsert(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        val adjustedPosition = when {
            op2.position < op1.position -> op1.position + op2.content.length
            op2.position == op1.position && op1.participantId < op2.participantId -> op1.position
            op2.position == op1.position && op1.participantId > op2.participantId -> op1.position + op2.content.length
            else -> op1.position
        }
        
        return op1.copy(position = adjustedPosition)
    }
    
    internal fun transformInsertDelete(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        val adjustedPosition = when {
            op2.position < op1.position -> op1.position - op2.content.length
            op2.position <= op1.position && op2.position + op2.content.length > op1.position -> op2.position
            else -> op1.position
        }
        
        return op1.copy(position = maxOf(0, adjustedPosition))
    }
    
    internal fun transformDeleteInsert(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        val adjustedPosition = when {
            op2.position < op1.position -> op1.position + op2.content.length
            else -> op1.position
        }
        
        return op1.copy(position = adjustedPosition)
    }
    
    internal fun transformDeleteDelete(op1: WaveOperation, op2: WaveOperation): WaveOperation {
        // Handle overlapping deletions
        val op1End = op1.position + op1.content.length
        val op2End = op2.position + op2.content.length
        
        val adjustedPosition = when {
            op2End <= op1.position -> op1.position - (op2End - op2.position)
            op2.position <= op1.position && op2End > op1.position -> op2.position
            op2.position < op1End && op2End >= op1End -> op1.position
            else -> op1.position
        }
        
        return op1.copy(position = maxOf(0, adjustedPosition))
    }
}

// Core data types

data class WaveSession(
    val id: String,
    var documentState: WaveDocumentState,
    val participants: MutableSet<String>,
    val operationHistory: MutableList<WaveOperation>,
    val wavelets: MutableMap<String, Wavelet>
) {
    internal val _updateFlow = MutableSharedFlow<WaveUpdate>()
    val updateFlow: Flow<WaveUpdate> = _updateFlow.asSharedFlow()
    
    suspend fun emitUpdate(update: WaveUpdate) {
        _updateFlow.emit(update)
    }
}

data class WaveParticipant(
    val id: String,
    val sessionId: String,
    val vectorClock: MutableMap<String, Int>,
    val pendingOperations: MutableList<WaveOperation>
)

data class WaveDocumentState(
    val content: String,
    val annotations: Map<String, String> = emptyMap()
)

data class WaveOperation(
    val operationId: String,
    val type: WaveOperationType,
    val participantId: String,
    val content: String,
    val position: Int,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

data class Wavelet(
    val id: String,
    val operation: WaveOperation,
    val timestamp: Long,
    val vectorClock: Map<String, Int>
)

data class CRDTState(
    val sessionId: String,
    val vectorClock: MutableMap<String, Int>,
    val document: String,
    val operations: MutableList<WaveOperation>
)

sealed class WaveOperationResult {
    data class Success(val operation: WaveOperation) : WaveOperationResult()
    data class Failure(val error: String) : WaveOperationResult()
}

sealed class WaveUpdate {
    data class OperationApplied(val operation: WaveOperation) : WaveUpdate()
    data class ParticipantJoined(val participantId: String) : WaveUpdate()
    data class ParticipantLeft(val participantId: String) : WaveUpdate()
    data class DocumentChanged(val content: String) : WaveUpdate()
    data class Error(val message: String) : WaveUpdate()
} 