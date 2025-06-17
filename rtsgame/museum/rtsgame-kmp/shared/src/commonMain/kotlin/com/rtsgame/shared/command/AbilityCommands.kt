package com.rtsgame.shared.command

import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.systems.AbilitySystem
import com.rtsgame.shared.systems.Effect
import com.rtsgame.shared.systems.EffectType
import com.rtsgame.shared.systems.TargetType
import com.rtsgame.shared.systems.AbilityType

sealed class AbilityCommand : Command {
    data class UseAbility(
        val unitId: String,
        val abilityId: String,
        val targetId: String? = null,
        val targetPosition: Position? = null
    ) : AbilityCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val unit = state.units[unitId] ?: return state
            val ability = AbilitySystem.getAbility(abilityId) ?: return state

            // Check if unit has the ability
            if (!unit.abilities.contains(abilityId)) return state

            // Check cooldown
            if (unit.abilityCooldowns[abilityId] ?: 0f > 0f) return state

            // Check energy cost
            if (unit.energy < ability.energyCost) return state

            // Execute ability based on target type
            val updatedUnit = when (ability.targetType) {
                TargetType.NONE -> {
                    AbilitySystem.executeAbility(ability, unit, null, null, state)
                }
                TargetType.SELF -> {
                    AbilitySystem.executeAbility(ability, unit, unit, null, state)
                }
                TargetType.UNIT -> {
                    val targetUnit = state.units[targetId] ?: return state
                    AbilitySystem.executeAbility(ability, unit, targetUnit, null, state)
                }
                TargetType.BUILDING -> {
                    val targetBuilding = state.buildings[targetId] ?: return state
                    AbilitySystem.executeAbility(ability, unit, targetBuilding, null, state)
                }
                TargetType.POSITION -> {
                    val position = targetPosition ?: return state
                    AbilitySystem.executeAbility(ability, unit, null, position, state)
                }
                TargetType.AREA -> {
                    val position = targetPosition ?: return state
                    AbilitySystem.executeAbility(ability, unit, null, position, state)
                }
            }

            return state.copy(units = state.units + (unitId to updatedUnit))
        }
    }

    data class ToggleAbility(
        val unitId: String,
        val abilityId: String
    ) : AbilityCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val unit = state.units[unitId] ?: return state
            val ability = AbilitySystem.getAbility(abilityId) ?: return state

            // Check if unit has the ability
            if (!unit.abilities.contains(abilityId)) return state

            // Check if ability is toggleable
            if (ability.type != AbilityType.TOGGLE) return state

            // Check energy cost
            if (unit.energy < ability.energyCost) return state

            // Toggle ability
            val updatedUnit = AbilitySystem.executeAbility(ability, unit, null, null, state)

            return state.copy(units = state.units + (unitId to updatedUnit))
        }
    }

    data class CancelAbility(
        val unitId: String,
        val abilityId: String
    ) : AbilityCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val unit = state.units[unitId] ?: return state
            val ability = AbilitySystem.getAbility(abilityId) ?: return state

            // Check if unit has the ability
            if (!unit.abilities.contains(abilityId)) return state

            // Check if ability is channeled
            if (ability.type != AbilityType.CHANNELED) return state

            // Cancel ability
            val updatedUnit = unit.copy(
                activeEffects = unit.activeEffects.filter { it.sourceAbilityId != abilityId }
            )

            return state.copy(units = state.units + (unitId to updatedUnit))
        }
    }
} 