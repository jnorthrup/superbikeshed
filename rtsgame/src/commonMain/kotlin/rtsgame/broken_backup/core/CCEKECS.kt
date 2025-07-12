package rtsgame.core

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*

/**
 * CCEK-Integrated ECS for RTS Game with Wave/CRDT Synergy
 * 
 * Uses existing context key APIs to shrink code and integrate with:
 * - Wave operational transformation for real-time collaboration
 * - CRDT for distributed state management
 * - CCEK for execution orchestration
 */

// === CCEK CONTEXT ELEMENTS FOR RTS ECS ===

/**
 * RTS Game context element - provides game state and ECS operations
 */
data class RTSGameContext(
    val world: ECSWorld,
    val sessionId: String,
    val waveEngine: WaveCRDTEngine? = null,
    val crdtRegistry: CRDTRegistry? = null
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RTSGameContext>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Entity operation context - tracks entity changes for Wave/CRDT sync
 */
data class EntityOperationContext(
    val operationId: String,
    val entityId: EntityId,
    val operationType: EntityOperationType,
    val timestamp: Instant = Clock.System.now()
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<EntityOperationContext>
    override val key: CoroutineContext.Key<*> = Key
    
    enum class EntityOperationType {
        CREATE, UPDATE, DELETE, COMPONENT_ADD, COMPONENT_REMOVE
    }
}

/**
 * Component change context - tracks component modifications
 */
data class ComponentChangeContext(
    val componentTypeId: ComponentTypeId,
    val changeType: ComponentChangeType,
    val oldValue: Any? = null,
    val newValue: Any? = null
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ComponentChangeContext>
    override val key: CoroutineContext.Key<*> = Key
    
    enum class ComponentChangeType {
        ADD, REMOVE, UPDATE
    }
}

// === WAVE/CRDT INTEGRATION TYPES ===

/**
 * Wave operation for entity changes
 */
@Serializable
data class EntityWaveOperation(
    val operationId: String,
    val entityId: EntityId,
    val operationType: EntityOperationContext.EntityOperationType,
    val componentChanges: Indexed<ComponentWaveChange>,
    val timestamp: Instant,
    val participantId: String
)

@Serializable
data class ComponentWaveChange(
    val componentTypeId: ComponentTypeId,
    val changeType: ComponentChangeContext.ComponentChangeType,
    val serializedData: String // JSON serialized component data
)

/**
 * CRDT entity state for distributed synchronization
 */
@Serializable
data class CRDTEntityState(
    val entityId: EntityId,
    val components: Indexed<CRDTComponentState>,
    val version: Long,
    val lastModified: Instant
)

@Serializable
data class CRDTComponentState(
    val typeId: ComponentTypeId,
    val data: String, // JSON serialized component
    val version: Long
)

// === CCEK-ENHANCED ECS WORLD ===

/**
 * CCEK-enhanced ECS World with Wave/CRDT integration
 */
class CCEKECSWorld(
    internal val sessionId: String,
    internal val waveEngine: WaveCRDTEngine? = null,
    internal val crdtRegistry: CRDTRegistry? = null
) {
    internal val world = ECSWorld()
    internal val operationHistory = mutableListOf<EntityWaveOperation>()
    internal val _operationFlow = MutableSharedFlow<EntityWaveOperation>()
    val operationFlow: Flow<EntityWaveOperation> = _operationFlow.asSharedFlow()
    
    /**
     * Create entity with CCEK context
     */
    suspend fun createEntity(): EntityId = withCCEKContext {
        val entityId = world.createEntity()
        
        // Create Wave operation
        val operation = EntityWaveOperation(
            operationId = generateOperationId(),
            entityId = entityId,
            operationType = EntityOperationContext.EntityOperationType.CREATE,
            componentChanges = 0 j { throw IndexOutOfBoundsException() },
            timestamp = Clock.System.now(),
            participantId = getCurrentParticipantId()
        )
        
        // Emit operation for Wave/CRDT sync
        emitOperation(operation)
        
        entityId
    }
    
    /**
     * Add component with CCEK context and Wave sync
     */
    suspend fun <T : Component> addComponent(
        entityId: EntityId, 
        component: T
    ) = withCCEKContext {
        world.addComponent(entityId, component)
        
        // Create component change operation
        val change = ComponentWaveChange(
            componentTypeId = component.typeId,
            changeType = ComponentChangeContext.ComponentChangeType.ADD,
            serializedData = serializeComponent(component)
        )
        
        val operation = EntityWaveOperation(
            operationId = generateOperationId(),
            entityId = entityId,
            operationType = EntityOperationContext.EntityOperationType.COMPONENT_ADD,
            componentChanges = 1 j { change },
            timestamp = Clock.System.now(),
            participantId = getCurrentParticipantId()
        )
        
        emitOperation(operation)
    }
    
    /**
     * Update component with Wave operational transformation
     */
    suspend fun <T : Component> updateComponent(
        entityId: EntityId,
        typeId: ComponentTypeId,
        update: (T) -> T
    ) = withCCEKContext {
        val oldComponent = world.getComponent<T>(entityId, typeId) ?: return@withCCEKContext
        val newComponent = update(oldComponent)
        
        world.addComponent(entityId, newComponent)
        
        val change = ComponentWaveChange(
            componentTypeId = typeId,
            changeType = ComponentChangeContext.ComponentChangeType.UPDATE,
            serializedData = serializeComponent(newComponent)
        )
        
        val operation = EntityWaveOperation(
            operationId = generateOperationId(),
            entityId = entityId,
            operationType = EntityOperationContext.EntityOperationType.UPDATE,
            componentChanges = 1 j { change },
            timestamp = Clock.System.now(),
            participantId = getCurrentParticipantId()
        )
        
        emitOperation(operation)
    }
    
    /**
     * Query entities with CCEK context
     */
    suspend fun query(vararg componentTypes: ComponentTypeId): Indexed<EntityId> = 
        withCCEKContext {
            world.query(*componentTypes)
        }
    
    /**
     * Batch process components with CCEK orchestration
     */
    suspend inline fun <T : Component> forEachBatch(
        typeId: ComponentTypeId,
        batchSize: Int = 64,
        action: (Indexed<EntityId>, Indexed<T>) -> Unit
    ) = withCCEKContext {
        val storage = world.getStorage<T>(typeId) ?: return@withCCEKContext
        
        val totalCount = storage.count
        var offset = 0
        
        while (offset < totalCount) {
            val batchCount = minOf(batchSize, totalCount - offset)
            val entities = \1 j { \2: Int -> storage.getEntityByIndex(offset + i) }
            val components = \1 j { \2: Int -> storage.getByIndex(offset + i) }
            
            action(entities, components)
            offset += batchCount
        }
    }
    
    /**
     * Apply remote Wave operation
     */
    suspend fun applyRemoteOperation(operation: EntityWaveOperation) = withCCEKContext {
        when (operation.operationType) {
            EntityOperationContext.EntityOperationType.CREATE -> {
                // Entity already exists, just apply component changes
                operation.componentChanges.toList().forEach { change ->
                    applyComponentChange(operation.entityId, change)
                }
            }
            EntityOperationContext.EntityOperationType.COMPONENT_ADD -> {
                operation.componentChanges.toList().forEach { change ->
                    applyComponentChange(operation.entityId, change)
                }
            }
            EntityOperationContext.EntityOperationType.UPDATE -> {
                operation.componentChanges.toList().forEach { change ->
                    applyComponentChange(operation.entityId, change)
                }
            }
            EntityOperationContext.EntityOperationType.DELETE -> {
                world.destroyEntity(operation.entityId)
            }
            EntityOperationContext.EntityOperationType.COMPONENT_REMOVE -> {
                operation.componentChanges.toList().forEach { change ->
                    world.removeComponent(operation.entityId, change.componentTypeId)
                }
            }
        }
    }
    
    // === PRIVATE HELPER METHODS ===
    
    internal suspend fun withCCEKContext(block: suspend () -> Unit) {
        val context = RTSGameContext(world, sessionId, waveEngine, crdtRegistry)
        withContext(context) {
            block()
        }
    }
    
    internal suspend fun withCCEKContext(block: suspend () -> EntityId): EntityId {
        val context = RTSGameContext(world, sessionId, waveEngine, crdtRegistry)
        return withContext(context) {
            block()
        }
    }
    
    internal suspend fun withCCEKContext(block: suspend () -> Indexed<EntityId>): Indexed<EntityId> {
        val context = RTSGameContext(world, sessionId, waveEngine, crdtRegistry)
        return withContext(context) {
            block()
        }
    }
    
    internal suspend fun emitOperation(operation: EntityWaveOperation) {
        operationHistory.add(operation)
        _operationFlow.emit(operation)
        
        // Apply to Wave engine if available
        waveEngine?.let { engine ->
            // Convert to Wave operation and apply
            val waveOp = convertToWaveOperation(operation)
            engine.applyOperation(sessionId, waveOp)
        }
        
        // Update CRDT if available
        crdtRegistry?.let { registry ->
            val crdtState = convertToCRDTState(operation)
            registry.updateEntity(operation.entityId.value.toString(), crdtState)
        }
    }
    
    internal fun applyComponentChange(entityId: EntityId, change: ComponentWaveChange) {
        when (change.changeType) {
            ComponentChangeContext.ComponentChangeType.ADD -> {
                val component = deserializeComponent(change.serializedData, change.componentTypeId)
                component?.let { world.addComponent(entityId, it) }
            }
            ComponentChangeContext.ComponentChangeType.UPDATE -> {
                val component = deserializeComponent(change.serializedData, change.componentTypeId)
                component?.let { world.addComponent(entityId, it) }
            }
            ComponentChangeContext.ComponentChangeType.REMOVE -> {
                world.removeComponent(entityId, change.componentTypeId)
            }
        }
    }
    
    internal fun generateOperationId(): String = 
        "op-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
    
    internal fun getCurrentParticipantId(): String = 
        coroutineContext[RTSGameContext.Key]?.sessionId ?: "unknown"
    
    internal fun serializeComponent(component: Component): String {
        // Use kotlinx.serialization to serialize component
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<Component>(), 
            component
        )
    }
    
    internal fun deserializeComponent(data: String, typeId: ComponentTypeId): Component? {
        // Use kotlinx.serialization to deserialize component
        return try {
            kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.serializer<Component>(), 
                data
            )
        } catch (e: Exception) {
            null
        }
    }
    
    internal fun convertToWaveOperation(operation: EntityWaveOperation): WaveOperation {
        // Convert to Wave operation format
        return WaveOperation(
            operationId = operation.operationId,
            type = WaveOperationType.ANNOTATE,
            participantId = operation.participantId,
            content = operation.componentChanges.toList().joinToString { it.serializedData },
            position = operation.entityId.value,
            timestamp = operation.timestamp.toEpochMilliseconds()
        )
    }
    
    internal fun convertToCRDTState(operation: EntityWaveOperation): Map<String, Any?> {
        return mapOf(
            "entityId" to operation.entityId.value,
            "operationType" to operation.operationType.name,
            "componentChanges" to operation.componentChanges.toList().map { 
                mapOf(
                    "typeId" to it.componentTypeId.value,
                    "changeType" to it.changeType.name,
                    "data" to it.serializedData
                )
            },
            "timestamp" to operation.timestamp.toEpochMilliseconds()
        )
    }
}

// === PLACEHOLDER TYPES FOR INTEGRATION ===

// These would be imported from the actual Wave/CRDT implementations
class WaveCRDTEngine {
    suspend fun applyOperation(sessionId: String, operation: WaveOperation): WaveOperationResult {
        return WaveOperationResult.Success(operation)
    }
}

class CRDTRegistry {
    fun updateEntity(entityId: String, data: Map<String, Any?>): CRDTEntity<*> {
        return CRDTEntity(entityId, data)
    }
}

data class CRDTEntity<T>(val id: String, val data: T)

data class WaveOperation(
    val operationId: String,
    val type: WaveOperationType,
    val participantId: String,
    val content: String,
    val position: Int,
    val timestamp: Long
)

enum class WaveOperationType {
    INSERT, DELETE, ANNOTATE
}

sealed class WaveOperationResult {
    data class Success(val operation: WaveOperation) : WaveOperationResult()
    data class Failure(val error: String) : WaveOperationResult()
} 