package gk.kademlia.messages

// Removed direct NUID and SubnetRoute imports as they are now represented by serializable forms.
// import gk.kademlia.id.NUID
// import gk.kademlia.include.SubnetRoute
import kotlinx.serialization.Serializable

// For KademliaPayload to be used with polymorphic serialization,
// all implementing classes must be registered or it must be a sealed interface.
@Serializable
sealed interface KademliaPayload

@Serializable
data class PingRequest(val uniqueId: String) : KademliaPayload

@Serializable
data class PongResponse(val uniqueId: String) : KademliaPayload

@Serializable
data class SerializableNUID(
    val idBytes: ByteArray,
    val netmaskBits: Int
) {
    // equals and hashCode for ByteArray members are important for data class correctness.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SerializableNUID
        if (!idBytes.contentEquals(other.idBytes)) return false
        if (netmaskBits != other.netmaskBits) return false
        return true
    }
    override fun hashCode(): Int {
        var result = idBytes.contentHashCode()
        result = 31 * result + netmaskBits
        return result
    }
}

@Serializable
data class SerializableSubnetRoute(
    val nuid: SerializableNUID,
    val address: String,
    val subnetId: String,
    val lastSeen: Long,
    val failedPings: Int
)
// No custom equals/hashCode needed for SerializableSubnetRoute if SerializableNUID has them.

// FIND_NODE
/**
 * @param targetNUIDProto Serialized representation of the NUID being sought.
 */
@Serializable
data class FindNodeRequest(val targetNUIDProto: ByteArray) : KademliaPayload {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FindNodeRequest
        if (!targetNUIDProto.contentEquals(other.targetNUIDProto)) return false
        return true
    }
    override fun hashCode(): Int {
        return targetNUIDProto.contentHashCode()
    }
}

/**
 * NodesResponse now uses SerializableSubnetRoute.
 * The generic TNum is removed as the specific NUID type information is
 * handled during conversion to/from SerializableNUID.
 */
@Serializable
data class NodesResponse(val nodes: List<SerializableSubnetRoute>) : KademliaPayload

@Serializable
data class BitswapEnvelope(
    val sourcePeerIdString: String, // The original sender of the Bitswap message
    val bitswapMessageBytes: ByteArray // Serialized BitswapMessage
) : KademliaPayload {
    // equals/hashCode for ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitswapEnvelope) return false // Changed from javaClass check for KClass
        if (sourcePeerIdString != other.sourcePeerIdString) return false
        if (!bitswapMessageBytes.contentEquals(other.bitswapMessageBytes)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = sourcePeerIdString.hashCode()
        result = 31 * result + bitswapMessageBytes.contentHashCode()
        return result
    }
}

// Example STORE message (can be uncommented and developed later)
/*
@Serializable
data class StoreRequest(val keyProto: ByteArray, val value: ByteArray) : KademliaPayload {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoreRequest
        if (!keyProto.contentEquals(other.keyProto)) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = keyProto.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}
@Serializable
data class StoreResponse(val success: Boolean) : KademliaPayload
*/

// Example FIND_VALUE message (can be uncommented and developed later)
/*
@Serializable
data class FindValueRequest(val keyProto: ByteArray) : KademliaPayload {
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FindValueRequest
        if (!keyProto.contentEquals(other.keyProto)) return false
        return true
    }
    override fun hashCode(): Int {
        return keyProto.contentHashCode()
    }
}
@Serializable
data class ValueResponse(val value: ByteArray?) : KademliaPayload { // value can be null if not found
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ValueResponse
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false
        return true
    }
    override fun hashCode(): Int {
        return value?.contentHashCode() ?: 0
    }
}
*/
