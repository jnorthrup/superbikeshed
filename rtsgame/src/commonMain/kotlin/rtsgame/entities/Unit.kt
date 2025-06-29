package rtsgame.entities

import borg.trikeshed.lib.*
import rtsgame.config.*
import rtsgame.codec.*
import rtsgame.core.*
import kotlin.math.*

// Constants from JS unit.js (or derived)
private const val WEIGHT_SPEED_PENALTY_FACTOR = 0.01
private const val DEFAULT_UNIT_SPEED = 1.0
private const val DEFAULT_UNIT_WEIGHT = 0.0
private const val SHIELD_EFFECTIVENESS_WATTAGE_BASELINE = 20.0
private const val DEFAULT_SHIELD_REGEN_RATE = 1.0 // Energy shield regen per second

/**
 * Core GameUnit implementation - Translating from JS version
 * Aiming for 1:1 feature parity as per JS_TO_KMP_RESCUE_PLAN.md
 */
class GameUnit(
    var id: Int,
    val unitTypeName: String, // Name of the unit type, e.g., "tank"
    var team: String,
    var x: Double,
    var y: Double,
    // Simulation reference is needed for accessing seeded random, entity manager, game state, etc.
    private val simulation: Simulation
) {
    val type: UnitType = UNIT_TYPES.byName(unitTypeName)
        ?: throw IllegalArgumentException("Unknown unit type: $unitTypeName")

    var hp: Double = type.maxHp
    var maxHp: Double = type.maxHp

    // Movement state
    var targetX: Double = x
    var targetY: Double = y
    var speed: Double // Calculated based on type.speed and type.unitWeight
    var angle: Double = simulation.seedRandom.nextDouble() * PI * 2.0
    var vx: Double = 0.0
    var vy: Double = 0.0
    var isMoving: Boolean = false
    var patrolTarget: Pair<Double, Double>? = null
    var path: List<Pair<Double, Double>>? = null
    var currentWaypointIndex: Int = 0
    var pathRequestCooldown: Double = 0.0
    private val PATH_REQUEST_INTERVAL = 0.5 // seconds

    // Combat state
    var target: GameUnit? = null // Can target other GameUnits or Buildings
    var attackRange: Double = type.range
    var attackDamage: Double = type.damage // For KMP, ensure UnitType has damage
    var attackCooldown: Double = 0.0 // Time remaining for cooldown
    var maxAttackCooldown: Double = type.attackSpeed // Cooldown duration in seconds (JS used frames)
    var lastFireTime: Long = 0L
    var preferredRange: Double = type.range * 0.8

    // Energy System
    var currentEnergy: Double = type.batteryCapacity ?: 0.0
    val batteryCapacity: Double = type.batteryCapacity ?: 0.0
    val generatorOutput: Double = type.generatorOutput ?: 0.0 // Energy per second
    val weaponEnergyCost: Double = type.weaponEnergyCost ?: 0.0

    // Shield System
    var currentEnergyShields: Double = type.maxEnergyShields ?: 0.0
    val maxEnergyShields: Double = type.maxEnergyShields ?: 0.0
    val shieldRegenRate: Double = type.shieldRegenRate ?: DEFAULT_SHIELD_REGEN_RATE // Energy shield regen per second
    // Old HP Shield (if needed for direct port of some JS logic, otherwise prefer energy shields)
    var shields: Double = type.shields ?: 0.0
    val maxShields: Double = type.shields ?: 0.0 // Max for old HP shield
    val shieldRegenOld: Double = type.shieldRegen ?: 0.0 // Regen for old HP shield


    // Status flags
    var isDead: Boolean = false
    var isSelected: Boolean = false
    var isStuck: Boolean = false
    var stuckFrames: Int = 0
    var isEscaping: Boolean = false
    var escapeAngle: Double = 0.0
    var escapeDuration: Double = 0.0 // In seconds
    private val STUCK_FRAMES_THRESHOLD = 30 // Frames before trying to unstick (approx 0.5s at 60fps)
    private val ESCAPE_MODE_DURATION = 1.0 // Seconds for escape maneuver
    private var lastPositionForStuckCheck: Pair<Double, Double> = x to y
    private val significantMoveThreshold: Double = type.size / 4.0


    // Command state
    var currentCommand: UnitCommand? = null
    var commandQueue: MutableList<UnitCommand> = mutableListOf()

    // AI state
    var lastAIDecisionTime: Long = 0L // Game time of last AI decision
    var autonomousBehaviorEnabled: Boolean = true // Renamed from autonomousBehavior to avoid conflict with potential class
    var lastTargetSwitchTime: Long = 0L
    var aggressiveness: Double = 0.7 + (simulation.seedRandom.nextDouble() * 0.3)
    var tacticalRole: String // Determined in init
    var militaryRank: String // Determined in init
    var survivalPriority: Double // Calculated in init
    var commandAuthority: Double // Base authority
    var effectiveAuthority: Double = 0.0


    // Construction / Support
    var constructionTask: ConstructionTask? = null // For ACU/Engineer
    val buildRate: Double = type.buildRate ?: 1.0

    // Abilities
    var grenadeCooldown: Double = 0.0 // For grenade ability

    // Misc
    private var captionCooldown: Double = 0.0 // In seconds

    init {
        // Calculate speed based on weight
        val baseSpeed = type.speed
        val weight = type.unitWeight ?: DEFAULT_UNIT_WEIGHT
        var speedDenominator = 1.0 + (weight * WEIGHT_SPEED_PENALTY_FACTOR)
        if (speedDenominator <= 0.1) speedDenominator = 0.1
        this.speed = baseSpeed / speedDenominator
        if (this.speed < 0) this.speed = 0.0

        this.tacticalRole = determineTacticalRole()
        this.militaryRank = determineMilitaryRank()
        this.survivalPriority = calculateSurvivalPriority()
        this.commandAuthority = type.tier.toDouble() * 10.0 + if (type.support == true) 5.0 else 0.0
        this.effectiveAuthority = this.commandAuthority
    }

        // Ported from JS: updateStuckDetection, basic movement, cooldowns
        // TODO: Port full defaultMovementAndTargeting, performSupportRole,
        // handleCombatPositioning, showStateCaption,
    // updateTacticalBehavior, executeAgglomeration, etc.

    fun update(simulation: Simulation, deltaTime: Double) {
        if (isDead) return

        // Regenerate energy & shields
        if (batteryCapacity > 0 && generatorOutput > 0) {
            currentEnergy = min(batteryCapacity, currentEnergy + generatorOutput * deltaTime)
        }
        if (maxEnergyShields > 0 && shieldRegenRate > 0) {
            currentEnergyShields = min(maxEnergyShields, currentEnergyShields + shieldRegenRate * deltaTime)
        }
        if (maxShields > 0 && shieldRegenOld > 0) {
            shields = min(maxShields, shields + shieldRegenOld * deltaTime)
        }

        // Cooldowns
        if (attackCooldown > 0) attackCooldown = max(0.0, attackCooldown - deltaTime)
        if (pathRequestCooldown > 0) pathRequestCooldown = max(0.0, pathRequestCooldown - deltaTime)
        if (type.grenadeAbility != null && grenadeCooldown > 0) {
            grenadeCooldown = max(0.0, grenadeCooldown - deltaTime)
        }
        if (captionCooldown > 0) captionCooldown = max(0.0, captionCooldown - deltaTime)


        // Primary logic from JS unit.update's main conditional branches
        if (type.support == true) {
            // performSupportRole(simulation, deltaTime) // TODO: Port this
            // Simplified placeholder for support logic
            if (currentCommand == null && autonomousBehaviorEnabled) {
                 executeAutonomousBehavior(simulation, deltaTime)
            } else {
                processCurrentCommand(simulation, deltaTime)
            }
        } else { // Non-support units
            // defaultMovementAndTargeting(simulation, deltaTime) // TODO: Port this complex method
            // Simplified placeholder for default movement and targeting
            if (currentCommand == null && autonomousBehaviorEnabled) {
                 executeAutonomousBehavior(simulation, deltaTime)
            } else {
                processCurrentCommand(simulation, deltaTime)
            }
        }
        
        applyMovement(simulation, deltaTime) // Apply calculated vx, vy
        updateStuckDetection(simulation, deltaTime) // Needs deltaTime for consistent frame counting if STUCK_FRAMES_THRESHOLD is frame-based
        
        // Other updates from JS (to be ported)
        // updateTacticalBehavior(simulation)
        // updateSurvivalBehaviors(simulation)
        // executeCommandHierarchy(simulation)
    }

    private fun processCurrentCommand(simulation: Simulation, deltaTime: Double) {
        currentCommand?.let { cmd ->
            when (cmd) {
                is MoveCommand -> processMove(cmd, deltaTime)
                is AttackCommand -> processAttack(cmd, simulation, deltaTime)
                is StopCommand -> processStop(cmd)
                is BuildCommand -> processBuild(cmd, simulation, deltaTime)
            }
        }
        if (currentCommand == null && commandQueue.isNotEmpty()) {
            currentCommand = commandQueue.removeAt(0)
            processCurrentCommand(simulation, deltaTime) // Process new command immediately
        }
    }
    
    private fun processMove(command: MoveCommand, deltaTime: Double) {
        val dx = command.x - x
        val dy = command.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        // TODO: Integrate pathfinding here if distance > threshold or direct path obstructed
        // For now, direct movement to targetX, targetY

        targetX = command.x // Ensure targetX/Y are set for direct movement logic
        targetY = command.y

        val effectiveSpeed = getCurrentSpeed(simulation.terrain) // Pass simulation.terrain for context
        val moveDistance = effectiveSpeed * deltaTime

        if (distance <= moveDistance || distance < 1.0) { // Reached destination
            vx = 0.0
            vy = 0.0
            x = command.x // Snap to final position
            y = command.y
            isMoving = false
            currentCommand = null
        } else { // Move towards target
            val normDx = dx / distance
            val normDy = dy / distance
            vx = normDx * effectiveSpeed
            vy = normDy * effectiveSpeed
            angle = atan2(normDy, normDx)
            isMoving = true
        }
    }

    private fun applyMovement(simulation: Simulation, deltaTime: Double) {
        val prevX = x
        val prevY = y

        x += vx * deltaTime
        y += vy * deltaTime

        // Basic World Boundary Check (from JS applyMovement)
        x = x.coerceIn(0.0, WORLD_SIZE.toDouble())
        y = y.coerceIn(0.0, WORLD_SIZE.toDouble())
        
        // TODO: Add collision detection/response with other units (from JS applyMovement)
        // This is a complex part involving iterating other units and resolving overlaps.
        // For now, only boundary check is ported.
    }

    private fun updateStuckDetection(simulation: Simulation, deltaTime: Double) {
        val distanceMovedSq = (x - lastPositionForStuckCheck.first).pow(2) + (y - lastPositionForStuckCheck.second).pow(2)
        val significantMoveThresholdSq = significantMoveThreshold.pow(2)

        val wasTryingToMove = currentCommand is MoveCommand || currentCommand is AttackCommand || isEscaping || vx != 0.0 || vy != 0.0

        if (wasTryingToMove && distanceMovedSq < significantMoveThresholdSq) {
            stuckFrames++
        } else {
            stuckFrames = 0
        }

        lastPositionForStuckCheck = x to y

        if (stuckFrames > STUCK_FRAMES_THRESHOLD && !isEscaping) {
            isEscaping = true
            // JS used Math.random(), KMP uses simulation.seedRandom.nextDouble()
            escapeAngle = angle + (if (simulation.seedRandom.nextDouble() < 0.5) PI / 2.0 else -PI / 2.0)
            escapeDuration = ESCAPE_MODE_DURATION
            // simulation.gameState.addEvent("stuck_escape", "$unitTypeName trying to unstick", 0, x to y)
        }

        if (isEscaping) {
            if (escapeDuration > 0) {
                val escapeSpeed = getCurrentSpeed(simulation.terrain)
                vx = cos(escapeAngle) * escapeSpeed
                vy = sin(escapeAngle) * escapeSpeed
                escapeDuration -= deltaTime
            } else {
                isEscaping = false
                stuckFrames = 0
                vx = 0.0
                vy = 0.0
                // Path might need recalculation if it was stuck on a path
                path = null
                // currentCommand might need to be re-evaluated or cleared
                if (currentCommand is MoveCommand || currentCommand is AttackCommand) {
                    // Potentially clear command or re-issue path request
                }
            }
        }
    }


    private fun processAttack(command: AttackCommand, simulation: Simulation, deltaTime: Double) {
        val targetUnit = command.target
        if (targetUnit.isDead) {
            this.target = null
            currentCommand = null
            return
        }
        this.target = targetUnit

        val dx = targetUnit.x - x
        val dy = targetUnit.y - y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance > attackRange) {
            val moveCmd = MoveCommand(targetUnit.x, targetUnit.y)
            processMove(moveCmd, deltaTime)
            return
        }
        
        isMoving = false
        vx = 0.0; vy = 0.0; // Stop movement when in range
        angle = atan2(dy, dx)

        if (attackCooldown <= 0.0) {
            if (currentEnergy >= weaponEnergyCost) {
                performAttack(targetUnit, simulation)
                currentEnergy -= weaponEnergyCost
                attackCooldown = type.attackSpeed
                lastFireTime = simulation.gameState.gameTime.toLong()
            } else {
                // Not enough energy
            }
        }
    }
    
    private fun processStop(command: StopCommand) {
        isMoving = false
        vx = 0.0; vy = 0.0;
        target = null
        targetX = x
        targetY = y
        path = null
        currentCommand = null
    }

    private fun processBuild(command: BuildCommand, simulation: Simulation, deltaTime: Double) {
        val dx = command.x - x
        val dy = command.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        val buildRange = 50.0 // Example from JS, should be from UnitType or GameConstants

        if (distance > buildRange) {
            processMove(MoveCommand(command.x, command.y), deltaTime)
            return
        }

        isMoving = false; vx = 0.0; vy = 0.0; // Stop at build site

        // Simplified: Assume construction starts/completes instantly for now if affordable
        // JS version has constructionTask and progress, which is more complex
        val buildingTypeData = BUILDING_TYPES.byName(command.buildingType)
        if (buildingTypeData != null) {
            val teamResources = simulation.resources[team]
            if (teamResources != null &&
                teamResources.mass >= buildingTypeData.cost.mass &&
                teamResources.energy >= buildingTypeData.cost.energy &&
                teamResources.computronium >= (buildingTypeData.cost.computronium ?: 0.0) // Use 0.0 if null
            ) {
                teamResources.mass -= buildingTypeData.cost.mass.toDouble()
                teamResources.energy -= buildingTypeData.cost.energy.toDouble()
                teamResources.computronium -= (buildingTypeData.cost.computronium ?: 0).toDouble()

                val newBuilding = Building(
                    id = simulation.entityManager.nextEntityId++,
                    buildingTypeName = command.buildingType,
                    team = team,
                    x = command.x,
                    y = command.y,
                    simulationInstance = simulation
                )
                simulation.entityManager.addBuilding(newBuilding)
                // simulation.gameState.addEvent("build", "$unitTypeName built ${command.buildingType}", 1, x to y)
            } else {
                // simulation.gameState.addEvent("build_fail", "Not enough resources for ${command.buildingType}", 1, x to y)
            }
        }
        currentCommand = null
    }


    private fun performAttack(targetUnit: GameUnit, simulation: Simulation) {
        targetUnit.takeDamage(type.damage, this, simulation)
        // TODO: Port projectile & effect creation from JS
        // simulation.entityManager.addProjectile(...)
        // simulation.entityManager.addEffect(...)
        // simulation.gameState.addEvent(...)
    }
    
    fun takeDamage(damage: Double, sourceUnit: GameUnit?, simulation: Simulation) {
        var remainingDamage = damage

        if (currentEnergyShields > 0) {
            val attackerWeaponEnergyCost = sourceUnit?.type?.weaponEnergyCost ?: SHIELD_EFFECTIVENESS_WATTAGE_BASELINE
            var shieldEffectiveness = attackerWeaponEnergyCost / SHIELD_EFFECTIVENESS_WATTAGE_BASELINE
            shieldEffectiveness = max(0.1, shieldEffectiveness)

            val damageToDealToShields = remainingDamage * shieldEffectiveness
            
            if (damageToDealToShields >= currentEnergyShields) {
                remainingDamage -= currentEnergyShields / shieldEffectiveness
                currentEnergyShields = 0.0
            } else {
                currentEnergyShields -= damageToDealToShields
                remainingDamage = 0.0
            }
        }

        if (remainingDamage > 0 && shields > 0) {
            val damageToOldShield = min(remainingDamage, shields)
            shields -= damageToOldShield
            remainingDamage -= damageToOldShield
        }

        if (remainingDamage > 0) {
            hp -= remainingDamage
        }

        if (hp <= 0) {
            hp = 0.0
            isDead = true
            // simulation.gameState.addEvent("death", "$unitTypeName destroyed!", 1, x to y)
        }
    }

    private fun executeAutonomousBehavior(simulation: Simulation, deltaTime: Double) {
        // TODO: Port full AutonomousBehavior.js logic or integrate its decision making here
        // Simplified: find nearest enemy and issue an attack command
        if (simulation.gameState.gameTime % 60 == 0) { // Simple throttle for AI decisions
             val nearbyEnemies = findNearbyEnemies(simulation, type.range * 1.5) // Use unit's actual range
             if (nearbyEnemies.isNotEmpty()) {
                val closestEnemy = nearbyEnemies.minByOrNull { distanceTo(it) }
                if (closestEnemy != null) {
                    issueCommand(AttackCommand(closestEnemy))
                }
            } else if (type.name == "Scout" && simulation.seedRandom.nextDouble() < 0.1) {
                val randomX = x + (simulation.seedRandom.nextDouble() - 0.5) * 400 // Wider roam for scouts
                val randomY = y + (simulation.seedRandom.nextDouble() - 0.5) * 400
                issueCommand(MoveCommand(randomX, randomY))
            }
        }
    }
    
    private fun findNearbyEnemies(simulation: Simulation, rangeParam: Double): List<GameUnit> {
        return simulation.entityManager.units.filter { otherUnit ->
            otherUnit.team != this.team && !otherUnit.isDead && distanceTo(otherUnit) <= rangeParam
        }
    }

    private fun distanceTo(other: GameUnit): Double {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }
    
    fun issueCommand(command: UnitCommand) {
        currentCommand = command
        // JS version often sets targetX/Y directly in issueCommand for MOVE.
        // KMP version's processMove already uses command.x/y.
        // For ATTACK, internal 'target' property is set by processAttack.
        // This keeps command processing centralized.
        if (command is MoveCommand) {
            isMoving = true // Indicate intent to move
        } else if (command is AttackCommand) {
            isMoving = true // Indicate intent to move towards attack target
        }
    }
    
    private fun getCurrentSpeed(terrainGrid: List<List<TerrainCell>>?): Double {
        // TODO: Implement terrain-based speed modification like in JS unit.js
        // This requires terrain data to be properly passed and accessed.
        // For now, returns the base calculated speed.
        // Example from JS:
        // if (this.type.movementType == 'amphibious') {
        //     const terrainType = gameContext.terrain[tileX][tileY];
        //     if (terrainType === TERRAIN_TYPES.WATER) return this.type.speedWater;
        //     return this.type.speedLand;
        // }
        return this.speed
    }
    
    private fun determineTacticalRole(): String {
        if (type.range > 150) return "sniper"
        // JS used type.speed (base speed from config), not this.speed (effective speed after weight)
        if ((type.speedLand ?: type.speed) > 60) return "scout"
        if (type.maxHp > 200) return "tank"
        if (type.support == true) return "support"
        return "assault"
    }

    private fun determineMilitaryRank(): String {
        if (type.name == "Commander") return "GENERAL"
        if (type.tier >= 3) return "COLONEL"
        if (type.tier >= 2) return "MAJOR"
        if (type.support == true) return "LIEUTENANT"
        return "SERGEANT"
    }

    private fun calculateSurvivalPriority(): Double {
        var priority = type.tier * 10.0
        if (type.name == "Commander") priority += 50.0
        if (type.support == true) priority += 15.0
        if (type.range > 150) priority += 10.0
        return priority
    }


    fun toEntityState(): EntityState {
        return EntityState(
            id = id,
            type = unitTypeName,
            team = team,
            x = x,
            y = y,
            hp = hp,
            maxHp = maxHp
        )
    }
}

// Supporting classes for Commands (can be moved to a separate file later)
enum class CommandType {
    MOVE, ATTACK, STOP, BUILD
}

sealed class UnitCommand(val type: CommandType)
class MoveCommand(val x: Double, val y: Double) : UnitCommand(CommandType.MOVE)
class AttackCommand(val target: GameUnit) : UnitCommand(CommandType.ATTACK)
class StopCommand : UnitCommand(CommandType.STOP)
class BuildCommand(val buildingType: String, val x: Double, val y: Double) : UnitCommand(CommandType.BUILD)

// Simplified ConstructionTask for now
data class ConstructionTask(
    val targetX: Double,
    val targetY: Double,
    val type: BuildingType,
    var progress: Double = 0.0,
    var buildingStarted: Boolean = false
)

// Extension property on Simulation to easily access units (example, adjust as needed)
val Simulation.units: List<GameUnit>
    get() = this.entityManager.units.filterIsInstance<GameUnit>()

// Extension property on Simulation to get nextEntityId (example, adjust as needed)
var Simulation.nextEntityId: Int
    get() = this.entityManager.nextEntityId
    set(value) { this.entityManager.nextEntityId = value }