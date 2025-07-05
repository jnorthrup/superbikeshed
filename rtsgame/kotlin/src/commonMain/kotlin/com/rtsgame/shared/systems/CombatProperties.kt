package com.rtsgame.shared.systems

import kotlinx.serialization.Serializable

data class AttackProperties(
    val damage: Float,
    val damageMultiplier: Float = 1.0f,
    val attackSpeed: Float = 1.0f,
    val range: Float = 1.0f,
    val damageType: DamageType = DamageType.NORMAL,
    val criticalChance: Float = 0.0f,
    val criticalMultiplier: Float = 1.5f,
    val splashRadius: Float = 0f,
    val splashDamage: Float = 0f,
    val penetration: Float = 0f,
    val accuracy: Float = 1.0f,
    val projectileSpeed: Float = 0f,
    val isMelee: Boolean = true,
    val canAttackAir: Boolean = false,
    val canAttackGround: Boolean = true,
    val canAttackBuildings: Boolean = true
)

data class DefenseProperties(
    val armor: Float = 0f,
    val armorMultiplier: Float = 1.0f,
    val shield: Float = 0f,
    val shieldRegen: Float = 0f,
    val shieldMultiplier: Float = 1.0f,
    val healingMultiplier: Float = 1.0f,
    val dodgeChance: Float = 0f,
    val damageReduction: Float = 0f,
    val armorType: ArmorType = ArmorType.LIGHT,
    val isInvulnerable: Boolean = false,
    val isImmuneToStun: Boolean = false,
    val isImmuneToRoot: Boolean = false,
    val isImmuneToSilence: Boolean = false,
    val isImmuneToDisarm: Boolean = false,
    val isImmuneToKnockback: Boolean = false,
    val isImmuneToPull: Boolean = false
)

enum class DamageType {
    NORMAL,
    PIERCING,
    EXPLOSIVE,
    ENERGY,
    PLASMA,
    LASER,
    ION,
    QUANTUM,
    PSIONIC,
    POISON,
    FIRE,
    ICE,
    LIGHTNING,
    ACID,
    RADIATION
}

enum class ArmorType {
    LIGHT,
    MEDIUM,
    HEAVY,
    FORTIFIED,
    BIOLOGICAL,
    MECHANICAL,
    ENERGY,
    QUANTUM
} 