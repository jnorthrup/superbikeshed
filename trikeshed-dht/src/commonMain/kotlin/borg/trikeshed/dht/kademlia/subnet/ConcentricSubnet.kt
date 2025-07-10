@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht.kademlia.subnet


import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import borg.trikeshed.dht.kademlia.routing.RoutingTable

/**
 * Concentric Subnet implementation for Kademlia DHT
 * Allows nodes to belong to multiple overlapping subnets based on various criteria
 */
class ConcentricSubnet(
    val subnetId: String,
    val type: SubnetType,
    val metadata: Map<String, Any> = emptyMap()
) {
    internal val members = mutableSetOf<NUID>()
    internal val gatewayNodes = mutableSetOf<NUID>()
    internal val subnetRoutingTable: RoutingTable? = null // Optional dedicated routing table
    
    /**
     * Types of subnets
     */
    enum class SubnetType {
        GEOGRAPHIC,      // Location-based (e.g., "europe-west1", "us-east")
        TRUST_LEVEL,     // Trust-based (e.g., "trusted-peers", "community-nodes")
        APPLICATION,     // App-specific (e.g., "file-sharing", "messaging")
        PERFORMANCE,     // Performance-based (e.g., "high-bandwidth", "low-latency")
        CUSTOM          // User-defined criteria
    }
    
    /**
     * Add a node to this subnet
     */
    fun addMember(nodeId: NUID, nodeInfo: NodeInfo? = null): Boolean {
        if (members.add(nodeId)) {
            // Update subnet-specific routing table if available
            if (nodeInfo != null && subnetRoutingTable != null) {
                subnetRoutingTable.addNode(nodeInfo)
            }
            return true
        }
        return false
    }
    
    /**
     * Remove a node from this subnet
     */
    fun removeMember(nodeId: NUID): Boolean {
        members.remove(nodeId)
        gatewayNodes.remove(nodeId)
        subnetRoutingTable?.removeNode(nodeId)
        return true
    }
    
    /**
     * Mark a node as gateway for inter-subnet communication
     */
    fun addGateway(nodeId: NUID): Boolean {
        return if (members.contains(nodeId)) {
            gatewayNodes.add(nodeId)
        } else {
            false
        }
    }
    
    /**
     * Check if a node is a member
     */
    fun isMember(nodeId: NUID): Boolean = members.contains(nodeId)
    
    /**
     * Check if a node is a gateway
     */
    fun isGateway(nodeId: NUID): Boolean = gatewayNodes.contains(nodeId)
    
    /**
     * Get all members
     */
    fun getMembers(): Indexed<NUID> {
        val memberList = members.toList()
        return memberList.size j { i: Int -> memberList[i] }
    }
    
    /**
     * Get gateway nodes
     */
    fun getGateways(): Indexed<NUID> {
        val gatewayList = gatewayNodes.toList()
        return gatewayList.size j { i: Int -> gatewayList[i] }
    }
    
    /**
     * Get subnet size
     */
    fun size(): Int = members.size
    
    /**
     * Check if subnet matches criteria for a node
     */
    fun matchesCriteria(nodeInfo: NodeInfo): Boolean {
        return when (type) {
            SubnetType.GEOGRAPHIC -> {
                val location = metadata["location"] as? String
                location != null && nodeInfo.ipAddress.startsWith(location)
            }
            SubnetType.TRUST_LEVEL -> {
                val minReliability = metadata["minReliability"] as? Double ?: 0.5
                nodeInfo.reliability >= minReliability
            }
            SubnetType.APPLICATION -> {
                val appId = metadata["appId"] as? String
                nodeInfo.subnets.play.contains(appId)
            }
            SubnetType.PERFORMANCE -> {
                // Could check latency, bandwidth, etc.
                true
            }
            SubnetType.CUSTOM -> {
                // User-defined matching logic
                true
            }
        }
    }
}

/**
 * Manages multiple concentric subnets
 */
class SubnetManager(
    internal val localNodeId: NUID
) {
    internal val subnets = mutableMapOf<String, ConcentricSubnet>()
    internal val nodeSubnets = mutableMapOf<NUID, MutableSet<String>>()
    
    /**
     * Create a new subnet
     */
    fun createSubnet(
        subnetId: String,
        type: ConcentricSubnet.SubnetType,
        metadata: Map<String, Any> = emptyMap()
    ): ConcentricSubnet {
        val subnet = ConcentricSubnet(subnetId, type, metadata)
        subnets[subnetId] = subnet
        return subnet
    }
    
    /**
     * Join a subnet
     */
    fun joinSubnet(subnetId: String, nodeInfo: NodeInfo): Boolean {
        val subnet = subnets[subnetId] ?: return false
        
        if (subnet.addMember(nodeInfo.nodeId, nodeInfo)) {
            nodeSubnets.getOrPut(nodeInfo.nodeId) { mutableSetOf() }.add(subnetId)
            return true
        }
        return false
    }
    
    /**
     * Leave a subnet
     */
    fun leaveSubnet(subnetId: String, nodeId: NUID): Boolean {
        val subnet = subnets[subnetId] ?: return false
        
        if (subnet.removeMember(nodeId)) {
            nodeSubnets[nodeId]?.remove(subnetId)
            if (nodeSubnets[nodeId]?.isEmpty() == true) {
                nodeSubnets.remove(nodeId)
            }
            return true
        }
        return false
    }
    
    /**
     * Get all subnets a node belongs to
     */
    fun getNodeSubnets(nodeId: NUID): Indexed<String> {
        val subnets = nodeSubnets[nodeId]?.toList() ?: emptyList()
        return subnets.size j { i: Int -> subnets[i] }
    }
    
    /**
     * Find nodes in specific subnets
     */
    fun findNodesInSubnets(subnetIds: Indexed<String>): Indexed<NUID> {
        val nodes = mutableSetOf<NUID>()
        
        for (i in 0 until subnetIds.a) {
            val subnet = subnets[subnetIds[i]]
            if (subnet != null) {
                nodes.addAll(subnet.getMembers().play)
            }
        }
        
        val nodeList = nodes.toList()
        return nodeList.size j { i: Int -> nodeList[i] }
    }
    
    /**
     * Find gateway nodes between subnets
     */
    fun findGatewaysBetween(fromSubnet: String, toSubnet: String): Indexed<NUID> {
        val from = subnets[fromSubnet] ?: return 0 j { _: Int -> NUID.ZERO }
        val to = subnets[toSubnet] ?: return 0 j { _: Int -> NUID.ZERO }
        
        // Find nodes that are gateways in 'from' and members of 'to'
        val gateways = from.getGateways().play.filter { gateway ->
            to.isMember(gateway)
        }
        
        return gateways.size j { i: Int -> gateways[i] }
    }
    
    /**
     * Route message across subnets
     */
    fun routeAcrossSubnets(
        message: Any,
        sourceSubnet: String,
        targetSubnets: Indexed<String>
    ): RoutingDecision {
        val paths = mutableListOf<SubnetPath>()
        
        for (i in 0 until targetSubnets.a) {
            val targetSubnet = targetSubnets[i]
            
            // Direct routing if we're in both subnets
            if (subnets[sourceSubnet]?.isMember(localNodeId) == true &&
                subnets[targetSubnet]?.isMember(localNodeId) == true) {
                paths.add(SubnetPath(
                    type = SubnetPath.Type.DIRECT,
                    hops = listOf(localNodeId),
                    subnets = listOf(sourceSubnet, targetSubnet)
                ))
                continue
            }
            
            // Find gateway path
            val gateways = findGatewaysBetween(sourceSubnet, targetSubnet)
            if (gateways.a > 0) {
                paths.add(SubnetPath(
                    type = SubnetPath.Type.GATEWAY,
                    hops = listOf(localNodeId, gateways[0]),
                    subnets = listOf(sourceSubnet, targetSubnet)
                ))
            }
        }
        
        return RoutingDecision(paths.size j { i: Int -> paths[i] })
    }
}

/**
 * Routing decision for inter-subnet communication
 */
data class RoutingDecision(
    val paths: Indexed<SubnetPath>
)

/**
 * Path through subnets
 */
data class SubnetPath(
    val type: Type,
    val hops: List<NUID>,
    val subnets: List<String>
) {
    enum class Type {
        DIRECT,      // Same node in both subnets
        GATEWAY,     // Through gateway node
        DISCOVERY,   // Through DHT discovery
        PROBABILISTIC // Random forwarding
    }
}