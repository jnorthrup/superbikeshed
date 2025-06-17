package gk.kademlia.bitswap

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// A simplified CID representation for now.
// A full CID includes version, codec, multihash.
// Using a simple ByteArray wrapper for the multihash part.
@Serializable
@JvmInline
value class CID(val bytes: ByteArray) { // Assuming bytes are the multihash
    override fun toString(): String = "CID(${bytes.joinToString("") { "%02x".format(it) }})" // Simple hex string

    // Explicit equals and hashCode for ByteArray content-based equality,
    // which is good practice even for JvmInline value classes when used in collections.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CID) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    companion object {
        fun fromString(str: String): CID { // Expects a simple hex string for dummy purposes
            if (str.length % 2 != 0) throw IllegalArgumentException("Hex string must have an even number of characters")
            val cleanStr = if (str.startsWith("CID(") && str.endsWith(")")) {
                str.substring(4, str.length -1)
            } else {
                str
            }
            if (cleanStr.isEmpty() && str.isNotEmpty()) { // e.g. CID() input
                 return CID(ByteArray(0))
            }


            val bytes = ByteArray(cleanStr.length / 2)
            try {
                for (i in cleanStr.indices step 2) {
                    bytes[i / 2] = ((Character.digit(cleanStr[i], 16) shl 4) + Character.digit(cleanStr[i + 1], 16)).toByte()
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid hex string format: $str", e)
            }
            return CID(bytes)
        }
    }
}
