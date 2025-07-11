package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*

/**
 * Wave CRDT implementation with full operational transformation,
 * convergence guarantees, and distributed operation support.
 * 
 * Maps Wave operations to TrikeShed's Indexed<T> for efficient columnar processing.
 */

@Serializable
data class WaveOperationId(val value: String) {
    companion object {
        fun generate(): WaveOperationId = WaveOperationId(
            "${System.currentTimeMillis()}-${(0..999999).random()}"
        )
    }
}

@Serializable
data class ParticipantId(val value: String)

@Serializable
data class WaveletId(val value: String)

@Serializable
data class DocumentId(val value: String)

/**
 * Wavelet - The fundamental unit of Wave collaboration.
 * Contains operations and participant information.
 */
@Serializable
data class Wavelet(
    val id: WaveletId,
    val documentId: DocumentId,
    val participants: Indexed<ParticipantId>,
    val operations: Indexed<WaveOperationData>,
    val version: Long,
    val lastModified: Instant = Clock.System.now()
) {
    fun addOperation(op: WaveOperationData): Wavelet = copy(
        operations = (operations.a + 1) j { i: Int ->
            if (i < operations.a) operations.b(i) else op
        },
        version = version + 1,
        lastModified = Clock.System.now()
    )
    
    fun withParticipant(participantId: ParticipantId): Wavelet = 
        if (participants.toList().contains(participantId)) this
        else copy(
            participants = (participants.a + 1) j { i: Int ->
                if (i < participants.a) participants.b(i) else participantId
            }
        )
}

/**
 * Wave operation data with metadata for distributed synchronization
 */
@Serializable
data class WaveOperationData(
    val id: WaveOperationId,
    val participantId: ParticipantId,
    val operation: SerializedWaveOp,
    val timestamp: Instant,
    val vectorClock: Map<String, Long>,
    val dependencies: Indexed<WaveOperationId>
)

/**
 * Serializable representation of Wave operations
 */
@Serializable
sealed class SerializedWaveOp {
    @Serializable
    @SerialName("retain")
    data class Retain(val count: Int) : SerializedWaveOp()
    
    @Serializable
    @SerialName("insert")
    data class Insert(val text: String, val position: Int) : SerializedWaveOp()
    
    @Serializable
    @SerialName("delete")
    data class Delete(val text: String, val position: Int) : SerializedWaveOp()
    
    @Serializable
    @SerialName("elementStart")
    data class ElementStart(val type: String, val attributes: Map<String, String>) : SerializedWaveOp()
    
    @Serializable
    @SerialName("elementEnd")
    data class ElementEnd(val type: String) : SerializedWaveOp()
    
    @Serializable
    @SerialName("annotationBoundary")
    data class AnnotationBoundary(
        val changes: Map<String, String?>,
        val position: Int
    ) : SerializedWaveOp()
    
    fun toWaveOp(): WaveDocumentOp = when (this) {
        is Retain -> WaveDocumentOp.Retain(count)
        is Insert -> WaveDocumentOp.Insert(text, position)
        is Delete -> WaveDocumentOp.Delete(text, position)
        is ElementStart -> WaveDocumentOp.ElementStart(type, attributes)
        is ElementEnd -> WaveDocumentOp.ElementEnd(type)
        is AnnotationBoundary -> WaveDocumentOp.AnnotationBoundary(changes, position)
    }
}

/**
 * Wave Document - Complete document with all wavelets
 */
data class WaveDocument(
    val id: DocumentId,
    val wavelets: Indexed<Wavelet>,
    val state: WaveDocumentState,
    val metadata: Map<String, String> = emptyMap()
) {
    fun getWavelet(id: WaveletId): Wavelet? = 
        wavelets.toList().find { it.id == id }
    
    fun addWavelet(wavelet: Wavelet): WaveDocument = copy(
        wavelets = (wavelets.a + 1) j { i: Int ->
            if (i < wavelets.a) wavelets.b(i) else wavelet
        }
    )
    
    fun updateState(newState: WaveDocumentState): WaveDocument = 
        copy(state = newState)
}

/**
 * Vector clock for distributed causality tracking
 */
class VectorClock(
    internal val participantId: ParticipantId,
    internal val clocks: MutableMap<String, Long> = mutableMapOf()
) {
    fun tick(): VectorClock {
        clocks[participantId.value] = (clocks[participantId.value] ?: 0) + 1
        return this
    }
    
    fun update(other: Map<String, Long>): VectorClock {
        other.forEach { (pid, clock) ->
            clocks[pid] = maxOf(clocks[pid] ?: 0, clock)
        }
        return this
    }
    
    fun happensBefore(other: Map<String, Long>): Boolean {
        return clocks.all { (pid, clock) ->
            clock <= (other[pid] ?: 0)
        }
    }
    
    fun concurrent(other: Map<String, Long>): Boolean {
        return !happensBefore(other) && !other.all { (pid, clock) ->
            clock <= (clocks[pid] ?: 0)
        }
    }
    
    fun toMap(): Map<String, Long> = clocks.toMap()
}

/**
 * Wave CRDT Engine with full distributed support
 */
class WaveCRDTEngine {
    internal val documents = mutableMapOf<DocumentId, WaveDocument>()
    internal val convergence = WaveCRDTConvergence()
    internal val operationQueues = mutableMapOf<DocumentId, MutableList<WaveOperationData>>()
    
    fun createDocument(id: DocumentId = DocumentId("doc-${System.currentTimeMillis()}")): WaveDocument {
        val doc = WaveDocument(
            id = id,
            wavelets = 0 j { throw IndexOutOfBoundsException() },
            state = WaveDocumentState("")
        )
        documents[id] = doc
        operationQueues[id] = mutableListOf()
        return doc
    }
    
    fun createWavelet(
        documentId: DocumentId,
        waveletId: WaveletId = WaveletId("wavelet-${System.currentTimeMillis()}")
    ): Wavelet? {
        val doc = documents[documentId] ?: return null
        val wavelet = Wavelet(
            id = waveletId,
            documentId = documentId,
            participants = 0 j { throw IndexOutOfBoundsException() },
            operations = 0 j { throw IndexOutOfBoundsException() },
            version = 0
        )
        documents[documentId] = doc.addWavelet(wavelet)
        return wavelet
    }
    
    suspend fun applyOperation(
        documentId: DocumentId,
        waveletId: WaveletId,
        participantId: ParticipantId,
        operation: WaveDocumentOp,
        vectorClock: VectorClock
    ): Either<WaveOperationData, OperationException> {
        val doc = documents[documentId] 
            ?: return Either.Right(OperationException("Document not found"))
        val wavelet = doc.getWavelet(waveletId)
            ?: return Either.Right(OperationException("Wavelet not found"))
        
        val opData = WaveOperationData(
            id = WaveOperationId.generate(),
            participantId = participantId,
            operation = operation.toSerializable(),
            timestamp = Clock.System.now(),
            vectorClock = vectorClock.tick().toMap(),
            dependencies = findDependencies(documentId, vectorClock.toMap())
        )
        
        val transformedOp = transformAgainstConcurrent(
            operation,
            documentId,
            opData.vectorClock
        )
        
        return when (val result = transformedOp.apply(doc.state)) {
            is Either.Left -> {
                val updatedWavelet = wavelet
                    .withParticipant(participantId)
                    .addOperation(opData)
                val updatedDoc = doc
                    .updateState(result.value)
                    .addWavelet(updatedWavelet)
                documents[documentId] = updatedDoc
                Either.Left(opData)
            }
            is Either.Right -> Either.Right(result.value)
        }
    }
    
    fun getDocument(id: DocumentId): WaveDocument? = documents[id]
    
    fun getDocumentState(id: DocumentId): WaveDocumentState? = 
        documents[id]?.state
    
    internal fun transformAgainstConcurrent(
        operation: WaveDocumentOp,
        documentId: DocumentId,
        vectorClock: Map<String, Long>
    ): WaveDocumentOp {
        val queue = operationQueues[documentId] ?: return operation
        val concurrent = queue.filter { op ->
            val opClock = op.vectorClock
            !vectorClock.all { (pid, clock) -> clock >= (opClock[pid] ?: 0) } &&
            !opClock.all { (pid, clock) -> clock >= (vectorClock[pid] ?: 0) }
        }
        
        return concurrent.fold(operation) { acc, opData ->
            val transform = convergence.transform(acc, opData.operation.toWaveOp())
            transform.a as WaveDocumentOp
        }
    }
    
    internal fun findDependencies(
        documentId: DocumentId,
        vectorClock: Map<String, Long>
    ): Indexed<WaveOperationId> {
        val queue = operationQueues[documentId] ?: return 0 j { throw IndexOutOfBoundsException() }
        val deps = queue.filter { op ->
            op.vectorClock.all { (pid, clock) -> 
                clock <= (vectorClock[pid] ?: 0)
            }
        }.map { it.id }
        
        return deps.size j { i: Int -> deps[i] }
    }
    
    suspend fun mergeWavelets(
        documentId: DocumentId,
        local: WaveletId,
        remote: WaveletId
    ): Either<Wavelet, OperationException> {
        val doc = documents[documentId]
            ?: return Either.Right(OperationException("Document not found"))
        val localWavelet = doc.getWavelet(local)
            ?: return Either.Right(OperationException("Local wavelet not found"))
        val remoteWavelet = doc.getWavelet(remote)
            ?: return Either.Right(OperationException("Remote wavelet not found"))
        
        val allOps = mergeOperations(localWavelet.operations, remoteWavelet.operations)
        val allParticipants = mergeParticipants(
            localWavelet.participants, 
            remoteWavelet.participants
        )
        
        val mergedWavelet = Wavelet(
            id = WaveletId("merged-${local.value}-${remote.value}"),
            documentId = documentId,
            participants = allParticipants,
            operations = allOps,
            version = maxOf(localWavelet.version, remoteWavelet.version) + 1
        )
        
        val updatedState = applyOperationsToState(
            doc.state,
            allOps.toList().map { it.operation.toWaveOp() }
        )
        
        return when (updatedState) {
            is Either.Left -> {
                documents[documentId] = doc
                    .addWavelet(mergedWavelet)
                    .updateState(updatedState.value)
                Either.Left(mergedWavelet)
            }
            is Either.Right -> Either.Right(updatedState.value)
        }
    }
    
    internal fun mergeOperations(
        ops1: Indexed<WaveOperationData>,
        ops2: Indexed<WaveOperationData>
    ): Indexed<WaveOperationData> {
        val allOps = (ops1.toList() + ops2.toList())
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
        return allOps.size j { i: Int -> allOps[i] }
    }
    
    internal fun mergeParticipants(
        p1: Indexed<ParticipantId>,
        p2: Indexed<ParticipantId>
    ): Indexed<ParticipantId> {
        val allParticipants = (p1.toList() + p2.toList()).distinct()
        return allParticipants.size j { i: Int -> allParticipants[i] }
    }
    
    internal fun applyOperationsToState(
        initialState: WaveDocumentState,
        operations: List<WaveDocumentOp>
    ): Either<WaveDocumentState, OperationException> {
        return operations.fold(Either.Left(initialState) as Either<WaveDocumentState, OperationException>) { acc, op ->
            when (acc) {
                is Either.Left -> op.apply(acc.value)
                is Either.Right -> acc
            }
        }
    }
}

/**
 * Extension to convert WaveDocumentOp to SerializedWaveOp
 */
fun WaveDocumentOp.toSerializable(): SerializedWaveOp = when (this) {
    is WaveDocumentOp.Retain -> SerializedWaveOp.Retain(count)
    is WaveDocumentOp.Insert -> SerializedWaveOp.Insert(text, position)
    is WaveDocumentOp.Delete -> SerializedWaveOp.Delete(text, position)
    is WaveDocumentOp.ElementStart -> SerializedWaveOp.ElementStart(type, attributes)
    is WaveDocumentOp.ElementEnd -> SerializedWaveOp.ElementEnd(type)
    is WaveDocumentOp.AnnotationBoundary -> SerializedWaveOp.AnnotationBoundary(changes, position)
}

/**
 * Extension functions for Indexed operations
 */
fun <T> Indexed<T>.toList(): List<T> = (0 until a).map { b(it) }

fun <T> Indexed<T>.flatMap(f: (T) -> List<T>): List<T> = 
    toList().flatMap(f)

fun <T> List<T>.toIdx(): Indexed<T> = size j { i: Int -> this[i] }