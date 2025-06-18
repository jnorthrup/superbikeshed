package com.rtsgame.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.security.MessageDigest
import kotlin.experimental.and

/**
 * Interface for content-addressed storage operations
 */
interface ContentAddressedStorage {
    /**
     * Store data and return its content identifier
     */
    suspend fun put(data: ByteArray): String

    /**
     * Retrieve data by its content identifier
     */
    suspend fun get(cid: String): ByteArray?

    /**
     * Check if content exists
     */
    suspend fun exists(cid: String): Boolean

    /**
     * Stream of updates for a specific CID
     */
    fun watch(cid: String): Flow<ByteArray>
}

/**
 * Implementation using IPFS-like content addressing
 */
class IPFSContentStorage : ContentAddressedStorage {
    private val storage = mutableMapOf<String, ByteArray>()
    private val watchers = mutableMapOf<String, MutableSharedFlow<ByteArray>>()

    override suspend fun put(data: ByteArray): String {
        val cid = generateCID(data)
        storage[cid] = data
        watchers[cid]?.emit(data)
        return cid
    }

    override suspend fun get(cid: String): ByteArray? = storage[cid]

    override suspend fun exists(cid: String): Boolean = storage.containsKey(cid)

    override fun watch(cid: String): Flow<ByteArray> {
        return watchers.getOrPut(cid) { MutableSharedFlow(replay = 1) }.asSharedFlow()
    }

    private fun generateCID(data: ByteArray): String {
        // Generate SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        
        // Convert to multihash format (0x12 = SHA-256, 0x20 = 32 bytes)
        val multihash = ByteArray(hash.size + 2)
        multihash[0] = 0x12 // SHA-256 code
        multihash[1] = 0x20 // 32 bytes length
        System.arraycopy(hash, 0, multihash, 2, hash.size)
        
        // Convert to base58btc (simplified for now)
        return "Qm" + multihash.joinToString("") { 
            String.format("%02x", it and 0xFF.toByte())
        }
    }
} 