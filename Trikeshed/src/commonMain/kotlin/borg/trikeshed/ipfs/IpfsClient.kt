package borg.trikeshed.ipfs

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.UVarint
import borg.trikeshed.lib.emptyIndexed
import borg.trikeshed.lib.plus
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.crypto.HashUtils
import borg.trikeshed.multiformats.HashType
import borg.trikeshed.multiformats.ContentType
import borg.trikeshed.multiformats.base.Base16 // Using Base16 for now for CID string
// import borg.trikeshed.multiformats.base.Base32 // Preferable for CIDv1 strings

/**
 * Represents a Multihash, a self-describing hash digest.
 * @param type The type of hash function used (e.g., SHA2_256).
 * @param digest The raw hash digest.
 */
data class Multihash(
    val type: HashType,
    val digest: Indexed<Byte>
) {
    init {
        if (type.defaultSizeInBytes > 0 && digest.size != type.defaultSizeInBytes) {
            // For SHA2-256, it must be 32 bytes.
            require(digest.size == type.defaultSizeInBytes) {
                "Digest size ${digest.size} does not match expected size ${type.defaultSizeInBytes} for hash type ${type.name}"
            }
        }
    }

    /**
     * Encodes the Multihash into its binary representation (varint(code) + varint(length) + digest).
     * @return An Indexed<Byte> containing the binary representation.
     */
    fun toBytes(): Indexed<Byte> {
        val codeVarint = UVarint.encode(type.code.toLong())
        val lengthVarint = UVarint.encode(digest.size.toLong())
        return codeVarint + lengthVarint + digest // Relies on plus operator for Indexed<Byte>
    }

    override fun toString(): String {
        return "Multihash(type=${type.name}, digest=${Base16.encode(digest)})"
    }

    companion object {
        /**
         * Decodes a Multihash from its binary representation.
         * @param bytes The Indexed<Byte> containing the binary Multihash.
         * @return The decoded Multihash.
         * @throws IllegalArgumentException if the bytes are malformed.
         */
        fun fromBytes(bytes: Indexed<Byte>): Multihash {
            val (codeVal, codeLen) = UVarint.decode(bytes)
            val hashType = HashType.entries.find { it.code == codeVal.toInt() }
                ?: throw IllegalArgumentException("Unknown multihash code: $codeVal")

            val (digestLenVal, digestLenLen) = UVarint.decode(bytes, codeLen)
            val digestLen = digestLenVal.toInt()

            if (hashType.defaultSizeInBytes > 0 && digestLen != hashType.defaultSizeInBytes) {
                require(digestLen == hashType.defaultSizeInBytes) {
                     "Decoded digest length $digestLen does not match expected size ${hashType.defaultSizeInBytes} for hash type ${hashType.name}"
                }
            }

            val digestOffset = codeLen + digestLenLen
            if (digestOffset + digestLen > bytes.size) {
                throw IllegalArgumentException("Multihash digest bytes underflow. Expected $digestLen bytes, got ${bytes.size - digestOffset}")
            }
            val digest = bytes.slice(digestOffset until digestOffset + digestLen)
            return Multihash(hashType, digest)
        }
    }
}

/**
 * Represents a Content Identifier (CID).
 * @property version The CID version (e.g., 0 or 1).
 * @property codec The content type codec (e.g., RAW, DAG_PB).
 * @property multihash The Multihash of the content.
 */
data class CID(
    val version: Int,
    val codec: ContentType,
    val multihash: Multihash
) {
    init {
        when (version) {
            0 -> {
                require(codec == ContentType.DAG_PB) { "CIDv0 must use DAG_PB codec." }
                require(multihash.type == HashType.SHA2_256 && multihash.digest.size == 32) {
                    "CIDv0 multihash must be SHA2-256 with 32-byte digest."
                }
            }
            1 -> {
                // No specific restrictions other than valid components for v1
            }
            else -> throw IllegalArgumentException("Unsupported CID version: $version")
        }
    }

    fun toBytes(): Indexed<Byte> {
        return when (version) {
            0 -> multihash.toBytes()
            1 -> {
                val versionByte = byteArrayOf(version.toByte()).toIndexed()
                val codecVarint = UVarint.encode(codec.code.toLong())
                versionByte + codecVarint + multihash.toBytes()
            }
            else -> throw IllegalStateException("Unsupported CID version for toBytes: $version")
        }
    }

    override fun toString(): String {
        return when (version) {
            0 -> {
                // TODO: Implement Base58btc for proper CIDv0 string.
                // Current placeholder is NOT a valid CIDv0 string.
                "Qm" + Base16.encode(multihash.digest)
            }
            1 -> {
                // TODO: Implement Base32 and Multibase prefix for proper CIDv1 string.
                // Current placeholder is a temporary, non-standard representation.
                "v1b16-" + Base16.encode(toBytes())
            }
            else -> throw IllegalStateException("Unsupported CID version for toString: $version")
        }
    }

    companion object {
        fun v1(codec: ContentType, multihash: Multihash): CID {
            return CID(version = 1, codec = codec, multihash = multihash)
        }

        fun v0(sha256Digest: Indexed<Byte>): CID {
            require(sha256Digest.size == 32) { "CIDv0 digest must be 32 bytes for SHA2-256" }
            val mh = Multihash(HashType.SHA2_256, sha256Digest)
            return CID(version = 0, codec = ContentType.DAG_PB, multihash = mh)
        }

        // TODO: Implement fromString(str: String) and fromBytes(bytes: Indexed<Byte>)
    }
}

data class PeerId(val id: Indexed<Byte>)

class IpfsStorage {
    internal val blocks = mutableMapOf<String, IpfsBlock>()

    fun putBlock(block: IpfsBlock) {
        blocks[block.cid.toString()] = block
    }

    fun getBlock(cid: CID): IpfsBlock? = blocks[cid.toString()]
}

data class IpfsBlock(
    val cid: CID,
    val data: Indexed<Byte>,
    val links: Indexed<IpfsLink> = emptyIndexed()
)

data class IpfsLink(
    val name: String,
    val cid: CID,
    val size: Long
)

data class IpfsStoreResult(val hash: String)
data class IpfsRetrieveResult(val content: Indexed<Byte>)
class IpfsConfig


/**
 * Minimal placeholder IPFS client.
 * This client is NOT yet functional for network operations.
 * It primarily demonstrates CID generation and local in-memory storage.
 */
class IpfsClient(
    val localPeerId: PeerId, // Placeholder
    val quicEngine: Any?,      // Placeholder for future networking
    val storage: IpfsStorage,
    val config: IpfsConfig = IpfsConfig()
) {
    /**
     * Adds data to the local IPFS store (in-memory).
     * Computes a CIDv1 with SHA2-256 hash and RAW codec.
     * @param data The data to add.
     * @return The CID of the added data.
     */
    suspend fun add(data: Indexed<Byte>): CID {
        val digest = HashUtils.sha256(data) // Uses the KMP SHA256 hasher
        val multihash = Multihash(HashType.SHA2_256, digest)
        val cid = CID.v1(ContentType.RAW, multihash) // Create a CIDv1

        val block = IpfsBlock(cid, data)
        storage.putBlock(block)

        return cid
    }

    /**
     * Stores data and returns a result object with the CID string.
     */
    suspend fun store(data: Indexed<Byte>): IpfsStoreResult {
        val cid = add(data)
        return IpfsStoreResult(hash = cid.toString())
    }

    /**
     * Retrieves data by its CID string from the local in-memory store.
     * TODO: This needs to parse the CID string properly once CID.fromString is implemented.
     */
    suspend fun retrieve(cidString: String): IpfsRetrieveResult? {
        // Current storage keys by cid.toString(). This lookup is temporary.
        // A proper implementation would parse cidString to a CID object first.
        val foundBlock = storage.blocks[cidString]
        return if (foundBlock != null) {
            IpfsRetrieveResult(content = foundBlock.data)
        } else {
            null
        }
    }
    // computeSimpleHash removed
}

// Helper slice, assuming CoreTypes.kt might not have this exact signature or if preferred locally.
// CoreTypes.kt has get(range: IntRange) which is similar.
internal fun Indexed<Byte>.slice(startIndexInclusive: Int until endIndexExclusive: Int): Indexed<Byte> {
    val start = startIndexInclusive
    val end = endIndexExclusive
    if (start < 0 || end > this.size || start > end) throw IndexOutOfBoundsException("Slice out of bounds: start=$start, end=$end, size=${this.size}")
    val count = end - start
    if (count == 0) return emptyIndexed()
    return count j { idx -> this[start + idx] } // Uses infix j from CoreTypes
}