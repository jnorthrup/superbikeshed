package com.rtsgame.shared.map

import kotlin.math.sqrt

/**
 * Represents a 2D position in the game world.
 * This is the canonical position class that all code should use.
 */
data class Position(val x: Double, val y: Double) {
    fun copy(
        x: Double = this.x,
        y: Double = this.y
    ): Position = Position(x, y)
    
    fun distanceTo(other: Position): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
    
    fun moveTo(target: Position, speed: Double): Position {
        val distance = distanceTo(target)
        if (distance <= speed) return target
        
        val ratio = speed / distance
        val newX = x + (target.x - x) * ratio
        val newY = y + (target.y - y) * ratio
        return Position(newX, newY)
    }
    
    companion object {
        val ZERO = Position(0.0, 0.0)
    }
} 