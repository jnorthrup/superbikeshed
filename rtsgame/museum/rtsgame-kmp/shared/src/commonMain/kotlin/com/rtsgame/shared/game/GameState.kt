package com.rtsgame.shared.game

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.Position
// Import the canonical ResourceType from the 'map' module
import com.rtsgame.shared.map.ResourceType
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val entities: Map<String, Entity> = emptyMap(),
    val resources: Map<Int, Map<ResourceType, Int>> = emptyMap(),
    val currentTime: Long = 0
) {
    fun addEntity(entity: Entity): GameState {
        return copy(entities = entities + (entity.id to entity))
    }

    fun removeEntity(entityId: String): GameState {
        return copy(entities = entities - entityId)
    }

    fun updateEntity(entity: Entity): GameState {
        return copy(entities = entities + (entity.id to entity))
    }

    fun getEntityAt(position: Position, radius: Float): Entity? {
        return entities.values.find { entity ->
            val dx = entity.position.x - position.x
            val dy = entity.position.y - position.y
            dx * dx + dy * dy <= radius * radius
        }
    }
}

// Removed local ResourceType enum, will use the one from com.rtsgame.shared.map
// @Serializable
// enum class ResourceType {
// GOLD,
// WOOD,
// FOOD
// }