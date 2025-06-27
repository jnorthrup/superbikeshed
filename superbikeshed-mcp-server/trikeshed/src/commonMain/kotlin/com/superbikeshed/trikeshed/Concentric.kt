package com.superbikeshed.trikeshed

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.experimental.xor

// Concentric Kademlia with dynamic key lengths
expect class ConcentricDHT {
    suspend fun join(bootstrap: NodeAddress?): Result<NodeID>
    suspend fun store(key: DynamicKey, value: ByteArray): Result<Unit>
    suspend fun find(key: DynamicKey): Result<ByteArray?>
    suspend fun route(target: NodeID): Result<List<NodeAddress>>
    suspend fun subnet(depth: Int): ConcentricSubnet
}

// Dynamic key length support - keys can be 160, 256, 512, or custom bits
@Serializable
data class DynamicKey(
    val bits: ByteArray,
    val length: Int
) {
    fun distance(other: DynamicKey): ByteArray {
        val maxLen = maxOf(bits.size, other.bits.size)
        val result = ByteArray(maxLen)
        for (i in 0 until maxLen) {
            val a = if (i < bits.size) bits[i] else 0
            val b = if (i < other.bits.size) other.bits[i] else 0
            result[i] = a xor b
        }
        return result
    }
    
    fun commonPrefixLength(other: DynamicKey): Int {
        var prefixLen = 0
        val minLen = minOf(bits.size, other.bits.size)
        for (i in 0 until minLen) {
            if (bits[i] == other.bits[i]) {
                prefixLen += 8
            } else {
                // Count matching bits in the differing byte
                val xor = bits[i] xor other.bits[i]
                for (bit in 7 downTo 0) {
                    if ((xor shr bit) and 1 == 0) {
                        prefixLen++
                    } else {
                        break
                    }
                }
                break
            }
        }
        return prefixLen
    }
}

@Serializable
data class NodeID(
    val key: DynamicKey,
    val subnet: Int = 0
)

@Serializable
data class NodeAddress(
    val id: NodeID,
    val endpoints: List<IPCEndpoint>
)

@Serializable
sealed class IPCEndpoint {
    @Serializable
    data class URing(val fd: Int) : IPCEndpoint()
    
    @Serializable
    data class SharedMemory(val shmId: String, val offset: Long) : IPCEndpoint()
    
    @Serializable
    data class UnixSocket(val path: String) : IPCEndpoint()
    
    @Serializable
    data class TCPSocket(val host: String, val port: Int) : IPCEndpoint()
    
    @Serializable
    data class QUICStream(val connectionId: String, val streamId: Long) : IPCEndpoint()
}

// Concentric subnet layers
expect class ConcentricSubnet {
    val depth: Int
    val keyLength: Int
    
    suspend fun broadcast(message: SubnetMessage): Result<Unit>
    suspend fun narrowcast(target: DynamicKey, message: SubnetMessage): Result<Unit>
    suspend fun subscribe(filter: MessageFilter): Flow<SubnetMessage>
}

@Serializable
data class SubnetMessage(
    val from: NodeID,
    val to: NodeID?,
    val payload: ByteArray,
    val ttl: Int = 64,
    val routePath: List<NodeID> = emptyList()
)

@Serializable
data class MessageFilter(
    val keyPrefix: ByteArray? = null,
    val subnet: Int? = null,
    val minKeyLength: Int? = null,
    val maxKeyLength: Int? = null
)

// N-way routing table for concentric rings
expect class NWayRoutingTable {
    suspend fun addNode(node: NodeAddress): Result<Unit>
    suspend fun removeNode(id: NodeID): Result<Unit>
    suspend fun findClosest(target: DynamicKey, n: Int): List<NodeAddress>
    suspend fun getSubnetPeers(subnet: Int): List<NodeAddress>
    suspend fun rebalance(): Result<Unit>
}

// Bucket structure for variable key lengths
@Serializable
data class KBucket(
    val rangeStart: DynamicKey,
    val rangeEnd: DynamicKey,
    val nodes: MutableList<NodeAddress>,
    val maxSize: Int = 20
) {
    fun canSplit(): Boolean = nodes.size >= maxSize
    
    fun split(): Pair<KBucket, KBucket> {
        val midpoint = ByteArray(rangeStart.bits.size) { i ->
            ((rangeStart.bits[i].toInt() and 0xFF) + 
             (rangeEnd.bits[i].toInt() and 0xFF)) / 2
        }.let { 
            it[it.size - 1] = (it[it.size - 1].toInt() or 1).toByte()
            it
        }
        
        val mid = DynamicKey(midpoint, rangeStart.length)
        val (left, right) = nodes.partition { 
            it.id.key.distance(rangeStart).contentCompare(it.id.key.distance(mid)) < 0
        }
        
        return KBucket(rangeStart, mid, left.toMutableList(), maxSize) to
               KBucket(mid, rangeEnd, right.toMutableList(), maxSize)
    }
}

// RXF-Rsync for live development sync
expect interface RXFSync {
    suspend fun watch(paths: List<String>): Flow<FileChange>
    suspend fun sync(change: FileChange): Result<Unit>
    suspend fun snapshot(): Result<SyncSnapshot>
    suspend fun restore(snapshot: SyncSnapshot): Result<Unit>
}

@Serializable
data class FileChange(
    val path: String,
    val type: ChangeType,
    val content: ByteArray? = null,
    val timestamp: Long,
    val gitHash: String? = null
)

enum class ChangeType {
    CREATE,
    MODIFY,
    DELETE,
    RENAME
}

@Serializable
data class SyncSnapshot(
    val files: Map<String, FileState>,
    val gitCommit: String?,
    val timestamp: Long
)

@Serializable
data class FileState(
    val size: Long,
    val hash: String,
    val lastModified: Long,
    val couchRev: String? = null
)

// CouchDB storage adapter for concentric DHT
expect interface ConcentricCouchAdapter {
    suspend fun storeSubnet(subnet: Int, data: SubnetData): Result<String>
    suspend fun getSubnet(subnet: Int, id: String): Result<SubnetData?>
    suspend fun querySubnet(subnet: Int, view: String, options: ViewOptions): Result<ViewResult>
    suspend fun replicateSubnet(subnet: Int, target: NodeAddress): Flow<ReplicationEvent>
}

@Serializable
data class SubnetData(
    val subnet: Int,
    val key: DynamicKey,
    val value: ByteArray,
    val timestamp: Long,
    val ttl: Long? = null
)

// Helper for ByteArray comparison
private fun ByteArray.contentCompare(other: ByteArray): Int {
    for (i in indices) {
        if (i >= other.size) return 1
        val cmp = this[i].compareTo(other[i])
        if (cmp != 0) return cmp
    }
    return size.compareTo(other.size)
}