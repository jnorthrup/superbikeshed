# Security and Network Considerations

This document outlines key security and network considerations for the DHT and Gossip services in Trikeshed.

## 1. Gossip Message Security

Ensuring the authenticity, integrity, and responsible use of the gossip network is crucial.

*   **Message Signing:**
    *   **Requirement:** All `GOSSIP_MESSAGE` instances must be digitally signed by the `publisherId`'s private key. The signature is a mandatory part of the message structure.
    *   **Verification:** Upon receiving a gossip message, nodes MUST verify the signature using the publisher's public key (which can be derived from its Node ID if Node IDs are hashes of public keys). This confirms the message originated from the claimed publisher and has not been tampered with in transit.
    *   **Unsigned Messages:** Messages with invalid or missing signatures should be discarded and not propagated.

*   **Spam Resistance (High-Level Strategies):**
    *   **Rate Limiting:** Nodes should implement rate limiting on the number of messages they process and propagate from any single peer or `publisherId` over a given time window. This helps mitigate simple flooding attacks.
    *   **Reputation System (Future Consideration):** A more advanced approach could involve a decentralized reputation system. Nodes that consistently contribute valid and useful gossip could have their messages prioritized or be more trusted. Conversely, nodes identified as sources of spam could be deprioritized or temporarily ignored.
    *   **Proof-of-Work (PoW) / Resource Expenditure (Advanced):** For certain types of widely broadcasted gossip, requiring a small PoW or other resource expenditure (e.g., token staking, if applicable) to publish could deter spam. This is a more complex feature and likely out of scope for an initial "remedial minimum" but is a known technique.
    *   **Seen Message Cache:** The basic mechanism of not re-propagating already seen messages (identified by `messageId`) inherently limits the impact of a single spam message being endlessly echoed.

*   **Encryption (Optional/Advanced):**
    *   **Default:** Gossip messages are generally assumed to be public within their propagation scope.
    *   **Targeted Encryption:** If specific gossip messages need to be confidential and intended only for a subset of agents (e.g., within a particular secure subnet), this would require:
        *   A separate key management system for distributing symmetric or asymmetric encryption keys to the authorized group.
        *   The `payload` of the `GOSSIP_MESSAGE` would be encrypted.
        *   This is an advanced feature beyond baseline gossip functionality.

## 2. DHT Operation Security

Maintaining the integrity of the DHT's routing and stored data is vital.

*   **Signed Records (for `PUT` Operations):**
    *   **Data Integrity/Authenticity:** When a `PUT(key, value)` operation occurs, it's highly desirable that the `value` itself is signed by the entity semantically responsible for that data. This signature should be stored alongside the value. Consumers fetching the value can then verify its authenticity and integrity, regardless of which DHT node stored it.
    *   **Alternatively:** The `PUT_MESSAGE` itself could be signed by the `originNodeId`, attesting that this node requested the storage. This doesn't protect against a malicious node storing bad data but does provide some accountability for the request.

*   **Preventing Routing Table Poisoning (High-Level):**
    *   Kademlia and similar DHTs are susceptible to routing table poisoning attacks where malicious nodes provide false information to corrupt routing.
    *   **Countermeasures:**
        *   **Round-Trip Verification:** Before adding a new node to a k-bucket, a node should attempt to communicate with it (e.g., send a PING and await a PONG). This confirms reachability and liveness.
        *   **Least Recently Seen Eviction:** When a k-bucket is full, Kademlia typically evicts the least recently seen node if a new, verified node needs to be added, making it harder for stale or malicious entries to persist.
        *   **Multiple Paths/Queries:** Important lookups can be made more robust by querying multiple nodes in parallel or iteratively, making it harder for a small number of malicious nodes to derail the process.

## 3. Concentric Subnet Security

Subnets introduce both opportunities and challenges for security.

*   **Access Control:**
    *   **Joining Subnets:** Mechanisms may be needed to control which nodes can claim membership in certain subnets, especially if subnets represent trust boundaries or restricted communities. This could involve:
        *   Pre-shared keys or certificates for joining a subnet.
        *   Approval by one or more trusted "gatekeeper" nodes for the subnet.
        *   A shared configuration list of members, maintained by a trusted authority or consensus mechanism.
    *   **Publishing/Subscribing:** Policies within a subnet might restrict who can publish gossip or subscribe to certain topics within that subnet.

*   **Inter-Subnet Communication:**
    *   If subnets have different trust levels, communication crossing subnet boundaries needs careful consideration.
    *   **Secure Channels:** Traffic between distinct security-level subnets might need to be routed through verified gateway nodes that enforce policies and potentially use explicitly secured channels (e.g., dedicated QUIC connections with mutual authentication).

## 4. Addressing Limited Network Addressability

The system must be robust to common network challenges like NAT.

*   **NAT Traversal:**
    *   **QUIC's Role:** QUIC, being UDP-based, is generally more NAT-friendly than TCP. It can leverage techniques like UDP hole punching.
    *   **Bootstrap Nodes:** Publicly addressable bootstrap nodes are essential for initial network entry and can help nodes discover their public IP and port.
    *   **STUN/TURN:** If QUIC's native capabilities and simple UDP hole punching are insufficient for some nodes behind highly restrictive NATs or firewalls, integrating STUN (Session Traversal Utilities for NAT) for public address discovery and TURN (Traversal Using Relays around NAT) for relaying traffic might be necessary. These introduce external dependencies.
*   **Relaying:**
    *   For nodes that absolutely cannot establish direct P2P connections, other nodes in the DHT/gossip network with better connectivity could act as relays.
    *   This adds latency and load on relay nodes and introduces points of centralization and potential trust issues, so it should be a fallback mechanism.
*   **Subnet Scope and Reachability:**
    *   Concentric subnets could be defined, in part, based on network topology and reachability. For instance, a subnet might consist of nodes within a single data center or a specific ISP's network, where direct communication is more reliable. This can enhance the efficiency of intra-subnet communication.

## 5. QUIC Security Benefits

Leveraging QUIC for all underlying transport provides significant baseline security.

*   **Encryption by Default:** QUIC mandates TLS 1.3 (or a compatible version) for establishing connections, meaning all DHT and gossip communication is automatically encrypted end-to-end between peers.
*   **Authentication:** QUIC connections involve certificate-based authentication (often self-signed in P2P contexts, where the public key is the identity), ensuring nodes are talking to the intended peer.
*   **Replay Attack Protection:** TLS 1.3 includes mechanisms to prevent replay attacks.
*   **Improved Handshake:** QUIC offers a faster and more secure connection establishment handshake compared to TCP+TLS.

By relying on QUIC, the application-level protocols (DHT, gossip) can focus on their specific logic, knowing that the transport layer provides a strong security foundation. However, this does not absolve the application layer from its own security responsibilities (like message signing and spam resistance).
