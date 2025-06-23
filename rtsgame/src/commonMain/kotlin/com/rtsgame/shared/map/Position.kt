package com.rtsgame.shared.map

/**
 * Represents a 2D position in the game world.
 * This is the canonical position class that all code should use.
 */
data class Position(
    val x: Float,
    val y: Float
) {
    fun copy(
        x: Float = this.x,
        y: Float = this.y
    ): Position = Position(x, y)
    
    fun distanceTo(other: Position): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt((dx * dx + dy * dy))
    }
    
    fun moveTowards(target: Position, speed: Float): Position {
        val distance = distanceTo(target)
        if (distance <= speed) return target
        
        val ratio = speed / distance
        val newX = x + (target.x - x) * ratio
        val newY = y + (target.y - y) * ratio
        return Position(newX, newY)
    }
    
    companion object {
        val ZERO = Position(0f, 0f)
    }
} 