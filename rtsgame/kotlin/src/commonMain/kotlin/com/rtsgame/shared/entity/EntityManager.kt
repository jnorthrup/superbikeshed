package com.rtsgame.shared.entity

import kotlin.math.*

/**
 * Enhanced Entity Manager - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - Component-based architecture with type safety
 * - Deterministic ID generation
 * - Efficient spatial indexing
 * - System scheduling with priority queuing
 * - Memory management and lifecycle tracking
 */
class EntityManager {
    
    // Entity storage with deterministic IDs
    internal val entities = mutableMapOf<Long, GameEntity>()
    internal var nextEntityId: Long = 1L
    
    // Component storage by type
    internal val componentStores = mutableMapOf<Class<*>, MutableMap<Long, Any>>()
    
    // Spatial indexing for performance
    internal val spatialIndex = SpatialIndex()
    
    // System scheduling
    internal val systems = mutableListOf<GameSystem>()
    internal val systemPriorities = mutableMapOf<GameSystem, Int>()
    
    // Entity lifecycle tracking
    internal val activeEntities = mutableSetOf<Long>()
    internal val pendingRemovals = mutableSetOf<Long>()
    internal val pendingAdditions = mutableListOf<GameEntity>()
    
    /**
     * Create a new entity with deterministic ID
     */
    fun createEntity(): Long {
        val entityId = nextEntityId++
        val entity = GameEntity(entityId)
        pendingAdditions.add(entity)
        return entityId
    }
    
    /**
     * Add an entity to the manager
     */
    fun addEntity(entity: GameEntity) {
        entities[entity.id] = entity
        activeEntities.add(entity.id)
        
        // Add to spatial index if it has a position
        entity.getComponent<Position>()?.let { pos ->
            spatialIndex.addEntity(entity.id, pos.x, pos.y)
        }
    }
    
    /**
     * Remove an entity from the manager
     */
    fun removeEntity(entityId: Long) {
        pendingRemovals.add(entityId)
    }
    
    /**
     * Get an entity by ID
     */
    fun getEntity(entityId: Long): GameEntity? {
        return entities[entityId]
    }
    
    /**
     * Get all entities
     */
    fun getAllEntities(): List<GameEntity> {
        return entities.values.toList()
    }
    
    /**
     * Add a component to an entity
     */
    fun <T : Any> addComponent(entityId: Long, component: T) {
        val componentType = component::class.java
        val store = componentStores.getOrPut(componentType) { mutableMapOf() }
        store[entityId] = component
        
        // Update entity's component cache
        entities[entityId]?.let { entity ->
            entity.addComponent(componentType, component)
        }
    }
    
    /**
     * Remove a component from an entity
     */
    fun <T : Any> removeComponent(entityId: Long, componentType: Class<T>) {
        componentStores[componentType]?.remove(entityId)
        
        // Update entity's component cache
        entities[entityId]?.let { entity ->
            entity.removeComponent(componentType)
        }
    }
    
    /**
     * Get a component from an entity
     */
    fun <T : Any> getComponent(entityId: Long, componentType: Class<T>): T? {
        return componentStores[componentType]?.get(entityId) as? T
    }
    
    /**
     * Get all entities with a specific component type
     */
    fun <T : Any> getEntitiesWithComponent(componentType: Class<T>): List<GameEntity> {
        val componentStore = componentStores[componentType] ?: return emptyList()
        return componentStore.keys.mapNotNull { entityId -> entities[entityId] }
    }
    
    /**
     * Find entities in range of a position
     */
    fun findEntitiesInRange(x: Double, y: Double, range: Double): List<GameEntity> {
        val entityIds = spatialIndex.getEntitiesInRange(x, y, range)
        return entityIds.mapNotNull { entities[it] }
    }
    
    /**
     * Find entities in range with specific component
     */
    fun <T : Any> findEntitiesInRangeWithComponent(
        x: Double, 
        y: Double, 
        range: Double, 
        componentType: Class<T>
    ): List<GameEntity> {
        val inRange = findEntitiesInRange(x, y, range)
        return inRange.filter { entity ->
            entity.hasComponent(componentType)
        }
    }
    
    /**
     * Register a system with priority
     */
    fun registerSystem(system: GameSystem, priority: Int = 0) {
        systems.add(system)
        systemPriorities[system] = priority
        systems.sortBy { systemPriorities[it] ?: 0 }
    }
    
    /**
     * Unregister a system
     */
    fun unregisterSystem(system: GameSystem) {
        systems.remove(system)
        systemPriorities.remove(system)
    }
    
    /**
     * Update all systems in priority order
     */
    fun update(deltaTime: Double) {
        // Process pending additions
        pendingAdditions.forEach { entity ->
            addEntity(entity)
        }
        pendingAdditions.clear()
        
        // Process pending removals
        pendingRemovals.forEach { entityId ->
            removeEntityInternal(entityId)
        }
        pendingRemovals.clear()
        
        // Update all systems in priority order
        systems.forEach { system ->
            system.update(this, deltaTime)
        }
        
        // Update spatial index for entities with position components
        updateSpatialIndex()
    }
    
    /**
     * Internal entity removal
     */
    internal fun removeEntityInternal(entityId: Long) {
        entities.remove(entityId)
        activeEntities.remove(entityId)
        spatialIndex.removeEntity(entityId)
        
        // Remove all components
        componentStores.values.forEach { store ->
            store.remove(entityId)
        }
    }
    
    /**
     * Update spatial index for all entities with positions
     */
    internal fun updateSpatialIndex() {
        entities.values.forEach { entity ->
            entity.getComponent<Position>()?.let { pos ->
                spatialIndex.updateEntity(entity.id, pos.x, pos.y)
            }
        }
    }
    
    /**
     * Get entity count
     */
    fun getEntityCount(): Int = entities.size
    
    /**
     * Get active entity count
     */
    fun getActiveEntityCount(): Int = activeEntities.size
    
    /**
     * Clear all entities
     */
    fun clear() {
        entities.clear()
        componentStores.clear()
        activeEntities.clear()
        pendingRemovals.clear()
        pendingAdditions.clear()
        spatialIndex.clear()
        nextEntityId = 1L
    }
    
    /**
     * Get system statistics
     */
    fun getStatistics(): EntityManagerStats {
        return EntityManagerStats(
            totalEntities = entities.size,
            activeEntities = activeEntities.size,
            pendingAdditions = pendingAdditions.size,
            pendingRemovals = pendingRemovals.size,
            componentTypes = componentStores.size,
            systems = systems.size
        )
    }
}

/**
 * Game Entity class with component support
 */
data class GameEntity(
    val id: Long,
    internal val components: MutableMap<Class<*>, Any> = mutableMapOf()
) {
    
    fun <T : Any> addComponent(componentType: Class<T>, component: T) {
        components[componentType] = component
    }
    
    fun <T : Any> removeComponent(componentType: Class<T>) {
        components.remove(componentType)
    }
    
    fun <T : Any> getComponent(componentType: Class<T>): T? {
        return components[componentType] as? T
    }
    
    fun <T : Any> hasComponent(componentType: Class<T>): Boolean {
        return components.containsKey(componentType)
    }
    
    fun getComponentTypes(): Set<Class<*>> = components.keys.toSet()
}

/**
 * Game System interface
 */
interface GameSystem {
    fun update(entityManager: EntityManager, deltaTime: Double)
}

/**
 * Spatial indexing for efficient range queries
 */
internal class SpatialIndex {
    internal data class SpatialEntity(
        val id: Long,
        var x: Double,
        var y: Double
    )
    
    internal val entities = mutableMapOf<Long, SpatialEntity>()
    internal val gridSize = 100.0 // Grid cell size
    internal val grid = mutableMapOf<Pair<Int, Int>, MutableSet<Long>>()
    
    fun addEntity(id: Long, x: Double, y: Double) {
        val entity = SpatialEntity(id, x, y)
        entities[id] = entity
        addToGrid(id, x, y)
    }
    
    fun removeEntity(id: Long) {
        val entity = entities.remove(id)
        if (entity != null) {
            removeFromGrid(id, entity.x, entity.y)
        }
    }
    
    fun updateEntity(id: Long, newX: Double, newY: Double) {
        val entity = entities[id]
        if (entity != null) {
            removeFromGrid(id, entity.x, entity.y)
            entity.x = newX
            entity.y = newY
            addToGrid(id, newX, newY)
        }
    }
    
    fun getEntitiesInRange(x: Double, y: Double, range: Double): Set<Long> {
        val result = mutableSetOf<Long>()
        val rangeSq = range * range
        
        // Calculate grid bounds to check
        val minGridX = ((x - range) / gridSize).toInt()
        val maxGridX = ((x + range) / gridSize).toInt()
        val minGridY = ((y - range) / gridSize).toInt()
        val maxGridY = ((y + range) / gridSize).toInt()
        
        for (gx in minGridX..maxGridX) {
            for (gy in minGridY..maxGridY) {
                val cellEntities = grid[Pair(gx, gy)] ?: continue
                cellEntities.forEach { entityId ->
                    val entity = entities[entityId] ?: return@forEach
                    val distanceSq = (entity.x - x).pow(2) + (entity.y - y).pow(2)
                    if (distanceSq <= rangeSq) {
                        result.add(entityId)
                    }
                }
            }
        }
        
        return result
    }
    
    internal fun addToGrid(id: Long, x: Double, y: Double) {
        val gridX = (x / gridSize).toInt()
        val gridY = (y / gridSize).toInt()
        val cell = Pair(gridX, gridY)
        grid.getOrPut(cell) { mutableSetOf() }.add(id)
    }
    
    internal fun removeFromGrid(id: Long, x: Double, y: Double) {
        val gridX = (x / gridSize).toInt()
        val gridY = (y / gridSize).toInt()
        val cell = Pair(gridX, gridY)
        grid[cell]?.remove(id)
    }
    
    fun clear() {
        entities.clear()
        grid.clear()
    }
}

/**
 * Statistics for Entity Manager
 */
data class EntityManagerStats(
    val totalEntities: Int,
    val activeEntities: Int,
    val pendingAdditions: Int,
    val pendingRemovals: Int,
    val componentTypes: Int,
    val systems: Int
)

/**
 * Position component
 */
data class Position(
    val x: Double,
    val y: Double
) 