package com.rtsgame.shared.systems

import com.rtsgame.shared.command.GameState
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.Position
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

data class Ability(
    val id: String,
    val name: String,
    val description: String,
    val cooldown: Float,
    val energyCost: Float,
    val range: Float,
    val duration: Float,
    val type: AbilityType,
    val targetType: TargetType,
    val effects: List<Effect>
)

enum class AbilityType {
    ACTIVE,
    PASSIVE,
    TOGGLE,
    CHANNELED
}

enum class TargetType {
    NONE,
    SELF,
    UNIT,
    BUILDING,
    POSITION,
    AREA
}

enum class EffectType {
    DAMAGE,
    HEAL,
    SHIELD,
    SPEED_BOOST,
    SLOW,
    STUN,
    ROOT,
    SILENCE,
    DISARM,
    INVISIBILITY,
    REVEAL,
    BUFF,
    DEBUFF,
    DOT,
    HOT,
    TELEPORT,
    KNOCKBACK,
    PULL,
    CLONE,
    SUMMON
}

data class Effect(
    val type: EffectType,
    val value: Float,
    val duration: Float,
    val sourceAbilityId: String,
    val sourceUnitId: String
)

class AbilitySystem(internal val gameMap: GameMap) {
    internal val abilities = mutableMapOf<String, Ability>()

    init {
        // Initialize basic abilities
        abilities["precision_shot"] = Ability(
            id = "precision_shot",
            name = "Precision Shot",
            description = "Deals high damage to a single target",
            cooldown = 5f,
            energyCost = 25f,
            range = 10f,
            duration = 0f,
            type = AbilityType.ACTIVE,
            targetType = TargetType.UNIT,
            effects = listOf(
                Effect(
                    type = EffectType.DAMAGE,
                    value = 50f,
                    duration = 0f,
                    sourceAbilityId = "precision_shot",
                    sourceUnitId = ""
                )
            )
        )

        abilities["flame_wall"] = Ability(
            id = "flame_wall",
            name = "Flame Wall",
            description = "Creates a wall of fire that damages enemies",
            cooldown = 15f,
            energyCost = 50f,
            range = 8f,
            duration = 5f,
            type = AbilityType.ACTIVE,
            targetType = TargetType.POSITION,
            effects = listOf(
                Effect(
                    type = EffectType.DOT,
                    value = 10f,
                    duration = 5f,
                    sourceAbilityId = "flame_wall",
                    sourceUnitId = ""
                )
            )
        )

        abilities["missile_barrage"] = Ability(
            id = "missile_barrage",
            name = "Missile Barrage",
            description = "Launches multiple missiles at an area",
            cooldown = 20f,
            energyCost = 75f,
            range = 15f,
            duration = 3f,
            type = AbilityType.CHANNELED,
            targetType = TargetType.AREA,
            effects = listOf(
                Effect(
                    type = EffectType.DAMAGE,
                    value = 30f,
                    duration = 0f,
                    sourceAbilityId = "missile_barrage",
                    sourceUnitId = ""
                )
            )
        )

        abilities["cloak"] = Ability(
            id = "cloak",
            name = "Cloak",
            description = "Makes the unit invisible",
            cooldown = 0f,
            energyCost = 20f,
            range = 0f,
            duration = 0f,
            type = AbilityType.TOGGLE,
            targetType = TargetType.SELF,
            effects = listOf(
                Effect(
                    type = EffectType.INVISIBILITY,
                    value = 1f,
                    duration = 0f,
                    sourceAbilityId = "cloak",
                    sourceUnitId = ""
                )
            )
        )
    }

    fun getAbility(id: String): Ability? = abilities[id]

    fun executeAbility(
        ability: Ability,
        source: Unit,
        target: Entity?,
        position: Position?,
        state: GameState
    ): Unit {
        // Apply effects based on target type
        val updatedUnit = when (ability.targetType) {
            TargetType.NONE -> applyEffects(ability, source, null, state)
            TargetType.SELF -> applyEffects(ability, source, source, state)
            TargetType.UNIT, TargetType.BUILDING -> applyEffects(ability, source, target, state)
            TargetType.POSITION, TargetType.AREA -> applyAreaEffects(ability, source, position, state)
        }

        // Update cooldown and energy
        return updatedUnit.copy(
            abilityCooldowns = updatedUnit.abilityCooldowns + (ability.id to ability.cooldown),
            energy = updatedUnit.energy - ability.energyCost
        )
    }

    internal fun applyEffects(
        ability: Ability,
        source: Unit,
        target: Entity?,
        state: GameState
    ): Unit {
        var updatedUnit = source
        ability.effects.forEach { effect ->
            val effectWithSource = effect.copy(sourceUnitId = source.id)
            when (effect.type) {
                EffectType.DAMAGE -> {
                    // Apply damage to target
                    target?.let { t ->
                        val damage = effect.value * (1 + source.attackProperties.damageMultiplier)
                        // TODO: Apply damage to target
                    }
                }
                EffectType.HEAL -> {
                    // Apply healing to target
                    target?.let { t ->
                        val healing = effect.value * (1 + source.defenseProperties.healingMultiplier)
                        // TODO: Apply healing to target
                    }
                }
                EffectType.SHIELD -> {
                    // Apply shield to target
                    target?.let { t ->
                        val shield = effect.value * (1 + source.defenseProperties.shieldMultiplier)
                        // TODO: Apply shield to target
                    }
                }
                else -> {
                    // Apply other effects
                    target?.let { t ->
                        // TODO: Apply other effects
                    }
                }
            }
        }
        return updatedUnit
    }

    internal fun applyAreaEffects(
        ability: Ability,
        source: Unit,
        position: Position?,
        state: GameState
    ): Unit {
        var updatedUnit = source
        if (position != null) {
            // Find all entities in area
            val affectedEntities = state.units.values.filter { unit ->
                unit.position.distanceTo(position) <= ability.range
            }

            // Apply effects to all affected entities
            affectedEntities.forEach { entity ->
                applyEffects(ability, source, entity, state)
            }
        }
        return updatedUnit
    }
} 