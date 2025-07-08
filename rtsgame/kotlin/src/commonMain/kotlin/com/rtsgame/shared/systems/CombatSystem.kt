package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Building
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.Position
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

data class AttackProperties(
    val damage: Float,
    val range: Float,
    val attackSpeed: Float,
    val splashRadius: Float = 0f,
    val damageType: DamageType,
    val armorPenetration: Float = 0f,
    val criticalChance: Float = 0f,
    val criticalMultiplier: Float = 1.5f
)

data class DefenseProperties(
    val armor: Float,
    val armorType: ArmorType,
    val damageReduction: Float = 0f,
    val shield: Float = 0f,
    val shieldRegen: Float = 0f,
    val shieldRegenDelay: Float = 5f
)

enum class DamageType {
    NORMAL,
    PIERCING,
    EXPLOSIVE,
    ENERGY,
    PLASMA
}

enum class ArmorType {
    LIGHT,
    MEDIUM,
    HEAVY,
    FORTIFIED,
    SHIELDED
}

class CombatSystem(internal val gameMap: GameMap) {
    internal val damageTypeEffectiveness = mapOf(
        DamageType.NORMAL to mapOf(
            ArmorType.LIGHT to 1.0f,
            ArmorType.MEDIUM to 0.75f,
            ArmorType.HEAVY to 0.5f,
            ArmorType.FORTIFIED to 0.25f,
            ArmorType.SHIELDED to 0.5f
        ),
        DamageType.PIERCING to mapOf(
            ArmorType.LIGHT to 1.5f,
            ArmorType.MEDIUM to 1.0f,
            ArmorType.HEAVY to 0.75f,
            ArmorType.FORTIFIED to 0.5f,
            ArmorType.SHIELDED to 0.25f
        ),
        DamageType.EXPLOSIVE to mapOf(
            ArmorType.LIGHT to 1.5f,
            ArmorType.MEDIUM to 1.25f,
            ArmorType.HEAVY to 1.0f,
            ArmorType.FORTIFIED to 0.75f,
            ArmorType.SHIELDED to 1.5f
        ),
        DamageType.ENERGY to mapOf(
            ArmorType.LIGHT to 1.0f,
            ArmorType.MEDIUM to 1.0f,
            ArmorType.HEAVY to 1.0f,
            ArmorType.FORTIFIED to 0.5f,
            ArmorType.SHIELDED to 2.0f
        ),
        DamageType.PLASMA to mapOf(
            ArmorType.LIGHT to 1.25f,
            ArmorType.MEDIUM to 1.25f,
            ArmorType.HEAVY to 1.25f,
            ArmorType.FORTIFIED to 1.0f,
            ArmorType.SHIELDED to 1.5f
        )
    )

    fun update(state: GameState): GameState {
        var updatedState = state

        // Update combat for all units
        state.entities.values
            .filterIsInstance<Unit>()
            .forEach { unit ->
                if (unit.isAttacking) {
                    val target = state.entities[unit.attackTargetId]
                    if (target != null && isInRange(unit, target)) {
                        val damage = calculateDamage(unit, target)
                        val updatedTarget = applyDamage(target, damage)
                        updatedState = updatedState.updateEntity(updatedTarget)
                    } else {
                        // Target is out of range or dead, stop attacking
                        val updatedUnit = unit.copy(isAttacking = false, attackTargetId = null)
                        updatedState = updatedState.updateEntity(updatedUnit)
                    }
                }
            }

        // Update shields and regeneration
        updatedState.entities.values
            .filter { it is Unit || it is Building }
            .forEach { entity ->
                if (entity.defenseProperties.shield < entity.defenseProperties.shieldRegen) {
                    val updatedEntity = regenerateShield(entity)
                    updatedState = updatedState.updateEntity(updatedEntity)
                }
            }

        return updatedState
    }

    internal fun isInRange(attacker: Unit, target: Entity): Boolean {
        val distance = calculateDistance(attacker.position, target.position)
        return distance <= attacker.attackProperties.range
    }

    internal fun calculateDistance(pos1: Position, pos2: Position): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    internal fun calculateDamage(attacker: Unit, target: Entity): Float {
        val baseDamage = attacker.attackProperties.damage
        val effectiveness = damageTypeEffectiveness[attacker.attackProperties.damageType]?.get(target.defenseProperties.armorType) ?: 1.0f
        val armorReduction = max(0f, target.defenseProperties.armor - attacker.attackProperties.armorPenetration)
        val damageReduction = target.defenseProperties.damageReduction

        var damage = baseDamage * effectiveness * (1f - armorReduction / (armorReduction + 100f)) * (1f - damageReduction)

        // Apply critical hit
        if (Math.random() < attacker.attackProperties.criticalChance) {
            damage *= attacker.attackProperties.criticalMultiplier
        }

        return damage
    }

    internal fun applyDamage(target: Entity, damage: Float): Entity {
        // Apply damage to shield first
        val remainingShield = max(0f, target.defenseProperties.shield - damage)
        val remainingDamage = max(0f, damage - target.defenseProperties.shield)

        return when (target) {
            is Unit -> target.copy(
                health = max(0f, target.health - remainingDamage),
                defenseProperties = target.defenseProperties.copy(shield = remainingShield)
            )
            is Building -> target.copy(
                health = max(0f, target.health - remainingDamage),
                defenseProperties = target.defenseProperties.copy(shield = remainingShield)
            )
            else -> target
        }
    }

    internal fun regenerateShield(entity: Entity): Entity {
        val newShield = min(
            entity.defenseProperties.shieldRegen,
            entity.defenseProperties.shield + entity.defenseProperties.shieldRegen
        )

        return when (entity) {
            is Unit -> entity.copy(
                defenseProperties = entity.defenseProperties.copy(shield = newShield)
            )
            is Building -> entity.copy(
                defenseProperties = entity.defenseProperties.copy(shield = newShield)
            )
            else -> entity
        }
    }

    fun findTargetsInRange(attacker: Unit, state: GameState): List<Entity> {
        return state.entities.values
            .filter { it.team != attacker.team }
            .filter { isInRange(attacker, it) }
            .sortedBy { calculateDistance(attacker.position, it.position) }
    }
} 