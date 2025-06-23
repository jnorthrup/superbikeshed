package com.rtsgame.shared.entity

import com.rtsgame.shared.map.Position

/**
 * Represents a game entity in the RTS game
 */
data class Entity(
    val id: String,
    val position: Position,
    val playerId: Int,
    val type: EntityType,
    val health: Int,
    val maxHealth: Int
) {
    fun move(newPosition: Position): Entity = copy(position = newPosition)
    fun damage(amount: Int): Entity = copy(health = (health - amount).coerceAtLeast(0))
    fun heal(amount: Int): Entity = copy(health = (health + amount).coerceAtMost(maxHealth))
}

enum class EntityType {
    WORKER,
    SOLDIER,
    BUILDING,
    RESOURCE
} 