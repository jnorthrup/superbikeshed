package com.rtsgame.shared.command

import com.rtsgame.shared.entity.Building
import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.systems.MovementSystem
import kotlinx.serialization.Serializable

interface Command {
    fun execute(state: GameState, gameMap: GameMap): GameState
}

data class GameState(
    val units: Map<String, Unit> = emptyMap(),
    val buildings: Map<String, Building> = emptyMap(),
    val resources: Map<String, Int> = emptyMap(),
    val currentTick: Long = 0
)

sealed class Command {
    abstract fun execute(state: GameState, gameMap: GameMap): GameState
}

data class MoveCommand(
    val entityId: String,
    val targetPosition: Position
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val movementSystem = MovementSystem(gameMap)
        return movementSystem.moveUnit(state, entityId, targetPosition)
    }
}

data class AttackCommand(
    val attackerId: String,
    val targetId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val attacker = state.entities[attackerId] ?: return state
        val target = state.entities[targetId] ?: return state
        // TODO: Implement attack logic
        return state
    }
}

data class BuildCommand(
    val builderId: String,
    val buildingType: String,
    val position: Position
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        // TODO: Implement building logic
        return state
    }
}

data class TrainUnitCommand(
    val buildingId: String,
    val unitType: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        // TODO: Implement unit training logic
        return state
    }
} 