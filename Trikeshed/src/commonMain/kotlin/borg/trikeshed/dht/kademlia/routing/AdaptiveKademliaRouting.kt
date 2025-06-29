@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.dht.kademlia.routing

import kotlinx.datetime.Clock
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import kotlinx.coroutines.*
import kotlin.math.*

// Type alias for NodeID
typealias NodeID = NUID
typealias Node = NodeInfo

/**
 * Adaptive Kademlia Routing with Learned Weights and Hadamard Products
 * 
 * The Hadamard product (element-wise multiplication) helps by:
 * 1. Combining multiple routing metrics into a single decision vector
 * 2. Allowing learned weights to modulate base XOR distances
 * 3. Incorporating temporal, reliability, and performance factors
 * 
 * Learning happens through:
 * - Success/failure feedback from actual routing attempts
 * - Latency measurements for each hop
 * - Node availability patterns over time
 * - Network topology changes
 */
class AdaptiveKademliaRouting(
    private val nodeId: NodeID,
    private val k: Int = 20 // k-bucket size
) {
    // Multi-dimensional routing metrics
    private val routingTable = RoutingTable(nodeId, k)
    private val nodeMetrics = mutableMapOf<NodeID, NodeMetrics>()
    
    // Learned weight vectors for different optimization goals
    private val weightVectors = WeightVectors()
    
    /**
     * Node metrics tracked over time
     */
    data class NodeMetrics(
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var totalLatencyMs: Long = 0,
        var lastSeenTimestamp: Long = 0,
        var bandwidthBytesPerSec: Long = 0,
        
        // Learned features
        val latencyHistory: MutableList<Long> = mutableListOf(),
        val availabilityWindows: MutableList<TimeWindow> = mutableListOf(),
        val routingSuccessRate: MutableMap<NodeID, Double> = mutableMapOf()
    ) {
        fun getSuccessRate(): Double = 
            if (successCount + failureCount == 0L) 0.5 
            else successCount.toDouble() / (successCount + failureCount)
        
        fun getAverageLatency(): Double =
            if (successCount == 0L) Double.MAX_VALUE
            else totalLatencyMs.toDouble() / successCount
            
        fun getPredictedAvailability(currentTime: Long): Double {
            // Use availability windows to predict if node is likely online
            return availabilityWindows
                .filter { it.contains(currentTime) }
                .map { it.confidence }
                .maxOrNull() ?: 0.5
        }
    }
    
    data class TimeWindow(
        val startHour: Int,
        val endHour: Int,
        val dayOfWeek: Int,
        val confidence: Double
    ) {
        fun contains(timestamp: Long): Boolean {
            // Check if timestamp falls within this window
            val hour = (timestamp / 3600000 % 24).toInt()
            val day = (timestamp / 86400000 % 7).toInt()
            return day == dayOfWeek && hour in startHour..endHour
        }
    }
    
    /**
     * Weight vectors for different routing objectives
     */
    class WeightVectors {
        // Base weights (learned through gradient descent)
        var distanceWeight = 1.0
        var latencyWeight = 0.5
        var reliabilityWeight = 0.8
        var bandwidthWeight = 0.3
        var availabilityWeight = 0.6
        
        // Learning rate for weight updates
        private val learningRate = 0.01
        
        fun updateWeights(
            routingResult: RoutingResult,
            selectedNode: NodeID,
            alternatives: List<NodeID>
        ) {
            // Gradient descent update based on routing outcome
            val reward = when (routingResult) {
                is RoutingResult.Success -> {
                    1.0 - (routingResult.latencyMs / 1000.0).coerceIn(0.0, 1.0)
                }
                is RoutingResult.Failure -> -0.5
                is RoutingResult.Timeout -> -1.0
            }
            
            // Update weights based on which features contributed to selection
            val gradient = computeGradient(selectedNode, alternatives, reward)
            
            distanceWeight += learningRate * gradient.distance
            latencyWeight += learningRate * gradient.latency
            reliabilityWeight += learningRate * gradient.reliability
            bandwidthWeight += learningRate * gradient.bandwidth
            availabilityWeight += learningRate * gradient.availability
            
            // Normalize weights
            normalizeWeights()
        }
        
        private fun normalizeWeights() {
            val sum = distanceWeight + latencyWeight + reliabilityWeight + 
                     bandwidthWeight + availabilityWeight
            if (sum > 0) {
                distanceWeight /= sum
                latencyWeight /= sum
                reliabilityWeight /= sum
                bandwidthWeight /= sum
                availabilityWeight /= sum
            }
        }
        
        private data class Gradient(
            val distance: Double,
            val latency: Double,
            val reliability: Double,
            val bandwidth: Double,
            val availability: Double
        )
        
        private fun computeGradient(
            selected: NodeID,
            alternatives: List<NodeID>,
            reward: Double
        ): Gradient {
            // Compute partial derivatives for each weight
            // This is simplified - real implementation would use backpropagation
            return Gradient(
                distance = reward * 0.1,
                latency = reward * -0.2,
                reliability = reward * 0.3,
                bandwidth = reward * 0.1,
                availability = reward * 0.2
            )
        }
    }
    
    /**
     * Find k closest nodes using learned weights and Hadamard product
     */
    fun findClosestNodes(
        target: NodeID,
        count: Int = k,
        optimizeFor: RoutingObjective = RoutingObjective.BALANCED
    ): List<WeightedNode> {
        val candidates = routingTable.getAllNodes()
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        // Compute feature vectors for each candidate
        val weightedNodes = candidates.play.map { node ->
            val metrics = nodeMetrics[node.nodeId] ?: NodeMetrics()
            
            // Feature vector components
            val features = FeatureVector(
                distance = normalizeDistance(node.nodeId.distanceTo(target)),
                latency = normalizeLatency(metrics.getAverageLatency()),
                reliability = metrics.getSuccessRate(),
                bandwidth = normalizeBandwidth(metrics.bandwidthBytesPerSec),
                availability = metrics.getPredictedAvailability(currentTime)
            )
            
            // Weight vector based on optimization objective
            val weights = when (optimizeFor) {
                RoutingObjective.LATENCY -> WeightVector(0.2, 0.5, 0.2, 0.0, 0.1)
                RoutingObjective.RELIABILITY -> WeightVector(0.2, 0.1, 0.5, 0.0, 0.2)
                RoutingObjective.BANDWIDTH -> WeightVector(0.2, 0.1, 0.1, 0.5, 0.1)
                RoutingObjective.BALANCED -> WeightVector(
                    weightVectors.distanceWeight,
                    weightVectors.latencyWeight,
                    weightVectors.reliabilityWeight,
                    weightVectors.bandwidthWeight,
                    weightVectors.availabilityWeight
                )
            }
            
            // Hadamard product: element-wise multiplication
            val score = features.hadamardProduct(weights).sum()
            
            WeightedNode(node, features, score)
        }
        
        // Sort by combined score (lower is better for distance-based metrics)
        return weightedNodes
            .sortedBy { it.score }
            .take(count)
    }
    
    /**
     * Update routing metrics based on routing attempt outcome
     */
    fun updateMetrics(
        nodeId: NodeID,
        result: RoutingResult,
        target: NodeID? = null
    ) {
        val metrics = nodeMetrics.getOrPut(nodeId) { NodeMetrics() }
        
        when (result) {
            is RoutingResult.Success -> {
                metrics.successCount++
                metrics.totalLatencyMs += result.latencyMs
                metrics.lastSeenTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                metrics.latencyHistory.add(result.latencyMs)
                
                // Keep only recent history
                if (metrics.latencyHistory.size > 100) {
                    metrics.latencyHistory.removeAt(0)
                }
                
                // Update routing success rate for specific targets
                target?.let {
                    val currentRate = metrics.routingSuccessRate[it] ?: 0.5
                    metrics.routingSuccessRate[it] = currentRate * 0.9 + 0.1 // EMA
                }
                
                // Learn availability patterns
                updateAvailabilityPattern(nodeId, true)
            }
            
            is RoutingResult.Failure -> {
                metrics.failureCount++
                
                target?.let {
                    val currentRate = metrics.routingSuccessRate[it] ?: 0.5
                    metrics.routingSuccessRate[it] = currentRate * 0.9 // Decay
                }
                
                updateAvailabilityPattern(nodeId, false)
            }
            
            is RoutingResult.Timeout -> {
                metrics.failureCount++
                // Timeout indicates possible network issue or node offline
            }
        }
        
        // Update global weights based on routing outcome
        if (target != null) {
            val alternatives = findClosestNodes(target, k * 2)
                .map { it.node.nodeId }
                .filter { it != nodeId }
            
            weightVectors.updateWeights(result, nodeId, alternatives)
        }
    }
    
    /**
     * Learn availability patterns from successful/failed connections
     */
    private fun updateAvailabilityPattern(nodeId: NodeID, success: Boolean) {
        val metrics = nodeMetrics[nodeId] ?: return
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val hour = (currentTime / 3600000 % 24).toInt()
        val day = (currentTime / 86400000 % 7).toInt()
        
        // Find or create window for current time
        val window = metrics.availabilityWindows.find { 
            it.dayOfWeek == day && it.startHour <= hour && it.endHour >= hour
        } ?: TimeWindow(hour, hour, day, 0.5).also {
            metrics.availabilityWindows.add(it)
        }
        
        // Update confidence using exponential moving average
        val index = metrics.availabilityWindows.indexOf(window)
        if (index >= 0) {
            val newConfidence = if (success) {
                window.confidence * 0.9 + 0.1
            } else {
                window.confidence * 0.9
            }
            metrics.availabilityWindows[index] = window.copy(confidence = newConfidence)
        }
    }
    
    /**
     * Predict best route using learned patterns
     */
    fun predictBestRoute(
        target: NodeID,
        maxHops: Int = 5
    ): List<NodeID> {
        val route = mutableListOf<NodeID>()
        var current = nodeId
        
        repeat(maxHops) {
            val candidates = findClosestNodes(target, k * 2)
                .filter { it.node.nodeId != current && it.node.nodeId !in route }
            
            if (candidates.isEmpty()) return route
            
            // Use learned routing success rates for specific target
            val bestNext = candidates.maxByOrNull { candidate ->
                val metrics = nodeMetrics[candidate.node.nodeId]
                val targetSuccessRate = metrics?.routingSuccessRate?.get(target) ?: 0.5
                candidate.score * targetSuccessRate
            }
            
            bestNext?.let {
                route.add(it.node.nodeId)
                current = it.node.nodeId
            }
            
            if (current == target) return route
        }
        
        return route
    }
    
    // Normalization functions
    private fun normalizeDistance(distance: NodeID): Double {
        // Convert XOR distance to 0-1 range (closer = lower)
        val bits = distance.toBitString().count { it == '1' }
        return bits.toDouble() / 160 // For 160-bit IDs
    }
    
    private fun normalizeLatency(latencyMs: Double): Double {
        // Normalize latency to 0-1 (lower is better)
        return 1.0 - (1.0 / (1.0 + latencyMs / 100))
    }
    
    private fun normalizeBandwidth(bps: Long): Double {
        // Normalize bandwidth to 0-1 (higher is better)
        return 1.0 - (1.0 / (1.0 + bps / 1_000_000.0))
    }
}

/**
 * Feature vector for routing decisions
 */
data class FeatureVector(
    val distance: Double,
    val latency: Double,
    val reliability: Double,
    val bandwidth: Double,
    val availability: Double
) {
    fun hadamardProduct(weights: WeightVector): FeatureVector {
        return FeatureVector(
            distance * weights.distance,
            latency * weights.latency,
            reliability * weights.reliability,
            bandwidth * weights.bandwidth,
            availability * weights.availability
        )
    }
    
    fun sum(): Double = distance + latency + reliability + bandwidth + availability
}

/**
 * Weight vector for different routing objectives
 */
data class WeightVector(
    val distance: Double,
    val latency: Double,
    val reliability: Double,
    val bandwidth: Double,
    val availability: Double
)

/**
 * Routing objectives
 */
enum class RoutingObjective {
    LATENCY,      // Minimize latency
    RELIABILITY,  // Maximize success rate
    BANDWIDTH,    // Maximize bandwidth
    BALANCED      // Use learned weights
}

/**
 * Routing result types
 */
sealed class RoutingResult {
    data class Success(val latencyMs: Long, val hops: Int) : RoutingResult()
    data class Failure(val reason: String) : RoutingResult()
    object Timeout : RoutingResult()
}

/**
 * Weighted node for routing decisions
 */
data class WeightedNode(
    val node: Node,
    val features: FeatureVector,
    val score: Double
)

// Extension function for NodeID
private fun NodeID.toBitString(): String {
    // Convert to bit string for distance calculation
    return this.toByteArray().joinToString("") { byte ->
        byte.toInt().and(0xFF).toString(2).padStart(8, '0')
    }
}