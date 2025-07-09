package rtsgame.ai

import rtsgame.core.*
import rtsgame.components.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Strategic AI system for high-level decision making
 */
class StrategicAISystem : System {
    private val random = Random.Default
    private var currentStrategy = Strategy.BALANCED
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Analyze current situation
        val analysis = analyzeSituation(world)
        
        // Update strategy based on analysis
        updateStrategy(analysis)
        
        // Make strategic decisions
        val decisions = makeStrategicDecisions(world, analysis)
        
        // Execute decisions
        executeDecisions(world, decisions)
    }
    
    private fun analyzeSituation(world: ECSWorld): SituationAnalysis {
        val myUnits = world.getEntitiesWithComponents(ComponentTypeId.OWNER, ComponentTypeId.ENTITY_TYPE)
            .filter { entityId ->
                val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
                val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
                owner?.teamId == 0 && entityType?.category == "unit"
            }
        
        val myBuildings = world.getEntitiesWithComponents(ComponentTypeId.OWNER, ComponentTypeId.ENTITY_TYPE)
            .filter { entityId ->
                val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
                val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
                owner?.teamId == 0 && entityType?.category == "building"
            }
        
        val enemyUnits = world.getEntitiesWithComponents(ComponentTypeId.OWNER, ComponentTypeId.ENTITY_TYPE)
            .filter { entityId ->
                val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
                val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
                owner?.teamId == 1 && entityType?.category == "unit"
            }
        
        val enemyBuildings = world.getEntitiesWithComponents(ComponentTypeId.OWNER, ComponentTypeId.ENTITY_TYPE)
            .filter { entityId ->
                val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
                val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
                owner?.teamId == 1 && entityType?.category == "building"
            }
        
        return SituationAnalysis(
            myUnitCount = myUnits.size,
            myBuildingCount = myBuildings.size,
            enemyUnitCount = enemyUnits.size,
            enemyBuildingCount = enemyBuildings.size,
            massIncome = 10.0,
            energyIncome = 15.0,
            currentMass = 100,
            currentEnergy = 150,
            extractorCount = 2,
            factoryCount = 1,
            militaryStrength = calculateMilitaryStrength(myUnits, world),
            enemyMilitaryStrength = calculateMilitaryStrength(enemyUnits, world),
            averageUnitDistance = 100.0,
            economicEfficiency = 1.0,
            threatProximity = 200.0
        )
    }
    
    private fun calculateMilitaryStrength(entities: List<EntityId>, world: ECSWorld): Double {
        return entities.sumOf { entityId ->
            val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
            when (entityType?.type) {
                "scout" -> 1.0
                "tank" -> 3.0
                "commander" -> 5.0
                else -> 0.5
            }
        }
    }
    
    private fun updateStrategy(analysis: SituationAnalysis) {
        currentStrategy = when {
            analysis.enemyMilitaryStrength > analysis.militaryStrength * 1.5 -> Strategy.DEFENSIVE_TURTLE
            analysis.militaryStrength > analysis.enemyMilitaryStrength * 1.5 -> Strategy.AGGRESSIVE_RUSH
            analysis.extractorCount < 3 -> Strategy.ECONOMY_FIRST
            analysis.enemyUnitCount > 0 && analysis.myUnitCount < 5 -> Strategy.HARASSMENT
            else -> Strategy.BALANCED
        }
    }
    
    private fun makeStrategicDecisions(world: ECSWorld, analysis: SituationAnalysis): List<StrategicDecision> {
        val decisions = mutableListOf<StrategicDecision>()
        
        when (currentStrategy) {
            Strategy.ECONOMY_FIRST -> {
                if (analysis.extractorCount < 3) {
                    decisions.add(StrategicDecision.BuildExtractor)
                }
            }
            Strategy.AGGRESSIVE_RUSH -> {
                if (analysis.myUnitCount < 10) {
                    decisions.add(StrategicDecision.BuildUnits)
                }
            }
            Strategy.DEFENSIVE_TURTLE -> {
                decisions.add(StrategicDecision.BuildDefenses)
            }
            Strategy.HARASSMENT -> {
                decisions.add(StrategicDecision.BuildScouts)
            }
            Strategy.BALANCED -> {
                if (random.nextBoolean()) {
                    decisions.add(StrategicDecision.BuildUnits)
                } else {
                    decisions.add(StrategicDecision.BuildExtractor)
                }
            }
        }
        
        return decisions
    }
    
    private fun executeDecisions(world: ECSWorld, decisions: List<StrategicDecision>) {
        decisions.forEach { decision ->
            when (decision) {
                StrategicDecision.BuildExtractor -> {
                    // Find a good location and build extractor
                    val position = findResourceLocation(world)
                    if (position != null) {
                        world.createEntity().let { entityId ->
                            world.addComponent(entityId, PositionComponent(position.x, position.y, 0f))
                            world.addComponent(entityId, OwnerComponent(0))
                            world.addComponent(entityId, EntityTypeComponent("massExtractor", "Mass Extractor", "building"))
                            world.addComponent(entityId, ResourceComponent("mass", 0f, 1000f, 10f))
                        }
                    }
                }
                StrategicDecision.BuildUnits -> {
                    // Build units at factory
                    val factories = world.getEntitiesWithComponents(ComponentTypeId.OWNER, ComponentTypeId.ENTITY_TYPE)
                        .filter { entityId ->
                            val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
                            val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
                            owner?.teamId == 0 && entityType?.type == "factory"
                        }
                    
                    factories.firstOrNull()?.let { factoryId ->
                        val factoryPos = world.getComponent<PositionComponent>(factoryId, ComponentTypeId.POSITION)
                        if (factoryPos != null) {
                            world.createEntity().let { entityId ->
                                world.addComponent(entityId, PositionComponent(factoryPos.x + 20f, factoryPos.y, 0f))
                                world.addComponent(entityId, OwnerComponent(0))
                                world.addComponent(entityId, EntityTypeComponent("tank", "Tank", "unit"))
                                world.addComponent(entityId, HealthComponent(100f, 100f))
                                world.addComponent(entityId, WeaponComponent(50f, 120f, 2f))
                            }
                        }
                    }
                }
                StrategicDecision.BuildDefenses -> {
                    // Build defensive structures
                }
                StrategicDecision.BuildScouts -> {
                    // Build scout units
                }
            }
        }
    }
    
    private fun findResourceLocation(world: ECSWorld): PositionComponent? {
        // Simple resource location finding
        return PositionComponent(100f + random.nextFloat() * 200f, 100f + random.nextFloat() * 200f, 0f)
    }
}

/**
 * Strategic decision types
 */
sealed class StrategicDecision {
    object BuildExtractor : StrategicDecision()
    object BuildUnits : StrategicDecision()
    object BuildDefenses : StrategicDecision()
    object BuildScouts : StrategicDecision()
}

/**
 * Strategy types
 */
enum class Strategy {
    ECONOMY_FIRST, AGGRESSIVE_RUSH, DEFENSIVE_TURTLE, HARASSMENT, BALANCED
}

/**
 * Situation analysis data
 */
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