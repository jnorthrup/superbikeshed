package rtsgame.entities

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import rtsgame.config.*
import rtsgame.codec.*
import rtsgame.core.Simulation // Ensure Simulation is imported
import rtsgame.core.TeamResourcesExtended // For resource checking
import kotlin.math.*

/**
 * Building system - Production, resource generation, tech progression
 * Direct translation from JS with exact behavior preservation
 */
class Building(
    var id: Int,
    val buildingTypeName: String, // Name of the building type, e.g., "landFactory"
    var team: String,
    var x: Double,
    var y: Double,
    // Simulation reference for accessing game state, entity manager, etc.
    private val simulationInstance: Simulation
) {
    val type: BuildingType = BUILDING_TYPES.byName(buildingTypeName)
        ?: throw IllegalArgumentException("Unknown building type: $buildingTypeName")

    var hp: Double = type.health
    var maxHp: Double = type.maxHp ?: type.health // Use maxHp from type if available, else health

    // Construction state
    var isUnderConstruction: Boolean = true
    var constructionProgress: Double = 0.0 // Progress in frames or seconds, ensure consistency
    val constructionTime: Int = type.buildTime // Assuming buildTime is in frames from BuildingType

    // Production state
    var productionQueue: MutableList<ProductionItem> = mutableListOf()
    var currentProduction: ProductionItem? = null
    var productionProgressInternal: Double = 0.0 // Renamed to avoid conflict with constructionProgress if used differently

    // Resource generation
    val income: Double = type.resourceGeneration?.amount ?: 0.0
    val resourceType: String? = type.resourceGeneration?.type

    // Rally point for unit production
    var rallyX: Double = x + (type.size / 2.0) // Default rally point slightly offset
    var rallyY: Double = y + (type.size / 2.0)

    // Power and connectivity (simplified for now)
    var isPowered: Boolean = true
    val powerConsumption: Double = 0.0 // TODO: Get from BuildingType if defined
    val powerGeneration: Double = 0.0  // TODO: Get from BuildingType if defined
    
    // Computronium Core (if applicable)
    val hasComputroniumCore: Boolean = type.hasComputroniumCore ?: false
    val coreEfficiency: Double = type.coreEfficiency ?: 1.0
    // private var computroniumCoreInstance: ComputroniumCore? = null // TODO: When ComputroniumSystem is ported

    init {
        // Specific setup based on BuildingType if needed, most properties now come from BuildingType directly
        // Example: if (type.name == "Land Factory") { this.rallyX = x + type.size; }

        // If it has a computronium core, register it with the manager
        // if (hasComputroniumCore) {
        //     val manager = simulationInstance.computroniumManagers[team]
        //     // manager?.addCore(this, coreEfficiency) // `this` refers to Building instance
        // }
    }
    
    fun update(simulation: Simulation, deltaTime: Double) {
        if (hp <= 0) return

        if (isUnderConstruction) {
            // Assuming constructionTime is in seconds, and deltaTime is in seconds.
            // JS version incremented progress by 1 per frame.
            // To match, if buildTime in BuildingType is frames, this should be:
            // constructionProgress += 1.0
            // If buildTime is seconds:
            constructionProgress += deltaTime

            if (constructionProgress >= type.buildTime.toDouble()) { // Compare with type's buildTime
                isUnderConstruction = false
                constructionProgress = type.buildTime.toDouble()
                simulationInstance.gameState.addEvent("build_complete", "$buildingTypeName constructed by $team", 1, x to y)
            }
            return
        }

        // updatePowerStatus(simulation) // TODO: Port power logic
        // if (!isPowered && type.name != "Energy Extractor") return // Example building type name

        if (resourceType != null && income > 0.0 && isPowered) {
            generateResources(simulation, deltaTime) // Pass deltaTime
        }

        if (isPowered) {
            updateProduction(simulation, deltaTime)
        }
    }
    
    private fun generateResources(simulation: Simulation, deltaTime: Double) {
        val teamRes = simulation.resources[team] ?: return
        
        val incomeThisTick = income * deltaTime // income is per second, scale by deltaTime
        
        when (resourceType) {
            RESOURCE_TYPES.MASS -> {
                teamRes.mass += incomeThisTick
                teamRes.massIncome = income // This should be the per-second rate for UI display
            }
            RESOURCE_TYPES.ENERGY -> {
                teamRes.energy += incomeThisTick
                teamRes.energyIncome = income
            }
            "COMPUTRONIUM" -> {
                teamRes.computronium += incomeThisTick
                teamRes.computroniumIncome = income
            }
        }
    }
    
    private fun updateProduction(simulation: Simulation, deltaTime: Double) {
        if (currentProduction == null && productionQueue.isNotEmpty()) {
            val nextItem = productionQueue.first()
            val unitTypeData = UNIT_TYPES.byName(nextItem.type)
            if (unitTypeData != null) {
                val teamRes = simulation.resources[team]!!
                // Check cost for starting production (full cost, not per tick)
                if (teamRes.mass >= unitTypeData.cost.mass &&
                    teamRes.energy >= unitTypeData.cost.energy &&
                    teamRes.computronium >= (unitTypeData.cost.computronium ?: 0.0)
                ) {
                    // Deduct full cost when starting
                    teamRes.mass -= unitTypeData.cost.mass.toDouble()
                    teamRes.energy -= unitTypeData.cost.energy.toDouble()
                    teamRes.computronium -= (unitTypeData.cost.computronium ?: 0).toDouble()

                    currentProduction = productionQueue.removeAt(0)
                    productionProgressInternal = 0.0
                    simulationInstance.gameState.addEvent("production_start", "Started producing ${currentProduction!!.type}", 0, x to y)
                } else {
                    // Not enough resources to start
                    // simulationInstance.gameState.addEvent("production_stall", "Cannot start ${nextItem.type}, insufficient resources", 1, x to y)
                    return
                }
            } else {
                 // simulationInstance.gameState.addEvent("production_error", "Unknown unit type ${nextItem.type} in queue", 2, x to y)
                productionQueue.removeAt(0) // Remove invalid item
                return
            }
        }

        currentProduction?.let { currentItem ->
            val unitTypeToProduce = UNIT_TYPES.byName(currentItem.type)
                ?: run {
                    // simulationInstance.gameState.addEvent("production_error", "Invalid unit type ${currentItem.type} during production", 2, x to y)
                    currentProduction = null // Clear invalid current production
                    return
                }

            val unitBuildTime = unitTypeToProduce.buildTime.toDouble()
            if (unitBuildTime <= 0) {
                // simulationInstance.gameState.addEvent("production_error", "Invalid build time for ${unitTypeToProduce.name}", 2, x to y)
                currentProduction = null
                return
            }

            productionProgressInternal += deltaTime

            if (productionProgressInternal >= unitBuildTime) {
                spawnUnit(simulation, currentItem.type)
                currentProduction = null
                productionProgressInternal = 0.0
            }
        }
    }
    
    private fun spawnUnit(simulation: Simulation, unitTypeNameToSpawn: String) {
        // Ensure UNIT_TYPES.byName returns a valid UnitType before creating GameUnit
        val unitTypeData = UNIT_TYPES.byName(unitTypeNameToSpawn)
            ?: run {
                // simulationInstance.gameState.addEvent("spawn_fail", "Unknown unit type $unitTypeNameToSpawn for spawning", 2, rallyX to rallyY)
                return
            }

        val newUnit = GameUnit(
            id = simulation.entityManager.nextEntityId++,
            unitTypeName = unitTypeNameToSpawn, // Pass the name
            team = team,
            x = rallyX,
            y = rallyY,
            simulation = simulation // Pass simulation instance
        )
        simulation.entityManager.addUnit(newUnit)
        // simulationInstance.gameState.addEvent("unit_spawned", "$unitTypeName produced", 1, rallyX to rallyY)

        // TODO: Port Effect class and add spawn effect
        // val effect = Effect(x = rallyX, y = rallyY, type = "spawn", duration = 30)
        // simulation.entityManager.addEffect(effect)
    }

    fun queueProduction(unitTypeName: String): Boolean {
        val unitTypeData = UNIT_TYPES.byName(unitTypeName) ?: return false // Ensure unit type exists
        // Check if this building can produce this unit type
        if (type.buildList?.contains(unitTypeName) != true && type.produces?.contains(unitTypeName) != true) {
            // simulationInstance.gameState.addEvent("production_fail", "Building ${this.buildingTypeName} cannot produce $unitTypeName", 1, this.x to this.y)
            return false
        }
        // simulationInstance.gameState.addEvent("production_queued", "$unitTypeName queued at ${this.buildingTypeName}", 0, this.x to this.y)
        productionQueue.add(ProductionItem(unitTypeName, unitTypeData.buildTime, unitTypeData.cost.mass, unitTypeData.cost.energy, unitTypeData.cost.computronium ?: 0))
        return true
    }
    
    fun cancelProduction(index: Int): ProductionItem? {
        return if (index == -1 && currentProduction != null) { // Cancel current
            val item = currentProduction
            // Refund partial resources based on progress
            val unitTypeData = UNIT_TYPES.byName(item!!.type)
            if(unitTypeData != null) {
                val unitBuildTime = unitTypeData.buildTime.toDouble()
                if (unitBuildTime > 0) {
                    val progressRatio = productionProgressInternal / unitBuildTime
                    val teamRes = simulationInstance.resources[team]
                    teamRes?.mass = teamRes?.mass?.plus(item.massCost * (1-progressRatio))!!
                    teamRes.energy = teamRes.energy.plus(item.energyCost * (1-progressRatio))
                    teamRes.computronium = teamRes.computronium.plus((item.computroniumCost ?: 0) * (1-progressRatio))
                }
            }
            currentProduction = null
            productionProgressInternal = 0.0
            item
        } else if (index >= 0 && index < productionQueue.size) {
            val item = productionQueue.removeAt(index)
            // Full refund for queued items
            val teamRes = simulationInstance.resources[team]
            teamRes?.mass = teamRes?.mass?.plus(item.massCost)!!
            teamRes.energy = teamRes.energy.plus(item.energyCost)
            teamRes.computronium = teamRes.computronium.plus(item.computroniumCost ?: 0)
            item
        } else {
            null
        }
    }

    fun setRallyPoint(newX: Double, newY: Double) {
        // TODO: Add validation for rally point placement (e.g., within map bounds, on valid terrain for units)
        rallyX = newX
        rallyY = newY
    }

    fun takeDamage(damage: Double) {
        hp -= damage
        if (hp < 0) hp = 0.0
        // if (hp == 0.0) simulationInstance.gameState.addEvent("building_destroyed", "$buildingTypeName destroyed", 2, x to y)
    }

    // Not needed if BuildingType holds all info
    // private fun createProductionItem(unitType: String): ProductionItem?
    
    fun getProductionQueueInfo(): List<ProductionItem> { // Kotlin idiomatic return type
        val items = mutableListOf<ProductionItem>()
        currentProduction?.let { items.add(it) }
        items.addAll(productionQueue)
        return items
    }

    fun getCompletionPercentage(): Double {
        return if (isUnderConstruction) {
            if (constructionTime > 0) constructionProgress / constructionTime else 0.0
        } else {
            currentProduction?.let {
                val unitTypeData = UNIT_TYPES.byName(it.type)
                if (unitTypeData != null && unitTypeData.buildTime > 0) {
                    productionProgressInternal / unitTypeData.buildTime
                } else 0.0
            } ?: 0.0
        }
    }
    
    fun toEntityState(): EntityState {
        return EntityState(
            id = id,
            type = buildingTypeName, // Use the original type name string
            team = team,
            x = x,
            y = y,
            hp = hp,
            maxHp = maxHp
        )
    }
}

/**
 * Production queue item
 */
data class ProductionItem(
    val type: String, // Unit type name
    val buildTime: Int, // Frames or seconds, must be consistent with UnitType
    val massCost: Int,
    val energyCost: Int,
    val computroniumCost: Int? = 0
)

// TODO: Port Effect and Projectile classes if they are substantially different from placeholders
// For now, assuming they are simple data classes or will be ported later.
class Effect(
    var x: Double, var y: Double, var type: String,
    var duration: Int, var life: Int = duration
) {
    fun update() { life-- }
}

class Projectile(
    var x: Double, var y: Double, val targetX: Double, val targetY: Double,
    val damage: Double, val speed: Double, val team: String
) {
    var shouldDestroy: Boolean = false
    fun update(simulation: Simulation, deltaTime: Double) {
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < speed * deltaTime) { // Simplified hit detection
            explode(simulation)
            shouldDestroy = true
            return
        }
        val moveDist = speed * deltaTime
        x += (dx / distance) * moveDist
        y += (dy / distance) * moveDist
    }
    private fun explode(simulation: Simulation) { /* TODO: Implement explosion logic */ }
}


// Extension property on Simulation to get nextEntityId (example, adjust as needed)
// This should ideally be part of EntityManager if it's responsible for ID generation
var Simulation.nextEntityId: Int
    get() = this.entityManager.nextEntityId // Delegate to EntityManager
    set(value) { this.entityManager.nextEntityId = value } // Delegate to EntityManager