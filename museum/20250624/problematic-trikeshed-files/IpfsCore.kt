package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.experimental.xor

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
    // Destruction notification callbacks
    private val destructionListeners = mutableListOf<(PeerInfo, String) -> Unit>()
    
    fun addDestructionListener(listener: (PeerInfo, String) -> Unit) {
        destructionListeners.add(listener)
    }
    
    private fun notifyDestruction(peer: PeerInfo, reason: String) {
        destructionListeners.forEach { it(peer, reason) }
        println("⚠️  KBUCKET DESTRUCTION: Peer ${peer.id} destroyed - $reason")
    }
    
    fun add(peer: PeerInfo): Boolean {
        if (peers.size < maxSize) {
            peers.add(peer)
            return true
        }
        // Bucket full - peer is silently rejected
        notifyDestruction(peer, "bucket full - rejected")
        return false
    }
    
    fun remove(peerId: PeerId) {
        val peerToRemove = peers.find { it.id == peerId }
        peers.removeAll { it.id == peerId }
        if (peerToRemove != null) {
            notifyDestruction(peerToRemove, "explicit removal")
        }
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
    
    // Destruction notification callbacks
    private val destructionListeners = mutableListOf<(PeerInfo, String) -> Unit>()
    
    init {
        // Add destruction listeners to all buckets
        buckets.forEach { bucket ->
            bucket.addDestructionListener { peer, reason ->
                destructionListeners.forEach { it(peer, reason) }
                println("⚠️  ROUTING TABLE DESTRUCTION: Peer ${peer.id} destroyed - $reason")
            }
        }
    }
    
    fun addDestructionListener(listener: (PeerInfo, String) -> Unit) {
        destructionListeners.add(listener)
    }
    
    fun addPeer(peer: PeerInfo) {
        if (peer.id == localId) return
        val bucketIndex = getBucketIndex(peer.id)
        val added = buckets[bucketIndex].add(peer)
        if (!added) {
            println("🔀 ROUTING TABLE: Peer ${peer.id} rejected from bucket $bucketIndex (full)")
        }
    }
    
    fun findClosestPeers(target: PeerId, count: Int = 20): Indexed<PeerInfo> {
        val allPeers = mutableListOf<Pair<PeerInfo, Int>>()
        
        for (bucket in buckets) {
            for (peer in bucket.peers) {
                val distance = xorDistance(peer.id, target)
                allPeers.add(peer to distance)
            }
        }
        
        allPeers.sortBy { it.second }
        val closest = allPeers.take(count).map { it.first }
        return closest.size j { closest[it] }
    }
    
    private fun getBucketIndex(peerId: PeerId): Int {
        val distance = xorDistance(localId, peerId)
        return leadingZeros(distance).coerceIn(0, 255)
    }
    
    private fun xorDistance(a: PeerId, b: PeerId): Int {
        // Simplified XOR distance
        var distance = 0
        val minLen = minOf(a.id.a, b.id.a)
        for (i in 0 until minLen) {
            distance += (a.id.b(i) xor b.id.b(i)).toInt()
        }
        return distance
    }
    
    private fun leadingZeros(distance: Int): Int {
        if (distance.z) return 256
        return 32 - distance.countLeadingZeroBits()
    }
    
    private fun minOf(a: Int, b: Int): Int = if (a < b) a else b
}

// Helper functions

private fun sha256(data: Indexed<Byte>): Indexed<Byte> {
    // Simplified SHA-256 stub - would use platform crypto
    val hash = ByteArray(32)
    for (i in 0 until minOf(data.a, 32)) {
        hash[i] = data.b(i)
    }
    return 32 j { hash[it] }
}

private fun base58Encode(bytes: Indexed<Byte>): String {
    // Simplified Base58 encoding
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val sb = StringBuilder()
    
    // Count leading zeros
    var zeros = 0
    for (i in 0 until bytes.a) {
        if (bytes.b(i).z) zeros++ else break
    }
    
    // Convert bytes to base58
    var num = 0L
    for (i in 0 until minOf(bytes.a, 8)) { // Simplified - only handle up to 8 bytes
        num = (num shl 8) or (bytes.b(i).toInt() and 0xFF).toLong()
    }
    
    while (num > 0) {
        val remainder = (num % 58).toInt()
        sb.insert(0, alphabet[remainder])
        num /= 58
    }
    
    // Add leading 1s for zeros
    repeat(zeros) { sb.insert(0, '1') }
    
    return sb.toString()
}

private fun base32Encode(bytes: Indexed<Byte>): String {
    // Simplified Base32 encoding
    val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
    val sb = StringBuilder()
    
    // Process in 5-byte chunks (40 bits -> 8 base32 chars)
    var i = 0
    while (i < bytes.a) {
        val b1 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        val b2 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        val b3 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        val b4 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        val b5 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        
        sb.append(alphabet[(b1 ushr 3) and 0x1F])
        sb.append(alphabet[((b1 and 0x07) shl 2) or ((b2 ushr 6) and 0x03)])
        sb.append(alphabet[(b2 ushr 1) and 0x1F])
        sb.append(alphabet[((b2 and 0x01) shl 4) or ((b3 ushr 4) and 0x0F)])
        sb.append(alphabet[((b3 and 0x0F) shl 1) or ((b4 ushr 7) and 0x01)])
        sb.append(alphabet[(b4 ushr 2) and 0x1F])
        sb.append(alphabet[((b4 and 0x03) shl 3) or ((b5 ushr 5) and 0x07)])
        sb.append(alphabet[b5 and 0x1F])
    }
    
    return sb.toString().trimEnd('a') // Remove padding
}

private fun base64Encode(bytes: Indexed<Byte>): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    
    var i = 0
    while (i < bytes.a) {
        val b1 = bytes.b(i++).toInt() and 0xFF
        val b2 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        val b3 = if (i < bytes.a) bytes.b(i++).toInt() and 0xFF else 0
        
        sb.append(alphabet[(b1 ushr 2) and 0x3F])
        sb.append(alphabet[((b1 and 0x03) shl 4) or ((b2 ushr 4) and 0x0F)])
        sb.append(if (i - 2 < bytes.a) alphabet[((b2 and 0x0F) shl 2) or ((b3 ushr 6) and 0x03)] else '=')
        sb.append(if (i - 1 < bytes.a) alphabet[b3 and 0x3F] else '=')
    }
    
    return sb.toString()
}

private fun encodeVarint(value: Long): Indexed<Byte> {
    val bytes = mutableListOf<Byte>()
    var v = value
    while (v >= 0x80) {
        bytes.add(((v and 0x7F) or 0x80).toByte())
        v = v ushr 7
    }
    bytes.add((v and 0x7F).toByte())
    return bytes.size j { bytes[it] }
}

private fun minOf(a: Int, b: Int): Int = if (a < b) a else b

private fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() }