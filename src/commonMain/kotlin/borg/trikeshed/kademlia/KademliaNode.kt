package borg.trikeshed.kademlia

import borg.trikeshed.lib.ByteSeries
import borg.trikeshed.net.common.UdpSocketService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class NodeId(val id: BigInteger) {
    fun distance(other: NodeId): BigInteger = id.xor(other.id)
    
    companion object {
        const val ID_LENGTH = 160 // bits
        const val K = 20 // bucket size
        const val ALPHA = 3 // concurrency factor
        const val REPUBLISH_INTERVAL = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
        const val HEALTH_CHECK_INTERVAL = 15 * 60 * 1000 // 15 minutes in milliseconds
    }
}

data class NodeInfo(
    val id: NodeId,
    val address: String,
    val port: Int,
    var lastSeen: Long = System.currentTimeMillis()
)

data class StoredValue(
    val value: ByteSeries,
    val publisher: NodeInfo,
    val timestamp: Long = System.currentTimeMillis()
)

class KademliaNode(
    private val nodeId: NodeId,
    private val address: String,
    private val port: Int,
    private val udpService: UdpSocketService
) {
    private val routingTable = RoutingTable(nodeId)
    private val storage = ConcurrentHashMap<ByteSeries, StoredValue>()
    private val rpcService = KademliaRPCService(this, udpService)
    private val rpcClient = KademliaRPCClient(udpService)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        startMaintenanceTasks()
    }

    fun getNodeInfo() = NodeInfo(nodeId, address, port)

    private fun startMaintenanceTasks() {
        // Start value republishing
        scope.launch {
            while (isActive) {
                republishValues()
                delay(REPUBLISH_INTERVAL)
            }
        }
        
        // Start health checks
        scope.launch {
            while (isActive) {
                checkNodeHealth()
                delay(HEALTH_CHECK_INTERVAL)
            }
        }
    }

    private suspend fun republishValues() {
        val now = System.currentTimeMillis()
        storage.forEach { (key, storedValue) ->
            if (now - storedValue.timestamp > REPUBLISH_INTERVAL) {
                val keyId = NodeId(BigInteger(key.toByteArray()))
                val nodes = iterativeFindNode(keyId)
                nodes.forEach { node ->
                    try {
                        rpcClient.store(node, key, storedValue.value)
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            }
        }
    }

    private suspend fun checkNodeHealth() {
        routingTable.getAllNodes().forEach { node ->
            scope.launch {
                try {
                    val alive = rpcClient.ping(node)
                    if (!alive) {
                        routingTable.remove(node)
                    } else {
                        node.lastSeen = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    routingTable.remove(node)
                }
            }
        }
    }

    suspend fun join(bootstrapNodes: List<NodeInfo>) {
        bootstrapNodes.forEach { node ->
            try {
                val nodes = iterativeFindNode(node.id)
                nodes.forEach { routingTable.add(it) }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    suspend fun iterativeFindNode(targetId: NodeId): List<NodeInfo> {
        val closestNodes = routingTable.getClosestNodes(targetId, NodeId.K)
        val results = mutableSetOf<NodeInfo>()
        val pending = closestNodes.toMutableList()
        val queried = mutableSetOf<NodeInfo>()
        
        while (pending.isNotEmpty() && results.size < NodeId.K) {
            val batch = pending.take(min(NodeId.ALPHA, pending.size))
            pending.removeAll(batch)
            queried.addAll(batch)
            
            val futures = batch.map { node ->
                scope.async {
                    try {
                        rpcClient.findNode(node, targetId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            
            val responses = futures.awaitAll()
            responses.forEach { nodes ->
                nodes.forEach { 
                    if (it.id != nodeId && it !in queried) {
                        results.add(it)
                        routingTable.add(it)
                        pending.add(it)
                    }
                }
            }
        }
        
        return results.sortedBy { it.id.distance(targetId) }.take(NodeId.K)
    }

    suspend fun store(key: ByteSeries, value: ByteSeries): Boolean {
        val keyId = NodeId(BigInteger(key.toByteArray()))
        val nodes = iterativeFindNode(keyId)
        
        val futures = nodes.map { node ->
            scope.async {
                try {
                    rpcClient.store(node, key, value)
                } catch (e: Exception) {
                    false
                }
            }
        }
        
        val results = futures.awaitAll()
        val success = results.any { it }
        
        if (success) {
            storage[key] = StoredValue(value, getNodeInfo())
        }
        
        return success
    }

    suspend fun findValue(key: ByteSeries): ByteSeries? {
        val keyId = NodeId(BigInteger(key.toByteArray()))
        val closestNodes = routingTable.getClosestNodes(keyId, NodeId.K)
        val pending = closestNodes.toMutableList()
        val queried = mutableSetOf<NodeInfo>()
        
        while (pending.isNotEmpty()) {
            val batch = pending.take(min(NodeId.ALPHA, pending.size))
            pending.removeAll(batch)
            queried.addAll(batch)
            
            val futures = batch.map { node ->
                scope.async {
                    try {
                        rpcClient.findValue(node, key)
                    } catch (e: Exception) {
                        Pair(null, null)
                    }
                }
            }
            
            val responses = futures.awaitAll()
            for ((value, nodes) in responses) {
                if (value != null) {
                    // Update routing table with nodes that responded
                    batch.forEach { routingTable.add(it) }
                    return value
                }
                nodes?.forEach { 
                    if (it !in queried) {
                        pending.add(it)
                    }
                }
            }
        }
        
        return null
    }

    fun shutdown() {
        scope.cancel()
    }
}

class RoutingTable(private val nodeId: NodeId) {
    private val buckets = Array(NodeId.ID_LENGTH) { mutableListOf<NodeInfo>() }
    
    fun add(node: NodeInfo) {
        val bucketIndex = getBucketIndex(node.id)
        val bucket = buckets[bucketIndex]
        
        if (bucket.size < NodeId.K) {
            if (node !in bucket) {
                bucket.add(node)
            }
        } else {
            // Implement bucket splitting or replacement policy
            val oldestNode = bucket.minByOrNull { it.lastSeen }
            if (oldestNode != null) {
                bucket.remove(oldestNode)
                bucket.add(node)
            }
        }
    }
    
    fun remove(node: NodeInfo) {
        val bucketIndex = getBucketIndex(node.id)
        buckets[bucketIndex].remove(node)
    }
    
    fun getClosestNodes(targetId: NodeId, count: Int): List<NodeInfo> {
        val bucketIndex = getBucketIndex(targetId)
        val nodes = mutableListOf<NodeInfo>()
        
        // Add nodes from the target bucket
        nodes.addAll(buckets[bucketIndex])
        
        // Add nodes from adjacent buckets if needed
        var left = bucketIndex - 1
        var right = bucketIndex + 1
        
        while (nodes.size < count && (left >= 0 || right < NodeId.ID_LENGTH)) {
            if (left >= 0) {
                nodes.addAll(buckets[left])
                left--
            }
            if (right < NodeId.ID_LENGTH) {
                nodes.addAll(buckets[right])
                right++
            }
        }
        
        return nodes.sortedBy { it.id.distance(targetId) }.take(count)
    }
    
    fun getAllNodes(): List<NodeInfo> {
        return buckets.flatMap { it }
    }
    
    private fun getBucketIndex(nodeId: NodeId): Int {
        val distance = this.nodeId.distance(nodeId)
        return if (distance == BigInteger.ZERO) 0 else {
            NodeId.ID_LENGTH - distance.bitLength()
        }
    }
} 