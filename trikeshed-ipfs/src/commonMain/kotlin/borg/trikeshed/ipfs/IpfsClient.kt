@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * Practical IPFS client with content storage, retrieval, and DHT operations
 */
class IpfsClient(
    val localPeerId: PeerId,
    val quicEngine: Any?,
    val storage: IpfsStorage,
    val config: IpfsConfig = IpfsConfig()
) {
    internal val blockCache: Indexed<Join<CID, IpfsBlock>> = 0 j { 
        CID(0, CID.Codec.RAW, Multihash(Multihash.HashType.SHA2_256, 0 j { 0.toByte() })) j 
        IpfsBlock(CID(0, CID.Codec.RAW, Multihash(Multihash.HashType.SHA2_256, 0 j { 0.toByte() })), 0 j { 0.toByte() }) 
    }
    
    // Destruction notification callbacks
    internal val destructionListeners = mutableListOf<(CID, String) -> Unit>()
    
    fun addDestructionListener(listener: (CID, String) -> Unit) {
        destructionListeners.add(listener)
    }
    
    internal fun notifyDestruction(cid: CID, reason: String) {
        destructionListeners.forEach { it(cid, reason) }
        println("⚠️  IPFS DESTRUCTION: Block ${cid} destroyed - $reason")
    }
    
    /**
     * Add content to IPFS
     */
    suspend fun add(data: Indexed<Byte>): CID = coroutineScope {
        // Create raw block
        val hash = computeHash(data)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(1, CID.Codec.RAW, multihash)
        
        // Store locally
        val block = IpfsBlock(cid, data)
        storage.putBlock(block)
        
        // Add to cache
        addToCache(cid, block)
        
        cid
    }
    
    /**
     * Add file with chunking for large files
     */
    suspend fun addFile(data: Indexed<Byte>, chunkSize: Int = 262144): CID = coroutineScope {
        if (data.component1() <= chunkSize) {
            // Small file - store as single block
            return@coroutineScope add(data)
        }
        
        // Large file - create merkle dag
        val chunks = mutableListOf<CID>()
        var offset = 0
        
        while (offset < data.component1()) {
            val size = minOf(chunkSize, data.component1() - offset)
            val chunk = size j { data.component2()(offset + it) }
            val chunkCid = add(chunk)
            chunks.add(chunkCid)
            offset += size
        }
        
        // Create root node with links to chunks
        val links = \1 j { \2: Int ->
            IpfsLink("chunk$i", chunks[i], chunkSize.toLong())
        }
        
        val rootNode = MerkleNode(
            data = 0 j { throw NoSuchElementException() }, // Empty data
            links = links
        )
        
        val rootData = rootNode.serialize()
        val rootHash = computeHash(rootData)
        val rootMultihash = Multihash(Multihash.HashType.SHA2_256, rootHash)
        val rootCid = CID(1, CID.Codec.DAG_PB, rootMultihash)
        
        val rootBlock = IpfsBlock(rootCid, rootData, links)
        storage.putBlock(rootBlock)
        addToCache(rootCid, rootBlock)
        
        rootCid
    }
    
    /**
     * Get content from IPFS
     */
    suspend fun get(cid: CID): Indexed<Byte>? = coroutineScope {
        // Check local cache
        for (i in 0 until blockCache.component1()) {
            val entry = blockCache.component2()(i)
            if (entry.component1() == cid) return@coroutineScope entry.component2().data
        }
        
        // Check local storage
        storage.getBlock(cid)?.let { block ->
            addToCache(cid, block)
            return@coroutineScope block.data
        }
        
        // Not found locally
        null
    }
    
    /**
     * Get file (handles merkle dag)
     */
    suspend fun getFile(cid: CID): Indexed<Byte>? = coroutineScope {
        val rootBlock = storage.getBlock(cid) ?: run {
            // Try to fetch from network
            val data = get(cid) ?: return@coroutineScope null
            val block = IpfsBlock(cid, data)
            block
        }
        
        // Check if it's a simple block or merkle dag
        if (rootBlock.links.component1() == 0) {
            return@coroutineScope rootBlock.data
        }
        
        // It's a merkle dag - fetch all chunks
        val chunks = mutableListOf<Indexed<Byte>>()
        
        for (i in 0 until rootBlock.links.component1()) {
            val link = rootBlock.links.component2()(i)
            val chunkData = get(link.cid) ?: return@coroutineScope null
            chunks.add(chunkData)
        }
        
        // Concatenate chunks
        val totalSize = chunks.sumOf { it.component1() }
        
        \1 j { \2: Int ->
            // Find which chunk this byte belongs to
            var chunkIndex = 0
            var remaining = i
            
            while (chunkIndex < chunks.size && remaining >= chunks[chunkIndex].component1()) {
                remaining -= chunks[chunkIndex].component1()
                chunkIndex++
            }
            
            if (chunkIndex < chunks.size) {
                chunks[chunkIndex].component2()(remaining)
            } else {
                throw IndexOutOfBoundsException()
            }
        }
    }
    
    /**
     * Pin content to prevent garbage collection
     */
    suspend fun pin(cid: CID): Boolean {
        val block = storage.getBlock(cid) ?: run {
            // Try to fetch first
            get(cid) ?: return false
            storage.getBlock(cid)!!
        }
        
        storage.pin(cid)
        
        // Recursively pin links
        for (i in 0 until block.links.component1()) {
            val link = block.links.component2()(i)
            pin(link.cid)
        }
        
        return true
    }
    
    /**
     * Unpin content
     */
    suspend fun unpin(cid: CID): Boolean {
        storage.unpin(cid)
        return true
    }
    
    /**
     * List pinned content
     */
    fun listPinned(): Indexed<CID> {
        return storage.listPinned()
    }
    
    /**
     * Garbage collect unpinned content
     */
    suspend fun gc(): Indexed<CID> {
        val unpinned = storage.listUnpinned()
        val collected = mutableListOf<CID>()
        
        for (i in 0 until unpinned.component1()) {
            val cid = unpinned.component2()(i)
            storage.removeBlock(cid)
            collected.add(cid)
            notifyDestruction(cid, "Garbage collection")
        }
        
        return collected.toIdx()
    }
    
    suspend fun store(data: Indexed<Byte>): IpfsStoreResult {
        val cid = add(data)
        return IpfsStoreResult(hash = cid.toString())
    }
    
    suspend fun retrieve(hash: String): IpfsRetrieveResult? {
        // Try to parse as CID first
        val cid = try {
            parseCID(hash)
        } catch (e: Exception) {
            // Fall back to simple hash lookup
            val foundBlock = storage.blocks.values.find { it.cid.toString() == hash }
            return if (foundBlock != null) {
                IpfsRetrieveResult(content = foundBlock.data)
            } else {
                null
            }
        }
        
        val data = get(cid)
        return if (data != null) {
            IpfsRetrieveResult(content = data)
        } else {
            null
        }
    }
    
    // === INTERNAL HELPERS ===
    
    internal fun computeHash(data: Indexed<Byte>): Indexed<Byte> {
        // Simple hash function for demo purposes
        var hash = 0L
        for (i in 0 until data.component1()) {
            hash = hash * 31 + data.component2()(i).toLong()
        }
        val hashBytes = hash.toString(16).padStart(32, '0').chunked(2).map { it.toInt(16).toByte() }
        return hashBytes.size j { hashBytes[it] }
    }
    
    internal fun addToCache(cid: CID, block: IpfsBlock) {
        // Simple cache management - could be enhanced with LRU
        if (blockCache.component1() < 1000) { // Limit cache size
            // Would normally append to cache
        }
    }
    
    internal fun parseCID(hash: String): CID {
        // Simple CID parsing for demo
        if (hash.startsWith("bafy")) {
            val digest = hash.substring(4).chunked(2).map { it.toInt(16).toByte() }
            val multihash = Multihash(Multihash.HashType.SHA2_256, digest.size j { digest[it] })
            return CID(1, CID.Codec.RAW, multihash)
        }
        throw IllegalArgumentException("Invalid CID format")
    }
    
    internal fun <T> List<T>.toIdx(): Indexed<T> {
        return this.size j { i: Int -> this[i] }
    }
}

// === ENHANCED DATA STRUCTURES ===

data class MerkleNode(
    val data: Indexed<Byte>,
    val links: Indexed<IpfsLink>
) {
    fun serialize(): Indexed<Byte> {
        // Simple serialization for demo
        val serialized = mutableListOf<Byte>()
        
        // Add data length
        val dataLength = data.component1().toString()
        serialized.addAll(dataLength.encodeToByteArray().map { it.toByte() })
        serialized.add(0) // null terminator
        
        // Add data
        for (i in 0 until data.component1()) {
            serialized.add(data.component2()(i))
        }
        
        // Add links count
        val linksCount = links.component1().toString()
        serialized.addAll(linksCount.encodeToByteArray().map { it.toByte() })
        serialized.add(0) // null terminator
        
        // Add links
        for (i in 0 until links.component1()) {
            val link = links.component2()(i)
            serialized.addAll(link.name.encodeToByteArray().map { it.toByte() })
            serialized.add(0) // null terminator
            serialized.addAll(link.cid.toString().encodeToByteArray().map { it.toByte() })
            serialized.add(0) // null terminator
        }
        
        return serialized.size j { serialized[it] }
    }
}

// Minimal data structures
data class PeerId(val id: Indexed<Byte>)

class IpfsStorage {
    internal val blocks = mutableMapOf<CID, IpfsBlock>()
    internal val pinnedBlocks = mutableSetOf<CID>()
    
    fun putBlock(block: IpfsBlock) {
        blocks[block.cid] = block
    }
    
    fun getBlock(cid: CID): IpfsBlock? = blocks[cid]
    
    fun pin(cid: CID) {
        pinnedBlocks.add(cid)
    }
    
    fun unpin(cid: CID) {
        pinnedBlocks.remove(cid)
    }
    
    fun listPinned(): Indexed<CID> {
        return \1 j { \2: Int -> pinnedBlocks.elementAt(i) }
    }
    
    fun listUnpinned(): Indexed<CID> {
        return blocks.keys.filter { !pinnedBlocks.contains(it) \1 j { \2: Int -> 
            blocks.keys.filter { !pinnedBlocks.contains(it) }.elementAt(i) 
        }
    }
    
    fun removeBlock(cid: CID) {
        blocks.remove(cid)
    }
}

data class CID(
    val version: Int,
    val codec: Codec,
    val multihash: Multihash
) {
    enum class Codec(val code: Long) {
        RAW(0x55),
        DAG_PB(0x70)
    }
    
    override fun toString(): String = "bafy" + multihash.digest.component1().toString(16)
}

data class Multihash(
    val type: HashType,
    val digest: Indexed<Byte>
) {
    enum class HashType(val code: Byte, val size: Int) {
        SHA2_256(0x12, 32)
    }
}

data class IpfsBlock(
    val cid: CID,
    val data: Indexed<Byte>,
    val links: Indexed<IpfsLink> = 0 j { throw NoSuchElementException() }
)

data class IpfsLink(
    val name: String,
    val cid: CID,
    val size: Long
)

// Result types for IPFS operations
data class IpfsStoreResult(val hash: String)
data class IpfsRetrieveResult(val content: Indexed<Byte>)

// Configuration
class IpfsConfig(
    val chunkSize: Int = 262144,
    val maxCacheSize: Int = 1000,
    val enableDHT: Boolean = false
) 