package borg.trikeshed.dht.kademlia.routing

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import borg.trikeshed.reactor.getCurrentTimeMillis

/**
 * K-bucket for Kademlia routing table
 * Maintains up to K peers at a specific distance range
 */
class KBucket(
    val k: Int = 20,
    val distanceRange: IntRange
) {
    private val nodes = mutableListOf<NodeInfo>()
    private val replacementCache = mutableListOf<NodeInfo>()
    
    /**
     * Add or update a node in the bucket
     */
    fun addNode(node: NodeInfo): Boolean {
        val existingIndex = nodes.indexOfFirst { it.nodeId == node.nodeId }
        
        if (existingIndex >= 0) {
            // Move to end (most recently seen)
            nodes.removeAt(existingIndex)
            nodes.add(node.copy(lastSeen = getCurrentTimeMillis()))
            return true
        }
        
        if (nodes.size < k) {
            nodes.add(node.copy(lastSeen = getCurrentTimeMillis()))
            return true
        }
        
        // Bucket full, add to replacement cache
        replacementCache.add(node)
        if (replacementCache.size > k) {
            replacementCache.removeAt(0)
        }
        return false
    }
    
    /**
     * Remove a node from the bucket
     */
    fun removeNode(nodeId: NUID): Boolean {
        val removed = nodes.removeIf { it.nodeId == nodeId }
        
        // Promote from replacement cache if available
        if (removed && replacementCache.isNotEmpty()) {
            nodes.add(replacementCache.removeAt(0))
        }
        
        return removed
    }
    
    /**
     * Get all nodes in the bucket
     */
    fun getNodes(): Indexed<NodeInfo> {
        return nodes.size j { i -> nodes[i] }
    }
    
    /**
     * Get nodes closest to target
     */
    fun getClosestNodes(target: NUID, count: Int): Indexed<NodeInfo> {
        val sorted = nodes.sortedBy { node ->
            node.nodeId.distanceTo(target).toHex()
        }
        val resultCount = minOf(count, sorted.size)
        return resultCount j { i -> sorted[i] }
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
     */
    fun split(midpoint: Int): Pair<KBucket, KBucket> {
        val lowerBucket = KBucket(k, distanceRange.first until midpoint)
        val upperBucket = KBucket(k, midpoint..distanceRange.last)
        
        for (node in nodes) {
            val prefixLength = node.nodeId.commonPrefixLength(NUID.ZERO) // Should use actual local node ID
            if (prefixLength < midpoint) {
                lowerBucket.addNode(node)
            } else {
                upperBucket.addNode(node)
            }
        }
        
        return lowerBucket to upperBucket
    }
}