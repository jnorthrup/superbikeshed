package com.rtsgame.shared.entity

import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.GatheringTool
import com.rtsgame.shared.map.ResourceType
import com.rtsgame.shared.systems.ArmorType
import com.rtsgame.shared.systems.AttackProperties
import com.rtsgame.shared.systems.DamageType
import com.rtsgame.shared.systems.DefenseProperties
import com.rtsgame.shared.systems.Effect
import com.rtsgame.shared.systems.EffectType
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.max
import kotlin.random.Random

data class Unit(
    override val id: String,
    override val position: Position,
    override val health: Float,
    override val maxHealth: Float,
    override val speed: Float,
    override val team: Int,
    val type: UnitType,
    val path: List<Position> = emptyList(),
    val currentPathIndex: Int = 0,
    val isGathering: Boolean = false,
    val gatheringTargetId: String? = null,
    val gatheringProgress: Float = 0f,
    val gatheringSpecialization: ResourceType? = null,
    val equippedTool: GatheringTool? = null,
    val gatheringEfficiency: Float = 1.0f,
    val gatheringExperience: Float = 0f,
    val gatheringLevel: Int = 1,
    val attackProperties: AttackProperties,
    val defenseProperties: DefenseProperties,
    val isAttacking: Boolean = false,
    val attackTargetId: String? = null,
    val attackCooldown: Float = 0f,
    val energy: Float = 100f,
    val maxEnergy: Float = 100f,
    val energyRegen: Float = 5f,
    val abilities: List<String> = emptyList(),
    val abilityCooldowns: Map<String, Float> = emptyMap(),
    val activeEffects: List<Effect> = emptyList(),
    val isCloaked: Boolean = false,
    val isStunned: Boolean = false,
    val isDisabled: Boolean = false,
    // Formation-related properties
    val formationAngle: Float = Random.nextFloat() * 2 * Math.PI.toFloat(),
    val formationOffset: Position = Position(0f, 0f),
    val leaderTargetPosition: Position? = null,
    val leaderPredictedPosition: Position? = null,
    val idealFormationSlotWorld: Position? = null,
    val velocity: Position = Position(0f, 0f),
    val angle: Float = 0f,
    val authority: Float = 1.0f,
    val patrolTarget: Position? = null
) : Entity() {
    fun updatePosition(newPosition: Position): Unit {
        return copy(position = newPosition)
    }

    fun setPath(newPath: List<Position>): Unit {
        return copy(path = newPath, currentPathIndex = 0)
    }

    fun startGathering(targetId: String): Unit {
        return copy(
            isGathering = true,
            gatheringTargetId = targetId,
            gatheringProgress = 0f
        )
    }

    fun stopGathering(): Unit {
        return copy(
            isGathering = false,
            gatheringTargetId = null,
            gatheringProgress = 0f
        )
    }

    fun gainGatheringExperience(amount: Float): Unit {
        val newExperience = gatheringExperience + amount
        val newLevel = if (newExperience >= gatheringLevel * 100f) {
            gatheringLevel + 1
        } else {
            gatheringLevel
        }
        return copy(
            gatheringExperience = newExperience,
            gatheringLevel = newLevel,
            gatheringEfficiency = 1.0f + (newLevel - 1) * 0.1f
        )
    }

    fun equipTool(tool: GatheringTool): Unit {
        return copy(equippedTool = tool)
    }

    fun calculateEffectiveGatherRate(resourceType: ResourceType): Float {
        val baseRate = resourceType.baseGatherRate
        val specializationBonus = if (gatheringSpecialization == resourceType) 1.5f else 1.0f
        val toolBonus = equippedTool?.efficiencyMultiplier ?: 1.0f
        return baseRate * specializationBonus * toolBonus * gatheringEfficiency
    }

    fun startAttacking(targetId: String): Unit {
        return copy(
            isAttacking = true,
            attackTargetId = targetId,
            attackCooldown = 0f
        )
    }

    fun stopAttacking(): Unit {
        return copy(
            isAttacking = false,
            attackTargetId = null,
            attackCooldown = 0f
        )
    }

    fun updateAttackCooldown(deltaTime: Float): Unit {
        return copy(
            attackCooldown = maxOf(0f, attackCooldown - deltaTime)
        )
    }

    fun updateEnergy(deltaTime: Float): Unit {
        val newEnergy = min(maxEnergy, energy + energyRegen * deltaTime)
        return copy(energy = newEnergy)
    }

    fun updateAbilityCooldowns(deltaTime: Float): Unit {
        val updatedCooldowns = abilityCooldowns.mapValues { (_, cooldown) ->
            maxOf(0f, cooldown - deltaTime)
        }.filter { it.value > 0f }
        return copy(abilityCooldowns = updatedCooldowns)
    }

    fun addAbility(abilityId: String): Unit {
        return copy(abilities = abilities + abilityId)
    }

    fun removeAbility(abilityId: String): Unit {
        return copy(abilities = abilities - abilityId)
    }

    fun addEffect(effect: Effect): Unit {
        return copy(activeEffects = activeEffects + effect)
    }

    fun removeEffect(effectType: EffectType): Unit {
        return copy(activeEffects = activeEffects.filter { it.type != effectType })
    }

    fun updateEffects(deltaTime: Float): Unit {
        val updatedEffects = activeEffects.mapNotNull { effect ->
            if (effect.duration <= 0f) null
            else effect.copy(duration = effect.duration - deltaTime)
        }
        return copy(activeEffects = updatedEffects)
    }

    fun updateVelocity(newVelocity: Position): Unit {
        return copy(velocity = newVelocity)
    }

    fun updateAngle(newAngle: Float): Unit {
        return copy(angle = newAngle)
    }

    fun updateAuthority(newAuthority: Float): Unit {
        return copy(authority = newAuthority)
    }

    fun updatePatrolTarget(newTarget: Position?): Unit {
        return copy(patrolTarget = newTarget)
    }
}

enum class UnitType {
    // Basic Units
    WORKER,
    SOLDIER,
    TANK,
    ARTILLERY,
    SCOUT,
    MEDIC,
    ENGINEER,
    COMMANDER,

    // Advanced Combat Units
    SNIPER,
    FLAMETHROWER,
    ANTI_AIR,
    SIEGE_ENGINE,
    STEALTH_UNIT,
    SHIELD_GENERATOR,
    PLASMA_TANK,
    BATTLE_MECH,

    // Support Units
    REPAIR_UNIT,
    JAMMER,
    RADAR_UNIT,
    TRANSPORT,
    SUPPLY_UNIT,
    CLONE_UNIT,
    NANITE_SWARM,
    QUANTUM_UNIT;

    val baseAttackProperties: AttackProperties
        get() = when (this) {
            // Basic Units
            WORKER -> AttackProperties(
                damage = 5f,
                range = 1f,
                attackSpeed = 1f,
                damageType = DamageType.NORMAL
            )
            SOLDIER -> AttackProperties(
                damage = 10f,
                range = 5f,
                attackSpeed = 1.5f,
                damageType = DamageType.PIERCING
            )
            TANK -> AttackProperties(
                damage = 25f,
                range = 3f,
                attackSpeed = 0.8f,
                splashRadius = 1f,
                damageType = DamageType.EXPLOSIVE
            )
            ARTILLERY -> AttackProperties(
                damage = 40f,
                range = 10f,
                attackSpeed = 0.5f,
                splashRadius = 2f,
                damageType = DamageType.EXPLOSIVE
            )
            SCOUT -> AttackProperties(
                damage = 8f,
                range = 4f,
                attackSpeed = 2f,
                damageType = DamageType.PIERCING
            )
            MEDIC -> AttackProperties(
                damage = 0f,
                range = 5f,
                attackSpeed = 1f,
                damageType = DamageType.NORMAL
            )
            ENGINEER -> AttackProperties(
                damage = 7f,
                range = 2f,
                attackSpeed = 1f,
                damageType = DamageType.NORMAL
            )
            COMMANDER -> AttackProperties(
                damage = 15f,
                range = 6f,
                attackSpeed = 1.2f,
                damageType = DamageType.ENERGY
            )

            // Advanced Combat Units
            SNIPER -> AttackProperties(
                damage = 50f,
                range = 15f,
                attackSpeed = 0.5f,
                damageType = DamageType.PIERCING,
                armorPenetration = 20f,
                criticalChance = 0.3f,
                criticalMultiplier = 2.0f
            )
            FLAMETHROWER -> AttackProperties(
                damage = 15f,
                range = 3f,
                attackSpeed = 2f,
                splashRadius = 2f,
                damageType = DamageType.ENERGY
            )
            ANTI_AIR -> AttackProperties(
                damage = 20f,
                range = 8f,
                attackSpeed = 1.5f,
                damageType = DamageType.PIERCING,
                armorPenetration = 10f
            )
            SIEGE_ENGINE -> AttackProperties(
                damage = 60f,
                range = 12f,
                attackSpeed = 0.3f,
                splashRadius = 3f,
                damageType = DamageType.EXPLOSIVE
            )
            STEALTH_UNIT -> AttackProperties(
                damage = 25f,
                range = 4f,
                attackSpeed = 1.2f,
                damageType = DamageType.PIERCING,
                criticalChance = 0.2f
            )
            SHIELD_GENERATOR -> AttackProperties(
                damage = 5f,
                range = 6f,
                attackSpeed = 1f,
                damageType = DamageType.ENERGY
            )
            PLASMA_TANK -> AttackProperties(
                damage = 35f,
                range = 5f,
                attackSpeed = 0.8f,
                splashRadius = 1.5f,
                damageType = DamageType.PLASMA
            )
            BATTLE_MECH -> AttackProperties(
                damage = 45f,
                range = 4f,
                attackSpeed = 0.7f,
                splashRadius = 1f,
                damageType = DamageType.EXPLOSIVE
            )

            // Support Units
            REPAIR_UNIT -> AttackProperties(
                damage = 0f,
                range = 3f,
                attackSpeed = 1f,
                damageType = DamageType.NORMAL
            )
            JAMMER -> AttackProperties(
                damage = 0f,
                range = 8f,
                attackSpeed = 1f,
                damageType = DamageType.ENERGY
            )
            RADAR_UNIT -> AttackProperties(
                damage = 0f,
                range = 12f,
                attackSpeed = 1f,
                damageType = DamageType.NORMAL
            )
            TRANSPORT -> AttackProperties(
                damage = 0f,
                range = 0f,
                attackSpeed = 0f,
                damageType = DamageType.NORMAL
            )
            SUPPLY_UNIT -> AttackProperties(
                damage = 0f,
                range = 0f,
                attackSpeed = 0f,
                damageType = DamageType.NORMAL
            )
            CLONE_UNIT -> AttackProperties(
                damage = 12f,
                range = 4f,
                attackSpeed = 1.5f,
                damageType = DamageType.NORMAL
            )
            NANITE_SWARM -> AttackProperties(
                damage = 8f,
                range = 2f,
                attackSpeed = 3f,
                damageType = DamageType.ENERGY
            )
            QUANTUM_UNIT -> AttackProperties(
                damage = 30f,
                range = 7f,
                attackSpeed = 1f,
                damageType = DamageType.PLASMA
            )
        }

    val baseDefenseProperties: DefenseProperties
        get() = when (this) {
            // Basic Units
            WORKER -> DefenseProperties(
                armor = 5f,
                armorType = ArmorType.LIGHT
            )
            SOLDIER -> DefenseProperties(
                armor = 10f,
                armorType = ArmorType.MEDIUM
            )
            TANK -> DefenseProperties(
                armor = 30f,
                armorType = ArmorType.HEAVY
            )
            ARTILLERY -> DefenseProperties(
                armor = 15f,
                armorType = ArmorType.MEDIUM
            )
            SCOUT -> DefenseProperties(
                armor = 8f,
                armorType = ArmorType.LIGHT
            )
            MEDIC -> DefenseProperties(
                armor = 12f,
                armorType = ArmorType.MEDIUM
            )
            ENGINEER -> DefenseProperties(
                armor = 15f,
                armorType = ArmorType.MEDIUM
            )
            COMMANDER -> DefenseProperties(
                armor = 20f,
                armorType = ArmorType.HEAVY,
                shield = 50f,
                shieldRegen = 5f
            )

            // Advanced Combat Units
            SNIPER -> DefenseProperties(
                armor = 8f,
                armorType = ArmorType.LIGHT,
                damageReduction = 0.1f
            )
            FLAMETHROWER -> DefenseProperties(
                armor = 15f,
                armorType = ArmorType.MEDIUM,
                damageReduction = 0.2f
            )
            ANTI_AIR -> DefenseProperties(
                armor = 12f,
                armorType = ArmorType.MEDIUM,
                shield = 20f,
                shieldRegen = 2f
            )
            SIEGE_ENGINE -> DefenseProperties(
                armor = 40f,
                armorType = ArmorType.FORTIFIED,
                damageReduction = 0.3f
            )
            STEALTH_UNIT -> DefenseProperties(
                armor = 10f,
                armorType = ArmorType.LIGHT,
                shield = 30f,
                shieldRegen = 3f
            )
            SHIELD_GENERATOR -> DefenseProperties(
                armor = 25f,
                armorType = ArmorType.HEAVY,
                shield = 100f,
                shieldRegen = 10f
            )
            PLASMA_TANK -> DefenseProperties(
                armor = 35f,
                armorType = ArmorType.HEAVY,
                shield = 40f,
                shieldRegen = 4f
            )
            BATTLE_MECH -> DefenseProperties(
                armor = 45f,
                armorType = ArmorType.FORTIFIED,
                shield = 60f,
                shieldRegen = 6f
            )

            // Support Units
            REPAIR_UNIT -> DefenseProperties(
                armor = 12f,
                armorType = ArmorType.MEDIUM,
                shield = 20f,
                shieldRegen = 2f
            )
            JAMMER -> DefenseProperties(
                armor = 10f,
                armorType = ArmorType.LIGHT,
                shield = 15f,
                shieldRegen = 1.5f
            )
            RADAR_UNIT -> DefenseProperties(
                armor = 8f,
                armorType = ArmorType.LIGHT,
                shield = 10f,
                shieldRegen = 1f
            )
            TRANSPORT -> DefenseProperties(
                armor = 20f,
                armorType = ArmorType.MEDIUM,
                shield = 30f,
                shieldRegen = 3f
            )
            SUPPLY_UNIT -> DefenseProperties(
                armor = 15f,
                armorType = ArmorType.MEDIUM,
                shield = 25f,
                shieldRegen = 2.5f
            )
            CLONE_UNIT -> DefenseProperties(
                armor = 10f,
                armorType = ArmorType.LIGHT,
                shield = 20f,
                shieldRegen = 2f
            )
            NANITE_SWARM -> DefenseProperties(
                armor = 5f,
                armorType = ArmorType.LIGHT,
                damageReduction = 0.4f
            )
            QUANTUM_UNIT -> DefenseProperties(
                armor = 30f,
                armorType = ArmorType.HEAVY,
                shield = 50f,
                shieldRegen = 5f
            )
        }
} 