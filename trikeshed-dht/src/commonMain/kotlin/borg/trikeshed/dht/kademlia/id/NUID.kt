@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht.kademlia.id


import borg.trikeshed.lib.*
import kotlin.random.Random
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Node Unique Identifier (NUID) for Kademlia DHT
 * Supports variable key lengths and cryptographic hashes
 * Uses TrikeShed Indexed<Byte> for efficient storage
 */
@Serializable
data class NUID(
    @Serializable(with = IndexedByteSerializer::class)
    val bytes: Indexed<Byte>
) {
    /**
     * Size in bytes
     */
    val size: Int get() = bytes.a

    /**
     * Calculate XOR distance to another NUID (fundamental to Kademlia routing)
     */
    fun distanceTo(other: NUID): NUID {
        val maxSize = maxOf(this.size, other.size)
        return NUID(maxSize j { i: Int ->
            val thisByte = if (i < this.size) this.bytes[i] else 0.toByte()
            val otherByte = if (i < other.size) other.bytes[i] else 0.toByte()
            (thisByte.toInt() xor otherByte.toInt()).toByte()
        })
    }

    /**
     * Get common prefix length (number of identical leading bits)
     * Used for k-bucket organization
     */
    fun commonPrefixLength(other: NUID): Int {
        var prefixBits = 0
        val minSize = minOf(this.size, other.size)
        
        for (i in 0 until minSize) {
            val thisByte = this.bytes[i].toInt() and 0xFF
            val otherByte = other.bytes[i].toInt() and 0xFF
            val xor = thisByte xor otherByte
            
            if (xor == 0) {
                prefixBits += 8
            } else {
                // Count leading zeros in XOR result
                prefixBits += when {
                    xor and 0x80 != 0 -> 0
                    xor and 0x40 != 0 -> 1
                    xor and 0x20 != 0 -> 2
                    xor and 0x10 != 0 -> 3
                    xor and 0x08 != 0 -> 4
                    xor and 0x04 != 0 -> 5
                    xor and 0x02 != 0 -> 6
                    else -> 7
                }
                break
            }
        }
        
        return prefixBits
    }

    /**
     * Convert to hex string for debugging
     */
    fun toHex(): String {
        return bytes.play.joinToString("") { 
            (it.toInt() and 0xFF).toString(16).padStart(2, '0') 
        }
    }

    /**
     * Convert to Base58 string (Bitcoin-style)
     */
    fun toBase58(): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val bytes = ByteArray(this.size) { i -> this.bytes[i] }
        
        // Count leading zeros
        var leadingZeros = 0
        for (b in bytes) {
            if (b == 0.toByte()) leadingZeros++
            else break
        }
        
        // Simple base58 encoding without BigInteger (common code)
        if (bytes.all { it == 0.toByte() }) {
            return "1".repeat(bytes.size)
        }
        
        val result = mutableListOf<Char>()
        var value = 0L
        for (byte in bytes) {
            value = (value * 256) + (byte.toInt() and 0xFF)
        }
        
        while (value > 0) {
            result.add(alphabet[(value % 58).toInt()])
            value /= 58
        }
        
        // Add leading '1's for leading zeros
        repeat(leadingZeros) { result.add('1') }
        
        return result.reversed().joinToString("")
    }

    override fun toString(): String = toBase58()

    // Extension to convert NUID to ByteArray
    fun toByteArray(): ByteArray = ByteArray(size) { i -> bytes[i] }

    companion object {
        /**
         * Generate random NUID with specified byte length
         */
        fun random(bytes: Int = 32): NUID {
            return NUID(bytes j { Random.nextBytes(1)[0] })
        }

        /**
         * Create NUID from SHA-256 hash
         */
        fun fromSHA256(data: ByteArray): NUID {
            // Placeholder - in real implementation would use platform crypto
            val hash = data.fold(0L) { acc, byte -> 
                ((acc shl 8) + (byte.toInt() and 0xFF)) and 0xFFFFFFFFL 
            }
            return NUID(32 j { i: Int -> 
                ((hash shr (i * 8)) and 0xFF).toByte()
            })
        }

        /**
         * Create NUID from hex string
         */
        fun fromHex(hex: String): NUID {
            val cleanHex = hex.replace("\\s".toRegex(), "")
            require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
            
            val bytes = cleanHex.chunked(2).map { 
                it.toInt(16).toByte() 
            }
            
            return NUID(bytes.size j { i: Int -> bytes[i] })
        }

        /**
         * Create NUID from raw byte array
         */
        fun fromBytes(bytes: ByteArray): NUID {
            return NUID(bytes.size j { i: Int -> bytes[i] })
        }

        /**
         * Zero NUID for testing
         */
        val ZERO = NUID(32 j { 0.toByte() })
        
        /**
         * Max NUID (all 0xFF bytes)
         */
        val MAX = NUID(32 j { 0xFF.toByte() })
    }
}

/**
 * Custom serializer for Indexed<Byte>
 */
object IndexedByteSerializer : KSerializer<Indexed<Byte>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IndexedByte", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Indexed<Byte>) {
        val bytes = ByteArray(value.a) { i -> value[i] }
        encoder.encodeString(bytes.toHexString())
    }
    
    override fun deserialize(decoder: Decoder): Indexed<Byte> {
        val hex = decoder.decodeString()
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.size j { i: Int -> bytes[i] }
    }
}

// Extension function to convert ByteArray to hex string
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }