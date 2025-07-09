package rtsgame.ai

import rtsgame.core.*
import rtsgame.components.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Neural network AI system for advanced decision making
 */
class NeuralNetworkAI : System {
    private val networks = mutableMapOf<EntityId, NeuralNetwork>()
    private val trainer = RLTrainer()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        val entitiesWithAI = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.OWNER,
            ComponentTypeId.ENTITY_TYPE
        )
        
        entitiesWithAI.forEach { entityId ->
            val network = networks.getOrPut(entityId) { NeuralNetwork() }
            
            // Get current state
            val state = encodeState(world, entityId)
            
            // Get AI decision
            val action = network.forward(state)
            val decision = interpretAction(action, entityId, world)
            
            // Execute decision
            executeDecision(world, entityId, decision)
            
            // Train network (simplified)
            trainer.train(network)
        }
    }
    
    private fun encodeState(world: ECSWorld, entityId: EntityId): FloatArray {
        val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION) ?: return FloatArray(64)
        val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER) ?: return FloatArray(64)
        
        val features = FloatArray(64)
        var idx = 0
        
        // Position features
        features[idx++] = position.x / 1000f  // Normalized position
        features[idx++] = position.y / 1000f
        
        // Team features
        features[idx++] = owner.teamId.toFloat()
        
        // Time features
        features[idx++] = sin(TimeUtils.currentTimeMillis() / 10000f)
        features[idx++] = cos(TimeUtils.currentTimeMillis() / 10000f)
        
        // Pad remaining with zeros
        while (idx < 64) {
            features[idx++] = 0f
        }
        
        return features
    }
    
    private fun interpretAction(actionIndex: Int, entityId: EntityId, world: ECSWorld): AIDecision {
        return when (actionIndex) {
            0 -> AIDecision.Move(randomDirection())
            1 -> AIDecision.AttackNearest(null)
            2 -> AIDecision.Flee
            3 -> AIDecision.GatherResource(null)
            4 -> AIDecision.FormUp
            5 -> AIDecision.Patrol
            6 -> AIDecision.Defend
            7 -> AIDecision.UseAbility(0)
            else -> AIDecision.Idle
        }
    }
    
    private fun executeDecision(world: ECSWorld, entityId: EntityId, decision: AIDecision) {
        when (decision) {
            is AIDecision.Move -> {
                val command = CommandComponent("move", targetX = decision.target.x, targetY = decision.target.y)
                world.addComponent(entityId, command)
            }
            is AIDecision.AttackNearest -> {
                decision.target?.let { targetId ->
                    val command = CommandComponent("attack", targetId = targetId.value)
                    world.addComponent(entityId, command)
                }
            }
            is AIDecision.Flee -> {
                val command = CommandComponent("flee")
                world.addComponent(entityId, command)
            }
            is AIDecision.GatherResource -> {
                decision.target?.let { targetId ->
                    val command = CommandComponent("gather", targetId = targetId.value)
                    world.addComponent(entityId, command)
                }
            }
            is AIDecision.FormUp -> {
                val command = CommandComponent("formup")
                world.addComponent(entityId, command)
            }
            is AIDecision.Patrol -> {
                val command = CommandComponent("patrol")
                world.addComponent(entityId, command)
            }
            is AIDecision.Defend -> {
                val command = CommandComponent("defend")
                world.addComponent(entityId, command)
            }
            is AIDecision.UseAbility -> {
                val command = CommandComponent("ability", priority = decision.abilityId)
                world.addComponent(entityId, command)
            }
            is AIDecision.Idle -> {
                // Do nothing
            }
        }
    }
    
    private fun randomDirection(): PositionComponent {
        val angle = Random.nextFloat() * PI * 2
        return PositionComponent(
            cos(angle).toFloat() * 100f,
            sin(angle).toFloat() * 100f
        )
    }
}

/**
 * Neural network implementation
 */
class NeuralNetwork {
    private val inputSize = 64
    private val hiddenSize = 32
    private val outputSize = 8
    
    private val weights1 = Array(inputSize) { FloatArray(hiddenSize) { Random.nextFloat() * 2f - 1f } }
    private val bias1 = FloatArray(hiddenSize) { Random.nextFloat() * 2f - 1f }
    private val weights2 = Array(hiddenSize) { FloatArray(outputSize) { Random.nextFloat() * 2f - 1f } }
    private val bias2 = FloatArray(outputSize) { Random.nextFloat() * 2f - 1f }
    
    private val hidden = FloatArray(hiddenSize)
    private val output = FloatArray(outputSize)
    
    fun forward(input: FloatArray): Int {
        // Hidden layer with ReLU
        for (j in 0 until hiddenSize) {
            var sum = bias1[j]
            for (i in 0 until inputSize) {
                sum += input[i] * weights1[i][j]
            }
            hidden[j] = maxOf(0f, sum)  // ReLU
        }
        
        // Output layer with softmax
        var maxLogit = Float.NEGATIVE_INFINITY
        for (k in 0 until outputSize) {
            var sum = bias2[k]
            for (j in 0 until hiddenSize) {
                sum += hidden[j] * weights2[j][k]
            }
            output[k] = sum
            maxLogit = maxOf(maxLogit, sum)
        }
        
        // Stable softmax
        var sumExp = 0f
        for (k in 0 until outputSize) {
            output[k] = exp(output[k] - maxLogit)
            sumExp += output[k]
        }
        
        for (k in 0 until outputSize) {
            output[k] /= sumExp
        }
        
        // Return action with highest probability
        return output.indices.maxByOrNull { output[it] } ?: 0
    }
}

/**
 * AI decision types
 */
sealed class AIDecision {
    data class Move(val target: PositionComponent) : AIDecision()
    data class AttackNearest(val target: EntityId?) : AIDecision()
    object Flee : AIDecision()
    data class GatherResource(val target: EntityId?) : AIDecision()
    object FormUp : AIDecision()
    object Patrol : AIDecision()
    object Defend : AIDecision()
    data class UseAbility(val abilityId: Int) : AIDecision()
    object Idle : AIDecision()
}

/**
 * Reinforcement Learning trainer for AI improvement
 */
class RLTrainer {
    fun train(network: NeuralNetwork) {
        // Simplified training - would implement proper RL here
    }
}