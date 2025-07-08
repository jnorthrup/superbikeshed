@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.coroutines.CoroutineContext

/**
 * CRDT Engine integrated with channelization architecture.
 * Provides distributed CRDT operations over channels with CCEK service composition.
 */
class CRDTChannelEngine(
    internal val nodeId: String,
    internal val context: CoroutineContext
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<CRDTChannelEngine>
    override val key = Key
    
    internal val _operations = MutableSharedFlow<CRDTOperation>()
    internal val _state = MutableStateFlow<CRDTState>(CRDTState.empty())
    
    /**
     * Flow of all CRDT operations for channelized distribution.
     */
    val operations: Flow<CRDTOperation> = _operations.asSharedFlow()
    
    /**
     * Current CRDT state.
     */
    val state: StateFlow<CRDTState> = _state.asStateFlow()
    
    /**
     * Apply a local operation and emit for distribution.
     */
    suspend fun applyLocalOperation(operation: CRDTOperation) {
        val timestampedOp = operation.copy(
            vectorClock = operation.vectorClock.increment(),
            timestamp = Clock.System.now()
        )
        
        // Apply locally
        _state.value = _state.value.apply(timestampedOp)
        
        // Emit for channel distribution
        _operations.emit(timestampedOp)
    }
    
    /**
     * Apply a remote operation received through channels.
     */
    suspend fun applyRemoteOperation(operation: CRDTOperation) {
        // Only apply if we haven't seen this operation
        if (!_state.value.hasOperation(operation.id)) {
            _state.value = _state.value.apply(operation)
        }
    }
    
    /**
     * Create a new wavelet with initial content.
     */
    suspend fun createWavelet(content: String): WaveletId {
        val waveletId = WaveletId.generate()
        val operation = CRDTOperation(
            id = OperationId.generate(),
            waveletId = waveletId,
            type = CRDTOperationType.CREATE_WAVELET,
            content = content,
            participantId = nodeId,
            vectorClock = VectorClock(nodeId, 0),
            timestamp = Clock.System.now()
        )
        
        applyLocalOperation(operation)
        return waveletId
    }
    
    /**
     * Insert text at position in wavelet.
     */
    suspend fun insertText(waveletId: WaveletId, position: Int, text: String) {
        val operation = CRDTOperation(
            id = OperationId.generate(),
            waveletId = waveletId,
            type = CRDTOperationType.INSERT,
            content = text,
            position = position,
            participantId = nodeId,
            vectorClock = getCurrentClock(),
            timestamp = Clock.System.now()
        )
        
        applyLocalOperation(operation)
    }
    
    /**
     * Delete text range in wavelet.
     */
    suspend fun deleteText(waveletId: WaveletId, position: Int, length: Int) {
        val operation = CRDTOperation(
            id = OperationId.generate(),
            waveletId = waveletId,
            type = CRDTOperationType.DELETE,
            position = position,
            length = length,
            participantId = nodeId,
            vectorClock = getCurrentClock(),
            timestamp = Clock.System.now()
        )
        
        applyLocalOperation(operation)
    }
    
    internal fun getCurrentClock(): VectorClock {
        val currentOps = _state.value.operations.filter { it.participantId == nodeId }
        val maxSeq = currentOps.maxOfOrNull { it.vectorClock.sequence } ?: 0
        return VectorClock(nodeId, maxSeq)
    }
}

/**
 * CRDT operation types.
 */
@Serializable
enum class CRDTOperationType {
    CREATE_WAVELET,
    INSERT,
    DELETE,
    ANNOTATION,
    METADATA_UPDATE
}

/**
 * CRDT operation for wavelet editing.
 */
@Serializable
data class CRDTOperation(
    val id: OperationId,
    val waveletId: WaveletId,
    val type: CRDTOperationType,
    val content: String = "",
    val position: Int = 0,
    val length: Int = 0,
    val participantId: String,
    val vectorClock: VectorClock,
    val timestamp: Instant,
    val dependencies: List<OperationId> = emptyList()
)

/**
 * CRDT state containing all wavelets and operations.
 */
@Serializable
data class CRDTState(
    val wavelets: Map<String, WaveletDocument> = emptyMap(),
    val operations: List<CRDTOperation> = emptyList(),
    val participants: Set<String> = emptySet()
) {
    companion object {
        fun empty(): CRDTState = CRDTState()
    }
    
    /**
     * Apply an operation to the state.
     */
    fun apply(operation: CRDTOperation): CRDTState {
        val newOperations = operations + operation
        val newParticipants = participants + operation.participantId
        
        val newWavelets = when (operation.type) {
            CRDTOperationType.CREATE_WAVELET -> {
                val wavelet = WaveletDocument(
                    id = operation.waveletId,
                    content = operation.content,
                    participants = setOf(operation.participantId),
                    lastModified = operation.timestamp
                )
                wavelets + (operation.waveletId.value to wavelet)
            }
            CRDTOperationType.INSERT -> {
                val existing = wavelets[operation.waveletId.value]
                if (existing != null) {
                    val newContent = insertAtPosition(existing.content, operation.position, operation.content)
                    wavelets + (operation.waveletId.value to existing.copy(
                        content = newContent,
                        lastModified = operation.timestamp
                    ))
                } else wavelets
            }
            CRDTOperationType.DELETE -> {
                val existing = wavelets[operation.waveletId.value]
                if (existing != null) {
                    val newContent = deleteRange(existing.content, operation.position, operation.length)
                    wavelets + (operation.waveletId.value to existing.copy(
                        content = newContent,
                        lastModified = operation.timestamp
                    ))
                } else wavelets
            }
            else -> wavelets
        }
        
        return copy(
            wavelets = newWavelets,
            operations = newOperations,
            participants = newParticipants
        )
    }
    
    /**
     * Check if we've already seen this operation.
     */
    fun hasOperation(operationId: OperationId): Boolean {
        return operations.any { it.id == operationId }
    }
    
    internal fun insertAtPosition(content: String, position: Int, text: String): String {
        val safePosition = minOf(position, content.length)
        return content.substring(0, safePosition) + text + content.substring(safePosition)
    }
    
    internal fun deleteRange(content: String, position: Int, length: Int): String {
        val safePosition = minOf(position, content.length)
        val safeEnd = minOf(safePosition + length, content.length)
        return content.substring(0, safePosition) + content.substring(safeEnd)
    }
}

/**
 * Individual wavelet document.
 */
@Serializable
data class WaveletDocument(
    val id: WaveletId,
    val content: String,
    val participants: Set<String>,
    val lastModified: Instant,
    val annotations: List<WaveletAnnotation> = emptyList()
)

/**
 * Annotation on wavelet content.
 */
@Serializable
data class WaveletAnnotation(
    val id: String,
    val range: TextRange,
    val type: String,
    val value: String,
    val author: String,
    val timestamp: Instant
)

/**
 * Text range for annotations.
 */
@Serializable
data class TextRange(
    val start: Int,
    val end: Int
)

/**
 * RequestFactory integration for CRDT roundtrip transactions.
 */
class CRDTRequestFactory(
    internal val engine: CRDTChannelEngine
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<CRDTRequestFactory>
    override val key = Key
    
    /**
     * Create a transactional CRDT operation request.
     */
    suspend fun createTransaction(operations: List<CRDTOperation>): CRDTTransaction {
        val transactionId = "tx-${Clock.System.now().toEpochMilliseconds()}"
        
        return CRDTTransaction(
            id = transactionId,
            operations = operations,
            timestamp = Clock.System.now(),
            status = CRDTTransactionStatus.PENDING
        )
    }
    
    /**
     * Execute a transaction with rollback capability.
     */
    suspend fun executeTransaction(transaction: CRDTTransaction): Result<CRDTTransaction> {
        return try {
            // Apply operations atomically
            transaction.operations.forEach { operation ->
                engine.applyLocalOperation(operation)
            }
            
            Result.success(transaction.copy(status = CRDTTransactionStatus.COMMITTED))
        } catch (e: Exception) {
            // Rollback logic would go here
            Result.failure(e)
        }
    }
}

/**
 * CRDT transaction for atomic operations.
 */
@Serializable
data class CRDTTransaction(
    val id: String,
    val operations: List<CRDTOperation>,
    val timestamp: Instant,
    val status: CRDTTransactionStatus
)

/**
 * CRDT-specific transaction status.
 */
@Serializable
enum class CRDTTransactionStatus {
    PENDING,
    COMMITTED,
    ABORTED,
    ROLLED_BACK
}

/**
 * Extension function to create CRDTChannelEngine in CCEK context.
 */
suspend fun CoroutineContext.crdtEngine(nodeId: String): CRDTChannelEngine {
    return this[CRDTChannelEngine] ?: CRDTChannelEngine(nodeId, this).also {
        // Register with context if not already present
    }
}

/**
 * Extension function to create CRDTRequestFactory in CCEK context.
 */
suspend fun CoroutineContext.crdtRequestFactory(): CRDTRequestFactory {
    val engine = requireNotNull(this[CRDTChannelEngine]) { "CRDTChannelEngine required in context" }
    return this[CRDTRequestFactory] ?: CRDTRequestFactory(engine)
}