package com.rtsgame.shared.game

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.map.ResourceType

/**
 * Represents the current state of the game.
 * This is the canonical interface that all GameState implementations must follow.
 */
data class GameState(
    val entities: Map<String, Entity>, // Map of entity ID to Entity
    val resources: Map<Int, Map<ResourceType, Int>>, // Map of player ID to resource type to amount
    val currentTime: Long // Current game time in ticks
) {
    fun copy(
        entities: Map<String, Entity> = this.entities,
        resources: Map<Int, Map<ResourceType, Int>> = this.resources,
        currentTime: Long = this.currentTime
    ): GameState = GameState(entities, resources, currentTime)
    
    val size: Int get() = entities.size
    
    fun getEntity(id: String): Entity? = entities[id]
    fun getResources(playerId: Int): Map<ResourceType, Int> = resources[playerId] ?: emptyMap()
    
    fun updateEntity(entity: Entity): GameState {
        val newEntities = entities.toMutableMap()
        newEntities[entity.id] = entity
        return copy(entities = newEntities)
    }
    
    fun removeEntity(id: String): GameState {
        val newEntities = entities.toMutableMap()
        newEntities.remove(id)
        return copy(entities = newEntities)
    }
    
    fun updateResources(playerId: Int, resourceType: ResourceType, amount: Int): GameState {
        val playerResources = resources[playerId]?.toMutableMap() ?: mutableMapOf()
        playerResources[resourceType] = amount
        val newResources = resources.toMutableMap()
        newResources[playerId] = playerResources
        return copy(resources = newResources)
    }
    
    fun advance(): GameState = copy(currentTime = currentTime + 1)
} 