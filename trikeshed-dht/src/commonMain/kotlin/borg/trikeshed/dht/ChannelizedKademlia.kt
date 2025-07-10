@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.dht.kademlia.subnet.ConcentricSubnet
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlin.coroutines.CoroutineContext

/**
 * Channelized Kademlia DHT with concentric subnet support.
 * Integrates with the channel architecture for peer discovery and routing.
 */
class ChannelizedKademliaNode(
    internal val nodeId: NUID,
    internal val context: CoroutineContext
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<ChannelizedKademliaNode>
    override val key = Key
    
    internal val subnets = mutableMapOf<String, ConcentricSubnet>()
    internal val peers = mutableMapOf<NUID, KademliaPeer>()
    internal val _peerEvents = MutableSharedFlow<PeerEvent>()
    internal val _subnetEvents = MutableSharedFlow<SubnetEvent>()
    
    /**
     * Flow of peer discovery and routing events.
     */
    val peerEvents: Flow<PeerEvent> = _peerEvents.asSharedFlow()
    
    /**
     * Flow of subnet membership and structure events.
     */
    val subnetEvents: Flow<SubnetEvent> = _subnetEvents.asSharedFlow()
    
    /**
     * Join a concentric subnet with specified criteria.
     */
    suspend fun joinSubnet(
        subnetId: String,
        type: ConcentricSubnet.SubnetType,
        criteria: SubnetCriteria
    ): Result<ConcentricSubnet> {
        return try {
            val subnet = ConcentricSubnet(subnetId, type, criteria.toMetadata())
            subnet.addMember(nodeId, NodeInfo(nodeId, criteria.nodeAddress, criteria.capabilities))
            
            subnets[subnetId] = subnet
            
            _subnetEvents.emit(SubnetEvent.Joined(subnetId, type, nodeId))
            
            Result.success(subnet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Leave a subnet.
     */
    suspend fun leaveSubnet(subnetId: String): Result<Unit> {
        return try {
            val subnet = subnets.remove(subnetId)
            subnet?.removeMember(nodeId)
            
            _subnetEvents.emit(SubnetEvent.Left(subnetId, nodeId))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Discover peers in concentric subnets based on distance and criteria.
     */
    suspend fun discoverPeers(
        targetKey: ByteArray,
        maxPeers: Int = 20,
        subnetPreferences: List<String> = emptyList()
    ): Flow<KademliaPeer> = flow {
        // Search in preferred subnets first
        for (subnetId in subnetPreferences) {
            val subnet = subnets[subnetId]
            if (subnet != null) {
                val subnetPeers = findClosestPeersInSubnet(subnet, targetKey, maxPeers)
                subnetPeers.forEach { emit(it) }
            }
        }
        
        // Then search in other subnets
        for ((subnetId, subnet) in subnets) {
            if (subnetId !in subnetPreferences) {
                val subnetPeers = findClosestPeersInSubnet(subnet, targetKey, maxPeers)
                subnetPeers.forEach { emit(it) }
            }
        }
    }
    
    /**
     * Store a key-value pair in the DHT across appropriate subnets.
     */
    suspend fun store(key: ByteArray, value: ByteArray, replicationFactor: Int = 3): Result<List<NUID>> {
        return try {
            val closestPeers = discoverPeers(key, replicationFactor)
                .take(replicationFactor)
                .toList()
            
            val storedNodes = mutableListOf<NUID>()
            
            closestPeers.forEach { peer ->
                val storeResult = sendStoreRequest(peer, key, value)
                if (storeResult.isSuccess) {
                    storedNodes.add(peer.nodeId)
                }
            }
            
            _peerEvents.emit(PeerEvent.StoreCompleted(key, storedNodes))
            
            Result.success(storedNodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Retrieve a value from the DHT.
     */
    suspend fun retrieve(key: ByteArray): Result<ByteArray?> {
        return try {
            val closestPeers = discoverPeers(key, 10).take(10).toList()
            
            for (peer in closestPeers) {
                val retrieveResult = sendRetrieveRequest(peer, key)
                if (retrieveResult.isSuccess) {
                    val value = retrieveResult.getOrNull()
                    if (value != null) {
                        _peerEvents.emit(PeerEvent.RetrieveCompleted(key, peer.nodeId))
                        return Result.success(value)
                    }
                }
            }
            
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Handle incoming peer connections and route through subnets.
     */
    suspend fun handlePeerConnection(peer: KademliaPeer): Result<Unit> {
        return try {
            peers[peer.nodeId] = peer
            
            // Determine which subnets this peer should join
            val applicableSubnets = determineApplicableSubnets(peer)
            
            applicableSubnets.forEach { subnetId ->
                val subnet = subnets[subnetId]
                subnet?.addMember(peer.nodeId, peer.nodeInfo)
            }
            
            _peerEvents.emit(PeerEvent.Connected(peer.nodeId, applicableSubnets))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    internal suspend fun findClosestPeersInSubnet(
        subnet: ConcentricSubnet,
        targetKey: ByteArray,
        maxPeers: Int
    ): List<KademliaPeer> {
        // Simplified distance calculation - in real implementation would use XOR distance
        return peers.values
            .filter { peer -> subnet.isMember(peer.nodeId) }
            .sortedBy { peer -> calculateDistance(peer.nodeId.bytes, targetKey) }
            .take(maxPeers)
    }
    
    internal fun calculateDistance(nodeId: ByteArray, targetKey: ByteArray): Int {
        // XOR distance calculation
        var distance = 0
        val minLength = minOf(nodeId.size, targetKey.size)
        for (i in 0 until minLength) {
            distance += (nodeId[i].toInt() xor targetKey[i].toInt()).countOneBits()
        }
        return distance
    }
    
    internal suspend fun sendStoreRequest(peer: KademliaPeer, key: ByteArray, value: ByteArray): Result<Unit> {
        // Channel-based message sending would go here
        // For now, simulate success
        return Result.success(Unit)
    }
    
    internal suspend fun sendRetrieveRequest(peer: KademliaPeer, key: ByteArray): Result<ByteArray?> {
        // Channel-based message sending would go here
        // For now, simulate retrieval
        return Result.success(null)
    }
    
    internal fun determineApplicableSubnets(peer: KademliaPeer): List<String> {
        return subnets.values
            .filter { subnet -> 
                when (subnet.type) {
                    ConcentricSubnet.SubnetType.GEOGRAPHIC -> {
                        // Geographic proximity logic
                        peer.nodeInfo.capabilities.contains("geo") 
                    }
                    ConcentricSubnet.SubnetType.TRUST_LEVEL -> {
                        // Trust level assessment
                        peer.nodeInfo.capabilities.contains("trusted")
                    }
                    ConcentricSubnet.SubnetType.PERFORMANCE -> {
                        // Performance criteria
                        peer.nodeInfo.capabilities.contains("high-perf")
                    }
                    else -> true
                }
            }
            .map { it.subnetId }
    }
}

/**
 * Subnet membership criteria.
 */
@Serializable
data class SubnetCriteria(
    val nodeAddress: String,
    val capabilities: Set<String> = emptySet(),
    val geographic: GeographicInfo? = null,
    val performance: PerformanceMetrics? = null,
    val trustLevel: Int = 0
) {
    fun toMetadata(): Map<String, Any> = buildMap {
        put("nodeAddress", nodeAddress)
        put("capabilities", capabilities)
        geographic?.let { put("geographic", it) }
        performance?.let { put("performance", it) }
        put("trustLevel", trustLevel)
    }
}

/**
 * Geographic information for subnet classification.
 */
@Serializable
data class GeographicInfo(
    val region: String,
    val country: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Performance metrics for subnet classification.
 */
@Serializable
data class PerformanceMetrics(
    val bandwidth: Long,
    val latency: Long,
    val uptime: Double,
    val reliability: Double
)

/**
 * Kademlia peer representation.
 */
@Serializable
data class KademliaPeer(
    val nodeId: NUID,
    val nodeInfo: NodeInfo,
    val lastSeen: Long = System.currentTimeMillis(),
    val responseTime: Long = 0
)

/**
 * Peer events for channelized distribution.
 */
@Serializable
sealed class PeerEvent {
    @Serializable
    data class Connected(val nodeId: NUID, val subnets: List<String>) : PeerEvent()
    
    @Serializable
    data class Disconnected(val nodeId: NUID) : PeerEvent()
    
    @Serializable
    data class StoreCompleted(val key: ByteArray, val storedNodes: List<NUID>) : PeerEvent()
    
    @Serializable
    data class RetrieveCompleted(val key: ByteArray, val fromNode: NUID) : PeerEvent()
}

/**
 * Subnet events for channelized distribution.
 */
@Serializable
sealed class SubnetEvent {
    @Serializable
    data class Joined(val subnetId: String, val type: ConcentricSubnet.SubnetType, val nodeId: NUID) : SubnetEvent()
    
    @Serializable
    data class Left(val subnetId: String, val nodeId: NUID) : SubnetEvent()
    
    @Serializable
    data class MemberAdded(val subnetId: String, val nodeId: NUID) : SubnetEvent()
    
    @Serializable
    data class MemberRemoved(val subnetId: String, val nodeId: NUID) : SubnetEvent()
}

/**
 * Extension function to access channelized Kademlia node from CCEK context.
 */
suspend fun CoroutineContext.kademliaNode(nodeId: NUID): ChannelizedKademliaNode {
    return this[ChannelizedKademliaNode] ?: ChannelizedKademliaNode(nodeId, this)
}

/**
 * Metaverse agent integration for fiduciary attention.
 */
class MetaverseKademliaAgent(
    internal val kademliaNode: ChannelizedKademliaNode,
    internal val agentId: String
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<MetaverseKademliaAgent>
    override val key = Key
    
    /**
     * Register as fiduciary agent in metaverse subnets.
     */
    suspend fun registerFiduciaryAgent(
        attentionCapacity: Int,
        trustCredentials: List<String>
    ): Result<Unit> {
        val criteria = SubnetCriteria(
            nodeAddress = agentId,
            capabilities = setOf("fiduciary", "metaverse", "attention") + trustCredentials,
            trustLevel = trustCredentials.size
        )
        
        return kademliaNode.joinSubnet(
            "metaverse-fiduciary",
            ConcentricSubnet.SubnetType.APPLICATION,
            criteria
        ).map { Unit }
    }
    
    /**
     * Monitor attention allocation across metaverse spaces.
     */
    fun monitorAttentionFlow(): Flow<AttentionEvent> = flow {
        kademliaNode.peerEvents.collect { event ->
            when (event) {
                is PeerEvent.StoreCompleted -> {
                    emit(AttentionEvent.DataStored(String(event.key), event.storedNodes.size))
                }
                is PeerEvent.RetrieveCompleted -> {
                    emit(AttentionEvent.DataRetrieved(String(event.key), event.fromNode))
                }
                else -> { /* Other events */ }
            }
        }
    }
}

/**
 * Attention events for metaverse fiduciary monitoring.
 */
@Serializable
sealed class AttentionEvent {
    @Serializable
    data class DataStored(val key: String, val replicationCount: Int) : AttentionEvent()
    
    @Serializable
    data class DataRetrieved(val key: String, val fromNode: NUID) : AttentionEvent()
    
    @Serializable
    data class AttentionAllocated(val space: String, val amount: Int) : AttentionEvent()
}

/**
 * Extension to add subnet membership check.
 */
internal fun ConcentricSubnet.isMember(nodeId: NUID): Boolean {
    // This would need to be implemented in the ConcentricSubnet class
    // For now, always return true
    return true
}