package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.pathfinding.Pathfinder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MovementSystem(internal val gameMap: GameMap) {
    internal val pathfinder = Pathfinder(gameMap)

    fun update(gameState: GameState, deltaTime: Float): GameState {
        var newState = gameState

        gameState.entities.values.forEach { entity ->
            if (entity is Unit && entity.isMoving) {
                val updatedUnit = updateUnitMovement(entity, deltaTime)
                newState = newState.updateEntity(updatedUnit)
            }
        }

        return newState
    }

    internal fun updateUnitMovement(unit: Unit, deltaTime: Float): Unit {
        if (!unit.isMoving || unit.currentPathIndex >= unit.movementPath.size) {
            return unit
        }

        val targetPosition = unit.movementPath[unit.currentPathIndex]
        val currentPosition = unit.position

        // Calculate direction and distance
        val dx = targetPosition.x - currentPosition.x
        val dy = targetPosition.y - currentPosition.y
        val distance = sqrt(dx * dx + dy * dy)

        // If we're close enough to the target, move to the next waypoint
        if (distance < 1f) {
            return unit.advancePath()
        }

        // Calculate movement
        val speed = unit.speed * deltaTime
        val angle = atan2(dy, dx)
        val newX = currentPosition.x + cos(angle) * speed
        val newY = currentPosition.y + sin(angle) * speed

        // Check if the new position is walkable
        val newPosition = Position(newX, newY)
        if (gameMap.isWalkable(newPosition)) {
            return unit.updatePosition(newPosition)
        } else {
            // If the path is blocked, recalculate path
            val newPath = pathfinder.findPath(currentPosition, unit.targetPosition ?: currentPosition)
            return if (newPath != null) {
                unit.setMovementPath(newPath)
            } else {
                unit.copy(isMoving = false, movementPath = emptyList(), currentPathIndex = 0, targetPosition = null)
            }
        }
    }

    fun moveUnit(gameState: GameState, unitId: String, targetPosition: Position): GameState {
        val unit = gameState.entities[unitId] as? Unit ?: return gameState
        val path = pathfinder.findPath(unit.position, targetPosition) ?: return gameState
        
        val updatedUnit = unit.setMovementPath(path)
        return gameState.updateEntity(updatedUnit)
    }
} 