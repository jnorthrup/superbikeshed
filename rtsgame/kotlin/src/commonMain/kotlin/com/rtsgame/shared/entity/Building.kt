package com.rtsgame.shared.entity

import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.ResourceType
import com.rtsgame.shared.systems.ProcessingQueue
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.max

data class Building(
    override val id: String,
    override val position: Position,
    override val health: Float,
    override val maxHealth: Float,
    override val speed: Float = 0f,
    override val team: Int,
    val type: BuildingType,
    val productionQueue: List<String> = emptyList(),
    val storedResources: Float = 0f,
    val maxStorage: Float = 1000f,
    val resourceType: ResourceType? = null,
    val processingQueue: List<ProcessingQueue> = emptyList(),
    val energyProduction: Float = 0f,
    val energyConsumption: Float = 0f,
    val processingEfficiency: Float = 1.0f,
    val processingLevel: Int = 1
) : Entity() {
    fun addResources(amount: Float): Building {
        val newAmount = minOf(storedResources + amount, maxStorage)
        return copy(storedResources = newAmount)
    }

    fun removeResources(amount: Float): Building {
        val newAmount = maxOf(storedResources - amount, 0f)
        return copy(storedResources = newAmount)
    }

    fun hasEnoughResources(amount: Float): Boolean {
        return storedResources >= amount
    }

    fun gainProcessingExperience(amount: Float): Building {
        val newLevel = (processingLevel + amount / 100f).toInt()
        val newEfficiency = 1.0f + (newLevel - 1) * 0.1f
        return copy(
            processingLevel = newLevel,
            processingEfficiency = newEfficiency
        )
    }
}

enum class BuildingType {
    BARRACKS,
    FACTORY,
    HEADQUARTERS,
    STORAGE_DEPOT,
    FURNACE,
    ASSEMBLY_PLANT,
    ADVANCED_FACTORY,
    POWER_PLANT,
    REFINERY,
    RESEARCH_LAB;

    val baseEnergyProduction: Float
        get() = when (this) {
            POWER_PLANT -> 100f
            else -> 0f
        }

    val baseEnergyConsumption: Float
        get() = when (this) {
            FURNACE -> 10f
            ASSEMBLY_PLANT -> 20f
            ADVANCED_FACTORY -> 30f
            REFINERY -> 25f
            RESEARCH_LAB -> 15f
            else -> 0f
        }

    val baseProcessingEfficiency: Float
        get() = when (this) {
            FURNACE -> 1.0f
            ASSEMBLY_PLANT -> 1.2f
            ADVANCED_FACTORY -> 1.5f
            REFINERY -> 1.3f
            RESEARCH_LAB -> 1.4f
            else -> 0f
        }
}

enum class ResourceType {
    GOLD,
    WOOD,
    FOOD
} 