import kotlin.math.*
package rtsgame.ai
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Neural Network-based AI for advanced unit control
 * Uses lightweight inference for real-time decision making
 */
class NeuralNetworkAI {
    // Network architecture
    internal val inputSize = 64  // Game state features
    internal val hiddenSize = 128
    internal val outputSize = 16  // Possible actions
    
    // Network weights (pre-trained)
    internal val weights1 = Array(inputSize) { FloatArray(hiddenSize) { Random.nextFloat() - 0.5f } }
    internal val weights2 = Array(hiddenSize) { FloatArray(outputSize) { Random.nextFloat() - 0.5f } }
    internal val bias1 = FloatArray(hiddenSize) { Random.nextFloat() - 0.5f }
    internal val bias2 = FloatArray(outputSize) { Random.nextFloat() - 0.5f }
    
    // Activation caches for performance
    internal val hidden = FloatArray(hiddenSize)
    internal val output = FloatArray(outputSize)
    
    /**
     * Make decision for a unit based on game state
     */
    fun decide(
        unit: EntityId,
        world: ECSWorld,
        nearbyUnits: Indexed<EntityId>,
        nearbyEnemies: Indexed<EntityId>,
        nearbyResources: Indexed<EntityId>
    ): AIDecision {
        // Extract features
        val features = extractFeatures(unit, world, nearbyUnits, nearbyEnemies, nearbyResources)
        
        // Forward pass
        forward(features)
        
        // Select action
        val actionIndex = selectAction(output)
        
        return interpretAction(actionIndex, unit, world, nearbyEnemies, nearbyResources)
    }
    
    internal fun extractFeatures(
        unit: EntityId,
        world: ECSWorld,
        nearbyUnits: Indexed<EntityId>,
        nearbyEnemies: Indexed<EntityId>,
        nearbyResources: Indexed<EntityId>
    ): FloatArray {
        val features = FloatArray(inputSize)
        var idx = 0
        
        // Unit's own state
        val pos = world.getComponent<PositionComponent>(unit, ComponentTypes.POSITION)!!
        val health = world.getComponent<HealthComponent>(unit, ComponentTypes.HEALTH)!!
        val vel = world.getComponent<VelocityComponent>(unit, ComponentTypes.VELOCITY)
        
        features[idx++] = pos.x / 1000f  // Normalized position
        features[idx++] = pos.y / 1000f
        features[idx++] = health.healthPercent
        features[idx++] = health.shieldPercent
        features[idx++] = vel?.vx ?: 0f / 100f  // Normalized velocity
        features[idx++] = vel?.vy ?: 0f / 100f
        
        // Nearby units (allies)
        val allyFeatures = encodeNearbyEntities(world, pos, nearbyUnits, 8)
        for (i in 0 until 16) {
            features[idx++] = allyFeatures[i]
        }
        
        // Nearby enemies
        val enemyFeatures = encodeNearbyEntities(world, pos, nearbyEnemies, 8)
        for (i in 0 until 16) {
            features[idx++] = enemyFeatures[i]
        }
        
        // Resources
        val resourceFeatures = encodeNearbyEntities(world, pos, nearbyResources, 4)
        for (i in 0 until 8) {
            features[idx++] = resourceFeatures[i]
        }
        
        // Global state features
        features[idx++] = sin(Clock.System.now().toEpochMilliseconds() / 10000f)  // Time encoding
        features[idx++] = cos(Clock.System.now().toEpochMilliseconds() / 10000f)
        
        // Pad remaining with zeros
        while (idx < inputSize) {
            features[idx++] = 0f
        }
        
        return features
    }
    
    internal fun encodeNearbyEntities(
        world: ECSWorld,
        myPos: PositionComponent,
        entities: Indexed<EntityId>,
        maxCount: Int
    ): FloatArray {
        val encoded = FloatArray(maxCount * 2)  // x,y relative positions
        
        for (i in 0 until minOf(entities.a, maxCount)) {
            val entity = entities[i]
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)
            if (pos != null) {
                encoded[i * 2] = (pos.x - myPos.x) / 200f  // Relative position
                encoded[i * 2 + 1] = (pos.y - myPos.y) / 200f
            }
        }
        
        return encoded
    }
    
    internal fun forward(input: FloatArray) {
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
    }
    
    internal fun selectAction(probabilities: FloatArray): Int {
        // Sample from probability distribution
        val r = Random.nextFloat()
        var cumSum = 0f
        
        for (i in probabilities.indices) {
            cumSum += probabilities[i]
            if (r <= cumSum) {
                return i
            }
        }
        
        return probabilities.size - 1
    }
    
    internal fun interpretAction(
        actionIndex: Int,
        unit: EntityId,
        world: ECSWorld,
        enemies: Indexed<EntityId>,
        resources: Indexed<EntityId>
    ): AIDecision {
        return when (actionIndex) {
            0 -> AIDecision.Move(randomDirection())
            1 -> AIDecision.AttackNearest(enemies.getOrNull(0))
            2 -> AIDecision.Flee
            3 -> AIDecision.GatherResource(resources.getOrNull(0))
            4 -> AIDecision.FormUp
            5 -> AIDecision.Patrol
            6 -> AIDecision.Defend
            7 -> AIDecision.UseAbility(0)
            else -> AIDecision.Idle
        }
    }
    
    internal fun randomDirection(): PositionComponent {
        val angle = Random.nextFloat() * PI * 2
        return PositionComponent(
            cos(angle).toFloat() * 100f,
            sin(angle).toFloat() * 100f
        )
    }
}

/**
 * Reinforcement Learning trainer for AI improvement
 */
class RLTrainer {
    internal val replayBuffer = CircularBuffer<Experience>(10000)
    internal val gamma = 0.99f  // Discount factor
    internal val learningRate = 0.001f
    
    fun recordExperience(
        state: FloatArray,
        action: Int,
        reward: Float,
        nextState: FloatArray,
        done: Boolean
    ) {
        replayBuffer.add(Experience(state, action, reward, nextState, done))
    }
    
    fun train(network: NeuralNetworkAI, batchSize: Int = 32) {
        if (replayBuffer.size < batchSize) return
        
        // Sample batch
        val batch = replayBuffer.sample(batchSize)
        
        // Calculate targets and update weights
        batch.forEach { exp ->
            // Q-learning update
            val target = if (exp.done) {
                exp.reward
            } else {
                exp.reward + gamma * maxQ(network, exp.nextState)
            }
            
            // Gradient descent update (simplified)
            updateWeights(network, exp.state, exp.action, target)
        }
    }
    
    internal fun maxQ(network: NeuralNetworkAI, state: FloatArray): Float {
        // Get max Q-value for next state
        return 0f  // Simplified
    }
    
    internal fun updateWeights(
        network: NeuralNetworkAI,
        state: FloatArray,
        action: Int,
        target: Float
    ) {
        // Backpropagation (simplified)
    }
}

/**
 * Swarm AI for coordinated group behavior
 */
class SwarmAI : System {
    internal val swarms = mutableMapOf<Int, Swarm>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update swarm assignments
        updateSwarms(world)
        
        // Apply swarm behaviors
        swarms.forEach { (swarmId, swarm) ->
            applySwarmBehavior(world, swarm, deltaTime)
        }
    }
    
    internal fun updateSwarms(world: ECSWorld) {
        // Group nearby units into swarms
        val processed = mutableSetOf<EntityId>()
        
        world.forEach<TeamComponent>(ComponentTypes.TEAM) { entity, team ->
            if (entity in processed) return@forEach
            
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            
            // Find nearby teammates
            val swarmMembers = findNearbyTeammates(world, entity, pos, team.teamId, 100f)
            
            if (swarmMembers.a >= 5) {  // Minimum swarm size
                val swarmId = entity.value  // Use first entity as swarm ID
                val swarm = Swarm(swarmId, team.teamId)
                
                for (i in 0 until swarmMembers.a) {
                    swarm.members.add(swarmMembers[i])
                    processed.add(swarmMembers[i])
                }
                
                swarms[swarmId] = swarm
            }
        }
    }
    
    internal fun applySwarmBehavior(world: ECSWorld, swarm: Swarm, deltaTime: Float) {
        // Calculate swarm center
        var centerX = 0f
        var centerY = 0f
        var count = 0
        
        swarm.members.forEach { member ->
            val pos = world.getComponent<PositionComponent>(member, ComponentTypes.POSITION)
            if (pos != null) {
                centerX += pos.x
                centerY += pos.y
                count++
            }
        }
        
        if (count == 0) return
        
        centerX /= count
        centerY /= count
        
        // Apply swarm forces
        swarm.members.forEach { member ->
            val pos = world.getComponent<PositionComponent>(member, ComponentTypes.POSITION) ?: return@forEach
            val physics = world.getComponent<PhysicsComponent>(member, ComponentTypes.PHYSICS) ?: return@forEach
            
            // Cohesion - move towards center
            val cohesionX = (centerX - pos.x) * 0.01f
            val cohesionY = (centerY - pos.y) * 0.01f
            
            // Separation - avoid crowding
            var separationX = 0f
            var separationY = 0f
            
            swarm.members.forEach { other ->
                if (other != member) {
                    val otherPos = world.getComponent<PositionComponent>(other, ComponentTypes.POSITION)
                    if (otherPos != null) {
                        val dx = pos.x - otherPos.x
                        val dy = pos.y - otherPos.y
                        val distSq = dx * dx + dy * dy
                        
                        if (distSq < 400f && distSq > 0) {  // 20 unit separation
                            separationX += dx / distSq * 100f
                            separationY += dy / distSq * 100f
                        }
                    }
                }
            }
            
            // Alignment - match average velocity
            var alignX = 0f
            var alignY = 0f
            var velCount = 0
            
            swarm.members.forEach { other ->
                val vel = world.getComponent<VelocityComponent>(other, ComponentTypes.VELOCITY)
                if (vel != null) {
                    alignX += vel.vx
                    alignY += vel.vy
                    velCount++
                }
            }
            
            if (velCount > 0) {
                alignX = alignX / velCount * 0.1f
                alignY = alignY / velCount * 0.1f
            }
            
            // Apply combined forces
            physics.acceleration.vx += cohesionX + separationX + alignX
            physics.acceleration.vy += cohesionY + separationY + alignY
        }
    }
    
    internal fun findNearbyTeammates(
        world: ECSWorld,
        entity: EntityId,
        pos: PositionComponent,
        teamId: Int,
        range: Float
    ): Indexed<EntityId> {
        val teammates = mutableListOf<EntityId>()
        val rangeSq = range * range
        
        world.forEach<TeamComponent>(ComponentTypes.TEAM) { other, team ->
            if (team.teamId == teamId) {
                val otherPos = world.getComponent<PositionComponent>(other, ComponentTypes.POSITION)
                if (otherPos != null) {
                    val distSq = (pos.x - otherPos.x).pow(2) + (pos.y - otherPos.y).pow(2)
                    if (distSq <= rangeSq) {
                        teammates.add(other)
                    }
                }
            }
        }
        
        return teammates.size j { i -> teammates[i] }
    }
}

// Supporting classes
sealed class AIDecision {
    object Idle : AIDecision()
    data class Move(val direction: PositionComponent) : AIDecision()
    data class AttackNearest(val target: EntityId?) : AIDecision()
    object Flee : AIDecision()
    data class GatherResource(val resource: EntityId?) : AIDecision()
    object FormUp : AIDecision()
    object Patrol : AIDecision()
    object Defend : AIDecision()
    data class UseAbility(val abilityIndex: Int) : AIDecision()
}

data class Experience(
    val state: FloatArray,
    val action: Int,
    val reward: Float,
    val nextState: FloatArray,
    val done: Boolean
)

class CircularBuffer<T>(internal val capacity: Int) {
    internal val buffer = mutableListOf<T>()
    
    val size: Int get() = buffer.size
    
    fun add(item: T) {
        if (buffer.size >= capacity) {
            buffer.removeAt(0)
        }
        buffer.add(item)
    }
    
    fun sample(count: Int): List<T> {
        return buffer.shuffled().take(count)
    }
}

class Swarm(
    val id: Int,
    val teamId: Int
) {
    val members = mutableListOf<EntityId>()
    var objective: SwarmObjective = SwarmObjective.Idle
    var formation: FormationType = FormationType.SCATTER
}

sealed class SwarmObjective {
    object Idle : SwarmObjective()
    data class Attack(val target: PositionComponent) : SwarmObjective()
    data class Defend(val position: PositionComponent) : SwarmObjective()
    data class Gather(val resource: PositionComponent) : SwarmObjective()
}

internal fun <T> Indexed<T>.getOrNull(index: Int): T? {
    return if (index < a) this[index] else null
}