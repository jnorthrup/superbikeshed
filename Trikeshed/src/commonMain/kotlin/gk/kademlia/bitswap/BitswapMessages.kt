package gk.kademlia.bitswap

import kotlinx.serialization.Serializable

// Based on specs.ipfs.tech/bitswap-protocol/#bitswap-1-2-0-wire-format

@Serializable
data class BitswapMessage(
    val wantlist: Wantlist? = null,
    val payload: List<BlockContainer>? = null,
    val blockPresences: List<BlockPresence>? = null,
    val pendingBytes: Int = 0 // Default to 0, as it's often not present for simple messages
)

@Serializable
data class Wantlist(
    val entries: List<Entry>? = null, // Null if empty, or emptyList() if preferred by encoder settings
    val full: Boolean = false
) {
    @Serializable
    data class Entry(
        val block: CID,
        val priority: Int = 1,
        val cancel: Boolean = false,
        val wantType: WantType = WantType.Block,
        val sendDontHave: Boolean = false
    )

    @Serializable
    enum class WantType {
        Block, // Value 0 in protobuf
        Have   // Value 1 in protobuf
    }
}

@Serializable
data class BlockContainer(
    val prefix: ByteArray,
    val data: ByteArray
) {
    // equals/hashCode for ByteArray fields
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockContainer) return false // Changed from javaClass != other?.javaClass for typical Kotlin style
        if (!prefix.contentEquals(other.prefix)) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = prefix.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable
data class BlockPresence(
    val cid: CID,
    val type: BlockPresenceType
)

@Serializable
enum class BlockPresenceType {
    Have,     // Value 0 in protobuf
    DontHave  // Value 1 in protobuf
}
