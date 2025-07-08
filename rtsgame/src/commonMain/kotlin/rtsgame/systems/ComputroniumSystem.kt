import kotlin.math.*
package rtsgame.systems
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.config.*
import rtsgame.entities.*
import rtsgame.codec.DeterministicRandom
import kotlin.math.*

/**
 * Computronium Management System - Advanced resource for computational warfare
 * Direct translation from JS with exact behavior preservation
 */
class ComputroniumManager(val team: String) {
    
    // Core state
    var computronium: Double = 10.0 // Starting amount
    var computroniumIncome: Double = 0.0
    internal var computroniumCores = mutableListOf<ComputroniumCore>()
    internal var activeProcesses = mutableListOf<ComputationalProcess>()
    
    // Efficiency tracking
    internal var lastEfficiencyUpdate: Long = 0
    internal var currentEfficiency: Double = 1.0
    internal var diningPhilosophersContention: Double = 0.0
    
    // Proof of Work state
    internal var proofOfWorkQueue = mutableListOf<ProofOfWorkTask>()
    internal var completedProofs: Int = 0
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val currentTime = simulation.gameState.gameTime.toLong()
        
        // Update cores and calculate income
        updateCores(simulation, deltaTime)
        
        // Process computational tasks
        processComputationalTasks(deltaTime)
        
        // Handle proof of work
        processProofOfWork(simulation, deltaTime)
        
        // Update efficiency metrics
        if (currentTime - lastEfficiencyUpdate >= 60) { // Every second at 60 FPS
            updateEfficiency()
            lastEfficiencyUpdate = currentTime
        }
        
        // Generate computronium income
        val teamResources = simulation.resources[team]
        if (teamResources != null) {
            teamResources.computronium += computroniumIncome.toInt()
            teamResources.computroniumIncome = computroniumIncome
            computronium = teamResources.computronium.toDouble()
        }
    }
    
    internal fun updateCores(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val buildings = simulation.buildings.filter { it.team == team }
        
        // Find computronium-generating buildings
        val extractors = buildings.filter { 
            it.type == "computroniumExtractor" && !it.isUnderConstruction && it.hp > 0 
        }
        val cores = buildings.filter { 
            it.type == "advancedComputroniumCore" && !it.isUnderConstruction && it.hp > 0 
        }
        
        // Update core list
        computroniumCores.clear()
        
        extractors.forEach { building ->
            computroniumCores.add(ComputroniumCore(
                id = building.id,
                type = CoreType.EXTRACTOR,
                x = building.x,
                y = building.y,
                baseRate = COMPUTRONIUM_CONFIG.BASE_GENERATION_RATE,
                efficiency = calculateCoreEfficiency(building, buildings),
                powerLevel = if (building.isPowered) 1.0 else 0.0
            ))
        }
        
        cores.forEach { building ->
            computroniumCores.add(ComputroniumCore(
                id = building.id,
                type = CoreType.ADVANCED,
                x = building.x,
                y = building.y,
                baseRate = COMPUTRONIUM_CONFIG.BASE_GENERATION_RATE * 4.0,
                efficiency = calculateCoreEfficiency(building, buildings),
                powerLevel = if (building.isPowered) 1.0 else 0.0
            ))
        }
        
        // Calculate total income
        computroniumIncome = computroniumCores.sumOf { core ->
            core.baseRate * core.efficiency * core.powerLevel
        }
    }
    
    internal fun calculateCoreEfficiency(core: Building, allBuildings: List<Building>): Double {
        // Base efficiency
        var efficiency = COMPUTRONIUM_CONFIG.MAX_CORE_EFFICIENCY
        
        // Distance to other cores affects efficiency (dining philosophers problem)
        val nearbyCore = allBuildings.filter { 
            it != core && 
            (it.type == "computroniumExtractor" || it.type == "advancedComputroniumCore") &&
            !it.isUnderConstruction
        }
        
        nearbyCore.forEach { other ->
            val distance = sqrt((core.x - other.x).pow(2) + (core.y - other.y).pow(2))
            if (distance < 200.0) { // Interference range
                val interferenceRatio = 1.0 - (distance / 200.0)
                efficiency -= COMPUTRONIUM_CONFIG.DINING_PHILOSOPHERS_PENALTY * interferenceRatio
            }
        }
        
        return efficiency.coerceIn(COMPUTRONIUM_CONFIG.MIN_CORE_EFFICIENCY, COMPUTRONIUM_CONFIG.MAX_CORE_EFFICIENCY)
    }
    
    internal fun processComputationalTasks(deltaTime: Double) {
        activeProcesses.removeAll { process ->
            process.progress += deltaTime * currentEfficiency
            
            if (process.progress >= process.duration) {
                completeProcess(process)
                true
            } else false
        }
    }
    
    internal fun completeProcess(process: ComputationalProcess) {
        when (process.type) {
            ProcessType.COMMAND_LATENCY_REDUCTION -> {
                // Improve command response time
                val improvement = process.computroniumCost * COMPUTRONIUM_CONFIG.C2_COMPUTRONIUM_BONUS
                // Apply to command hierarchy (would need reference)
            }
            ProcessType.TACTICAL_ANALYSIS -> {
                // Improve AI decision making
                // Results would be applied to strategic AI
            }
            ProcessType.RESOURCE_OPTIMIZATION -> {
                // Temporary efficiency boost
                currentEfficiency += 0.1
            }
            ProcessType.PROOF_OF_WORK -> {
                completedProofs++
            }
        }
    }
    
    internal fun processProofOfWork(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        if (proofOfWorkQueue.isEmpty()) return
        
        val availableComputronium = minOf(computronium, 10.0) // Limit spending per frame
        var remainingBudget = availableComputronium
        
        proofOfWorkQueue.removeAll { task ->
            if (remainingBudget <= 0) return@removeAll false
            
            val costThisFrame = minOf(task.remainingCost, remainingBudget)
            task.remainingCost -= costThisFrame
            remainingBudget -= costThisFrame
            computronium -= costThisFrame
            
            if (task.remainingCost <= 0) {
                executeProofOfWorkResult(task, simulation)
                true
            } else false
        }
    }
    
    internal fun executeProofOfWorkResult(task: ProofOfWorkTask, simulation: rtsgame.core.Simulation) {
        when (task.operation) {
            PoWOperation.ENHANCED_TARGETING -> {
                // Improve accuracy for all units in area
                val units = simulation.units.filter { unit ->
                    unit.team == team &&
                    sqrt((unit.x - task.x).pow(2) + (unit.y - task.y).pow(2)) <= task.radius
                }
                
                units.forEach { unit ->
                    // Temporary accuracy boost (would need unit enhancement system)
                    unit.veterancyLevel = when (unit.veterancyLevel) {
                        VETERANCY_LEVELS.GREEN -> VETERANCY_LEVELS.REGULAR
                        VETERANCY_LEVELS.REGULAR -> VETERANCY_LEVELS.VETERAN
                        else -> unit.veterancyLevel
                    }
                }
            }
            PoWOperation.RESOURCE_BURST -> {
                // Temporary resource generation boost
                val teamResources = simulation.resources[team]
                teamResources?.let { resources ->
                    resources.mass += (task.totalCost * 10).toInt()
                    resources.energy += (task.totalCost * 15).toInt()
                }
            }
            PoWOperation.ELECTRONIC_WARFARE -> {
                // Disrupt enemy computronium in area
                val enemyManagers = getEnemyComputroniumManagers(simulation)
                enemyManagers.forEach { manager ->
                    manager.addInterference(task.x, task.y, task.radius, task.totalCost)
                }
            }
        }
    }
    
    fun addInterference(x: Double, y: Double, radius: Double, strength: Double) {
        // Reduce efficiency of cores in affected area
        computroniumCores.forEach { core ->
            val distance = sqrt((core.x - x).pow(2) + (core.y - y).pow(2))
            if (distance <= radius) {
                val interferenceRatio = 1.0 - (distance / radius)
                core.efficiency *= (1.0 - interferenceRatio * strength * 0.1).coerceAtLeast(0.1)
            }
        }
    }
    
    internal fun updateEfficiency() {
        // Calculate dining philosophers contention
        diningPhilosophersContention = 0.0
        
        for (i in computroniumCores.indices) {
            for (j in i + 1 until computroniumCores.size) {
                val core1 = computroniumCores[i]
                val core2 = computroniumCores[j]
                val distance = sqrt((core1.x - core2.x).pow(2) + (core1.y - core2.y).pow(2))
                
                if (distance < 300.0) { // Contention range
                    diningPhilosophersContention += (300.0 - distance) / 300.0
                }
            }
        }
        
        // Apply contention to global efficiency
        val contentionPenalty = diningPhilosophersContention * COMPUTRONIUM_CONFIG.DINING_PHILOSOPHERS_PENALTY
        currentEfficiency = (1.0 - contentionPenalty).coerceAtLeast(COMPUTRONIUM_CONFIG.MIN_CORE_EFFICIENCY)
    }
    
    // Public interface for command hierarchy
    fun canAffordOperation(cost: Double): Boolean = computronium >= cost
    
    fun requestComputationalProcess(type: ProcessType, cost: Double, duration: Double): Boolean {
        if (!canAffordOperation(cost)) return false
        
        computronium -= cost
        activeProcesses.add(ComputationalProcess(
            type = type,
            computroniumCost = cost,
            duration = duration,
            progress = 0.0
        ))
        
        return true
    }
    
    fun requestProofOfWork(operation: PoWOperation, x: Double, y: Double, radius: Double = 100.0): Boolean {
        val cost = COMPUTRONIUM_CONFIG.COMPUTATIONAL_WARFARE_COST
        if (!canAffordOperation(cost)) return false
        
        proofOfWorkQueue.add(ProofOfWorkTask(
            operation = operation,
            x = x,
            y = y,
            radius = radius,
            totalCost = cost,
            remainingCost = cost
        ))
        
        return true
    }
    
    fun getCommandLatencyReduction(): Double {
        val computroniumBonus = completedProofs * COMPUTRONIUM_CONFIG.C2_COMPUTRONIUM_BONUS
        return COMPUTRONIUM_CONFIG.C2_LATENCY_BASE - computroniumBonus
    }
    
    fun getComputroniumStatus(): ComputroniumStatus {
        return ComputroniumStatus(
            available = computronium,
            income = computroniumIncome,
            efficiency = currentEfficiency,
            coreCount = computroniumCores.size,
            activeProcesses = activeProcesses.size,
            pendingProofs = proofOfWorkQueue.size,
            contention = diningPhilosophersContention
        )
    }
    
    internal fun getEnemyComputroniumManagers(simulation: rtsgame.core.Simulation): List<ComputroniumManager> {
        // This would need access to other team managers
        // For now, return empty list
        return emptyList()
    }
}

// Supporting data structures
data class ComputroniumCore(
    val id: Int,
    val type: CoreType,
    val x: Double,
    val y: Double,
    val baseRate: Double,
    var efficiency: Double,
    var powerLevel: Double
)

enum class CoreType {
    EXTRACTOR, ADVANCED
}

data class ComputationalProcess(
    val type: ProcessType,
    val computroniumCost: Double,
    val duration: Double,
    var progress: Double
)

enum class ProcessType {
    COMMAND_LATENCY_REDUCTION,
    TACTICAL_ANALYSIS,
    RESOURCE_OPTIMIZATION,
    PROOF_OF_WORK
}

data class ProofOfWorkTask(
    val operation: PoWOperation,
    val x: Double,
    val y: Double,
    val radius: Double,
    val totalCost: Double,
    var remainingCost: Double
)

enum class PoWOperation {
    ENHANCED_TARGETING,
    RESOURCE_BURST,
    ELECTRONIC_WARFARE
}

data class ComputroniumStatus(
    val available: Double,
    val income: Double,
    val efficiency: Double,
    val coreCount: Int,
    val activeProcesses: Int,
    val pendingProofs: Int,
    val contention: Double
)

internal val rtsgame.core.Simulation.units: List<GameUnit>
    get() = entityManager.units.mapNotNull { it as? GameUnit }

internal val rtsgame.core.Simulation.buildings: List<Building>
    get() = entityManager.buildings.mapNotNull { it as? Building }