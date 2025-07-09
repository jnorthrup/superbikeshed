package rtsgame.core

import rtsgame.components.*

/**
 * Entity ID type
 */
@JvmInline
value class EntityId(val value: Int) {
    companion object {
        val INVALID = EntityId(-1)
    }
}

/**
 * Component storage for ECS
 */
class ComponentStorage<T : Component> {
    private val components = mutableMapOf<EntityId, T>()
    
    fun add(entityId: EntityId, component: T) {
        components[entityId] = component
    }
    
    fun remove(entityId: EntityId) {
        components.remove(entityId)
    }
    
    fun get(entityId: EntityId): T? = components[entityId]
    
    fun has(entityId: EntityId): Boolean = components.containsKey(entityId)
    
    fun getAll(): Map<EntityId, T> = components.toMap()
    
    fun clear() {
        components.clear()
    }
}

/**
 * ECS World that manages entities and components
 */
class ECSWorld {
    private var nextEntityId = 0
    private val entities = mutableSetOf<EntityId>()
    private val componentStorages = mutableMapOf<ComponentTypeId, ComponentStorage<*>>()
    
    fun createEntity(): EntityId {
        val entityId = EntityId(nextEntityId++)
        entities.add(entityId)
        return entityId
    }
    
    fun destroyEntity(entityId: EntityId) {
        entities.remove(entityId)
        componentStorages.values.forEach { storage ->
            storage.remove(entityId)
        }
    }
    
    fun hasEntity(entityId: EntityId): Boolean = entities.contains(entityId)
    
    fun getAllEntities(): Set<EntityId> = entities.toSet()
    
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> addComponent(entityId: EntityId, component: T) {
        val storage = componentStorages.getOrPut(component.typeId) { ComponentStorage<T>() } as ComponentStorage<T>
        storage.add(entityId, component)
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> removeComponent(entityId: EntityId, componentType: ComponentTypeId) {
        val storage = componentStorages[componentType] as? ComponentStorage<T>
        storage?.remove(entityId)
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(entityId: EntityId, componentType: ComponentTypeId): T? {
        val storage = componentStorages[componentType] as? ComponentStorage<T>
        return storage?.get(entityId)
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> hasComponent(entityId: EntityId, componentType: ComponentTypeId): Boolean {
        val storage = componentStorages[componentType] as? ComponentStorage<T>
        return storage?.has(entityId) ?: false
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponents(componentType: ComponentTypeId): Map<EntityId, T> {
        val storage = componentStorages[componentType] as? ComponentStorage<T>
        return storage?.getAll() ?: emptyMap()
    }
    
    fun getEntitiesWithComponents(vararg componentTypes: ComponentTypeId): Set<EntityId> {
        return entities.filter { entityId ->
            componentTypes.all { componentType ->
                hasComponent(entityId, componentType)
            }
        }.toSet()
    }
    
    fun clear() {
        entities.clear()
        componentStorages.clear()
        nextEntityId = 0
    }
}

/**
 * System interface for ECS
 */
interface System {
    fun update(world: ECSWorld, deltaTime: Float)
}

/**
 * System manager for coordinating system updates
 */
class SystemManager {
    private val systems = mutableListOf<System>()
    
    fun addSystem(system: System) {
        systems.add(system)
    }
    
    fun removeSystem(system: System) {
        systems.remove(system)
    }
    
    fun update(world: ECSWorld, deltaTime: Float) {
        systems.forEach { system ->
            system.update(world, deltaTime)
        }
    }
    
    fun clear() {
        systems.clear()
    }
} 