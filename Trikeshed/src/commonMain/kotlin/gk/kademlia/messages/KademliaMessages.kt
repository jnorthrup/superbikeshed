package gk.kademlia.messages

import gk.kademlia.id.NUID // NUID might not be directly used in these data classes if we use ByteArray for NUIDs
import gk.kademlia.include.SubnetRoute // SubnetRoute is used in NodesResponse

// Base marker interface for Kademlia payloads
interface KademliaPayload

// PING
data class PingRequest(val uniqueId: String) : KademliaPayload // uniqueId to match request/response
data class PongResponse(val uniqueId: String) : KademliaPayload

// FIND_NODE
/**
 * @param targetNUIDProto Serialized representation of the NUID being sought.
 *        Using ByteArray for robustness in messaging, actual NUID<TNum> conversion
 *        will be handled by codec/agent logic.
 */
data class FindNodeRequest(val targetNUIDProto: ByteArray) : KademliaPayload {
    // equals and hashCode for ByteArray members are good practice if this class is used in sets/maps.
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
 * @param nodes List of SubnetRoutes. SubnetRoute itself contains NUID<TNum>.
 *        The serialization of SubnetRoute (and NUID within it) needs to be handled
 *        by the chosen serialization library.
 */
data class NodesResponse<TNum : Comparable<TNum>>(val nodes: List<SubnetRoute<TNum>>) : KademliaPayload
// Note: For NodesResponse to be easily serializable with kotlinx.serialization,
// SubnetRoute and NUID would need to be @Serializable or have custom serializers.
// This definition assumes that will be handled.

// Example STORE message (can be uncommented and developed later)
/*
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
data class StoreResponse(val success: Boolean) : KademliaPayload
*/

// Example FIND_VALUE message (can be uncommented and developed later)
/*
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
data class ValueResponse(val value: ByteArray?) : KademliaPayload {
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
