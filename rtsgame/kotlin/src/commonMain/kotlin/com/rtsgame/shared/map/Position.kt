package com.rtsgame.shared.map

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

data class Position(
    val x: Float,
    val y: Float
) {
    fun distanceTo(other: Position): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun directionTo(other: Position): Position {
        val dx = other.x - x
        val dy = other.y - y
        val length = distanceTo(other)
        return if (length > 0) {
            Position(dx / length, dy / length)
        } else {
            Position(0f, 0f)
        }
    }

    operator fun plus(other: Position): Position {
        return Position(x + other.x, y + other.y)
    }

    operator fun minus(other: Position): Position {
        return Position(x - other.x, y - other.y)
    }

    operator fun times(scalar: Float): Position {
        return Position(x * scalar, y * scalar)
    }

    operator fun div(scalar: Float): Position {
        return Position(x / scalar, y / scalar)
    }
} 