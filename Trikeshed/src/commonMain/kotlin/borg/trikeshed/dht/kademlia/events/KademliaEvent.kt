@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht.kademlia.events


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID

/**
 * Base interface for all Kademlia DHT events
 * Uses TrikeShed patterns with Indexed types and variable key lengths
 */
sealed interface KademliaEvent {
    val timestamp: Long
    val messageId: NUID
    val sourceNodeId: NUID
}

/**
 * PING - Basic connectivity check
 */
data class PingEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val targetNodeId: NUID,
    val subnets: Indexed<String> = 0 j { "" }
) : KademliaEvent

/**
 * PONG - Response to PING
 */
data class PongEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val respondingToMessageId: NUID,
    val respondingToNodeId: NUID,
    val activeSubnets: Indexed<String> = 0 j { "" },
    val routingTableSize: Int = 0
) : KademliaEvent

/**
 * FIND_NODE - Request for nodes closest to target
 */
data class FindNodeEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val targetNodeId: NUID,
    val maxResults: Int = 20,
    val preferredSubnets: Indexed<String> = 0 j { "" }
) : KademliaEvent

/**
 * FOUND_NODES - Response to FIND_NODE
 */
data class FoundNodesEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val respondingToMessageId: NUID,
    val targetNodeId: NUID,
    val nodes: Indexed<NodeInfo>
) : KademliaEvent

/**
 * STORE - Request to store key-value pair
 */
data class StoreEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val key: Indexed<Byte>,
    val value: Indexed<Byte>,
    val ttl: Long = 0,
    val replicationFactor: Int = 3
) : KademliaEvent

/**
 * STORE_RESPONSE - Acknowledgment of store operation
 */
data class StoreResponseEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val respondingToMessageId: NUID,
    val key: Indexed<Byte>,
    val success: Boolean,
    val errorCode: Int = 0
) : KademliaEvent

/**
 * FIND_VALUE - Request to find value by key
 */
data class FindValueEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val key: Indexed<Byte>,
    val maxHops: Int = 20
) : KademliaEvent

/**
 * FOUND_VALUE - Response with requested value
 */
data class FoundValueEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val respondingToMessageId: NUID,
    val key: Indexed<Byte>,
    val value: Indexed<Byte>,
    val providingNodes: Indexed<NodeInfo>
) : KademliaEvent

/**
 * JOIN_REQUEST - Request to join the DHT network
 */
data class JoinRequestEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val proposedNodeId: NUID,
    val publicKey: Indexed<Byte> = 0 j { 0.toByte() },
    val formerNodeId: NUID? = null
) : KademliaEvent

/**
 * JOIN_RESPONSE - Response to join request
 */
data class JoinResponseEvent(
    override val timestamp: Long,
    override val messageId: NUID,
    override val sourceNodeId: NUID,
    val respondingToMessageId: NUID,
    val accepted: Boolean,
    val message: String,
    val knownNodes: Indexed<NodeInfo> = 0 j { NodeInfo(NUID.ZERO, "", 0, 0 j { "" }) }
) : KademliaEvent

/**
 * Node information for DHT operations
 */
data class NodeInfo(
    val nodeId: NUID,
    val ipAddress: String,
    val port: Int,
    val subnets: Indexed<String>,
    val publicKey: Indexed<Byte> = 0 j { 0.toByte() },
    val lastSeen: Long = 0,
    val reliability: Double = 1.0
)