# Agent-Bus Interaction with DHT and Gossip Services

This document outlines how agents, presumably running within a `Nexus`-like bus architecture, would interact with the underlying DHT and Gossip services.

## 1. Agent Service Interface (Conceptual Kotlin-like interfaces)

Agents would interact with the networking services through a well-defined interface. This interface abstracts the complexities of direct network communication and DHT/gossip protocols.

```kotlin
// Defined in message_formats_and_serialization.md
// data class NodeInfo(val nodeId: ByteArray, val ipAddress: String, val port: Int, val subnets: List<String>)
// data class GossipMessage(val messageId: ByteArray, val publisherId: ByteArray, val targetSubnets: List<String>, val payload: ByteArray, val timestamp: Long, val signature: ByteArray)

/**
 * Represents a handle to an active subscription, allowing for unsubscribing.
 */
interface SubscriptionHandle {
    fun unsubscribe()
}

/**
 * Provides agents with access to DHT and Gossip functionalities.
 */
interface AgentNetworkingService {
    // --- DHT Operations ---

    /**
     * Stores a key-value pair in the DHT.
     * The operation is routed to the nodes closest to the key.
     * @param key The key under which the value is stored.
     * @param value The value to store.
     * @return True if the PUT operation was successfully initiated, false otherwise.
     *         Note: Success here usually means the request was sent; confirmation of storage
     *         might require a separate mechanism or be probabilistic.
     */
    suspend fun put(key: ByteArray, value: ByteArray): Boolean

    /**
     * Retrieves a value from the DHT by its key.
     * The lookup is routed through the DHT to find nodes responsible for the key.
     * @param key The key of the value to retrieve.
     * @return The value as a ByteArray if found, or null otherwise.
     */
    suspend fun get(key: ByteArray): ByteArray?

    /**
     * Finds nodes in the DHT that are closest to a given Node ID.
     * Useful for understanding network topology or connecting to specific peers.
     * @param nodeId The Node ID to find neighbors for.
     * @return A list of NodeInfo objects representing the closest known nodes.
     */
    suspend fun findNode(nodeId: ByteArray): List<NodeInfo>

    // --- Gossip Operations ---

    /**
     * Publishes a message to the gossip network.
     * The message will be disseminated to other nodes, potentially guided by targetSubnets.
     * @param payload The content of the message to be gossiped.
     * @param targetSubnets Optional list of subnet IDs to guide initial propagation.
     * @return True if the gossip message was successfully submitted for propagation, false otherwise.
     */
    suspend fun publishGossip(payload: ByteArray, targetSubnets: List<String> = emptyList()): Boolean

    /**
     * Subscribes to gossip messages. The handler will be invoked when a relevant message is received.
     * Relevance can be based on implicit subnet membership or explicit subscription criteria (TBD).
     * @param handler A lambda function that processes incoming GossipMessage objects.
     * @return A SubscriptionHandle that can be used to unsubscribe later.
     */
    fun subscribeToGossip(handler: (message: GossipMessage) -> Unit): SubscriptionHandle

    // Alternative for gossip reception using Kotlin Flows (could co-exist or replace handler):
    // /**
    //  * Returns a Flow that emits GossipMessage objects as they are received.
    //  * This allows for reactive processing of gossip.
    //  * @return A Flow of GossipMessage.
    //  */
    // fun getGossipFlow(): kotlinx.coroutines.flow.Flow<GossipMessage>
}
```

## 2. Interaction with DHT

Agents utilize the DHT for various distributed application needs:

*   **`put(key, value)`**:
    *   Agents can store application-specific data, metadata, or pointers to larger data (e.g., an IPFS CID) in the DHT.
    *   Examples: Storing user profiles, service discovery information, or content availability records.
*   **`get(key)`**:
    *   Agents retrieve previously stored data by its key.
    *   This is the primary way agents look up information in the distributed store.
*   **`findNode(nodeId)`**:
    *   While less common for typical application logic, agents might use this to:
        *   Discover peers for direct, application-level communication outside of gossip.
        *   Gain insights into network proximity for locality-aware operations.
        *   Bootstrap connections to specific services identified by a Node ID.

## 3. Interaction with Gossip Service

Agents use the gossip service for disseminating and receiving timely information:

*   **`publishGossip(payload, targetSubnets)`**:
    *   Agents publish messages they want to share with a wider audience or specific sub-communities.
    *   Examples: Real-time updates, event notifications, presence information, or discovery of new data/services.
    *   The `targetSubnets` parameter allows an agent to give a hint to the gossip service about the intended initial reach of the message, potentially focusing propagation efforts on relevant parts of the network.
*   **`subscribeToGossip(handler)` or `getGossipFlow()`**:
    *   Agents express their interest in receiving certain types of gossip.
    *   Subscription could be implicit (e.g., receiving all gossip within the agent's own subnet(s)) or based on more explicit criteria (e.g., topics, keywords, originating publisher, if such filtering mechanisms are implemented).
    *   The `handler` function (or the collector of the `Flow`) processes incoming `GossipMessage` objects, allowing the agent to react to new information.

## 4. Agent Identity and Context

The agent's environment and identity play a crucial role:

*   **Implicit Identity:** When an agent calls `put` or `publishGossip`, its own Node ID (associated with the local Trikeshed node it's running on) would typically be implicitly included as the `originNodeId` in DHT messages or `publisherId` in gossip messages. This allows for accountability and source tracking.
*   **Subnet Membership:** An agent's membership in one or more concentric subnets (a property of its underlying Trikeshed node) would be a key factor in:
    *   **Gossip Filtering:** The gossip service might automatically filter incoming gossip, delivering only messages relevant to the agent's subnets (unless subscribed more broadly).
    *   **Gossip Propagation:** Messages published by an agent might preferentially propagate within its own subnet(s) by default.

## 5. Underlying Mechanism (Brief)

*   **Abstraction Layer:** The `AgentNetworkingService` interface serves as an abstraction layer. Agents interact with this service without needing to know the intricate details of DHT routing algorithms, gossip propagation protocols, or QUIC stream management.
*   **Nexus Integration:** This service would likely be provided to agents by the `Nexus` (or equivalent agent bus/framework) as a standard capability. The `Nexus` would manage the lifecycle of the underlying DHT client and gossip service components.
*   **Internal Operations:** Internally, implementations of `AgentNetworkingService` would translate the agent's requests into specific DHT and gossip protocol messages (as defined in `message_formats_and_serialization.md`), manage network connections (likely via the QUIC implementation in `Trikeshed/src/commonMain/kotlin/borg/trikeshed/net/quic/`), and handle message serialization/deserialization.
