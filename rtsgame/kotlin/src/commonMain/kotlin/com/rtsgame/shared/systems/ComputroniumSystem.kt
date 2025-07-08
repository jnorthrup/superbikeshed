package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.EntityManager
import kotlin.math.*

/**
 * Computronium System - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - Resource conversion: Energy to computronium conversion rates
 * - Processing power: Sophisticated processing allocation algorithms
 * - Efficiency metrics: Performance optimization calculations
 */
class ComputroniumSystem(
    internal val entityManager: EntityManager
) {
    
    companion object {
        // Conversion Constants
        const val ENERGY_TO_COMPUTRONIUM_RATIO = 100.0 // 100 energy = 1 computronium
        const val COMPUTRONIUM_TO_ENERGY_RATIO = 0.01 // 1 computronium = 0.01 energy (lossy)
        
        // Processing Power Constants
        const val BASE_PROCESSING_POWER = 1000.0 // operations per second
        const val COMPUTRONIUM_PROCESSING_MULTIPLIER = 10.0 // 10x processing per computronium unit
        const val EFFICIENCY_DECAY_RATE = 0.95 // 5% efficiency loss per cycle
        
        // Allocation Types
        enum class AllocationType(val priority: Int, val efficiency: Double) {
            SECURITY(1, 0.9),      // High priority, high efficiency
            PROCESSING(2, 0.8),    // Medium priority, good efficiency
            STORAGE(3, 0.7),       // Lower priority, moderate efficiency
            NETWORK(4, 0.6)        // Lowest priority, lower efficiency
        }
    }
    
    // Computronium Component
    data class ComputroniumComponent(
        var amount: Double = 0.0,
        var processingPower: Double = BASE_PROCESSING_POWER,
        var efficiency: Double = 1.0,
        var allocations: MutableMap<AllocationType, Double> = mutableMapOf(),
        var lastUpdate: Long = System.currentTimeMillis()
    )
    
    // Energy Component
    data class EnergyComponent(
        var amount: Double = 1000.0,
        var generationRate: Double = 100.0, // per second
        var consumptionRate: Double = 50.0,  // per second
        var lastUpdate: Long = System.currentTimeMillis()
    )
    
    // Processing Task
    data class ProcessingTask(
        val id: Long,
        val taskType: TaskType,
        val complexity: Double,
        val priority: Int,
        val allocatedComputronium: Double,
        val progress: Double = 0.0,
        val isComplete: Boolean = false
    ) {
        enum class TaskType {
            SECURITY_BREACH_ATTEMPT,
            DATA_PROCESSING,
            NETWORK_OPTIMIZATION,
            STORAGE_COMPRESSION,
            AI_DECISION_MAKING
        }
    }
    
    /**
     * Convert energy to computronium
     */
    fun convertEnergyToComputronium(entityId: Long, energyAmount: Double): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val energy = entity.getComponent<EnergyComponent>() ?: return 0.0
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return 0.0
        
        // Check if enough energy available
        if (energy.amount < energyAmount) return 0.0
        
        // Calculate conversion
        val computroniumGained = energyAmount * ENERGY_TO_COMPUTRONIUM_RATIO
        
        // Apply conversion
        energy.amount -= energyAmount
        computronium.amount += computroniumGained
        
        // Update processing power
        updateProcessingPower(entityId)
        
        return computroniumGained
    }
    
    /**
     * Convert computronium back to energy (lossy process)
     */
    fun convertComputroniumToEnergy(entityId: Long, computroniumAmount: Double): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val energy = entity.getComponent<EnergyComponent>() ?: return 0.0
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return 0.0
        
        // Check if enough computronium available
        if (computronium.amount < computroniumAmount) return 0.0
        
        // Calculate conversion (lossy)
        val energyGained = computroniumAmount * COMPUTRONIUM_TO_ENERGY_RATIO
        
        // Apply conversion
        computronium.amount -= computroniumAmount
        energy.amount += energyGained
        
        // Update processing power
        updateProcessingPower(entityId)
        
        return energyGained
    }
    
    /**
     * Update processing power based on computronium amount
     */
    fun updateProcessingPower(entityId: Long) {
        val entity = entityManager.getEntity(entityId) ?: return
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return
        
        // Calculate base processing power
        var processingPower = BASE_PROCESSING_POWER
        
        // Add computronium bonus
        processingPower += computronium.amount * COMPUTRONIUM_PROCESSING_MULTIPLIER
        
        // Apply efficiency modifier
        processingPower *= computronium.efficiency
        
        computronium.processingPower = processingPower
    }
    
    /**
     * Allocate computronium to different functions
     */
    fun allocateComputronium(entityId: Long, allocationType: AllocationType, amount: Double): Boolean {
        val entity = entityManager.getEntity(entityId) ?: return false
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return false
        
        // Check if enough computronium available
        val currentAllocation = computronium.allocations[allocationType] ?: 0.0
        val totalAllocated = computronium.allocations.values.sum()
        val available = computronium.amount - totalAllocated + currentAllocation
        
        if (available < amount) return false
        
        // Update allocation
        computronium.allocations[allocationType] = amount
        
        return true
    }
    
    /**
     * Calculate processing efficiency
     */
    fun calculateEfficiency(entityId: Long): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return 0.0
        
        var efficiency = 1.0
        
        // Apply allocation efficiency bonuses
        computronium.allocations.forEach { (allocationType, amount) ->
            if (amount > 0) {
                efficiency *= allocationType.efficiency
            }
        }
        
        // Apply time-based decay
        val timeSinceUpdate = System.currentTimeMillis() - computronium.lastUpdate
        val decayCycles = timeSinceUpdate / 1000.0 // 1 second cycles
        efficiency *= EFFICIENCY_DECAY_RATE.pow(decayCycles)
        
        return maxOf(0.1, efficiency) // Minimum 10% efficiency
    }
    
    /**
     * Process a task using allocated computronium
     */
    fun processTask(entityId: Long, task: ProcessingTask, deltaTime: Double): ProcessingTask {
        val entity = entityManager.getEntity(entityId) ?: return task
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return task
        
        // Calculate processing power for this task type
        val allocationType = when (task.taskType) {
            ProcessingTask.TaskType.SECURITY_BREACH_ATTEMPT -> AllocationType.SECURITY
            ProcessingTask.TaskType.DATA_PROCESSING -> AllocationType.PROCESSING
            ProcessingTask.TaskType.NETWORK_OPTIMIZATION -> AllocationType.NETWORK
            ProcessingTask.TaskType.STORAGE_COMPRESSION -> AllocationType.STORAGE
            ProcessingTask.TaskType.AI_DECISION_MAKING -> AllocationType.PROCESSING
        }
        
        val allocatedPower = computronium.allocations[allocationType] ?: 0.0
        val efficiency = calculateEfficiency(entityId)
        
        // Calculate progress increment
        val progressIncrement = (allocatedPower * efficiency * deltaTime) / task.complexity
        val newProgress = task.progress + progressIncrement
        
        // Check if task is complete
        val isComplete = newProgress >= 1.0
        
        return task.copy(
            progress = minOf(1.0, newProgress),
            isComplete = isComplete
        )
    }
    
    /**
     * Optimize computronium allocation for maximum efficiency
     */
    fun optimizeAllocation(entityId: Long): Map<AllocationType, Double> {
        val entity = entityManager.getEntity(entityId) ?: return emptyMap()
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return emptyMap()
        
        val totalComputronium = computronium.amount
        val optimizedAllocation = mutableMapOf<AllocationType, Double>()
        
        // Sort allocation types by priority (lower number = higher priority)
        val sortedTypes = AllocationType.values().sortedBy { it.priority }
        
        var remainingComputronium = totalComputronium
        
        // Allocate based on priority and efficiency
        sortedTypes.forEach { allocationType ->
            val allocation = minOf(remainingComputronium * 0.3, remainingComputronium) // Max 30% per type
            if (allocation > 0) {
                optimizedAllocation[allocationType] = allocation
                remainingComputronium -= allocation
            }
        }
        
        return optimizedAllocation
    }
    
    /**
     * Get processing power for a specific allocation type
     */
    fun getProcessingPowerForType(entityId: Long, allocationType: AllocationType): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return 0.0
        
        val allocatedAmount = computronium.allocations[allocationType] ?: 0.0
        val efficiency = calculateEfficiency(entityId)
        
        return allocatedAmount * allocationType.efficiency * efficiency
    }
    
    /**
     * Update energy generation and consumption
     */
    fun updateEnergy(entityId: Long, deltaTime: Double) {
        val entity = entityManager.getEntity(entityId) ?: return
        val energy = entity.getComponent<EnergyComponent>() ?: return
        
        // Calculate net energy change
        val netChange = (energy.generationRate - energy.consumptionRate) * deltaTime
        energy.amount += netChange
        
        // Ensure energy doesn't go negative
        energy.amount = maxOf(0.0, energy.amount)
        
        energy.lastUpdate = System.currentTimeMillis()
    }
    
    /**
     * Calculate total system efficiency
     */
    fun calculateSystemEfficiency(entityId: Long): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val computronium = entity.getComponent<ComputroniumComponent>() ?: return 0.0
        val energy = entity.getComponent<EnergyComponent>() ?: return 0.0
        
        // Calculate efficiency based on multiple factors
        val computroniumEfficiency = calculateEfficiency(entityId)
        val energyEfficiency = if (energy.amount > 0) 1.0 else 0.0
        val allocationEfficiency = if (computronium.allocations.isNotEmpty()) 0.8 else 0.5
        
        return (computroniumEfficiency + energyEfficiency + allocationEfficiency) / 3.0
    }
} 