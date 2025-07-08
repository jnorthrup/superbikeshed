package com.rtsgame.shared.network

import kotlin.math.*

/**
 * Network Physics System - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - Deterministic network delay simulation
 * - Connection types: Fiber optic, copper, wireless, satellite
 * - Bandwidth management and congestion simulation
 * - Light speed physics modeling
 */
class NetworkPhysics {
    
    companion object {
        // Physical constants
        const val LIGHT_SPEED_KM_S = 299792.458
        const val LIGHT_SPEED_M_S = 299792458.0
        
        // Connection type speed factors
        const val FIBER_OPTIC_SPEED_FACTOR = 0.67 // 67% of light speed
        const val COPPER_SPEED_FACTOR = 0.5 // 50% of light speed
        const val WIRELESS_SPEED_FACTOR = 0.3 // 30% of light speed
        const val SATELLITE_SPEED_FACTOR = 0.1 // 10% of light speed
        
        // Network constants
        const val MAX_ACCEPTABLE_LATENCY = 500.0 // milliseconds
        const val BASE_BANDWIDTH_MBPS = 1000.0 // 1 Gbps
        const val CONGESTION_THRESHOLD = 0.8 // 80% utilization
    }
    
    // Connection types
    enum class ConnectionType(
        val speedFactor: Double,
        val baseBandwidth: Double,
        val reliability: Double,
        val cost: Double
    ) {
        FIBER_OPTIC(FIBER_OPTIC_SPEED_FACTOR, BASE_BANDWIDTH_MBPS, 0.99, 1.0),
        COPPER(COPPER_SPEED_FACTOR, BASE_BANDWIDTH_MBPS * 0.1, 0.95, 0.3),
        WIRELESS(WIRELESS_SPEED_FACTOR, BASE_BANDWIDTH_MBPS * 0.05, 0.9, 0.1),
        SATELLITE(SATELLITE_SPEED_FACTOR, BASE_BANDWIDTH_MBPS * 0.01, 0.8, 0.05)
    }
    
    // Network connection
    data class NetworkConnection(
        val sourceId: Long,
        val targetId: Long,
        val connectionType: ConnectionType,
        val distanceKm: Double,
        val bandwidthMbps: Double,
        val currentUtilization: Double = 0.0,
        val packetLoss: Double = 0.0,
        val lastUpdate: Long = System.currentTimeMillis()
    ) {
        fun calculateLatency(): Double {
            val effectiveSpeed = LIGHT_SPEED_KM_S * connectionType.speedFactor
            val propagationDelay = (distanceKm / effectiveSpeed) * 1000 // Convert to milliseconds
            
            // Add processing delay based on utilization
            val processingDelay = if (currentUtilization > CONGESTION_THRESHOLD) {
                (currentUtilization - CONGESTION_THRESHOLD) * 100 // 100ms max additional delay
            } else {
                0.0
            }
            
            return propagationDelay + processingDelay
        }
        
        fun calculateBandwidth(): Double {
            val baseBandwidth = connectionType.baseBandwidth
            val utilizationPenalty = if (currentUtilization > CONGESTION_THRESHOLD) {
                1.0 - (currentUtilization - CONGESTION_THRESHOLD) * 0.5
            } else {
                1.0
            }
            
            return baseBandwidth * utilizationPenalty
        }
        
        fun calculateReliability(): Double {
            val baseReliability = connectionType.reliability
            val utilizationPenalty = if (currentUtilization > CONGESTION_THRESHOLD) {
                (currentUtilization - CONGESTION_THRESHOLD) * 0.1
            } else {
                0.0
            }
            
            return maxOf(0.0, baseReliability - utilizationPenalty)
        }
    }
    
    // Network node
    data class NetworkNode(
        val nodeId: Long,
        val connections: MutableMap<Long, NetworkConnection> = mutableMapOf(),
        val processingPower: Double = 1000.0, // operations per second
        val bufferSize: Int = 1000, // packet buffer size
        val currentBufferUsage: Int = 0,
        val packetQueue: MutableList<NetworkPacket> = mutableListOf()
    ) {
        fun canProcessPacket(): Boolean {
            return currentBufferUsage < bufferSize
        }
        
        fun addPacket(packet: NetworkPacket): Boolean {
            if (canProcessPacket()) {
                packetQueue.add(packet)
                return true
            }
            return false
        }
        
        fun processPackets(deltaTime: Double): List<NetworkPacket> {
            val processedPackets = mutableListOf<NetworkPacket>()
            val packetsToProcess = minOf(
                (processingPower * deltaTime).toInt(),
                packetQueue.size
            )
            
            repeat(packetsToProcess) {
                if (packetQueue.isNotEmpty()) {
                    val packet = packetQueue.removeAt(0)
                    processedPackets.add(packet)
                }
            }
            
            return processedPackets
        }
    }
    
    // Network packet
    data class NetworkPacket(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val data: ByteArray,
        val priority: Int,
        val timestamp: Long,
        val ttl: Int = 100 // time to live
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return id == (other as NetworkPacket).id
        }
        
        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
    
    // Network topology
    data class NetworkTopology(
        val nodes: MutableMap<Long, NetworkNode> = mutableMapOf(),
        val globalCongestion: Double = 0.0,
        val totalBandwidth: Double = 0.0,
        val totalUtilization: Double = 0.0
    ) {
        fun addNode(node: NetworkNode) {
            nodes[node.nodeId] = node
        }
        
        fun removeNode(nodeId: Long) {
            nodes.remove(nodeId)
        }
        
        fun addConnection(connection: NetworkConnection) {
            val sourceNode = nodes[connection.sourceId]
            val targetNode = nodes[connection.targetId]
            
            sourceNode?.connections?.put(connection.targetId, connection)
            targetNode?.connections?.put(connection.sourceId, connection.copy(
                sourceId = connection.targetId,
                targetId = connection.sourceId
            ))
        }
        
        fun removeConnection(sourceId: Long, targetId: Long) {
            nodes[sourceId]?.connections?.remove(targetId)
            nodes[targetId]?.connections?.remove(sourceId)
        }
        
        fun calculateGlobalMetrics() {
            var totalBandwidth = 0.0
            var totalUtilization = 0.0
            var connectionCount = 0
            
            nodes.values.forEach { node ->
                node.connections.values.forEach { connection ->
                    totalBandwidth += connection.bandwidthMbps
                    totalUtilization += connection.currentUtilization
                    connectionCount++
                }
            }
            
            this.totalBandwidth = totalBandwidth
            this.totalUtilization = if (connectionCount > 0) totalUtilization / connectionCount else 0.0
            this.globalCongestion = if (totalBandwidth > 0) totalUtilization else 0.0
        }
    }
    
    /**
     * Calculate distance between two points in kilometers
     */
    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy) / 1000.0 // Convert to kilometers
    }
    
    /**
     * Calculate network latency between two points
     */
    fun calculateLatency(
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        connectionType: ConnectionType,
        utilization: Double = 0.0
    ): Double {
        val distance = calculateDistance(x1, y1, x2, y2)
        val effectiveSpeed = LIGHT_SPEED_KM_S * connectionType.speedFactor
        
        // Propagation delay
        val propagationDelay = (distance / effectiveSpeed) * 1000 // Convert to milliseconds
        
        // Processing delay based on utilization
        val processingDelay = if (utilization > CONGESTION_THRESHOLD) {
            (utilization - CONGESTION_THRESHOLD) * 100
        } else {
            0.0
        }
        
        return propagationDelay + processingDelay
    }
    
    /**
     * Calculate bandwidth between two points
     */
    fun calculateBandwidth(
        connectionType: ConnectionType,
        utilization: Double = 0.0
    ): Double {
        val baseBandwidth = connectionType.baseBandwidth
        val utilizationPenalty = if (utilization > CONGESTION_THRESHOLD) {
            1.0 - (utilization - CONGESTION_THRESHOLD) * 0.5
        } else {
            1.0
        }
        
        return baseBandwidth * utilizationPenalty
    }
    
    /**
     * Calculate packet loss probability
     */
    fun calculatePacketLoss(
        connectionType: ConnectionType,
        utilization: Double = 0.0,
        distance: Double = 0.0
    ): Double {
        val basePacketLoss = 1.0 - connectionType.reliability
        val distancePenalty = distance * 0.001 // 0.1% per km
        val utilizationPenalty = if (utilization > CONGESTION_THRESHOLD) {
            (utilization - CONGESTION_THRESHOLD) * 0.1
        } else {
            0.0
        }
        
        return minOf(1.0, basePacketLoss + distancePenalty + utilizationPenalty)
    }
    
    /**
     * Route packet through network
     */
    fun routePacket(
        topology: NetworkTopology,
        packet: NetworkPacket,
        deltaTime: Double
    ): NetworkPacket? {
        val sourceNode = topology.nodes[packet.sourceId]
        val targetNode = topology.nodes[packet.targetId]
        
        if (sourceNode == null || targetNode == null) return null
        
        // Check if direct connection exists
        val directConnection = sourceNode.connections[packet.targetId]
        if (directConnection != null) {
            // Direct route
            val latency = directConnection.calculateLatency()
            val packetLoss = directConnection.calculatePacketLoss()
            
            // Check for packet loss
            if (Math.random() < packetLoss) {
                return null // Packet lost
            }
            
            // Update packet timestamp
            return packet.copy(timestamp = packet.timestamp + latency.toLong())
        }
        
        // Find route through intermediate nodes
        val route = findRoute(topology, packet.sourceId, packet.targetId)
        if (route.isEmpty()) return null
        
        var totalLatency = 0.0
        var currentPacket = packet
        
        // Route through intermediate nodes
        for (i in 0 until route.size - 1) {
            val currentNodeId = route[i]
            val nextNodeId = route[i + 1]
            val node = topology.nodes[currentNodeId]
            
            if (node == null) return null
            
            // Process packet at current node
            if (!node.addPacket(currentPacket)) {
                return null // Buffer full
            }
            
            val processedPackets = node.processPackets(deltaTime)
            val processedPacket = processedPackets.find { it.id == currentPacket.id }
            
            if (processedPacket == null) {
                return null // Packet not processed
            }
            
            // Calculate latency to next node
            val connection = node.connections[nextNodeId]
            if (connection == null) return null
            
            val latency = connection.calculateLatency()
            totalLatency += latency
            
            currentPacket = processedPacket
        }
        
        return currentPacket.copy(timestamp = packet.timestamp + totalLatency.toLong())
    }
    
    /**
     * Find route between two nodes using Dijkstra's algorithm
     */
    internal fun findRoute(
        topology: NetworkTopology,
        sourceId: Long,
        targetId: Long
    ): List<Long> {
        val distances = mutableMapOf<Long, Double>()
        val previous = mutableMapOf<Long, Long>()
        val unvisited = mutableSetOf<Long>()
        
        // Initialize
        topology.nodes.keys.forEach { nodeId ->
            distances[nodeId] = Double.MAX_VALUE
            unvisited.add(nodeId)
        }
        distances[sourceId] = 0.0
        
        while (unvisited.isNotEmpty()) {
            // Find node with minimum distance
            val currentNode = unvisited.minByOrNull { distances[it] ?: Double.MAX_VALUE }
            if (currentNode == null || distances[currentNode] == Double.MAX_VALUE) break
            
            unvisited.remove(currentNode)
            
            // Check if we reached the target
            if (currentNode == targetId) break
            
            // Update distances to neighbors
            val node = topology.nodes[currentNode] ?: continue
            node.connections.keys.forEach { neighborId ->
                if (neighborId in unvisited) {
                    val connection = node.connections[neighborId] ?: return@forEach
                    val latency = connection.calculateLatency()
                    val newDistance = distances[currentNode]!! + latency
                    
                    if (newDistance < (distances[neighborId] ?: Double.MAX_VALUE)) {
                        distances[neighborId] = newDistance
                        previous[neighborId] = currentNode
                    }
                }
            }
        }
        
        // Reconstruct path
        val path = mutableListOf<Long>()
        var current = targetId
        while (current != sourceId) {
            path.add(0, current)
            current = previous[current] ?: break
        }
        path.add(0, sourceId)
        
        return if (path.size > 1) path else emptyList()
    }
    
    /**
     * Update network topology
     */
    fun updateTopology(topology: NetworkTopology, deltaTime: Double) {
        // Update all nodes
        topology.nodes.values.forEach { node ->
            node.processPackets(deltaTime)
        }
        
        // Update connection utilizations
        topology.nodes.values.forEach { node ->
            node.connections.values.forEach { connection ->
                // Simulate traffic patterns
                val trafficVariation = sin(System.currentTimeMillis() / 1000.0) * 0.1
                val newUtilization = maxOf(0.0, minOf(1.0, 
                    connection.currentUtilization + trafficVariation * deltaTime
                ))
                
                connection.copy(currentUtilization = newUtilization)
            }
        }
        
        // Calculate global metrics
        topology.calculateGlobalMetrics()
    }
} 