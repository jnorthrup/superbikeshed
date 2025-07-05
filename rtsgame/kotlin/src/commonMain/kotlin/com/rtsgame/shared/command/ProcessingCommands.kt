package com.rtsgame.shared.command

import com.rtsgame.shared.entity.Building
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.systems.ProcessingSystem
import kotlinx.serialization.Serializable

data class StartProcessingCommand(
    val buildingId: String,
    val recipeId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val processingSystem = ProcessingSystem(gameMap)
        return processingSystem.startProcessing(state, buildingId, recipeId)
    }
}

data class CancelProcessingCommand(
    val buildingId: String,
    val queueIndex: Int
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val building = state.entities[buildingId] as? Building ?: return state
        if (queueIndex >= building.processingQueue.size) return state

        val updatedBuilding = building.copy(
            processingQueue = building.processingQueue.filterIndexed { index, _ -> index != queueIndex }
        )
        return state.updateEntity(updatedBuilding)
    }
}

data class PrioritizeProcessingCommand(
    val buildingId: String,
    val queueIndex: Int
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val building = state.entities[buildingId] as? Building ?: return state
        if (queueIndex >= building.processingQueue.size) return state

        val queue = building.processingQueue.toMutableList()
        val item = queue.removeAt(queueIndex)
        queue.add(0, item)

        val updatedBuilding = building.copy(processingQueue = queue)
        return state.updateEntity(updatedBuilding)
    }
}

data class UpgradeProcessingCommand(
    val buildingId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val building = state.entities[buildingId] as? Building ?: return state
        val updatedBuilding = building.gainProcessingExperience(100f)
        return state.updateEntity(updatedBuilding)
    }
} 