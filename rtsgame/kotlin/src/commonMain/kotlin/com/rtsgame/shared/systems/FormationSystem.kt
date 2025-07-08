package com.rtsgame.shared.systems

import com.rtsgame.shared.config.FormationConfig
import com.rtsgame.shared.entity.Unit
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.Position
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FormationSystem(internal val config: FormationConfig) {
    companion object {
        internal const val DEFAULT_UNIT_SIZE = 10f
    }

    fun updateFormation(unit: Unit, gameState: GameState): Unit {
        val leader = findLeader(unit, gameState)
        return if (leader == null || leader == unit) {
            // Unit is its own leader
            updateLeader(unit)
        } else {
            // Unit is a follower
            updateFollower(unit, leader)
        }
    }

    internal fun findLeader(unit: Unit, gameState: GameState): Unit? {
        val strategicRange = config.commandRanges.strategic
        return gameState.entities.values
            .filterIsInstance<Unit>()
            .filter { it.team == unit.team && it != unit }
            .filter { it.position.distanceTo(unit.position) <= strategicRange }
            .maxByOrNull { it.authority }
    }

    internal fun updateLeader(unit: Unit): Unit {
        // Leader uses default movement and targeting
        return unit.copy(
            leaderTargetPosition = unit.patrolTarget,
            leaderPredictedPosition = null,
            idealFormationSlotWorld = null
        )
    }

    internal fun updateFollower(unit: Unit, leader: Unit): Unit {
        // Check if follower is too far from leader
        val maxSeparationDistance = config.commandRanges.strategic * config.maxFollowerSeparationDistanceFactor
        if (unit.position.distanceTo(leader.position) > maxSeparationDistance) {
            // Regroup using A* pathfinding
            return unit.copy(
                patrolTarget = leader.position,
                leaderTargetPosition = null,
                leaderPredictedPosition = null,
                idealFormationSlotWorld = null
            )
        }

        // Predict leader's future position
        val predictedPosition = predictLeaderPosition(leader)
        val idealSlot = calculateIdealFormationSlot(unit, predictedPosition)

        // Calculate and apply steering forces
        val steering = calculateSteeringForces(unit, idealSlot)
        return applySteeringForces(unit, steering, predictedPosition, idealSlot)
    }

    internal fun predictLeaderPosition(leader: Unit): Position {
        val predictionTime = config.predictionTimeSeconds
        return Position(
            leader.position.x + leader.velocity.x * predictionTime,
            leader.position.y + leader.velocity.y * predictionTime
        )
    }

    internal fun calculateIdealFormationSlot(unit: Unit, leaderPosition: Position): Position {
        val angle = unit.formationAngle
        val distance = config.minFormationSlotDistance
        return Position(
            leaderPosition.x + cos(angle) * distance,
            leaderPosition.y + sin(angle) * distance
        )
    }

    internal fun calculateSteeringForces(unit: Unit, targetPosition: Position): Position {
        var steering = Position(0f, 0f)

        // Seek/Arrive force
        val seekForce = calculateSeekForce(unit, targetPosition)
        steering = steering + seekForce

        // Separation force
        val separationForce = calculateSeparationForce(unit)
        steering = steering + separationForce * config.steeringWeights.separation

        // Terrain avoidance force
        val terrainForce = calculateTerrainAvoidanceForce(unit)
        steering = steering + terrainForce * config.steeringWeights.terrainAvoidance

        // Truncate by max force
        val maxForce = config.defaultMaxForce
        val forceMagnitude = sqrt(steering.x * steering.x + steering.y * steering.y)
        if (forceMagnitude > maxForce) {
            steering = steering * (maxForce / forceMagnitude)
        }

        return steering
    }

    internal fun calculateSeekForce(unit: Unit, targetPosition: Position): Position {
        val toTarget = targetPosition - unit.position
        val distance = sqrt(toTarget.x * toTarget.x + toTarget.y * toTarget.y)
        
        // Arrive behavior: slow down when close to target
        val arrivalRadius = DEFAULT_UNIT_SIZE * config.arrivalRadiusFactor
        val speed = if (distance < arrivalRadius) {
            unit.speed * (distance / arrivalRadius)
        } else {
            unit.speed
        }

        return if (distance > 0) {
            toTarget * (speed / distance)
        } else {
            Position(0f, 0f)
        }
    }

    internal fun calculateSeparationForce(unit: Unit): Position {
        var force = Position(0f, 0f)
        val neighborRadius = DEFAULT_UNIT_SIZE * config.neighborRadiusFactor

        // TODO: Get nearby units from game state
        val nearbyUnits = emptyList<Unit>() // Placeholder

        for (other in nearbyUnits) {
            if (other == unit) continue

            val toUnit = unit.position - other.position
            val distance = sqrt(toUnit.x * toUnit.x + toUnit.y * toUnit.y)

            if (distance < neighborRadius) {
                val strength = 1f - (distance / neighborRadius)
                force = force + toUnit * (strength / distance)
            }
        }

        return force
    }

    internal fun calculateTerrainAvoidanceForce(unit: Unit): Position {
        // TODO: Implement terrain avoidance using feeler
        return Position(0f, 0f)
    }

    internal fun applySteeringForces(
        unit: Unit,
        steering: Position,
        predictedPosition: Position,
        idealSlot: Position
    ): Unit {
        // Apply steering to velocity
        val newVelocity = unit.velocity + steering

        // Truncate velocity by max speed
        val speed = sqrt(newVelocity.x * newVelocity.x + newVelocity.y * newVelocity.y)
        val finalVelocity = if (speed > unit.speed) {
            newVelocity * (unit.speed / speed)
        } else {
            newVelocity
        }

        // Update angle smoothly
        val newAngle = if (speed > 0) {
            val targetAngle = kotlin.math.atan2(finalVelocity.y, finalVelocity.x)
            val angleDiff = targetAngle - unit.angle
            val maxTurn = config.defaultMaxTurnRateRadiansPerFrame
            unit.angle + angleDiff.coerceIn(-maxTurn, maxTurn)
        } else {
            unit.angle
        }

        return unit.copy(
            velocity = finalVelocity,
            angle = newAngle,
            leaderPredictedPosition = predictedPosition,
            idealFormationSlotWorld = idealSlot
        )
    }
} 