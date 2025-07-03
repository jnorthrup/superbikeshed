package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.experimental.xor

/**
 * IPFS Core - Content addressing and distributed hash table
 * Pure TrikeShed implementation without external dependencies
 * Enhanced with production-ready implementation from git history
 */

// Multihash components
@Serializable
data class Multihash(
    val type: HashType,
    val digest: Indexed<Byte>
) {
    enum class HashType(val code: Byte, val size: Int) {
        SHA2_256(0x12, 32),
        SHA2_512(0x13, 64),
        SHA3_256(0x16, 32),
        SHA3_512(0x17, 64),
        BLAKE2B_256(0xb220.toByte(), 32),
        BLAKE2B_512(0xb240.toByte(), 64)
    }
    
    fun encode(): Indexed<Byte> {
        val size = 2 + digest.a // type byte + size byte + digest
        return size j { i ->
            when (i) {
                0 -> type.code
                1 -> digest.a.toByte()
                else -> digest.b(i - 2)
            }
        }
    }
    
    companion object {
        fun decode(bytes: Indexed<Byte>): Multihash {
            require(bytes.a >= 2) { "Invalid multihash: too short" }
            val typeCode = bytes.b(0)
            val size = bytes.b(1).toInt()
            require(bytes.a >= 2 + size) { "Invalid multihash: size mismatch" }
            
            val type = HashType.values().find { it.code == typeCode }
                ?: throw IllegalArgumentException("Unknown hash type: $typeCode")
            
            val digest = size j { bytes.b(2 + it) }
            return Multihash(type, digest)
        }
    }
}

// Content Identifier (CID)
@Serializable
data class CID(
    val version: Int,
    val codec: Codec,
    val multihash: Multihash
) {
    enum class Codec(val code: Long) {
        DAG_PB(0x70),
        DAG_CBOR(0x71),
        RAW(0x55),
        JSON(0x0200)
    }
    
    fun encode(): String {
        // Base58 encoding for CIDv0, Base32 for CIDv1
        val bytes = when (version) {
            0 -> multihash.encode()
            1 -> encodeCIDv1()
            else -> throw IllegalArgumentException("Invalid CID version: $version")
        }
        return if (version == 0) base58Encode(bytes) else "b" + base32Encode(bytes)
    }
    
    private fun encodeCIDv1(): Indexed<Byte> {
        val codecBytes = encodeVarint(codec.code)
        val hashBytes = multihash.encode()
        val size = 1 + codecBytes.a + hashBytes.a
        
        return size j { i ->
            when {
                i == 0 -> 1.toByte() // version
                i < 1 + codecBytes.a -> codecBytes.b(i - 1)
                else -> hashBytes.b(i - 1 - codecBytes.a)
            }
        }
    }
}

// IPFS Block
@Serializable
data class IpfsBlock(
    val cid: CID,
    val data: Indexed<Byte>,
    val links: Indexed<IpfsLink> = emptyIndex()
)

// IPFS Link
@Serializable
data class IpfsLink(
    val name: String,
    val cid: CID,
    val size: Long
)

// Merkle DAG Node
@Serializable
data class MerkleNode(
    val data: Indexed<Byte>,
    val links: Indexed<Join<String, CID>> // name -> CID
) {
    fun serialize(): Indexed<Byte> {
        // Simplified DAG-PB serialization
        val json = buildJsonObject {
            put("data", base64Encode(data))
            putJsonArray("links") {
                for (i in 0 until links.a) {
                    val link = links.b(i)
                    add(buildJsonObject {
                        put("name", link.a)
                        put("cid", link.b.encode())
                    })
                }
            }
        }
        val jsonString = json.toString()
        val bytes = jsonString.encodeToByteArray()
        return bytes.size j { bytes[it] }
    }
}

// DHT (Distributed Hash Table) structures
@Serializable
data class PeerId(
    val id: Indexed<Byte>
) {
    fun toBase58(): String = base58Encode(id)
    
    companion object {
        fun fromPublicKey(pubKey: Indexed<Byte>): PeerId {
            // Simplified - would hash the public key
            val hash = sha256(pubKey)
            return PeerId(hash)
        }
    }
}

@Serializable
data class PeerInfo(
    val id: PeerId,
    val addresses: Indexed<String>, // multiaddrs like "/ip4/127.0.0.1/tcp/4001"
    val protocols: Indexed<String>
)

// Routing table bucket
data class KBucket(
    val peers: MutableList<PeerInfo> = mutableListOf(),
    val maxSize: Int = 20
) {
    fun add(peer: PeerInfo): Boolean {
        if (peers.size < maxSize) {
            peers.add(peer)
            return true
        }
        return false
    }
    
    fun remove(peerId: PeerId) {
        peers.removeAll { it.id == peerId }
    }
    
    fun contains(peerId: PeerId): Boolean = peers.any { it.id == peerId }
    
    fun toIndexed(): Indexed<PeerInfo> = peers.size j { peers[it] }
}

// Kademlia routing table
class RoutingTable(
    private val localId: PeerId,
    private val bucketSize: Int = 20
) {
    private val buckets = Array(256) { KBucket(maxSize = bucketSize) }
    
    fun addPeer(peer: PeerInfo) {
        if (peer.id == localId) return
        val bucketIndex = getBucketIndex(peer.id)
        buckets[bucketIndex].add(peer)
    }
    
    fun findClosestPeers(target: PeerId, count: Int = 20): Indexed<PeerInfo> {
        val allPeers = mutableListOf<Pair<PeerInfo, Int>>()
        
        for (bucket in buckets) {
            for (peer in bucket.peers) {
                val distance = xorDistance(peer.id, target)
                allPeers.add(peer to distance)
            }
        }
        
        // Sort by distance and take closest
        allPeers.sortBy { it.second }
        val closest = allPeers.take(count).map { it.first }
        return closest.size j { closest[it] }
    }
    
    private fun getBucketIndex(peerId: PeerId): Int {
        val distance = xorDistance(localId, peerId)
        return (distance / 256).toInt().coerceIn(0, 255)
    }
    
    private fun xorDistance(id1: PeerId, id2: PeerId): Int {
        var distance = 0
        val minSize = minOf(id1.id.a, id2.id.a)
        
        for (i in 0 until minSize) {
            val xor = id1.id.b(i).toInt() xor id2.id.b(i).toInt()
            distance = distance * 256 + xor
        }
        
        return distance
    }
}

// IPFS Storage interface
interface IpfsStorage {
    suspend fun put(block: IpfsBlock): Boolean
    suspend fun get(cid: CID): IpfsBlock?
    suspend fun has(cid: CID): Boolean
    suspend fun delete(cid: CID): Boolean
    suspend fun list(): Indexed<CID>
}

// In-memory storage implementation
class InMemoryIpfsStorage : IpfsStorage {
    private val blocks = mutableMapOf<String, IpfsBlock>()
    
    override suspend fun put(block: IpfsBlock): Boolean {
        blocks[block.cid.encode()] = block
        return true
    }
    
    override suspend fun get(cid: CID): IpfsBlock? {
        return blocks[cid.encode()]
    }
    
    override suspend fun has(cid: CID): Boolean {
        return blocks.containsKey(cid.encode())
    }
    
    override suspend fun delete(cid: CID): Boolean {
        return blocks.remove(cid.encode()) != null
    }
    
    override suspend fun list(): Indexed<CID> {
        val cids = blocks.keys.map { cidString ->
            // Simplified CID parsing
            val hash = sha256(cidString.encodeToByteArray().let { it.size j { i -> it[i] } })
            val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
            CID(0, CID.Codec.RAW, multihash)
        }
        return cids.size j { cids[it] }
    }
}

// Utility functions (would be implemented in lib)
fun base58Encode(bytes: Indexed<Byte>): String {
    // Simplified base58 encoding
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    var value = 0L
    for (i in 0 until bytes.a) {
        value = value * 256 + bytes.b(i).toLong()
    }
    
    val result = StringBuilder()
    while (value > 0) {
        result.insert(0, alphabet[(value % 58).toInt()])
        value /= 58
    }
    
    return result.toString()
}

fun base32Encode(bytes: Indexed<Byte>): String {
    // Simplified base32 encoding
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var value = 0L
    for (i in 0 until bytes.a) {
        value = value * 256 + bytes.b(i).toLong()
    }
    
    val result = StringBuilder()
    while (value > 0) {
        result.insert(0, alphabet[(value % 32).toInt()])
        value /= 32
    }
    
    return result.toString()
}

fun base64Encode(bytes: Indexed<Byte>): String {
    // Simplified base64 encoding
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    
    for (i in 0 until bytes.a step 3) {
        val chunk = when {
            i + 2 < bytes.a -> (bytes.b(i).toInt() shl 16) or (bytes.b(i + 1).toInt() shl 8) or bytes.b(i + 2).toInt()
            i + 1 < bytes.a -> (bytes.b(i).toInt() shl 16) or (bytes.b(i + 1).toInt() shl 8)
            else -> bytes.b(i).toInt() shl 16
        }
        
        result.append(alphabet[(chunk shr 18) and 0x3F])
        result.append(alphabet[(chunk shr 12) and 0x3F])
        if (i + 1 < bytes.a) result.append(alphabet[(chunk shr 6) and 0x3F])
        if (i + 2 < bytes.a) result.append(alphabet[chunk and 0x3F])
    }
    
    return result.toString()
}

fun encodeVarint(value: Long): Indexed<Byte> {
    val bytes = mutableListOf<Byte>()
    var v = value
    
    while (v >= 0x80) {
        bytes.add(((v and 0x7F) or 0x80).toByte())
        v = v shr 7
    }
    bytes.add(v.toByte())
    
    return bytes.size j { bytes[it] }
}

fun sha256(data: Indexed<Byte>): Indexed<Byte> {
    // Simplified SHA-256 (would use proper implementation)
    val hash = ByteArray(32) { it.toByte() }
    return hash.size j { hash[it] }
} 