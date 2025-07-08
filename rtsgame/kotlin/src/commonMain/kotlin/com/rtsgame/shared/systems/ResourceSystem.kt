package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.ResourceProperties
import com.rtsgame.shared.map.ResourceType
import kotlinx.serialization.Serializable

data class ResourceNode(
    val id: String,
    val position: Position,
    val properties: ResourceProperties,
    var amount: Int,
    val maxAmount: Int,
    var respawnTimer: Float = 0f,
    var isDepleted: Boolean = false,
    var environmentalDamage: Float = 0f
) {
    val type: ResourceType get() = properties.type
    val gatherRate: Float get() = properties.type.baseGatherRate * properties.purity * properties.accessibility
    val respawnTime: Float get() = properties.type.respawnTime * (1f + environmentalDamage)
}

class ResourceSystem(internal val gameMap: GameMap) {
    internal val resourceNodes = mutableMapOf<String, ResourceNode>()

    fun update(gameState: GameState, deltaTime: Float): GameState {
        var newState = gameState

        // Update resource nodes
        resourceNodes.forEach { (nodeId, node) ->
            if (node.isDepleted) {
                if (node.properties.isRenewable) {
                    node.respawnTimer += deltaTime
                    if (node.respawnTimer >= node.respawnTime) {
                        node.amount = node.maxAmount
                        node.isDepleted = false
                        node.respawnTimer = 0f
                        // Reduce environmental damage over time for renewable resources
                        node.environmentalDamage = (node.environmentalDamage - 0.1f).coerceAtLeast(0f)
                    }
                }
            }
        }

        // Update units that are gathering resources
        gameState.entities.values.forEach { entity ->
            if (entity is Unit && entity.isGathering) {
                val updatedUnit = updateUnitGathering(entity, deltaTime)
                newState = newState.updateEntity(updatedUnit)
            }
        }

        return newState
    }

    internal fun updateUnitGathering(unit: Unit, deltaTime: Float): Unit {
        val node = resourceNodes[unit.targetResourceNodeId] ?: return unit.copy(isGathering = false)
        
        if (node.isDepleted) {
            return unit.copy(isGathering = false)
        }

        // Calculate effective gather rate based on unit's specialization and tools
        val effectiveGatherRate = unit.getEffectiveGatherRate(node.type)
        val resourcesGathered = effectiveGatherRate * deltaTime
        val actualGathered = minOf(resourcesGathered, node.amount.toFloat())
        
        node.amount -= actualGathered.toInt()
        if (node.amount <= 0) {
            node.isDepleted = true
            node.amount = 0
            // Increase environmental damage when depleting a node
            node.environmentalDamage += node.properties.environmentalImpact
        }

        // Calculate experience gain based on resources gathered
        val experienceGain = actualGathered * 0.1f
        val updatedUnit = unit.gainGatheringExperience(experienceGain)

        return updatedUnit.copy(
            carriedResources = unit.carriedResources + actualGathered,
            isGathering = !node.isDepleted && unit.carriedResources < unit.maxCarryCapacity
        )
    }

    fun startGathering(gameState: GameState, unitId: String, nodeId: String): GameState {
        val unit = gameState.entities[unitId] as? Unit ?: return gameState
        val node = resourceNodes[nodeId] ?: return gameState

        if (node.isDepleted || unit.carriedResources >= unit.maxCarryCapacity) {
            return gameState
        }

        // Check if unit has required tool
        node.type.requiredTool?.let { requiredTool ->
            if (unit.equippedTool != requiredTool) {
                return gameState
            }
        }

        val updatedUnit = unit.copy(
            isGathering = true,
            targetResourceNodeId = nodeId
        )

        return gameState.updateEntity(updatedUnit)
    }

    fun depositResources(gameState: GameState, unitId: String, buildingId: String): GameState {
        val unit = gameState.entities[unitId] as? Unit ?: return gameState
        val building = gameState.entities[buildingId] as? Building ?: return gameState

        if (unit.carriedResources <= 0) {
            return gameState
        }

        val updatedUnit = unit.copy(
            carriedResources = 0f,
            isGathering = false,
            targetResourceNodeId = null
        )

        val updatedBuilding = building.copy(
            storedResources = building.storedResources + unit.carriedResources
        )

        return gameState
            .updateEntity(updatedUnit)
            .updateEntity(updatedBuilding)
    }

    fun addResourceNode(node: ResourceNode): String {
        resourceNodes[node.id] = node
        return node.id
    }

    fun getResourceNodeAt(position: Position): ResourceNode? {
        return resourceNodes.values.find { node ->
            val dx = node.position.x - position.x
            val dy = node.position.y - position.y
            dx * dx + dy * dy <= 1f // Within 1 unit distance
        }
    }

    fun getResourceNodesByType(type: ResourceType): List<ResourceNode> {
        return resourceNodes.values.filter { it.type == type }
    }

    fun getResourceNodesByPurity(minPurity: Float): List<ResourceNode> {
        return resourceNodes.values.filter { it.properties.purity >= minPurity }
    }
} 