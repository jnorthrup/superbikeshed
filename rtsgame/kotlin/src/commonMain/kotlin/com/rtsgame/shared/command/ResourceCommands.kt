package com.rtsgame.shared.command

import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.GatheringTool
import com.rtsgame.shared.map.ResourceType
import com.rtsgame.shared.systems.ResourceSystem
import kotlinx.serialization.Serializable

data class GatherResourceCommand(
    val unitId: String,
    val nodeId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val resourceSystem = ResourceSystem(gameMap)
        return resourceSystem.startGathering(state, unitId, nodeId)
    }
}

data class DepositResourceCommand(
    val unitId: String,
    val buildingId: String
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val resourceSystem = ResourceSystem(gameMap)
        return resourceSystem.depositResources(state, unitId, buildingId)
    }
}

data class AutoGatherCommand(
    val unitId: String,
    val resourceType: ResourceType
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val resourceSystem = ResourceSystem(gameMap)
        
        // Find nearest resource node of the specified type
        val nearestNode = resourceSystem.getResourceNodesByType(resourceType)
            .filter { !it.isDepleted }
            .minByOrNull { node ->
                val dx = node.position.x - unit.position.x
                val dy = node.position.y - unit.position.y
                dx * dx + dy * dy
            } ?: return state

        // Start gathering if we found a valid node
        return resourceSystem.startGathering(state, unitId, nearestNode.id)
    }
}

data class EquipToolCommand(
    val unitId: String,
    val tool: GatheringTool
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val updatedUnit = unit.equipTool(tool)
        return state.updateEntity(updatedUnit)
    }
}

data class FindHighPurityResourceCommand(
    val unitId: String,
    val resourceType: ResourceType,
    val minPurity: Float
) : Command() {
    override fun execute(state: GameState, gameMap: GameMap): GameState {
        val unit = state.entities[unitId] as? Unit ?: return state
        val resourceSystem = ResourceSystem(gameMap)
        
        // Find nearest high-purity resource node
        val nearestNode = resourceSystem.getResourceNodesByType(resourceType)
            .filter { it.properties.purity >= minPurity && !it.isDepleted }
            .minByOrNull { node ->
                val dx = node.position.x - unit.position.x
                val dy = node.position.y - unit.position.y
                dx * dx + dy * dy
            } ?: return state

        return resourceSystem.startGathering(state, unitId, nearestNode.id)
    }
} 