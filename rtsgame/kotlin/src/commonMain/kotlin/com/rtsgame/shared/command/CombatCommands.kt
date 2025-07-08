package com.rtsgame.shared.command

import com.rtsgame.shared.entity.Building
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.systems.CombatSystem
import kotlinx.serialization.Serializable

data class AttackCommand(
    val unitId: String,
    val targetId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val target = state.entities[targetId] ?: return state

        if (unit.team == target.team) return state

        val combatSystem = CombatSystem(gameMap)
        val updatedUnit = unit.startAttacking(targetId)
        return state.updateEntity(updatedUnit)
    }
}

data class StopAttackCommand(
    val unitId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val updatedUnit = unit.stopAttacking()
        return state.updateEntity(updatedUnit)
    }
}

data class AttackMoveCommand(
    val unitId: String,
    val targetPosition: Position
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val path = gameMap.findPath(unit.position, targetPosition) ?: return state

        val updatedUnit = unit.copy(
            path = path,
            currentPathIndex = 0,
            isAttacking = true
        )
        return state.updateEntity(updatedUnit)
    }
}

data class AreaAttackCommand(
    val unitId: String,
    val targetPosition: Position
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val combatSystem = CombatSystem(gameMap)

        // Find all enemy units in range of the target position
        val targetsInRange = state.entities.values
            .filter { it.team != unit.team }
            .filter { it is Unit || it is Building }
            .filter { target ->
                val distance = calculateDistance(target.position, targetPosition)
                distance <= unit.attackProperties.splashRadius
            }
            .sortedBy { calculateDistance(it.position, targetPosition) }

        if (targetsInRange.isEmpty()) return state

        // Attack the closest target
        val updatedUnit = unit.startAttacking(targetsInRange.first().id)
        return state.updateEntity(updatedUnit)
    }

    internal fun calculateDistance(pos1: Position, pos2: Position): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

data class DefendPositionCommand(
    val unitId: String,
    val position: Position,
    val radius: Float
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val combatSystem = CombatSystem(gameMap)

        // Find all enemy units in the defense radius
        val targetsInRange = state.entities.values
            .filter { it.team != unit.team }
            .filter { it is Unit || it is Building }
            .filter { target ->
                val distance = calculateDistance(target.position, position)
                distance <= radius
            }
            .sortedBy { calculateDistance(it.position, position) }

        if (targetsInRange.isEmpty()) return state

        // Attack the closest target
        val updatedUnit = unit.startAttacking(targetsInRange.first().id)
        return state.updateEntity(updatedUnit)
    }

    internal fun calculateDistance(pos1: Position, pos2: Position): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
} 