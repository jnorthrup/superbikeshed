package com.rtsgame.shared.game

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.map.ResourceType

/**
 * Represents the current state of the RTS game
 */
data class GameState(
    val entities: Map<String, Entity>,
    val resources: Map<Int, Map<ResourceType, Int>>,
    val currentTime: Long
) {
    fun copy(
        entities: Map<String, Entity> = this.entities,
        resources: Map<Int, Map<ResourceType, Int>> = this.resources,
        currentTime: Long = this.currentTime
    ): GameState = GameState(entities, resources, currentTime)
    
    val size: Int get() = entities.size
    
    fun getEntity(id: String): Entity? = entities[id]
    fun getResources(playerId: Int): Map<ResourceType, Int> = resources[playerId] ?: emptyMap()
    
    fun updateEntity(id: String, entity: Entity): GameState {
        return copy(entities = entities + (id to entity))
    }
    
    fun removeEntity(id: String): GameState {
        val newEntities = entities.toMutableMap()
        newEntities.remove(id)
        return copy(entities = newEntities)
    }
    
    fun updateResources(playerId: Int, resourceType: ResourceType, amount: Int): GameState {
        val playerResources = resources[playerId] ?: emptyMap()
        val updatedResources = playerResources + (resourceType to (playerResources[resourceType] ?: 0) + amount)
        return copy(resources = resources + (playerId to updatedResources))
    }
    
    fun advance(): GameState = copy(currentTime = currentTime + 1)
} 