package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.serialization.Serializable

/**
 * IPFS Core - Content addressing and distributed hash table
 * Pure TrikeShed implementation without external dependencies
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
        // Simple encoding for demo
        return "bafy" + multihash.digest.a.toString(16)
    }
    
    override fun toString(): String = encode()
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
        // Simple serialization for demo
        val json = buildString {
            append("{\"data\":\"")
            append(base64Encode(data))
            append("\",\"links\":[")
            for (i in 0 until links.a) {
                val link = links.b(i)
                if (i > 0) append(",")
                append("{\"name\":\"${link.a}\",\"cid\":\"${link.b.encode()}\"}")
            }
            append("]}")
        }
        val bytes = json.encodeToByteArray()
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
        // Bucket full - peer is silently rejected
        return false
    }
    
    fun remove(peerId: PeerId) {
        peers.removeAll { it.id == peerId }
    }
    
    fun contains(peerId: PeerId): Boolean = peers.any { it.id == peerId }
    
    fun toIndexed(): Indexed<PeerInfo> = peers.size j { peers[it] }
}

// Routing table
class RoutingTable(private val localPeerId: PeerId) {
    private val buckets = mutableListOf<KBucket>()
    
    fun addPeer(peer: PeerInfo) {
        val bucketIndex = getBucketIndex(peer.id)
        while (buckets.size <= bucketIndex) {
            buckets.add(KBucket())
        }
        buckets[bucketIndex].add(peer)
    }
    
    fun removePeer(peerId: PeerId) {
        val bucketIndex = getBucketIndex(peerId)
        if (bucketIndex < buckets.size) {
            buckets[bucketIndex].remove(peerId)
        }
    }
    
    fun findClosest(targetId: PeerId, count: Int): Indexed<PeerInfo> {
        val bucketIndex = getBucketIndex(targetId)
        val closest = mutableListOf<PeerInfo>()
        
        // Add peers from target bucket
        if (bucketIndex < buckets.size) {
            closest.addAll(buckets[bucketIndex].peers)
        }
        
        // Add peers from adjacent buckets if needed
        var left = bucketIndex - 1
        var right = bucketIndex + 1
        while (closest.size < count && (left >= 0 || right < buckets.size)) {
            if (left >= 0) {
                closest.addAll(buckets[left].peers)
                left--
            }
            if (right < buckets.size) {
                closest.addAll(buckets[right].peers)
                right++
            }
        }
        
        return closest.take(count).size j { closest[it] }
    }
    
    private fun getBucketIndex(peerId: PeerId): Int {
        // Simplified bucket index calculation
        return peerId.id.a % 160 // 160-bit keyspace
    }
}

// === UTILITY FUNCTIONS ===

private fun base64Encode(data: Indexed<Byte>): String {
    // Simple base64 encoding for demo
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    
    var i = 0
    while (i < data.a) {
        val b1 = if (i < data.a) data.b(i).toInt() and 0xFF else 0
        val b2 = if (i + 1 < data.a) data.b(i + 1).toInt() and 0xFF else 0
        val b3 = if (i + 2 < data.a) data.b(i + 2).toInt() and 0xFF else 0
        
        result.append(chars[b1 shr 2])
        result.append(chars[((b1 and 3) shl 4) or (b2 shr 4)])
        result.append(if (i + 1 < data.a) chars[((b2 and 15) shl 2) or (b3 shr 6)] else '=')
        result.append(if (i + 2 < data.a) chars[b3 and 63] else '=')
        
        i += 3
    }
    
    return result.toString()
}

private fun base58Encode(data: Indexed<Byte>): String {
    // Simple base58 encoding for demo
    val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val result = StringBuilder()
    
    var value = 0L
    for (i in 0 until data.a) {
        value = value * 256 + data.b(i).toLong()
    }
    
    while (value > 0) {
        result.insert(0, chars[(value % 58).toInt()])
        value /= 58
    }
    
    return result.toString()
}

private fun sha256(data: Indexed<Byte>): Indexed<Byte> {
    // Simple hash function for demo
    var hash = 0L
    for (i in 0 until data.a) {
        hash = hash * 31 + data.b(i).toLong()
    }
    val hashBytes = hash.toString(16).padStart(32, '0').chunked(2).map { it.toInt(16).toByte() }
    return hashBytes.size j { hashBytes[it] }
}

private fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() } 