import kotlin.math.*
package rtsgame.entities
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import rtsgame.config.*
import rtsgame.codec.*
import kotlin.math.*

/**
 * Building system - Production, resource generation, tech progression
 * Direct translation from JS with exact behavior preservation
 */
class Building(
    var id: Int,
    var type: String,
    var team: String,
    var x: Double,
    var y: Double,
    var maxHp: Double = BUILDING_HEALTH.toDouble(),
    var hp: Double = BUILDING_HEALTH.toDouble()
) {
    // Construction state
    var isUnderConstruction: Boolean = true
    var constructionProgress: Double = 0.0
    var constructionTime: Int = BUILDING_CONSTRUCTION_TIME
    
    // Production state
    var productionQueue: MutableList<ProductionItem> = mutableListOf()
    var currentProduction: ProductionItem? = null
    var productionProgress: Double = 0.0
    
    // Resource generation
    var income: Double = 0.0
    var resourceType: String? = null
    
    // Rally point for unit production
    var rallyX: Double = x
    var rallyY: Double = y
    
    // Power and connectivity
    var isPowered: Boolean = true
    var powerConsumption: Double = 0.0
    var powerGeneration: Double = 0.0
    
    init {
        setupBuildingType()
    }
    
    internal fun setupBuildingType() {
        when (type) {
            "massExtractor" -> {
                resourceType = RESOURCE_TYPES.MASS
                income = BUILDING_YIELDS.EXTRACTOR.mass.toDouble()
                constructionTime = 180 // 3 seconds at 60 FPS
            }
            "energyExtractor" -> {
                resourceType = RESOURCE_TYPES.ENERGY
                income = BUILDING_YIELDS.ENERGY_PLANT.energy.toDouble()
                powerGeneration = 10.0
                constructionTime = 120 // 2 seconds
            }
            "landFactory" -> {
                constructionTime = 600 // 10 seconds
                powerConsumption = 5.0
                rallyX = x + 50.0 // Default rally point
                rallyY = y
            }
            "airFactory" -> {
                constructionTime = 720 // 12 seconds
                powerConsumption = 8.0
                rallyX = x
                rallyY = y + 50.0
            }
            "navalFactory" -> {
                constructionTime = 900 // 15 seconds
                powerConsumption = 12.0
            }
            "computroniumExtractor" -> {
                resourceType = "COMPUTRONIUM"
                income = 0.5
                constructionTime = 300 // 5 seconds
                powerConsumption = 3.0
            }
            "advancedComputroniumCore" -> {
                resourceType = "COMPUTRONIUM"
                income = 2.0
                constructionTime = 1200 // 20 seconds
                powerConsumption = 15.0
            }
        }
    }
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        if (hp <= 0) return
        
        // Handle construction
        if (isUnderConstruction) {
            updateConstruction()
            return
        }
        
        // Check power status
        updatePowerStatus(simulation)
        
        if (!isPowered && type != "energyExtractor") {
            // Most buildings don't work without power
            return
        }
        
        // Generate resources
        if (resourceType != null && income > 0) {
            generateResources(simulation)
        }
        
        // Handle production
        updateProduction(simulation, deltaTime)
    }
    
    internal fun updateConstruction() {
        constructionProgress += 1.0 // 1 per frame
        
        if (constructionProgress >= constructionTime) {
            isUnderConstruction = false
            constructionProgress = constructionTime.toDouble()
        }
    }
    
    internal fun updatePowerStatus(simulation: rtsgame.core.Simulation) {
        if (type == "energyExtractor") {
            isPowered = true // Energy plants always work
            return
        }
        
        // Simple power calculation - check if team has positive energy
        val teamResources = simulation.resources[team]
        isPowered = teamResources?.energy ?: 0 > 0
    }
    
    internal fun generateResources(simulation: rtsgame.core.Simulation) {
        val teamResources = simulation.resources[team] ?: return
        
        when (resourceType) {
            RESOURCE_TYPES.MASS -> {
                teamResources.mass += income.toInt()
                teamResources.massIncome += income
            }
            RESOURCE_TYPES.ENERGY -> {
                teamResources.energy += income.toInt()
                teamResources.energyIncome += income
            }
            "COMPUTRONIUM" -> {
                teamResources.computronium += income.toInt()
                teamResources.computroniumIncome += income
            }
        }
    }
    
    internal fun updateProduction(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val current = currentProduction
        
        if (current == null) {
            // Start next item in queue
            if (productionQueue.isNotEmpty()) {
                currentProduction = productionQueue.removeAt(0)
                productionProgress = 0.0
            }
            return
        }
        
        // Check if we can afford to continue production
        val teamResources = simulation.resources[team] ?: return
        if (!canAffordProduction(teamResources, current)) {
            // Pause production if resources unavailable
            return
        }
        
        // Continue production
        productionProgress += 1.0 // 1 per frame
        
        // Consume resources gradually during production
        val progressRatio = 1.0 / current.buildTime
        deductProductionResources(teamResources, current, progressRatio)
        
        if (productionProgress >= current.buildTime) {
            // Production complete
            completeProduction(simulation, current)
            currentProduction = null
            productionProgress = 0.0
        }
    }
    
    internal fun completeProduction(simulation: rtsgame.core.Simulation, item: ProductionItem) {
        when (item.type) {
            "scout", "tank", "artillery", "fighter", "submarine" -> {
                spawnUnit(simulation, item.type)
            }
            // Handle other production types
        }
    }
    
    internal fun spawnUnit(simulation: rtsgame.core.Simulation, unitType: String) {
        val unit = GameUnit(
            id = simulation.nextEntityId++,
            type = unitType,
            team = team,
            x = rallyX,
            y = rallyY
        )
        
        // Set unit stats based on type
        when (unitType) {
            "scout" -> {
                unit.speed = 4.0
                unit.hp = 50.0
                unit.maxHp = 50.0
                unit.attackRange = 80.0
            }
            "tank" -> {
                unit.speed = 1.5
                unit.hp = 200.0
                unit.maxHp = 200.0
                unit.attackRange = 120.0
            }
            "artillery" -> {
                unit.speed = 1.0
                unit.hp = 80.0
                unit.maxHp = 80.0
                unit.attackRange = 300.0
            }
            "fighter" -> {
                unit.speed = 6.0
                unit.hp = 80.0
                unit.maxHp = 80.0
                unit.attackRange = 100.0
            }
            "submarine" -> {
                unit.speed = 2.0
                unit.hp = 150.0
                unit.maxHp = 150.0
                unit.attackRange = 150.0
            }
        }
        
        simulation.entityManager.addUnit(unit)
        
        // Create spawn effect
        val effect = Effect(
            x = rallyX,
            y = rallyY,
            type = "spawn",
            duration = 30
        )
        simulation.entityManager.addEffect(effect)
    }
    
    fun queueProduction(unitType: String): Boolean {
        val productionItem = createProductionItem(unitType) ?: return false
        productionQueue.add(productionItem)
        return true
    }
    
    fun cancelProduction(index: Int): ProductionItem? {
        return if (index == -1) {
            // Cancel current production
            val current = currentProduction
            currentProduction = null
            productionProgress = 0.0
            current
        } else if (index >= 0 && index < productionQueue.size) {
            productionQueue.removeAt(index)
        } else {
            null
        }
    }
    
    fun setRallyPoint(newX: Double, newY: Double) {
        rallyX = newX
        rallyY = newY
    }
    
    fun takeDamage(damage: Double) {
        hp -= damage
        if (hp < 0) hp = 0.0
    }
    
    internal fun createProductionItem(unitType: String): ProductionItem? {
        return when (unitType) {
            "scout" -> ProductionItem(unitType, 180, 50, 25) // 3s, 50 mass, 25 energy
            "tank" -> ProductionItem(unitType, 300, 100, 50)  // 5s, 100 mass, 50 energy
            "artillery" -> ProductionItem(unitType, 480, 150, 100) // 8s
            "fighter" -> ProductionItem(unitType, 240, 80, 120) // 4s
            "submarine" -> ProductionItem(unitType, 360, 120, 80) // 6s
            else -> null
        }
    }
    
    internal fun canAffordProduction(resources: TeamResourcesExtended, item: ProductionItem): Boolean {
        return resources.mass >= item.massCost && resources.energy >= item.energyCost
    }
    
    internal fun deductProductionResources(
        resources: TeamResourcesExtended, 
        item: ProductionItem, 
        ratio: Double
    ) {
        val massToDeduct = (item.massCost * ratio).toInt()
        val energyToDeduct = (item.energyCost * ratio).toInt()
        
        resources.mass = maxOf(0, resources.mass - massToDeduct)
        resources.energy = maxOf(0, resources.energy - energyToDeduct)
    }
    
    fun getProductionQueueInfo(): Indexed<ProductionItem> {
        val items = mutableListOf<ProductionItem>()
        
        currentProduction?.let { items.add(it) }
        items.addAll(productionQueue)
        
        return \1 j { \2: Int -> items[i] }
    }
    
    fun getCompletionPercentage(): Double {
        return if (isUnderConstruction) {
            constructionProgress / constructionTime
        } else {
            currentProduction?.let { productionProgress / it.buildTime } ?: 0.0
        }
    }
    
    fun toEntityState(): EntityState {
        return EntityState(
            id = id,
            type = type,
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
    val type: String,
    val buildTime: Int, // frames
    val massCost: Int,
    val energyCost: Int
)

/**
 * Effect system for visual feedback
 */
class Effect(
    var x: Double,
    var y: Double,
    var type: String,
    var duration: Int,
    var life: Int = duration
) {
    var scale: Double = 1.0
    var alpha: Double = 1.0
    
    fun update() {
        life--
        
        // Fade out over time
        alpha = life.toDouble() / duration
        
        // Scale effects based on type
        when (type) {
            "explosion" -> {
                scale = 1.0 + (1.0 - alpha) * 2.0 // Expand as it fades
            }
            "spawn" -> {
                scale = alpha * 1.5 // Shrink as it fades
            }
        }
    }
}

/**
 * Projectile system for ranged combat
 */
class Projectile(
    var x: Double,
    var y: Double,
    val targetX: Double,
    val targetY: Double,
    val damage: Double,
    val speed: Double,
    val team: String
) {
    var shouldDestroy: Boolean = false
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance < 2.0) {
            // Hit target area
            explode(simulation)
            shouldDestroy = true
            return
        }
        
        // Move towards target
        val moveDistance = speed * deltaTime
        val normalizedDx = dx / distance
        val normalizedDy = dy / distance
        
        x += normalizedDx * moveDistance
        y += normalizedDy * moveDistance
    }
    
    internal fun explode(simulation: rtsgame.core.Simulation) {
        // Create explosion effect
        val effect = Effect(
            x = x,
            y = y,
            type = "explosion",
            duration = 20
        )
        simulation.entityManager.addEffect(effect)
        
        // Damage nearby units
        val blastRadius = PROJECTILE_BLAST_RADIUS.toDouble()
        val nearbyUnits = simulation.units.filter { unit ->
            !unit.isDead && unit.team != team &&
            sqrt((unit.x - x).pow(2) + (unit.y - y).pow(2)) <= blastRadius
        }
        
        nearbyUnits.forEach { unit ->
            unit.takeDamage(damage, null)
        }
    }
}

internal val rtsgame.core.Simulation.units: List<GameUnit>
    get() = entityManager.units.mapNotNull { it as? GameUnit }

internal val rtsgame.core.Simulation.nextEntityId: Int
    get() = entityManager.nextEntityId++