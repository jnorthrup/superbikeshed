package com.rtsgame.shared.command

import com.rtsgame.shared.config.FormationConfig
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.GameMap
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.systems.FormationSystem
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed class FormationCommand : Command {
    @Serializable
    data class SetFormation(
        val unitIds: List<String>,
        val formationType: FormationType,
        val targetPosition: Position
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val units = unitIds.mapNotNull { state.entities[it] as? Unit }
            if (units.isEmpty()) return state

            // Set the first unit as the leader
            val leader = units.first()
            var updatedState = state.updateEntity(leader.copy(
                patrolTarget = targetPosition,
                leaderTargetPosition = targetPosition
            ))

            // Calculate formation offsets for followers
            val offsets = calculateFormationOffsets(formationType, units.size)
            for (i in 1 until units.size) {
                val unit = units[i]
                val offset = offsets[i - 1]
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = offset,
                    patrolTarget = targetPosition
                ))
            }

            return updatedState
        }
    }

    @Serializable
    data class ChangeFormation(
        val unitIds: List<String>,
        val formationType: FormationType
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val units = unitIds.mapNotNull { state.entities[it] as? Unit }
            if (units.isEmpty()) return state

            // Calculate new formation offsets
            val offsets = calculateFormationOffsets(formationType, units.size)
            var updatedState = state

            // Update each unit's formation offset
            for (i in units.indices) {
                val unit = units[i]
                val offset = if (i == 0) Position(0f, 0f) else offsets[i - 1]
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = offset
                ))
            }

            return updatedState
        }
    }

    @Serializable
    data class SetLeader(
        val unitId: String
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val unit = state.entities[unitId] as? Unit ?: return state
            return state.updateEntity(unit.copy(
                authority = unit.authority + 1f
            ))
        }
    }

    @Serializable
    data class DisbandFormation(
        val unitIds: List<String>
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            var updatedState = state
            for (id in unitIds) {
                val unit = state.entities[id] as? Unit ?: continue
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = Position(0f, 0f),
                    leaderTargetPosition = null,
                    leaderPredictedPosition = null,
                    idealFormationSlotWorld = null,
                    patrolTarget = null
                ))
            }
            return updatedState
        }
    }

    @Serializable
    data class RotateFormation(
        val unitIds: List<String>,
        val angleDegrees: Float
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val units = unitIds.mapNotNull { state.entities[it] as? Unit }
            if (units.isEmpty()) return state

            val leader = units.first()
            val angleRadians = angleDegrees * (PI / 180f)
            var updatedState = state

            // Rotate each unit's formation offset around the leader
            for (unit in units) {
                val offset = unit.formationOffset
                val rotatedOffset = Position(
                    x = (offset.x * cos(angleRadians) - offset.y * sin(angleRadians)).toFloat(),
                    y = (offset.x * sin(angleRadians) + offset.y * cos(angleRadians)).toFloat()
                )
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = rotatedOffset
                ))
            }

            return updatedState
        }
    }

    @Serializable
    data class ChangeFormationFacing(
        val unitIds: List<String>,
        val targetPosition: Position
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val units = unitIds.mapNotNull { state.entities[it] as? Unit }
            if (units.isEmpty()) return state

            val leader = units.first()
            val currentPos = leader.position
            val direction = Position(
                targetPosition.x - currentPos.x,
                targetPosition.y - currentPos.y
            )
            val angle = kotlin.math.atan2(direction.y, direction.x)
            var updatedState = state

            // Rotate formation to face the target
            for (unit in units) {
                val offset = unit.formationOffset
                val rotatedOffset = Position(
                    x = (offset.x * cos(angle) - offset.y * sin(angle)).toFloat(),
                    y = (offset.x * sin(angle) + offset.y * cos(angle)).toFloat()
                )
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = rotatedOffset,
                    patrolTarget = targetPosition
                ))
            }

            return updatedState
        }
    }

    @Serializable
    data class MoveFormation(
        val unitIds: List<String>,
        val targetPosition: Position,
        val maintainFacing: Boolean = true
    ) : FormationCommand() {
        override fun execute(state: GameState, gameMap: GameMap): GameState {
            val units = unitIds.mapNotNull { state.entities[it] as? Unit }
            if (units.isEmpty()) return state

            val leader = units.first()
            var updatedState = state.updateEntity(leader.copy(
                patrolTarget = targetPosition,
                leaderTargetPosition = targetPosition
            ))

            // Move all units while maintaining formation
            for (unit in units) {
                val offset = if (maintainFacing) unit.formationOffset else {
                    // Calculate new offset based on target position
                    val direction = Position(
                        targetPosition.x - leader.position.x,
                        targetPosition.y - leader.position.y
                    )
                    val angle = kotlin.math.atan2(direction.y, direction.x)
                    val originalOffset = unit.formationOffset
                    Position(
                        x = (originalOffset.x * cos(angle) - originalOffset.y * sin(angle)).toFloat(),
                        y = (originalOffset.x * sin(angle) + originalOffset.y * cos(angle)).toFloat()
                    )
                }
                updatedState = updatedState.updateEntity(unit.copy(
                    formationOffset = offset,
                    patrolTarget = targetPosition
                ))
            }

            return updatedState
        }
    }

    internal fun calculateFormationOffsets(formationType: FormationType, unitCount: Int): List<Position> {
        val offsets = mutableListOf<Position>()
        val spacing = 20f // Base spacing between units

        when (formationType) {
            FormationType.LINE -> {
                // Horizontal line formation
                for (i in 1 until unitCount) {
                    offsets.add(Position(i * spacing, 0f))
                }
            }
            FormationType.COLUMN -> {
                // Vertical line formation
                for (i in 1 until unitCount) {
                    offsets.add(Position(0f, i * spacing))
                }
            }
            FormationType.WEDGE -> {
                // V-shaped formation
                val halfCount = (unitCount - 1) / 2
                for (i in 1 until unitCount) {
                    val row = (i - 1) / 2 + 1
                    val side = if (i % 2 == 0) 1 else -1
                    offsets.add(Position(side * row * spacing, row * spacing))
                }
            }
            FormationType.BOX -> {
                // Square/rectangular formation
                val sideLength = kotlin.math.ceil(kotlin.math.sqrt((unitCount - 1).toDouble())).toInt()
                var index = 0
                for (row in 0 until sideLength) {
                    for (col in 0 until sideLength) {
                        if (index < unitCount - 1) {
                            offsets.add(Position(col * spacing, row * spacing))
                            index++
                        }
                    }
                }
            }
            FormationType.CIRCLE -> {
                // Circular formation
                val radius = spacing * (unitCount - 1) / (2 * PI)
                for (i in 1 until unitCount) {
                    val angle = 2 * PI * i / (unitCount - 1)
                    offsets.add(Position(
                        (radius * cos(angle)).toFloat(),
                        (radius * sin(angle)).toFloat()
                    ))
                }
            }
            FormationType.SCATTER -> {
                // Random scatter formation
                for (i in 1 until unitCount) {
                    val angle = 2 * PI * kotlin.random.Random.nextDouble()
                    val distance = spacing * kotlin.random.Random.nextDouble(0.5, 1.5)
                    offsets.add(Position(
                        (distance * cos(angle)).toFloat(),
                        (distance * sin(angle)).toFloat()
                    ))
                }
            }
        }

        return offsets
    }
}

enum class FormationType {
    LINE,      // Units form a horizontal line
    COLUMN,    // Units form a vertical line
    WEDGE,     // Units form a V shape
    BOX,       // Units form a square/rectangle
    CIRCLE,    // Units form a circle
    SCATTER    // Units spread out randomly
} 