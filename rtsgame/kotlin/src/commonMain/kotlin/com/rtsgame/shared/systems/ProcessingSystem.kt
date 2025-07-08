package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Building
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.ResourceType
import kotlinx.serialization.Serializable

data class ProcessingRecipe(
    val id: String,
    val name: String,
    val inputResources: Map<ResourceType, Float>,
    val outputResource: ResourceType,
    val outputAmount: Float,
    val processingTime: Float,
    val requiredBuilding: BuildingType,
    val energyCost: Float,
    val experienceGain: Float
)

data class ProcessingQueue(
    val recipeId: String,
    val progress: Float = 0f,
    val isPaused: Boolean = false
)

class ProcessingSystem(internal val gameMap: GameMap) {
    internal val recipes = mutableMapOf<String, ProcessingRecipe>()

    init {
        // Initialize basic processing recipes
        recipes["iron_ingot"] = ProcessingRecipe(
            id = "iron_ingot",
            name = "Iron Ingot",
            inputResources = mapOf(ResourceType.IRON to 2f),
            outputResource = ResourceType.REFINED_IRON,
            outputAmount = 1f,
            processingTime = 5f,
            requiredBuilding = BuildingType.FURNACE,
            energyCost = 5f,
            experienceGain = 10f
        )

        recipes["steel"] = ProcessingRecipe(
            id = "steel",
            name = "Steel",
            inputResources = mapOf(
                ResourceType.REFINED_IRON to 2f,
                ResourceType.COAL to 1f
            ),
            outputResource = ResourceType.STEEL,
            outputAmount = 1f,
            processingTime = 10f,
            requiredBuilding = BuildingType.FURNACE,
            energyCost = 10f,
            experienceGain = 20f
        )

        recipes["electronics"] = ProcessingRecipe(
            id = "electronics",
            name = "Electronics",
            inputResources = mapOf(
                ResourceType.COPPER to 2f,
                ResourceType.SILICON to 1f
            ),
            outputResource = ResourceType.ELECTRONICS,
            outputAmount = 1f,
            processingTime = 15f,
            requiredBuilding = BuildingType.ASSEMBLY_PLANT,
            energyCost = 15f,
            experienceGain = 30f
        )

        recipes["advanced_alloy"] = ProcessingRecipe(
            id = "advanced_alloy",
            name = "Advanced Alloy",
            inputResources = mapOf(
                ResourceType.STEEL to 2f,
                ResourceType.ALUMINUM to 1f,
                ResourceType.TITANIUM to 1f
            ),
            outputResource = ResourceType.ADVANCED_ALLOY,
            outputAmount = 1f,
            processingTime = 20f,
            requiredBuilding = BuildingType.ADVANCED_FACTORY,
            energyCost = 25f,
            experienceGain = 50f
        )
    }

    fun update(state: GameState): GameState {
        var updatedState = state

        state.entities.values
            .filterIsInstance<Building>()
            .filter { it.processingQueue.isNotEmpty() }
            .forEach { building ->
                val updatedBuilding = processBuilding(building)
                updatedState = updatedState.updateEntity(updatedBuilding)
            }

        return updatedState
    }

    internal fun processBuilding(building: Building): Building {
        if (building.processingQueue.isEmpty()) return building

        val updatedQueue = building.processingQueue.map { queueItem ->
            if (queueItem.isPaused) return@map queueItem

            val recipe = recipes[queueItem.recipeId] ?: return@map queueItem
            val progress = queueItem.progress + (1f / recipe.processingTime) * building.processingEfficiency

            if (progress >= 1f) {
                // Processing complete
                val outputAmount = recipe.outputAmount * building.processingEfficiency
                val updatedBuilding = building.copy(
                    storedResources = building.storedResources + outputAmount,
                    processingQueue = building.processingQueue.filter { it != queueItem }
                ).gainProcessingExperience(recipe.experienceGain)

                return processBuilding(updatedBuilding)
            }

            queueItem.copy(progress = progress)
        }

        return building.copy(processingQueue = updatedQueue)
    }

    fun startProcessing(state: GameState, buildingId: String, recipeId: String): GameState {
        val building = state.entities[buildingId] as? Building ?: return state
        val recipe = recipes[recipeId] ?: return state

        if (building.type != recipe.requiredBuilding) return state
        if (!hasRequiredResources(building, recipe)) return state

        val updatedBuilding = building.copy(
            processingQueue = building.processingQueue + ProcessingQueue(recipeId = recipeId),
            storedResources = consumeResources(building, recipe)
        )

        return state.updateEntity(updatedBuilding)
    }

    internal fun hasRequiredResources(building: Building, recipe: ProcessingRecipe): Boolean {
        return recipe.inputResources.all { (resource, amount) ->
            building.storedResources >= amount
        }
    }

    internal fun consumeResources(building: Building, recipe: ProcessingRecipe): Float {
        return building.storedResources - recipe.inputResources.values.sum()
    }
} 