import kotlin.math.*
package rtsgame.entities
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.config.*
import rtsgame.codec.*
import rtsgame.core.*
import kotlin.math.*

/**
 * Core GameUnit implementation - Direct translation from JS version
 * NO IMPROVEMENTS - preserving exact behavior
 */
class GameUnit(
    var id: Int,
    var type: String,
    var team: String,
    var x: Double,
    var y: Double,
    var maxHp: Double = UNIT_HEALTH.toDouble(),
    var hp: Double = UNIT_HEALTH.toDouble()
) {
    // Movement state
    var targetX: Double = x
    var targetY: Double = y
    var speed: Double = UNIT_SPEED
    var isMoving: Boolean = false
    
    // Combat state
    var target: GameUnit? = null
    var attackRange: Double = UNIT_ATTACK_RANGE.toDouble()
    var attackCooldown: Int = 0
    var maxAttackCooldown: Int = UNIT_ATTACK_COOLDOWN
    
    // Status flags
    var isDead: Boolean = false
    var isSelected: Boolean = false
    var veterancyLevel: String = VETERANCY_LEVELS.GREEN
    
    // Command state
    var currentCommand: UnitCommand? = null
    var commandQueue: MutableList<UnitCommand> = mutableListOf()
    
    // AI state
    var lastAIDecision: Long = 0
    var autonomousBehavior: Boolean = true
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        if (isDead) return
        
        // Update attack cooldown
        if (attackCooldown > 0) {
            attackCooldown--
        }
        
        // Process current command
        currentCommand?.let { command ->
            when (command.type) {
                CommandType.MOVE -> processMove(deltaTime)
                CommandType.ATTACK -> processAttack(simulation, deltaTime)
                CommandType.STOP -> processStop()
                CommandType.BUILD -> processBuild(simulation, deltaTime)
            }
        }
        
        // Execute autonomous behavior if no commands
        if (currentCommand == null && autonomousBehavior) {
            executeAutonomousBehavior(simulation)
        }
        
        // Process next command in queue
        if (currentCommand == null && commandQueue.isNotEmpty()) {
            currentCommand = commandQueue.removeAt(0)
        }
    }
    
    internal fun processMove(deltaTime: Double) {
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance < 1.0) {
            // Reached destination
            x = targetX
            y = targetY
            isMoving = false
            currentCommand = null
            return
        }
        
        // Move towards target
        val moveDistance = speed * deltaTime
        val normalizedDx = dx / distance
        val normalizedDy = dy / distance
        
        x += normalizedDx * moveDistance
        y += normalizedDy * moveDistance
        isMoving = true
    }
    
    internal fun processAttack(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val target = this.target ?: run {
            currentCommand = null
            return
        }
        
        if (target.isDead) {
            this.target = null
            currentCommand = null
            return
        }
        
        val dx = target.x - x
        val dy = target.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > attackRange) {
            // Move closer to target
            targetX = target.x
            targetY = target.y
            processMove(deltaTime)
            return
        }
        
        // In range - attack if cooldown ready
        if (attackCooldown <= 0) {
            performAttack(target, simulation)
            attackCooldown = maxAttackCooldown
        }
    }
    
    internal fun processStop() {
        isMoving = false
        target = null
        currentCommand = null
    }
    
    internal fun processBuild(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val buildCommand = currentCommand as? BuildCommand ?: run {
            currentCommand = null
            return
        }
        
        // Move to build location if not there
        val dx = buildCommand.x - x
        val dy = buildCommand.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 5.0) {
            targetX = buildCommand.x
            targetY = buildCommand.y
            processMove(deltaTime)
            return
        }
        
        // Start construction
        startConstruction(buildCommand, simulation)
        currentCommand = null
    }
    
    internal fun performAttack(target: GameUnit, simulation: rtsgame.core.Simulation) {
        // Create projectile
        val projectile = Projectile(
            x = this.x,
            y = this.y,
            targetX = target.x,
            targetY = target.y,
            damage = PROJECTILE_DAMAGE.toDouble(),
            speed = PROJECTILE_SPEED,
            team = this.team
        )
        
        simulation.entityManager.addProjectile(projectile)
        
        // Add combat event
        simulation.gameState.addEvent(
            "combat",
            "${this.type} attacks ${target.type}",
            1,
            this.x to this.y
        )
    }
    
    internal fun startConstruction(buildCommand: BuildCommand, simulation: rtsgame.core.Simulation) {
        // Check resources
        val teamResources = simulation.resources[team] ?: return
        val buildingType = getBuildingType(buildCommand.buildingType) ?: return
        
        if (canAfford(teamResources, buildingType)) {
            deductResources(teamResources, buildingType)
            
            val building = rtsgame.entities.Building(
                id = simulation.nextEntityId++,
                type = buildCommand.buildingType,
                team = team,
                x = buildCommand.x,
                y = buildCommand.y
            )
            
            simulation.entityManager.addBuilding(building)
        }
    }
    
    internal fun executeAutonomousBehavior(simulation: rtsgame.core.Simulation) {
        // Simple AI: attack nearby enemies or move to resource nodes
        val nearbyEnemies = findNearbyEnemies(simulation, 200.0)
        
        if (nearbyEnemies.isNotEmpty()) {
            val closestEnemy = nearbyEnemies.minByOrNull { enemy ->
                val dx = enemy.x - x
                val dy = enemy.y - y
                sqrt(dx * dx + dy * dy)
            }
            
            closestEnemy?.let {
                issueCommand(AttackCommand(it))
            }
        } else if (type == "scout") {
            // Scouts explore randomly
            val randomX = x + (simulation.seedRandom.nextDouble() - 0.5) * 100
            val randomY = y + (simulation.seedRandom.nextDouble() - 0.5) * 100
            issueCommand(MoveCommand(randomX, randomY))
        }
    }
    
    fun issueCommand(command: UnitCommand) {
        currentCommand = command
        
        when (command.type) {
            CommandType.MOVE -> {
                val moveCmd = command as MoveCommand
                targetX = moveCmd.x
                targetY = moveCmd.y
            }
            CommandType.ATTACK -> {
                val attackCmd = command as AttackCommand
                target = attackCmd.target
            }
            CommandType.STOP -> {
                // Nothing to set up
            }
            CommandType.BUILD -> {
                // Will be handled in processBuild
            }
        }
    }
    
    fun queueCommand(command: UnitCommand) {
        commandQueue.add(command)
    }
    
    fun takeDamage(damage: Double, attacker: GameUnit?) {
        hp -= damage
        
        if (hp <= 0) {
            hp = 0.0
            isDead = true
            
            // Gain veterancy for attacker
            attacker?.gainExperience(10)
        }
    }
    
    internal fun gainExperience(amount: Int) {
        // Simple veterancy system
        when (veterancyLevel) {
            VETERANCY_LEVELS.GREEN -> {
                if (amount >= 50) veterancyLevel = VETERANCY_LEVELS.REGULAR
            }
            VETERANCY_LEVELS.REGULAR -> {
                if (amount >= 100) veterancyLevel = VETERANCY_LEVELS.VETERAN
            }
            // etc.
        }
    }
    
    internal fun findNearbyEnemies(simulation: Simulation, range: Double): Indexed<GameUnit> {
        val nearby = mutableListOf<GameUnit>()
        for (i in 0 until simulation.units.component1()) {
            val unit = simulation.units[i]
            if (unit.team != this.team && !unit.isDead && distanceTo(unit) <= range) {
                nearby.add(unit)
            }
        }
        return nearby.size j { i: Int -> nearby[i] }
    }
    
    internal fun distanceTo(other: GameUnit): Double {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }
    
    internal fun canAfford(resources: TeamResourcesExtended, buildingType: BuildingCost): Boolean {
        return resources.mass >= buildingType.mass && 
               resources.energy >= buildingType.energy
    }
    
    internal fun deductResources(resources: TeamResourcesExtended, buildingType: BuildingCost) {
        resources.mass -= buildingType.mass
        resources.energy -= buildingType.energy
    }
    
    internal fun getBuildingType(typeName: String): BuildingCost? {
        return when (typeName) {
            "massExtractor" -> BuildingCost(BUILDING_COSTS.EXTRACTOR.mass, BUILDING_COSTS.EXTRACTOR.energy)
            "energyExtractor" -> BuildingCost(BUILDING_COSTS.ENERGY_PLANT.mass, BUILDING_COSTS.ENERGY_PLANT.energy)
            "landFactory" -> BuildingCost(BUILDING_COSTS.LAND_FACTORY.mass, BUILDING_COSTS.LAND_FACTORY.energy)
            else -> null
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

// Supporting classes
enum class CommandType {
    MOVE, ATTACK, STOP, BUILD
}

sealed class UnitCommand(val type: CommandType)

class MoveCommand(val x: Double, val y: Double) : UnitCommand(CommandType.MOVE)
class AttackCommand(val target: GameUnit) : UnitCommand(CommandType.ATTACK)
class StopCommand : UnitCommand(CommandType.STOP)
class BuildCommand(val buildingType: String, val x: Double, val y: Double) : UnitCommand(CommandType.BUILD)

data class BuildingCost(val mass: Int, val energy: Int)

internal val Simulation.units: Indexed<GameUnit>
    get() = entityManager.units.size j { i: Int -> entityManager.units[i] }

internal val Simulation.nextEntityId: Int
    get() = entityManager.nextEntityId++