package com.rtsgame.shared.config

import kotlinx.serialization.Serializable

data class FormationConfig(
    val predictionTimeSeconds: Float = 1.0f,
    val arrivalRadiusFactor: Float = 1.5f,
    val defaultMaxForce: Float = 10.0f,
    val defaultMaxTurnRateRadiansPerFrame: Float = 0.1f,
    val minFormationSlotDistance: Float = 50.0f,
    val maxFollowerSeparationDistanceFactor: Float = 2.0f,
    val steeringWeights: SteeringWeights = SteeringWeights(),
    val steeringFeelerLengthFactor: Float = 2.0f,
    val neighborRadiusFactor: Float = 1.5f,
    val commandRanges: CommandRanges = CommandRanges()
)

data class SteeringWeights(
    val separation: Float = 1.0f,
    val terrainAvoidance: Float = 1.0f
)

data class CommandRanges(
    val strategic: Float = 200.0f,
    val formationMinDistance: Float = 80.0f,
    val formationMaxDistance: Float = 150.0f
) 