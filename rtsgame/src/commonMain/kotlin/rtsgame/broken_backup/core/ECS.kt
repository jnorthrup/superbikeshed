import kotlin.math.*
package rtsgame.core
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * Ultra-high performance Entity Component System
 * Data-oriented design for cache efficiency and SIMD operations
 */

value class EntityId(val value: Int)

value class ComponentTypeId(val value: Int)

interface Component {
    val typeId: ComponentTypeId
}

/**
 * Component storage using Structure of Arrays (SoA) for cache efficiency
 */
abstract class ComponentStorage<T : Component> {
    abstract val typeId: ComponentTypeId
    internal val entityToIndex = mutableMapOf<EntityId, Int>()
    internal val indexToEntity = mutableListOf<EntityId>()
    
    abstract fun add(entity: EntityId, component: T)
    abstract fun remove(entity: EntityId)
    abstract fun get(entity: EntityId): T?
    abstract fun has(entity: EntityId): Boolean
    abstract fun clear()
    
    fun entities(): Indexed<EntityId> = \1 j { \2: Int -> indexToEntity[i] }
}

/**
 * Dense array storage for hot path components
 */
class DenseComponentStorage<T : Component>(
    override val typeId: ComponentTypeId,
    internal val initialCapacity: Int = 1024
) : ComponentStorage<T>() {
    internal var components = Array<Any?>(initialCapacity) { null }
    internal var size = 0
    
    @Suppress("UNCHECKED_CAST")
    override fun add(entity: EntityId, component: T) {
        if (entityToIndex.containsKey(entity)) {
            components[entityToIndex[entity]!!] = component
        } else {
            if (size >= components.size) {
                components = components.copyOf(components.size * 2)
            }
            entityToIndex[entity] = size
            indexToEntity.add(entity)
            components[size] = component
            size++
        }
    }
    
    override fun remove(entity: EntityId) {
        val index = entityToIndex[entity] ?: return
        val lastIndex = size - 1
        
        if (index != lastIndex) {
            components[index] = components[lastIndex]
            val movedEntity = indexToEntity[lastIndex]
            entityToIndex[movedEntity] = index
            indexToEntity[index] = movedEntity
        }
        
        components[lastIndex] = null
        entityToIndex.remove(entity)
        indexToEntity.removeAt(lastIndex)
        size--
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun get(entity: EntityId): T? {
        val index = entityToIndex[entity] ?: return null
        return components[index] as? T
    }
    
    override fun has(entity: EntityId): Boolean = entityToIndex.containsKey(entity)
    
    override fun clear() {
        components.fill(null, 0, size)
        entityToIndex.clear()
        indexToEntity.clear()
        size = 0
    }
    
    @Suppress("UNCHECKED_CAST")
    fun getByIndex(index: Int): T = components[index] as T
    
    fun getEntityByIndex(index: Int): EntityId = indexToEntity[index]
    
    val count: Int get() = size
}

/**
 * High-performance ECS World
 */
class ECSWorld {
    internal var nextEntityId = 1
    internal val entities = mutableSetOf<EntityId>()
    internal val componentStorages = mutableMapOf<ComponentTypeId, ComponentStorage<*>>()
    internal val entitySignatures = mutableMapOf<EntityId, MutableSet<ComponentTypeId>>()
    
    fun createEntity(): EntityId {
        val entity = EntityId(nextEntityId++)
        entities.add(entity)
        entitySignatures[entity] = mutableSetOf()
        return entity
    }
    
    fun destroyEntity(entity: EntityId) {
        entities.remove(entity)
        val signature = entitySignatures.remove(entity) ?: return
        
        signature.forEach { typeId ->
            componentStorages[typeId]?.remove(entity)
        }
    }
    
    fun <T : Component> addComponent(entity: EntityId, component: T) {
        val storage = getOrCreateStorage(component.typeId) as ComponentStorage<T>
        storage.add(entity, component)
        entitySignatures[entity]?.add(component.typeId)
    }
    
    fun <T : Component> removeComponent(entity: EntityId, typeId: ComponentTypeId) {
        componentStorages[typeId]?.remove(entity)
        entitySignatures[entity]?.remove(typeId)
    }
    
    fun <T : Component> getComponent(entity: EntityId, typeId: ComponentTypeId): T? {
        @Suppress("UNCHECKED_CAST")
        return componentStorages[typeId]?.get(entity) as? T
    }
    
    fun hasComponent(entity: EntityId, typeId: ComponentTypeId): Boolean {
        return componentStorages[typeId]?.has(entity) == true
    }
    
    fun <T : Component> getStorage(typeId: ComponentTypeId): DenseComponentStorage<T>? {
        @Suppress("UNCHECKED_CAST")
        return componentStorages[typeId] as? DenseComponentStorage<T>
    }
    
    internal fun getOrCreateStorage(typeId: ComponentTypeId): ComponentStorage<*> {
        return componentStorages.getOrPut(typeId) {
            DenseComponentStorage<Component>(typeId)
        }
    }
    
    fun clear() {
        entities.clear()
        entitySignatures.clear()
        componentStorages.values.forEach { it.clear() }
        nextEntityId = 1
    }
    
    /**
     * Query entities with specific component combinations
     */
    fun query(vararg componentTypes: ComponentTypeId): Indexed<EntityId> {
        val result = mutableListOf<EntityId>()
        
        entities.forEach { entity ->
            val signature = entitySignatures[entity] ?: return@forEach
            if (componentTypes.all { it in signature }) {
                result.add(entity)
            }
        }
        
        return \1 j { \2: Int -> result[i] }
    }
    
    /**
     * Optimized iteration for systems that process single component type
     */
    inline fun <T : Component> forEach(
        typeId: ComponentTypeId,
        action: (EntityId, T) -> Unit
    ) {
        val storage = getStorage<T>(typeId) ?: return
        
        for (i in 0 until storage.count) {
            action(storage.getEntityByIndex(i), storage.getByIndex(i))
        }
    }
    
    /**
     * Batch process components for SIMD optimization
     */
    inline fun <T : Component> forEachBatch(
        typeId: ComponentTypeId,
        batchSize: Int = 64,
        action: (entities: Indexed<EntityId>, components: Indexed<T>) -> Unit
    ) {
        val storage = getStorage<T>(typeId) ?: return
        val count = storage.count
        
        var i = 0
        while (i < count) {
            val remaining = minOf(batchSize, count - i)
            val entities = \1 j { \2: Int -> storage.getEntityByIndex(i + j) }
            val components = \1 j { \2: Int -> storage.getByIndex(i + j) }
            
            action(entities, components)
            i += remaining
        }
    }
}

/**
 * System interface for processing entities
 */
interface System {
    fun update(world: ECSWorld, deltaTime: Float)
}

/**
 * System that processes entities with specific components
 */
abstract class ComponentSystem(vararg val requiredComponents: ComponentTypeId) : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        val entities = world.query(*requiredComponents)
        
        for (i in 0 until entities.component1()) {
            processEntity(world, entities[i], deltaTime)
        }
    }
    
    abstract fun processEntity(world: ECSWorld, entity: EntityId, deltaTime: Float)
}

/**
 * Parallel processing system for CPU-intensive operations
 */
abstract class ParallelSystem(vararg requiredComponents: ComponentTypeId) : ComponentSystem(*requiredComponents) {
    override fun update(world: ECSWorld, deltaTime: Float) {
        val entities = world.query(*requiredComponents)
        
        // Process entities in parallel batches
        val batchSize = maxOf(1, entities.component1() / getProcessorCount())
        val batches = mutableListOf<Indexed<EntityId>>()
        
        var i = 0
        while (i < entities.component1()) {
            val end = minOf(i + batchSize, entities.component1())
            batches.add((end - \1 j { \2: Int -> entities[i + j] })
            i = end
        }
        
        // TODO: Actual parallel execution when coroutines available
        batches.forEach { batch ->
            processBatch(world, batch, deltaTime)
        }
    }
    
    abstract fun processBatch(world: ECSWorld, entities: Indexed<EntityId>, deltaTime: Float)
    
    internal fun getProcessorCount(): Int = 4 // TODO: Get actual processor count
}