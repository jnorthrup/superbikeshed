# Gossip Distribution Service Features

This document outlines the features of the Gossip Distribution Service within Trikeshed, designed for disseminating information across the network.

## 1. Message Publication (Gossip Submission)

*   **Submission Process:** Agents (applications or services running on a node) submit messages they wish to disseminate to their local Gossip Service instance.
*   **Role of Local DHT Node:** The agent's local DHT node acts as the entry point into the gossip network. It takes the submitted message and initiates the gossip propagation process.
*   **Basic Message Structure:**
    *   **`content`**: The actual data/payload of the message.
    *   **`timestamp`**: A timestamp indicating when the message was created, used for ordering and potentially for expiry.
    *   **`publisherId`**: The Node ID of the agent publishing the message. This allows for source identification and potentially for filtering or reputation.
    *   **(Optional) `targetSubnet(s)`**: An optional field to suggest or restrict the propagation of the gossip to specific concentric subnet(s). If not specified, the message might be gossiped more broadly or within the publisher's own subnet(s).

## 2. Subscription and Message Reception

*   **Expressing Interest:** Agents need a way to specify what types of gossip messages they are interested in receiving. This can be achieved through:
    *   **Implicit Subscription:** Agents might automatically receive gossip relevant to the concentric subnet(s) they are members of.
    *   **Explicit Subscription:** Agents could subscribe to specific topics, keywords, or messages originating from particular `publisherId`s or subnets. The exact mechanism for topic management and subscription needs further definition.
*   **Message Delivery:** The local Gossip Service, upon receiving a new gossip message (either directly from a local agent or from a peer), checks it against the subscriptions of its local agents. If there's a match, the message (or a notification) is delivered to the interested agent(s).

## 3. Gossip Propagation

The goal is to efficiently spread messages to interested parties without flooding the network.

*   **Intra-Subnet Gossip:**
    *   **Mechanism:** When a message is intended for or originates within a specific concentric subnet, it should primarily be propagated to other members of that same subnet.
    *   **Peer Selection:** This can be achieved by the gossiping node selecting a random subset of its known peers *within that subnet* to forward the message to. The DHT's routing table, with its subnet-aware features, can be leveraged to find peers belonging to the target subnet.
*   **Inter-Subnet Gossip:**
    *   **Mechanism:** For messages that need to cross subnet boundaries (e.g., a globally relevant announcement or a message explicitly targeted at multiple subnets).
    *   **Approaches:**
        *   **Gateway Nodes (Conceptual):** Certain nodes might act as gateways, explicitly configured to bridge gossip between specific subnets.
        *   **Probabilistic Forwarding:** Nodes near the "edge" of a subnet (in terms of DHT routing to nodes in other subnets) might probabilistically forward messages to peers in adjacent or target subnets.
        *   **DHT-assisted Discovery:** A node wishing to gossip to another subnet could use `FIND_NODE` to discover nodes in that target subnet and then directly gossip to them.
*   **Redundancy Control & Storm Prevention:**
    *   **Message Caches/Seen Lists:** Each node maintains a cache of recently seen message identifiers (e.g., hashes of messages). If a node receives a message it has already processed, it refrains from re-propagating it.
    *   **Time-To-Live (TTL) / Hop Limits:** Messages may carry a TTL or a hop count, which is decremented each time it's forwarded. Once it reaches zero, the message is no longer propagated. This prevents messages from circulating indefinitely.
    *   **Fanout Control:** Nodes will typically gossip a new message to a small, randomly selected set of peers (e.g., k peers) rather than all their peers, to control bandwidth usage.

## 4. Message Persistence and Retrieval (Conceptual)

While the primary role of the gossip service is live dissemination, there's a need to consider how messages (or their existence) are remembered.

*   **Short-Term Caching:** Nodes will cache messages they've seen or are actively gossiping for a limited time to aid propagation and avoid immediate re-fetching.
*   **Anchoring in DHT:** Hashes or identifiers of important gossip messages could be stored in the DHT (e.g., using `PUT(message_hash, publisher_nodeId)`). This allows nodes to later verify the existence of a message or find its original publisher.
*   **Persistent Storage (External):** For long-term storage of actual message *content*, especially for large messages, external systems are preferred. As mentioned in the original issue:
    *   **IPFS:** Could be used for content-addressable storage of message payloads. The gossip message would then only carry the IPFS CID (Content Identifier).
    *   **CouchDB (or similar K-V store):** Could serve as a persistent database for message content, indexed by message IDs or hashes.
    *   The gossip layer's primary responsibility remains the *discovery and propagation* of new messages or updates, with links (like CIDs or database keys) to the actual content stored elsewhere if needed.

## 5. Message Serialization

*   **Format:** All gossip messages exchanged over the network (including metadata and content) should be serialized using an efficient binary format, akin to `prautobeans`. This is crucial for minimizing network bandwidth consumption and reducing the computational overhead of serialization/deserialization. The same serialization library used for DHT operations should be reused here for consistency.
