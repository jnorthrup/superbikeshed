package com.rtsgame.shared.game

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.map.ResourceType
import kotlinx.serialization.Serializable
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.emptySeries

@Serializable
data class GameState(
    val entities: Series<Entity> = emptySeries(),
    val resources: Map<Int, Map<ResourceType, Int>> = emptyMap(),
    val currentTime: Long = 0
) {
    fun addEntity(entity: Entity): GameState {
        return copy(entities = (entities.play + entity).toList().toSeries())
    }

    fun removeEntity(entityId: String): GameState {
        return copy(entities = entities.play.filterNot { it.id == entityId }.toList().toSeries())
    }

    fun updateEntity(entity: Entity): GameState {
        return copy(entities = entities.play.map { if (it.id == entity.id) entity else it }.toList().toSeries())
    }

    fun getEntityAt(position: Position, radius: Float): Entity? {
        return entities.play.find { entity ->
            val dx = entity.position.x - position.x
            val dy = entity.position.y - position.y
            // Assuming entity.radius is the property to check against, similar to the original instruction's intent
            dx * dx + dy * dy <= radius * radius
        }
    }
}

@Serializable
enum class ResourceType {
    GOLD,
    WOOD,
    FOOD
} 