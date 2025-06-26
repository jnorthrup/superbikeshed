package borg.trikeshed.dht.agent

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.events.NodeInfo
import borg.trikeshed.dht.kademlia.id.NUID

/**
 * Provides agents with access to DHT and Gossip functionalities
 * Restored from original agent bus implementation
 */
interface AgentNetworkingService {
    
    // DHT Operations
    
    /**
     * Stores a key-value pair in the DHT
     */
    suspend fun put(key: Indexed<Byte>, value: Indexed<Byte>): Boolean

    /**
     * Retrieves a value from the DHT by its key
     */
    suspend fun get(key: Indexed<Byte>): Indexed<Byte>?

    /**
     * Finds nodes in the DHT that are closest to a given Node ID
     */
    suspend fun findNode(nodeId: NUID): Indexed<NodeInfo>

    // Gossip Operations
    
    /**
     * Publishes a message to the gossip network
     */
    suspend fun publishGossip(payload: Indexed<Byte>, targetSubnets: Indexed<String> = 0 j { "" }): Boolean

    /**
     * Subscribes to gossip messages
     */
    fun subscribeToGossip(handler: (message: GossipMessage) -> Unit): SubscriptionHandle
}

/**
 * Represents a handle to an active subscription
 */
interface SubscriptionHandle {
    fun unsubscribe()
}

/**
 * Gossip message structure
 */
data class GossipMessage(
    val messageId: NUID,
    val publisherId: NUID,
    val targetSubnets: Indexed<String>,
    val payload: Indexed<Byte>,
    val timestamp: Long,
    val signature: Indexed<Byte> = 0 j { 0.toByte() }
)