package com.rtsgame.shared.entity

import com.rtsgame.shared.map.Position

/**
 * Represents a game entity.
 * This is the canonical interface that all Entity implementations must follow.
 */
interface Entity {
    val id: String // Unique identifier
    var position: Position // Current position (mutable)
    var health: Float // Current health (mutable)
    val maxHealth: Float // Maximum health
    val speed: Float // Movement speed
    val team: Int // Team/player ID
    
    fun copy(
        id: String = this.id,
        position: Position = this.position,
        health: Float = this.health,
        maxHealth: Float = this.maxHealth,
        speed: Float = this.speed,
        team: Int = this.team
    ): Entity
} 