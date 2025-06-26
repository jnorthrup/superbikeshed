@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht.kademlia.routing


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import borg.trikeshed.reactor.getCurrentTimeMillis

/**
 * K-bucket for Kademlia routing table
 * Maintains up to K peers at a specific distance range
 * 
 * SAFEGUARD: All destructive operations now emit notifications
 */
class KBucket(
    val k: Int = 20,
    val distanceRange: IntRange
) {
    private val nodes = mutableListOf<NodeInfo>()
    private val replacementCache = mutableListOf<NodeInfo>()
    
    // Destruction notification callbacks
    private val destructionListeners = mutableListOf<(NodeInfo, String) -> Unit>()
    
    fun addDestructionListener(listener: (NodeInfo, String) -> Unit) {
        destructionListeners.add(listener)
    }
    
    private fun notifyDestruction(node: NodeInfo, reason: String) {
        destructionListeners.forEach { it(node, reason) }
        println("⚠️  KBUCKET DESTRUCTION: Node ${node.nodeId} destroyed - $reason")
    }
    
    /**
     * Add or update a node in the bucket
     */
    fun addNode(node: NodeInfo): Boolean {
        val existingIndex = nodes.indexOfFirst { it.nodeId == node.nodeId }
        
        if (existingIndex >= 0) {
            // Move to end (most recently seen)
            val oldNode = nodes[existingIndex]
            nodes.removeAt(existingIndex)
            nodes.add(node.copy(lastSeen = getCurrentTimeMillis()))
            notifyDestruction(oldNode, "moved to end of bucket")
            return true
        }
        
        if (nodes.size < k) {
            nodes.add(node.copy(lastSeen = getCurrentTimeMillis()))
            return true
        }
        
        // Bucket full, add to replacement cache
        replacementCache.add(node)
        if (replacementCache.size > k) {
            val destroyed = replacementCache.removeAt(0)
            notifyDestruction(destroyed, "replacement cache overflow")
        }
        return false
    }
    
    /**
     * Remove a node from the bucket
     */
    fun removeNode(nodeId: NUID): Boolean {
        val nodeToRemove = nodes.find { it.nodeId == nodeId }
        val removed = nodes.removeAll { it.nodeId == nodeId }
        
        if (removed && nodeToRemove != null) {
            notifyDestruction(nodeToRemove, "explicit removal")
        }
        
        // Promote from replacement cache if available
        if (removed && replacementCache.isNotEmpty()) {
            val promoted = replacementCache.removeAt(0)
            nodes.add(promoted)
            println("🔄 KBUCKET PROMOTION: Node ${promoted.nodeId} promoted from replacement cache")
        }
        
        return removed
    }
    
    /**
     * Get all nodes in the bucket
     */
    fun getNodes(): Indexed<NodeInfo> {
        return nodes.size j { i: Int -> nodes[i] }
    }
    
    /**
     * Get nodes closest to target
     */
    fun getClosestNodes(target: NUID, count: Int): Indexed<NodeInfo> {
        val sorted = nodes.sortedBy { node ->
            node.nodeId.distanceTo(target).toHex()
        }
        val resultCount = minOf(count, sorted.size)
        return resultCount j { i: Int -> sorted[i] }
    }
    
    /**
     * Check if bucket contains a node
     */
    fun contains(nodeId: NUID): Boolean {
        return nodes.any { it.nodeId == nodeId }
    }
    
    /**
     * Get bucket size
     */
    fun size(): Int = nodes.size
    
    /**
     * Check if bucket is full
     */
    fun isFull(): Boolean = nodes.size >= k
    
    /**
     * Split bucket (for when local node ID falls within range)
     * SAFEGUARD: Track all nodes during split to prevent silent loss
     */
    fun split(midpoint: Int): Pair<KBucket, KBucket> {
        val lowerBucket = KBucket(k, distanceRange.first until midpoint)
        val upperBucket = KBucket(k, midpoint..distanceRange.last)
        
        val splitResults = mutableListOf<Pair<NodeInfo, String>>()
        
        for (node in nodes) {
            val prefixLength = node.nodeId.commonPrefixLength(NUID.ZERO) // Should use actual local node ID
            if (prefixLength < midpoint) {
                val success = lowerBucket.addNode(node)
                splitResults.add(node to "lower bucket")
            } else {
                val success = upperBucket.addNode(node)
                splitResults.add(node to "upper bucket")
            }
        }
        
        // Report split results
        println("🔀 KBUCKET SPLIT: ${nodes.size} nodes redistributed")
        splitResults.forEach { (node, destination) ->
            println("  - Node ${node.nodeId} → $destination")
        }
        
        return lowerBucket to upperBucket
    }
}