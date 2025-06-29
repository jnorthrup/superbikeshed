#!/bin/bash
# task-ipfs.sh - Fire-and-forget IPFS integration task

set -euo pipefail

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

SWIMLANE_DIR="/Users/jim/work/v2superbikeshed/build/couch-ipfs-quic"
IPFS_DIR="$SWIMLANE_DIR/Trikeshed/src/commonMain/kotlin/borg/trikeshed/ipfs"

echo -e "${BLUE}Starting IPFS integration task...${NC}"

# Create directory
mkdir -p "$IPFS_DIR"

# Create IpfsContent.kt
cat > "$IPFS_DIR/IpfsContent.kt" << 'EOF'
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*

/**
 * IPFS Content-Addressed Storage for TrikeShed
 * 
 * Maps IPFS's content addressing to TrikeShed's Join<A,B> foundation.
 * Provides efficient CID packing and content-addressed immutable storage.
 */

/**
 * Content Identifier (CID) for IPFS
 * Packed representation for efficient storage:
 * - Version (2 bits)
 * - Codec (14 bits) 
 * - Hash function (16 bits)
 * - Hash digest (32 bytes)
 */
@JvmInline
value class CID(val bytes: ByteArray) {
    val version: Int get() = (bytes[0].toInt() shr 6) and 0x03
    val codec: Int get() = ((bytes[0].toInt() and 0x3F) shl 8) or (bytes[1].toInt() and 0xFF)
    val hashFunction: Int get() = (bytes[2].toInt() shl 8) or (bytes[3].toInt() and 0xFF)
    
    override fun toString(): String = TODO("Implement base58/base32 encoding")
    
    companion object {
        fun v0(hash: ByteArray): CID = TODO("Create CIDv0 from hash")
        fun v1(codec: Int, hash: ByteArray): CID = TODO("Create CIDv1")
    }
}

/**
 * IPFS content as a Join of CID and data
 */
typealias IpfsContent<T> = Join<CID, T>

/**
 * IPFS block with size metadata
 */
data class IpfsBlock(
    val cid: CID,
    val data: ByteArray,
    val size: Long
) {
    /**
     * Pack block metadata into a Long
     * Bits 0-31: Size in bytes (up to 4GB)
     * Bits 32-47: First 2 bytes of CID
     * Bits 48-63: Flags and block type
     */
    val packedMeta: Long
        get() = (size and 0xFFFFFFFF) or
            ((cid.bytes[0].toLong() and 0xFF) shl 32) or
            ((cid.bytes[1].toLong() and 0xFF) shl 40)
}

/**
 * IPFS DAG node linking to other content
 */
data class DagNode(
    val links: Indexed<DagLink>,
    val data: ByteArray?
)

/**
 * Link in IPFS DAG
 */
data class DagLink(
    val name: String,
    val cid: CID,
    val size: Long
)

/**
 * Pinning state for content
 */
enum class PinType {
    DIRECT,    // Directly pinned
    RECURSIVE, // Recursively pinned (with children)
    INDIRECT   // Pinned as child of recursive pin
}

/**
 * Pin status with packed metadata
 */
data class PinStatus(
    val cid: CID,
    val type: PinType,
    val pinCount: Int
) {
    /**
     * Pack pin status
     * Bits 0-15: Pin count
     * Bits 16-17: Pin type
     * Bits 18-31: Reserved
     */
    val packed: Int
        get() = (pinCount and 0xFFFF) or
            (type.ordinal shl 16)
}

/**
 * IPFS object types using TrikeShed's taxonomy
 */
sealed interface IpfsObject {
    val cid: CID
    
    data class Raw(override val cid: CID, val data: ByteArray) : IpfsObject
    data class Directory(override val cid: CID, val entries: Indexed<DagLink>) : IpfsObject
    data class File(override val cid: CID, val chunks: Indexed<CID>) : IpfsObject
    data class Symlink(override val cid: CID, val target: String) : IpfsObject
}

/**
 * IPFS MFS (Mutable File System) path
 */
@JvmInline
value class MfsPath(val path: String) {
    init {
        require(path.startsWith("/")) { "MFS path must be absolute" }
    }
    
    val parent: MfsPath? 
        get() = path.substringBeforeLast("/").takeIf { it.isNotEmpty() }?.let { MfsPath(it) }
    
    val name: String
        get() = path.substringAfterLast("/")
}

/**
 * Efficient IPLD storage using TrikeShed packing
 */
sealed interface IpldValue {
    data class Null(val unit: Unit = Unit) : IpldValue
    data class Bool(val value: Boolean) : IpldValue
    data class Integer(val value: Long) : IpldValue
    data class Float(val value: Double) : IpldValue
    data class String(val value: kotlin.String) : IpldValue
    data class Bytes(val value: ByteArray) : IpldValue
    data class List(val values: Indexed<IpldValue>) : IpldValue
    data class Map(val entries: Indexed<Join<kotlin.String, IpldValue>>) : IpldValue
    data class Link(val cid: CID) : IpldValue
}

/**
 * Swarm peer information
 */
data class PeerInfo(
    val id: String,
    val addresses: Indexed<String>,
    val latency: Long? = null
) {
    /**
     * Pack peer metrics
     * Bits 0-31: Latency in microseconds (null = -1)
     * Bits 32-47: Address count
     * Bits 48-63: Connection flags
     */
    val packedMetrics: Long
        get() = ((latency ?: -1L) and 0xFFFFFFFF) or
            (addresses.size.toLong() shl 32)
}

/**
 * Bandwidth statistics
 */
data class BandwidthStats(
    val totalIn: Long,
    val totalOut: Long,
    val rateIn: Double,
    val rateOut: Double
)

/**
 * IPFS repository statistics
 */
data class RepoStats(
    val repoSize: Long,
    val storageMax: Long,
    val numObjects: Long,
    val repoPath: String,
    val version: String
) {
    val usagePercent: Double
        get() = (repoSize.toDouble() / storageMax) * 100
}
EOF

# Create IpfsClient.kt
cat > "$IPFS_DIR/IpfsClient.kt" << 'EOF'
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*

/**
 * IPFS Client Interface for TrikeShed
 * 
 * Provides content-addressed storage with:
 * - Immutable content via CIDs
 * - Distributed pinning and retrieval
 * - PubSub for real-time updates
 * - Integration with CouchDB document model
 */
interface IpfsClient {
    /**
     * Add content to IPFS, returning its CID
     */
    suspend fun add(data: ByteArray): Either<IpfsError, CID>
    
    /**
     * Add with options
     */
    suspend fun addWithOptions(
        data: ByteArray,
        pin: Boolean = true,
        wrapWithDirectory: Boolean = false,
        onlyHash: Boolean = false
    ): Either<IpfsError, AddResult>
    
    /**
     * Get content by CID
     */
    suspend fun cat(cid: CID): Either<IpfsError, ByteArray>
    
    /**
     * Get content with size limit
     */
    suspend fun catRange(
        cid: CID,
        offset: Long = 0,
        length: Long? = null
    ): Either<IpfsError, ByteArray>
    
    /**
     * List directory contents
     */
    suspend fun ls(cid: CID): Either<IpfsError, Indexed<DagLink>>
    
    /**
     * Pin content locally
     */
    suspend fun pin(cid: CID, recursive: Boolean = true): Either<IpfsError, Unit>
    
    /**
     * Unpin content
     */
    suspend fun unpin(cid: CID): Either<IpfsError, Unit>
    
    /**
     * List pinned content
     */
    suspend fun pinLs(type: PinType? = null): Either<IpfsError, Indexed<PinStatus>>
    
    /**
     * Get object stats
     */
    suspend fun objectStat(cid: CID): Either<IpfsError, ObjectStats>
    
    /**
     * Resolve IPNS name
     */
    suspend fun nameResolve(name: String): Either<IpfsError, CID>
    
    /**
     * Publish to IPNS
     */
    suspend fun namePublish(cid: CID, key: String? = null): Either<IpfsError, IpnsEntry>
    
    /**
     * Subscribe to pubsub topic
     */
    suspend fun pubsubSub(topic: String): Either<IpfsError, PubsubStream>
    
    /**
     * Publish to pubsub topic
     */
    suspend fun pubsubPub(topic: String, data: ByteArray): Either<IpfsError, Unit>
    
    /**
     * Get swarm peers
     */
    suspend fun swarmPeers(): Either<IpfsError, Indexed<PeerInfo>>
    
    /**
     * Get bandwidth stats
     */
    suspend fun statsBw(): Either<IpfsError, BandwidthStats>
    
    /**
     * Get repo stats
     */
    suspend fun statsRepo(): Either<IpfsError, RepoStats>
    
    /**
     * MFS operations
     */
    suspend fun filesLs(path: MfsPath): Either<IpfsError, Indexed<MfsEntry>>
    suspend fun filesWrite(path: MfsPath, data: ByteArray, create: Boolean = true): Either<IpfsError, Unit>
    suspend fun filesRead(path: MfsPath): Either<IpfsError, ByteArray>
    suspend fun filesMv(from: MfsPath, to: MfsPath): Either<IpfsError, Unit>
    suspend fun filesRm(path: MfsPath, recursive: Boolean = false): Either<IpfsError, Unit>
}

/**
 * IPFS errors
 */
sealed interface IpfsError {
    data class NotFound(val cid: CID) : IpfsError
    data class Timeout(val operation: String) : IpfsError
    data class Network(val message: String, val cause: Throwable? = null) : IpfsError
    data class Invalid(val reason: String) : IpfsError
    data class QuotaExceeded(val current: Long, val max: Long) : IpfsError
}

/**
 * Add operation result
 */
data class AddResult(
    val cid: CID,
    val size: Long,
    val path: String? = null
)

/**
 * Object statistics
 */
data class ObjectStats(
    val cid: CID,
    val numLinks: Int,
    val blockSize: Int,
    val linksSize: Int,
    val dataSize: Int,
    val cumulativeSize: Long
) {
    /**
     * Pack stats into two Longs for efficient storage
     */
    val packedStats1: Long
        get() = (numLinks.toLong() and 0xFFFF) or
            ((blockSize.toLong() and 0xFFFF) shl 16) or
            ((linksSize.toLong() and 0xFFFF) shl 32) or
            ((dataSize.toLong() and 0xFFFF) shl 48)
            
    val packedStats2: Long
        get() = cumulativeSize
}

/**
 * IPNS entry
 */
data class IpnsEntry(
    val name: String,
    val value: CID,
    val validityType: String? = null,
    val validity: String? = null,
    val sequence: Long? = null,
    val ttl: Long? = null,
    val pubKey: String? = null
)

/**
 * Pubsub message stream
 */
interface PubsubStream {
    suspend fun next(): PubsubMessage?
    fun close()
}

/**
 * Pubsub message
 */
data class PubsubMessage(
    val from: String,
    val data: ByteArray,
    val seqno: ByteArray,
    val topic: String
)

/**
 * MFS directory entry
 */
data class MfsEntry(
    val name: String,
    val type: MfsType,
    val size: Long,
    val cid: CID
)

enum class MfsType {
    FILE,
    DIRECTORY
}

/**
 * Content cache with latency tiers
 */
class IpfsCache(
    private val maxSize: Int = 1000,
    private val maxBytes: Long = 100_000_000 // 100MB
) {
    private data class CacheEntry(
        val data: ByteArray,
        val timestamp: Long,
        val accessCount: Int = 0
    )
    
    private val cache = mutableMapOf<CID, CacheEntry>()
    private var totalBytes = 0L
    
    /**
     * Get from cache
     * Returns null if not found or expired
     */
    fun get(cid: CID, maxAgeMillis: Long = 3600_000): ByteArray? {
        val entry = cache[cid] ?: return null
        val age = System.currentTimeMillis() - entry.timestamp
        
        return if (age < maxAgeMillis) {
            // Update access count
            cache[cid] = entry.copy(accessCount = entry.accessCount + 1)
            entry.data
        } else {
            // Expired
            evict(cid)
            null
        }
    }
    
    /**
     * Put into cache
     */
    fun put(cid: CID, data: ByteArray) {
        val size = data.size.toLong()
        
        // Evict if necessary
        while (cache.size >= maxSize || totalBytes + size > maxBytes) {
            evictLRU()
        }
        
        cache[cid] = CacheEntry(data, System.currentTimeMillis())
        totalBytes += size
    }
    
    private fun evict(cid: CID) {
        cache.remove(cid)?.let {
            totalBytes -= it.data.size
        }
    }
    
    private fun evictLRU() {
        // Find least recently used
        val lru = cache.entries.minByOrNull { 
            it.value.timestamp + it.value.accessCount * 60_000 
        }
        lru?.let { evict(it.key) }
    }
}

/**
 * Extension functions for IPFS operations
 */

/**
 * Add any serializable content
 */
suspend inline fun <reified T> IpfsClient.addJson(content: T): Either<IpfsError, CID> =
    TODO("Implement JSON serialization and add")

/**
 * Get content as typed object
 */
suspend inline fun <reified T> IpfsClient.catJson(cid: CID): Either<IpfsError, T> =
    TODO("Implement cat and JSON deserialization")

/**
 * Create a directory structure
 */
suspend fun IpfsClient.addDirectory(
    entries: Indexed<Join<String, ByteArray>>
): Either<IpfsError, CID> = TODO("Implement directory creation")
EOF

# Create IpfsPubSub.kt
cat > "$IPFS_DIR/IpfsPubSub.kt" << 'EOF'
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow

/**
 * IPFS PubSub for Distributed Updates
 * 
 * Enables real-time synchronization across the network using
 * TrikeShed's efficient message packing strategies.
 */

/**
 * PubSub topic with type safety
 */
@JvmInline
value class Topic(val name: String) {
    init {
        require(name.isNotBlank()) { "Topic name cannot be blank" }
        require(!name.contains(" ")) { "Topic name cannot contain spaces" }
    }
}

/**
 * Message envelope for pubsub
 */
data class Envelope<T>(
    val topic: Topic,
    val sender: String,
    val payload: T,
    val timestamp: Long = System.currentTimeMillis(),
    val sequence: Long
) {
    /**
     * Pack envelope metadata
     * Bits 0-31: Sequence number
     * Bits 32-63: Timestamp delta from epoch in seconds
     */
    val packedMeta: Long
        get() = (sequence and 0xFFFFFFFF) or
            ((timestamp / 1000) shl 32)
}

/**
 * Document update notification
 */
data class DocUpdate(
    val docId: String,
    val rev: String,
    val cid: CID,
    val deleted: Boolean = false
)

/**
 * Replication event
 */
sealed interface ReplicationEvent {
    val source: String
    val target: String
    val timestamp: Long
    
    data class Started(
        override val source: String,
        override val target: String,
        override val timestamp: Long,
        val continuous: Boolean
    ) : ReplicationEvent
    
    data class Progress(
        override val source: String,
        override val target: String,
        override val timestamp: Long,
        val docsRead: Int,
        val docsWritten: Int,
        val docsFailed: Int
    ) : ReplicationEvent {
        /**
         * Pack progress stats
         * Bits 0-19: Docs read (up to 1M)
         * Bits 20-39: Docs written (up to 1M)
         * Bits 40-59: Docs failed (up to 1M)
         */
        val packedStats: Long
            get() = (docsRead.toLong() and 0xFFFFF) or
                ((docsWritten.toLong() and 0xFFFFF) shl 20) or
                ((docsFailed.toLong() and 0xFFFFF) shl 40)
    }
    
    data class Completed(
        override val source: String,
        override val target: String,
        override val timestamp: Long,
        val docsRead: Int,
        val docsWritten: Int,
        val docsFailed: Int,
        val endSeq: Long
    ) : ReplicationEvent
    
    data class Error(
        override val source: String,
        override val target: String,
        override val timestamp: Long,
        val error: String
    ) : ReplicationEvent
}

/**
 * Index update notification
 */
data class IndexUpdate(
    val database: String,
    val designDoc: String,
    val viewName: String,
    val updateSeq: Long,
    val purgeSeq: Long
)

/**
 * Cluster membership change
 */
sealed interface ClusterEvent {
    val nodeId: String
    val timestamp: Long
    
    data class NodeJoined(
        override val nodeId: String,
        override val timestamp: Long,
        val address: String
    ) : ClusterEvent
    
    data class NodeLeft(
        override val nodeId: String,
        override val timestamp: Long,
        val reason: String
    ) : ClusterEvent
    
    data class NodeHealthy(
        override val nodeId: String,
        override val timestamp: Long,
        val load: Double,
        val memoryUsed: Long,
        val diskUsed: Long
    ) : ClusterEvent
}

/**
 * PubSub service interface
 */
interface IpfsPubSubService {
    /**
     * Subscribe to a topic
     */
    suspend fun subscribe(topic: Topic): Flow<ByteArray>
    
    /**
     * Subscribe with typed deserialization
     */
    suspend fun <T> subscribe(
        topic: Topic,
        deserializer: (ByteArray) -> T
    ): Flow<T>
    
    /**
     * Publish to a topic
     */
    suspend fun publish(topic: Topic, data: ByteArray)
    
    /**
     * Publish typed content
     */
    suspend fun <T> publish(
        topic: Topic,
        payload: T,
        serializer: (T) -> ByteArray
    )
    
    /**
     * List subscribed topics
     */
    suspend fun topics(): Indexed<Topic>
    
    /**
     * List peers subscribed to a topic
     */
    suspend fun peers(topic: Topic): Indexed<String>
    
    /**
     * Unsubscribe from a topic
     */
    suspend fun unsubscribe(topic: Topic)
}

/**
 * Standard topics for CouchDB-IPFS integration
 */
object StandardTopics {
    /**
     * Document updates for a database
     */
    fun docUpdates(database: String) = Topic("couch/db/$database/updates")
    
    /**
     * Replication events
     */
    fun replication(database: String) = Topic("couch/db/$database/replication")
    
    /**
     * View index updates
     */
    fun indexUpdates(database: String) = Topic("couch/db/$database/indexes")
    
    /**
     * Cluster membership
     */
    val clusterEvents = Topic("couch/cluster/events")
    
    /**
     * Global changes feed
     */
    val globalChanges = Topic("couch/changes/all")
    
    /**
     * IPFS pinning coordination
     */
    fun pinCoordination(namespace: String) = Topic("ipfs/pins/$namespace")
}

/**
 * Message batching for efficiency
 */
class MessageBatcher<T>(
    private val maxBatchSize: Int = 100,
    private val maxDelayMillis: Long = 100,
    private val packer: (Indexed<T>) -> ByteArray
) {
    private val pending = mutableListOf<T>()
    private var lastFlush = System.currentTimeMillis()
    
    /**
     * Add message to batch
     * Returns packed batch if ready to send
     */
    fun add(message: T): ByteArray? {
        pending.add(message)
        
        val now = System.currentTimeMillis()
        val shouldFlush = pending.size >= maxBatchSize || 
            (now - lastFlush) >= maxDelayMillis
        
        return if (shouldFlush) {
            flush()
        } else {
            null
        }
    }
    
    /**
     * Force flush of pending messages
     */
    fun flush(): ByteArray? {
        if (pending.isEmpty()) return null
        
        val batch = pending.toList()
        pending.clear()
        lastFlush = System.currentTimeMillis()
        
        return packer(batch.size j { i -> batch[i] })
    }
}

/**
 * Distributed lock coordination via PubSub
 */
class DistributedLock(
    private val pubsub: IpfsPubSubService,
    private val nodeId: String
) {
    /**
     * Try to acquire a lock
     */
    suspend fun tryAcquire(
        resource: String,
        ttlMillis: Long = 30_000
    ): Either<LockError, LockHandle> = TODO("Implement distributed locking")
    
    sealed interface LockError {
        data class AlreadyLocked(val holder: String) : LockError
        data class Timeout(val waitedMillis: Long) : LockError
        data class Network(val error: IpfsError) : LockError
    }
    
    data class LockHandle(
        val resource: String,
        val holder: String,
        val acquiredAt: Long,
        val ttl: Long
    ) {
        suspend fun release() = TODO("Release lock")
        suspend fun renew(ttlMillis: Long) = TODO("Renew lock")
    }
}

/**
 * Extension functions for typed pubsub
 */

/**
 * Subscribe to document updates
 */
suspend fun IpfsPubSubService.subscribeDocUpdates(
    database: String
): Flow<DocUpdate> = subscribe(
    StandardTopics.docUpdates(database)
) { bytes ->
    TODO("Deserialize DocUpdate")
}

/**
 * Publish document update
 */
suspend fun IpfsPubSubService.publishDocUpdate(
    database: String,
    update: DocUpdate
) = publish(
    StandardTopics.docUpdates(database),
    update
) { u ->
    TODO("Serialize DocUpdate")
}
EOF

echo -e "${GREEN}✓ IPFS integration created successfully!${NC}"
echo "Location: $IPFS_DIR"