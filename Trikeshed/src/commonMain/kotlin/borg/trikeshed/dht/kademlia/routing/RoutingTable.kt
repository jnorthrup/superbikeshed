package borg.trikeshed.dht.kademlia.routing

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo

/**
 * Kademlia routing table with k-buckets
 * Organizes peers by XOR distance from local node
 * 
 * SAFEGUARD: All destructive operations now emit notifications
 */
class RoutingTable(
    private val localNodeId: NUID,
    private val k: Int = 20
) {
    private val buckets = mutableListOf<KBucket>()
    
    // Destruction notification callbacks
    private val destructionListeners = mutableListOf<(NodeInfo, String) -> Unit>()
    
    init {
        // Initialize with single bucket covering entire keyspace
        buckets.add(KBucket(k, 0..256))
        
        // Add destruction listeners to all buckets
        buckets.forEach { bucket ->
            bucket.addDestructionListener { node, reason ->
                destructionListeners.forEach { it(node, reason) }
                println("⚠️  ROUTING TABLE DESTRUCTION: Node ${node.nodeId} destroyed - $reason")
            }
        }
    }
    
    fun addDestructionListener(listener: (NodeInfo, String) -> Unit) {
        destructionListeners.add(listener)
    }
    
    /**
     * Add a node to the routing table
     */
    fun addNode(node: NodeInfo): Boolean {
        if (node.nodeId == localNodeId) return false
        
        val bucketIndex = getBucketIndex(node.nodeId)
        val bucket = buckets[bucketIndex]
        
        val added = bucket.addNode(node)
        
        // Split bucket if it's full and contains our local node ID range
        if (!added && bucket.isFull() && shouldSplitBucket(bucketIndex)) {
            println("🔀 ROUTING TABLE: Splitting bucket $bucketIndex due to overflow")
            splitBucket(bucketIndex)
            return addNode(node) // Retry after split
        }
        
        return added
    }
    
    /**
     * Remove a node from the routing table
     */
    fun removeNode(nodeId: NUID): Boolean {
        val bucketIndex = getBucketIndex(nodeId)
        val bucket = buckets[bucketIndex]
        val nodeToRemove = bucket.getNodes().play.find { it.nodeId == nodeId }
        
        val removed = bucket.removeNode(nodeId)
        
        if (removed && nodeToRemove != null) {
            println("🗑️  ROUTING TABLE REMOVAL: Node ${nodeToRemove.nodeId} removed from bucket $bucketIndex")
        }
        
        return removed
    }
    
    /**
     * Find K closest nodes to target
     */
    fun findClosestNodes(target: NUID, count: Int = k): Indexed<NodeInfo> {
        val candidates = mutableListOf<NodeInfo>()
        
        // Start with bucket containing target
        val targetBucketIndex = getBucketIndex(target)
        candidates.addAll(buckets[targetBucketIndex].getNodes().play)
        
        // Expand search to adjacent buckets until we have enough candidates
        var distance = 1
        while (candidates.size < count * 2 && distance <= buckets.size) {
            // Check bucket below
            val lowerIndex = targetBucketIndex - distance
            if (lowerIndex >= 0) {
                candidates.addAll(buckets[lowerIndex].getNodes().play)
            }
            
            // Check bucket above
            val upperIndex = targetBucketIndex + distance
            if (upperIndex < buckets.size) {
                candidates.addAll(buckets[upperIndex].getNodes().play)
            }
            
            distance++
        }
        
        // Sort by distance and take closest
        val sorted = candidates.sortedBy { node ->
            node.nodeId.distanceTo(target).toHex()
        }
        
        val resultCount = minOf(count, sorted.size)
        return resultCount j { i -> sorted[i] }
    }
    
    /**
     * Get all nodes in routing table
     */
    fun getAllNodes(): Indexed<NodeInfo> {
        val allNodes = mutableListOf<NodeInfo>()
        for (bucket in buckets) {
            allNodes.addAll(bucket.getNodes().play)
        }
        return allNodes.size j { i -> allNodes[i] }
    }
    
    /**
     * Get bucket count
     */
    fun getBucketCount(): Int = buckets.size
    
    /**
     * Get total node count
     */
    fun getNodeCount(): Int = buckets.sumOf { it.size() }
    
    private fun getBucketIndex(nodeId: NUID): Int {
        val prefixLength = localNodeId.commonPrefixLength(nodeId)
        
        // Find bucket that covers this prefix length
        for (i in buckets.indices) {
            val bucket = buckets[i]
            if (prefixLength in bucket.distanceRange) {
                return i
            }
        }
        
        // Fallback to last bucket
        return buckets.size - 1
    }
    
    private fun shouldSplitBucket(bucketIndex: Int): Boolean {
        val bucket = buckets[bucketIndex]
        
        // Only split if local node ID falls within this bucket's range
        val localPrefixLength = localNodeId.commonPrefixLength(NUID.ZERO)
        return localPrefixLength in bucket.distanceRange
    }
    
    private fun splitBucket(bucketIndex: Int) {
        val oldBucket = buckets[bucketIndex]
        val midpoint = (oldBucket.distanceRange.first + oldBucket.distanceRange.last) / 2
        
        println("🔀 ROUTING TABLE SPLIT: Bucket $bucketIndex splitting at midpoint $midpoint")
        println("  - Old range: ${oldBucket.distanceRange}")
        
        val (lowerBucket, upperBucket) = oldBucket.split(midpoint)
        
        // Add destruction listeners to new buckets
        lowerBucket.addDestructionListener { node, reason ->
            destructionListeners.forEach { it(node, reason) }
            println("⚠️  LOWER BUCKET DESTRUCTION: Node ${node.nodeId} destroyed - $reason")
        }
        upperBucket.addDestructionListener { node, reason ->
            destructionListeners.forEach { it(node, reason) }
            println("⚠️  UPPER BUCKET DESTRUCTION: Node ${node.nodeId} destroyed - $reason")
        }
        
        // Replace old bucket with split buckets
        buckets.removeAt(bucketIndex)
        buckets.add(bucketIndex, lowerBucket)
        buckets.add(bucketIndex + 1, upperBucket)
        
        println("  - New buckets: ${lowerBucket.distanceRange} and ${upperBucket.distanceRange}")
    }
}