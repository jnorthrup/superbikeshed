package borg.trikeshed.sumo.bitgraph

import borg.trikeshed.lib.*

/**
 * Bitgraph operations implementation
 */
class BitgraphOperations {
    
    inner class BitgraphNode(val name: String, val bits: Long) {
        fun copy(name: String = this.name, bits: Long = this.bits) = BitgraphNode(name, bits)
    }
    
    fun createNode(name: String, bits: Long): BitgraphNode {
        return BitgraphNode(name, bits)
    }
    
    fun isSubsumedBy(child: BitgraphNode, parent: BitgraphNode): Boolean {
        // Child has all parent's bits
        return (child.bits and parent.bits) == parent.bits
    }
    
    fun intersect(n1: BitgraphNode, n2: BitgraphNode): Long {
        return n1.bits and n2.bits
    }
    
    fun union(n1: BitgraphNode, n2: BitgraphNode): Long {
        return n1.bits or n2.bits
    }
    
    fun isMemberOf(node: BitgraphNode, unionBits: Long): Boolean {
        return (node.bits and unionBits) == node.bits
    }
    
    fun areSiblings(n1: BitgraphNode, n2: BitgraphNode): Boolean {
        // Share significant parent bits but differ in specifics
        val commonBits = n1.bits and n2.bits
        val xorBits = n1.bits xor n2.bits
        
        // Heuristic: siblings if they share >50% bits and have some differences
        val sharedCount = commonBits.countOneBits()
        val totalBits = (n1.bits or n2.bits).countOneBits()
        
        return sharedCount > totalBits / 2 && xorBits != 0L
    }
    
    fun assignRole(node: BitgraphNode, roleBit: Long): BitgraphNode {
        return node.copy(bits = node.bits or roleBit)
    }
    
    fun hasRole(node: BitgraphNode, roleBit: Long): Boolean {
        return (node.bits and roleBit) != 0L
    }
    
    fun batchSubsumptionCheck(nodes: List<BitgraphNode>, mask: Long): List<Boolean> {
        return nodes.map { node ->
            (node.bits and mask) != 0L
        }
    }
    
    fun similarity(n1: BitgraphNode, n2: BitgraphNode): Double {
        val xor = n1.bits xor n2.bits
        val distance = xor.countOneBits()
        val maxBits = maxOf(n1.bits.countOneBits(), n2.bits.countOneBits())
        
        return 1.0 - (distance.toDouble() / maxBits)
    }
    
    fun findPath(from: BitgraphNode, to: BitgraphNode): List<Long> {
        val path = mutableListOf<Long>()
        var current = from.bits
        
        // Simple bit reduction towards target
        while (current != to.bits && current != 0L) {
            path.add(current)
            // Remove least significant differing bit
            val diff = current xor to.bits
            val lsb = diff and -diff
            current = current and lsb.inv()
        }
        
        if (current == to.bits) path.add(to.bits)
        return path
    }
    
    fun negate(node: BitgraphNode, mask: Long): Long {
        return node.bits.inv() and mask
    }
    
    fun addFeature(node: BitgraphNode, feature: Feature): BitgraphNode {
        return node.copy(bits = node.bits or feature.bit)
    }
    
    fun clusterByBitPattern(nodes: List<BitgraphNode>, k: Int): List<List<BitgraphNode>> {
        // Simple k-means-like clustering based on bit patterns
        val clusters = MutableList(k) { mutableListOf<BitgraphNode>() }
        
        // Initialize cluster centers
        val centers = nodes.take(k).map { it.bits }.toMutableList()
        
        // Assign nodes to nearest cluster
        nodes.forEach { node ->
            val nearestCluster = centers.indices.minByOrNull { i ->
                (node.bits xor centers[i]).countOneBits()
            } ?: 0
            clusters[nearestCluster].add(node)
        }
        
        return clusters
    }
}

enum class Feature(val bit: Long) {
    PHYSICAL(0b10),
    ANIMATE(0b100),
    RATIONAL(0b1000)
}

/**
 * Extension functions for bitgraph operations
 */
internal fun Long.countOneBits(): Int {
    var count = 0
    var n = this
    while (n != 0L) {
        count += (n and 1).toInt()
        n = n ushr 1
    }
    return count
}

