import kotlin.math.*
package rtsgame.ai
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.config.*
import rtsgame.entities.*
import rtsgame.codec.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Strategic AI - High-level decision making for RTS teams
 * Direct translation from JS with exact decision patterns preserved
 */
class StrategicAI(
    val team: String,
    internal val random: Random
) {
    // Decision state
    internal var lastDecisionTime: Long = 0
    internal var currentStrategy: Strategy = Strategy.ECONOMY_FIRST
    internal var economicPhase: String = ECONOMIC_PHASES.SURVIVAL
    internal var threatLevel: ThreatLevel = ThreatLevel.LOW
    
    // Strategic goals
    internal var targetExtractorCount: Int = 3
    internal var targetFactoryCount: Int = 1
    internal var militaryFocus: MilitaryFocus = MilitaryFocus.BALANCED
    
    // Decision history for learning
    internal val decisionHistory = mutableListOf<AIDecision>()
    internal val strategyScores = mutableMapOf<Strategy, Double>()
    
    fun makeDecisions(simulation: rtsgame.core.Simulation): List<RTSRequest> {
        val currentTime = simulation.gameState.gameTime.toLong()
        
        // Only make decisions at intervals
        if (currentTime - lastDecisionTime < AI_DECISION_INTERVAL) {
            return emptyList()
        }
        
        lastDecisionTime = currentTime
        
        val decisions = mutableListOf<RTSRequest>()
        
        // Analyze current situation
        val analysis = analyzeSituation(simulation)
        updateStrategy(analysis)
        
        // Make strategic decisions
        decisions.addAll(makeEconomicDecisions(simulation, analysis))
        decisions.addAll(makeMilitaryDecisions(simulation, analysis))
        decisions.addAll(makeProductionDecisions(simulation, analysis))
        decisions.addAll(makeTacticalDecisions(simulation, analysis))
        
        // Record decisions for learning
        recordDecisions(decisions, analysis)
        
        return decisions
    }
    
    internal fun analyzeSituation(simulation: rtsgame.core.Simulation): SituationAnalysis {
        val myUnits = simulation.units.filter { it.team == team && !it.isDead }
        val myBuildings = simulation.buildings.filter { it.team == team && it.hp > 0 }
        val enemyUnits = simulation.units.filter { it.team != team && !it.isDead }
        val enemyBuildings = simulation.buildings.filter { it.team != team && it.hp > 0 }
        
        val resources = simulation.resources[team] ?: TeamResourcesExtended(0, 0, 0)
        
        return SituationAnalysis(
            myUnitCount = myUnits.size,
            myBuildingCount = myBuildings.size,
            enemyUnitCount = enemyUnits.size,
            enemyBuildingCount = enemyBuildings.size,
            massIncome = resources.massIncome,
            energyIncome = resources.energyIncome,
            currentMass = resources.mass,
            currentEnergy = resources.energy,
            extractorCount = myBuildings.count { it.type == "massExtractor" },
            factoryCount = myBuildings.count { it.type.contains("Factory") },
            militaryStrength = calculateMilitaryStrength(myUnits),
            enemyMilitaryStrength = calculateMilitaryStrength(enemyUnits),
            averageUnitDistance = calculateAverageDistanceToEnemy(myUnits, enemyUnits),
            economicEfficiency = calculateEconomicEfficiency(resources),
            threatProximity = calculateThreatProximity(myBuildings, enemyUnits)
        )
    }
    
    internal fun makeEconomicDecisions(
        simulation: rtsgame.core.Simulation, 
        analysis: SituationAnalysis
    ): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        
        // Determine economic priorities
        when (economicPhase) {
            ECONOMIC_PHASES.SURVIVAL -> {
                if (analysis.extractorCount < 2) {
                    decisions.addAll(buildExtractors(simulation, 2 - analysis.extractorCount))
                }
                if (analysis.extractorCount >= 2) {
                    economicPhase = ECONOMIC_PHASES.EXPANSION
                }
            }
            
            ECONOMIC_PHASES.EXPANSION -> {
                if (analysis.extractorCount < 4 && analysis.currentMass > 100) {
                    decisions.addAll(buildExtractors(simulation, 1))
                }
                if (analysis.extractorCount >= 4) {
                    economicPhase = ECONOMIC_PHASES.ADVANCED
                }
            }
            
            ECONOMIC_PHASES.ADVANCED -> {
                if (analysis.extractorCount < 6 && analysis.currentMass > 200) {
                    decisions.addAll(buildExtractors(simulation, 1))
                }
                if (analysis.energyIncome < analysis.massIncome * 1.5) {
                    decisions.addAll(buildEnergyPlants(simulation, 1))
                }
            }
        }
        
        return decisions
    }
    
    internal fun makeMilitaryDecisions(
        simulation: rtsgame.core.Simulation,
        analysis: SituationAnalysis
    ): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        
        // Assess military needs
        val militaryRatio = if (analysis.enemyMilitaryStrength > 0) {
            analysis.militaryStrength / analysis.enemyMilitaryStrength
        } else 1.0
        
        when {
            militaryRatio < 0.5 -> {
                // We're weak - build defensively
                threatLevel = ThreatLevel.HIGH
                decisions.addAll(buildDefensiveUnits(simulation, analysis))
            }
            militaryRatio < 0.8 -> {
                // Roughly equal - balanced production
                threatLevel = ThreatLevel.MEDIUM
                decisions.addAll(buildBalancedArmy(simulation, analysis))
            }
            militaryRatio > 1.5 -> {
                // We're strong - go aggressive
                threatLevel = ThreatLevel.LOW
                decisions.addAll(buildOffensiveUnits(simulation, analysis))
                decisions.addAll(coordinateAttacks(simulation, analysis))
            }
        }
        
        return decisions
    }
    
    internal fun makeProductionDecisions(
        simulation: rtsgame.core.Simulation,
        analysis: SituationAnalysis
    ): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        val factories = simulation.buildings.filter { 
            it.team == team && it.type.contains("Factory") && !it.isUnderConstruction 
        }
        
        // Build factories if needed
        if (analysis.factoryCount < targetFactoryCount && analysis.currentMass > 200) {
            decisions.addAll(buildFactories(simulation, 1))
        }
        
        // Queue unit production
        for (factory in factories) {
            val queueSize = (factory.getProductionQueueInfo() as Indexed<ProductionItem>).size
            if (queueSize < 2) { // Keep 2 items in queue
                val unitType = selectUnitForProduction(analysis)
                decisions.add(RTSRequest.QueueUnit(
                    factoryId = factory.id,
                    unitType = unitType,
                    frameNumber = simulation.gameState.gameTime.toLong(),
                    timestamp = simulation.gameState.gameTime.toDouble()
                ))
            }
        }
        
        return decisions
    }
    
    internal fun makeTacticalDecisions(
        simulation: rtsgame.core.Simulation,
        analysis: SituationAnalysis
    ): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        val myUnits = simulation.units.filter { it.team == team && !it.isDead }
        
        // Coordinate idle units
        val idleUnits = myUnits.filter { it.currentCommand == null }
        
        when (currentStrategy) {
            Strategy.AGGRESSIVE_RUSH -> {
                decisions.addAll(coordinateRush(simulation, idleUnits))
            }
            Strategy.DEFENSIVE_TURTLE -> {
                decisions.addAll(coordinateDefense(simulation, idleUnits))
            }
            Strategy.ECONOMY_FIRST -> {
                decisions.addAll(coordinateEconomicSupport(simulation, idleUnits))
            }
            Strategy.HARASSMENT -> {
                decisions.addAll(coordinateHarassment(simulation, idleUnits))
            }
            Strategy.BALANCED -> {
                // No special tactical action for balanced
            }
        }
        
        return decisions
    }
    
    internal fun buildExtractors(simulation: rtsgame.core.Simulation, count: Int): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        val commanders = simulation.units.filter { it.team == team && it.type == "commander" }
        val resourceNodes = simulation.resourceNodes.filter { !it.occupied }
        
        var built = 0
        for (commander in commanders) {
            if (built >= count) break
            
            val nearestNode = resourceNodes.minByOrNull { node ->
                sqrt((node.x - commander.x).pow(2) + (node.y - commander.y).pow(2))
            }
            
            nearestNode?.let { node ->
                decisions.add(RTSRequest.BuildStructure(
                    builderId = commander.id,
                    buildingType = if (node.type == RESOURCE_TYPES.MASS) "massExtractor" else "energyExtractor",
                    x = node.x,
                    y = node.y,
                    frameNumber = simulation.gameState.gameTime.toLong(),
                    timestamp = simulation.gameState.gameTime.toDouble()
                ))
                node.occupied = true
                built++
            }
        }
        
        return decisions
    }
    
    internal fun selectUnitForProduction(analysis: SituationAnalysis): String {
        return when (militaryFocus) {
            MilitaryFocus.SCOUTS -> if (random.nextDouble() < 0.7) "scout" else "tank"
            MilitaryFocus.HEAVY -> if (random.nextDouble() < 0.6) "tank" else "artillery"
            MilitaryFocus.ARTILLERY -> if (random.nextDouble() < 0.5) "artillery" else "tank"
            MilitaryFocus.BALANCED -> {
                val options = listOf("scout", "tank", "artillery")
                options[random.nextInt(options.size)]
            }
        }
    }
    
    internal fun coordinateAttacks(
        simulation: rtsgame.core.Simulation,
        analysis: SituationAnalysis
    ): List<RTSRequest> {
        val decisions = mutableListOf<RTSRequest>()
        val myUnits = simulation.units.filter { 
            it.team == team && !it.isDead && it.type in listOf("tank", "artillery")
        }
        
        if (myUnits.size >= 3) { // Attack with groups of 3+
            val enemyBuildings = simulation.buildings.filter { it.team != team && it.hp > 0 }
            val target = enemyBuildings.minByOrNull { building ->
                myUnits.minOf { unit ->
                    sqrt((building.x - unit.x).pow(2) + (building.y - unit.y).pow(2))
                }
            }
            
            target?.let { targetBuilding ->
                for (unit in myUnits.take(3)) {
                    decisions.add(RTSRequest.MoveUnit(
                        unitId = unit.id,
                        x = targetBuilding.x + random.nextDouble(-20.0, 20.0),
                        y = targetBuilding.y + random.nextDouble(-20.0, 20.0),
                        frameNumber = simulation.gameState.gameTime.toLong(),
                        timestamp = simulation.gameState.gameTime.toDouble()
                    ))
                }
            }
        }
        
        return decisions
    }
    
    internal fun updateStrategy(analysis: SituationAnalysis) {
        val previousStrategy = currentStrategy
        
        // Strategy selection logic
        currentStrategy = when {
            analysis.enemyMilitaryStrength > analysis.militaryStrength * 1.5 -> Strategy.DEFENSIVE_TURTLE
            analysis.militaryStrength > analysis.enemyMilitaryStrength * 1.5 -> Strategy.AGGRESSIVE_RUSH
            analysis.extractorCount < 3 -> Strategy.ECONOMY_FIRST
            analysis.enemyUnitCount > 0 && analysis.myUnitCount < 5 -> Strategy.HARASSMENT
            else -> Strategy.BALANCED
        }
        
        // Update strategy score if changed
        if (previousStrategy != currentStrategy) {
            recordStrategyChange(previousStrategy, currentStrategy, analysis)
        }
    }
    
    internal fun recordDecisions(decisions: List<RTSRequest>, analysis: SituationAnalysis) {
        for (decision in decisions) {
            decisionHistory.add(AIDecision(
                type = decision::class.simpleName ?: "Unknown",
                timestamp = decision.timestamp,
                strategy = currentStrategy,
                analysis = analysis,
                success = null // Will be evaluated later
            ))
        }
        
        // Limit history size
        if (decisionHistory.size > 1000) {
            decisionHistory.removeAt(0)
        }
    }
    
    // Helper calculation functions
    internal fun calculateMilitaryStrength(units: List<GameUnit>): Double {
        return units.sumOf { unit ->
            when (unit.type) {
                "scout" -> 1.0
                "tank" -> 3.0
                "artillery" -> 2.5
                "fighter" -> 2.0
                "submarine" -> 2.5
                else -> 0.5
            }
        }
    }
    
    internal fun calculateAverageDistanceToEnemy(myUnits: List<GameUnit>, enemyUnits: List<GameUnit>): Double {
        if (myUnits.isEmpty() || enemyUnits.isEmpty()) return Double.MAX_VALUE
        
        return myUnits.sumOf { myUnit ->
            enemyUnits.minOf { enemyUnit ->
                sqrt((myUnit.x - enemyUnit.x).pow(2) + (myUnit.y - enemyUnit.y).pow(2))
            }
        } / myUnits.size
    }
    
    internal fun calculateEconomicEfficiency(resources: TeamResourcesExtended): Double {
        val totalIncome = resources.massIncome + resources.energyIncome
        val resourceBalance = minOf(resources.mass, resources.energy).toDouble()
        return totalIncome * (1.0 + resourceBalance / 1000.0)
    }
    
    internal fun calculateThreatProximity(buildings: List<Building>, enemyUnits: List<GameUnit>): Double {
        if (buildings.isEmpty() || enemyUnits.isEmpty()) return 0.0
        
        return buildings.minOf { building ->
            enemyUnits.minOf { unit ->
                sqrt((building.x - unit.x).pow(2) + (building.y - unit.y).pow(2))
            }
        }
    }
    
    // Stub implementations for remaining methods
    internal fun buildEnergyPlants(simulation: rtsgame.core.Simulation, count: Int) = emptyList<RTSRequest>()
    internal fun buildDefensiveUnits(simulation: rtsgame.core.Simulation, analysis: SituationAnalysis) = emptyList<RTSRequest>()
    internal fun buildBalancedArmy(simulation: rtsgame.core.Simulation, analysis: SituationAnalysis) = emptyList<RTSRequest>()
    internal fun buildOffensiveUnits(simulation: rtsgame.core.Simulation, analysis: SituationAnalysis) = emptyList<RTSRequest>()
    internal fun buildFactories(simulation: rtsgame.core.Simulation, count: Int) = emptyList<RTSRequest>()
    internal fun coordinateRush(simulation: rtsgame.core.Simulation, units: List<GameUnit>) = emptyList<RTSRequest>()
    internal fun coordinateDefense(simulation: rtsgame.core.Simulation, units: List<GameUnit>) = emptyList<RTSRequest>()
    internal fun coordinateEconomicSupport(simulation: rtsgame.core.Simulation, units: List<GameUnit>) = emptyList<RTSRequest>()
    internal fun coordinateHarassment(simulation: rtsgame.core.Simulation, units: List<GameUnit>) = emptyList<RTSRequest>()
    internal fun recordStrategyChange(old: Strategy, new: Strategy, analysis: SituationAnalysis) {}
}

// Supporting data classes and enums
enum class Strategy {
    ECONOMY_FIRST, AGGRESSIVE_RUSH, DEFENSIVE_TURTLE, HARASSMENT, BALANCED
}

enum class ThreatLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class MilitaryFocus {
    SCOUTS, HEAVY, ARTILLERY, BALANCED
}

data class SituationAnalysis(
    val myUnitCount: Int,
    val myBuildingCount: Int,
    val enemyUnitCount: Int,
    val enemyBuildingCount: Int,
    val massIncome: Double,
    val energyIncome: Double,
    val currentMass: Int,
    val currentEnergy: Int,
    val extractorCount: Int,
    val factoryCount: Int,
    val militaryStrength: Double,
    val enemyMilitaryStrength: Double,
    val averageUnitDistance: Double,
    val economicEfficiency: Double,
    val threatProximity: Double
)

data class AIDecision(
    val type: String,
    val timestamp: Double,
    val strategy: Strategy,
    val analysis: SituationAnalysis,
    var success: Boolean?
)

internal val rtsgame.core.Simulation.units: List<GameUnit>
    get() = entityManager.units.mapNotNull { it as? GameUnit }

internal val rtsgame.core.Simulation.buildings: List<Building>
    get() = entityManager.buildings.mapNotNull { it as? Building }