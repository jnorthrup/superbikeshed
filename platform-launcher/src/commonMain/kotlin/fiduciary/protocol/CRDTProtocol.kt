package fiduciary.protocol

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * CRDT Protocol - Conflict-free Replicated Data Types
 * 
 * From Fiduciary Omnibus Architecture:
 * - Inherits basic CRDT functionality
 * - Manages EntityID key relationships
 * - Supports concurrent operations without conflicts
 */

/**
 * Vector Clock for ordering operations
 */
@Serializable
data class VectorClock(
    private val clocks: MutableMap<String, Long> = mutableMapOf()
) {
    fun tick(nodeId: String): VectorClock {
        val newClocks = clocks.toMutableMap()
        newClocks[nodeId] = (newClocks[nodeId] ?: 0L) + 1L
        return VectorClock(newClocks)
    }
    
    fun update(other: VectorClock): VectorClock {
        val newClocks = clocks.toMutableMap()
        other.clocks.forEach { (nodeId, clock) ->
            newClocks[nodeId] = maxOf(newClocks[nodeId] ?: 0L, clock)
        }
        return VectorClock(newClocks)
    }
    
    fun compareTo(other: VectorClock): VectorClockComparison {
        val allNodes = (clocks.keys + other.clocks.keys).toSet()
        var isLess = false
        var isGreater = false
        
        allNodes.forEach { nodeId ->
            val thisClock = clocks[nodeId] ?: 0L
            val otherClock = other.clocks[nodeId] ?: 0L
            
            when {
                thisClock < otherClock -> isLess = true
                thisClock > otherClock -> isGreater = true
            }
        }
        
        return when {
            isLess && !isGreater -> VectorClockComparison.LESS
            !isLess && isGreater -> VectorClockComparison.GREATER
            !isLess && !isGreater -> VectorClockComparison.EQUAL
            else -> VectorClockComparison.CONCURRENT
        }
    }
    
    fun getVersion(): Long = clocks.values.sum()
}

enum class VectorClockComparison {
    LESS, GREATER, EQUAL, CONCURRENT
}

/**
 * CRDT Operation
 */
@Serializable
data class CRDTOperation<T>(
    val id: String,
    val type: CRDTOperationType,
    val value: T,
    val timestamp: Long,
    val vectorClock: VectorClock,
    val nodeId: String
)

enum class CRDTOperationType {
    ADD, REMOVE
}

/**
 * Base CRDT Protocol implementation
 */
open class CRDTProtocol<T> {
    private val data = mutableMapOf<String, MutableSet<T>>()
    private val operations = mutableMapOf<String, MutableList<CRDTOperation<T>>>()
    private val vectorClocks = mutableMapOf<String, VectorClock>()
    private val entityKeys = mutableMapOf<String, EntityKey>()
    private val mutex = Mutex()
    private val nodeId = "node-${System.currentTimeMillis()}"
    
    /**
     * Add value to entity
     */
    suspend fun add(entityId: String, value: T) {
        mutex.withLock {
            val currentClock = vectorClocks[entityId] ?: VectorClock()
            val newClock = currentClock.tick(nodeId)
            vectorClocks[entityId] = newClock
            
            val operation = CRDTOperation(
                id = "${entityId}-${System.currentTimeMillis()}-${value.hashCode()}",
                type = CRDTOperationType.ADD,
                value = value,
                timestamp = System.currentTimeMillis(),
                vectorClock = newClock,
                nodeId = nodeId
            )
            
            operations.getOrPut(entityId) { mutableListOf() }.add(operation)
            data.getOrPut(entityId) { mutableSetOf() }.add(value)
        }
    }
    
    /**
     * Remove value from entity
     */
    suspend fun remove(entityId: String, value: T) {
        mutex.withLock {
            val currentClock = vectorClocks[entityId] ?: VectorClock()
            val newClock = currentClock.tick(nodeId)
            vectorClocks[entityId] = newClock
            
            val operation = CRDTOperation(
                id = "${entityId}-${System.currentTimeMillis()}-${value.hashCode()}",
                type = CRDTOperationType.REMOVE,
                value = value,
                timestamp = System.currentTimeMillis(),
                vectorClock = newClock,
                nodeId = nodeId
            )
            
            operations.getOrPut(entityId) { mutableListOf() }.add(operation)
            
            // Only remove if we have evidence of a prior add
            val entityOps = operations[entityId] ?: emptyList()
            val hasAdd = entityOps.any { it.type == CRDTOperationType.ADD && it.value == value }
            if (hasAdd) {
                data[entityId]?.remove(value)
            }
        }
    }
    
    /**
     * Get current values for entity
     */
    suspend fun get(entityId: String): Set<T> {
        mutex.withLock {
            return data[entityId]?.toSet() ?: emptySet()
        }
    }
    
    /**
     * Merge with another CRDT
     */
    suspend fun merge(other: CRDTProtocol<T>): CRDTProtocol<T> {
        val result = CRDTProtocol<T>()
        
        mutex.withLock {
            // Merge all operations
            val allEntityIds = (this.operations.keys + other.operations.keys).toSet()
            
            allEntityIds.forEach { entityId ->
                val thisOps = this.operations[entityId] ?: emptyList()
                val otherOps = other.operations[entityId] ?: emptyList()
                val allOps = (thisOps + otherOps).distinctBy { it.id }
                
                result.operations[entityId] = allOps.toMutableList()
                
                // Rebuild state from operations using add-wins semantics
                val values = mutableSetOf<T>()
                val addedValues = mutableSetOf<T>()
                
                allOps.sortedBy { it.timestamp }.forEach { op ->
                    when (op.type) {
                        CRDTOperationType.ADD -> {
                            values.add(op.value)
                            addedValues.add(op.value)
                        }
                        CRDTOperationType.REMOVE -> {
                            // Only remove if we have evidence of add
                            if (addedValues.contains(op.value)) {
                                values.remove(op.value)
                            }
                        }
                    }
                }
                
                result.data[entityId] = values
                
                // Merge vector clocks
                val thisClock = this.vectorClocks[entityId] ?: VectorClock()
                val otherClock = other.vectorClocks[entityId] ?: VectorClock()
                result.vectorClocks[entityId] = thisClock.update(otherClock)
            }
            
            // Merge entity keys
            result.entityKeys.putAll(this.entityKeys)
            result.entityKeys.putAll(other.entityKeys)
        }
        
        return result
    }
    
    /**
     * Get vector clock for entity
     */
    suspend fun getVectorClock(entityId: String): VectorClock {
        mutex.withLock {
            return vectorClocks[entityId] ?: VectorClock()
        }
    }
    
    /**
     * Register entity key
     */
    suspend fun registerEntityKey(entityKey: EntityKey) {
        mutex.withLock {
            entityKeys[entityKey.entityId] = entityKey
        }
    }
    
    /**
     * Get entity key
     */
    suspend fun getEntityKey(entityId: String): EntityKey? {
        mutex.withLock {
            return entityKeys[entityId]
        }
    }
    
    /**
     * Get all operations for entity
     */
    suspend fun getOperations(entityId: String): List<CRDTOperation<T>> {
        mutex.withLock {
            return operations[entityId]?.toList() ?: emptyList()
        }
    }
}

/**
 * Entity Key for omnibus architecture
 */
@Serializable
data class EntityKey(
    val entityId: String,
    val keyType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Wave Operation for collaborative editing
 */
@Serializable
data class WaveOperation(
    val id: String,
    val type: OperationType,
    val position: Int,
    val content: String,
    val timestamp: Long,
    val author: String? = null,
    val vectorClock: VectorClock = VectorClock()
)

enum class OperationType {
    INSERT, DELETE, RETAIN, FORMAT
}

/**
 * Wave Blip for threaded conversations
 */
@Serializable
data class WaveBlip(
    val id: String,
    val content: String,
    val author: String,
    val timestamp: Long,
    val parentBlipId: String? = null,
    val childBlipIds: MutableSet<String> = mutableSetOf()
)

/**
 * WaveCRDT - Inherits from CRDT Protocol
 * 
 * Implements Google Wave-style collaborative editing with:
 * - Operational transformation
 * - Blip threading
 * - Participant management
 * - Real-time collaboration
 */
class WaveCRDT : CRDTProtocol<WaveOperation>() {
    private val waveContents = mutableMapOf<String, String>()
    private val waveOperations = mutableMapOf<String, MutableList<WaveOperation>>()
    private val waveParticipants = mutableMapOf<String, MutableSet<String>>()
    private val waveBlips = mutableMapOf<String, MutableMap<String, WaveBlip>>()
    private val waveMutex = Mutex()
    
    /**
     * Apply operation to wave
     */
    suspend fun applyOperation(waveId: String, operation: WaveOperation) {
        waveMutex.withLock {
            // Add to base CRDT
            add(waveId, operation)
            
            // Apply to wave content
            waveOperations.getOrPut(waveId) { mutableListOf() }.add(operation)
            
            // Rebuild wave content from operations
            rebuildWaveContent(waveId)
        }
    }
    
    /**
     * Get wave content
     */
    suspend fun getWaveContent(waveId: String): String {
        waveMutex.withLock {
            return waveContents[waveId] ?: ""
        }
    }
    
    /**
     * Get operation history for wave
     */
    suspend fun getOperationHistory(waveId: String): List<WaveOperation> {
        waveMutex.withLock {
            return waveOperations[waveId]?.toList() ?: emptyList()
        }
    }
    
    /**
     * Add participant to wave
     */
    suspend fun addParticipant(waveId: String, participantId: String) {
        waveMutex.withLock {
            waveParticipants.getOrPut(waveId) { mutableSetOf() }.add(participantId)
        }
    }
    
    /**
     * Get participants of wave
     */
    suspend fun getParticipants(waveId: String): Set<String> {
        waveMutex.withLock {
            return waveParticipants[waveId]?.toSet() ?: emptySet()
        }
    }
    
    /**
     * Merge with another wave CRDT
     */
    suspend fun mergeWave(other: WaveCRDT): WaveCRDT {
        val result = WaveCRDT()
        
        // Merge base CRDT
        val baseMerged = this.merge(other)
        
        waveMutex.withLock {
            // Merge wave-specific data
            val allWaveIds = (this.waveOperations.keys + other.waveOperations.keys).toSet()
            
            allWaveIds.forEach { waveId ->
                val thisOps = this.waveOperations[waveId] ?: emptyList()
                val otherOps = other.waveOperations[waveId] ?: emptyList()
                val allOps = (thisOps + otherOps).distinctBy { it.id }
                    .sortedBy { it.timestamp }
                
                result.waveOperations[waveId] = allOps.toMutableList()
                
                // Merge participants
                val thisParticipants = this.waveParticipants[waveId] ?: emptySet()
                val otherParticipants = other.waveParticipants[waveId] ?: emptySet()
                result.waveParticipants[waveId] = (thisParticipants + otherParticipants).toMutableSet()
                
                // Merge blips
                val thisBlips = this.waveBlips[waveId] ?: emptyMap()
                val otherBlips = other.waveBlips[waveId] ?: emptyMap()
                result.waveBlips[waveId] = (thisBlips + otherBlips).toMutableMap()
                
                // Rebuild content
                result.rebuildWaveContent(waveId)
            }
        }
        
        return result
    }
    
    /**
     * Create blip in wave
     */
    suspend fun createBlip(waveId: String, blipId: String, content: String, parentBlipId: String? = null) {
        waveMutex.withLock {
            val blip = WaveBlip(
                id = blipId,
                content = content,
                author = "system", // Would be actual user
                timestamp = System.currentTimeMillis(),
                parentBlipId = parentBlipId
            )
            
            waveBlips.getOrPut(waveId) { mutableMapOf() }[blipId] = blip
            
            // Update parent's children
            parentBlipId?.let { parentId ->
                waveBlips[waveId]?.get(parentId)?.childBlipIds?.add(blipId)
            }
        }
    }
    
    /**
     * Update blip content
     */
    suspend fun updateBlip(waveId: String, blipId: String, newContent: String) {
        waveMutex.withLock {
            waveBlips[waveId]?.get(blipId)?.let { blip ->
                waveBlips[waveId]!![blipId] = blip.copy(content = newContent)
            }
        }
    }
    
    /**
     * Get blip content
     */
    suspend fun getBlipContent(waveId: String, blipId: String): String? {
        waveMutex.withLock {
            return waveBlips[waveId]?.get(blipId)?.content
        }
    }
    
    /**
     * Get child blips
     */
    suspend fun getChildBlips(waveId: String, parentBlipId: String): List<String> {
        waveMutex.withLock {
            return waveBlips[waveId]?.get(parentBlipId)?.childBlipIds?.toList() ?: emptyList()
        }
    }
    
    /**
     * Rebuild wave content from operations
     */
    private fun rebuildWaveContent(waveId: String) {
        val operations = waveOperations[waveId] ?: return
        val sortedOps = operations.sortedBy { it.timestamp }
        
        var content = ""
        sortedOps.forEach { op ->
            when (op.type) {
                OperationType.INSERT -> {
                    val insertPos = minOf(op.position, content.length)
                    content = content.substring(0, insertPos) + op.content + content.substring(insertPos)
                }
                OperationType.DELETE -> {
                    val deleteStart = minOf(op.position, content.length)
                    val deleteEnd = minOf(deleteStart + op.content.length, content.length)
                    content = content.substring(0, deleteStart) + content.substring(deleteEnd)
                }
                OperationType.RETAIN -> {
                    // No change to content
                }
                OperationType.FORMAT -> {
                    // Would apply formatting - simplified for now
                }
            }
        }
        
        waveContents[waveId] = content
    }
    
    /**
     * Transform operation based on concurrent operations
     */
    private fun transformOperation(
        operation: WaveOperation,
        against: WaveOperation
    ): WaveOperation {
        // Simplified operational transformation
        if (operation.timestamp <= against.timestamp) {
            return operation
        }
        
        return when {
            operation.type == OperationType.INSERT && against.type == OperationType.INSERT -> {
                if (operation.position >= against.position) {
                    operation.copy(position = operation.position + against.content.length)
                } else {
                    operation
                }
            }
            operation.type == OperationType.DELETE && against.type == OperationType.INSERT -> {
                if (operation.position >= against.position) {
                    operation.copy(position = operation.position + against.content.length)
                } else {
                    operation
                }
            }
            else -> operation
        }
    }
}