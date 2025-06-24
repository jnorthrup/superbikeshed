# Kademlia/DHT-like Functionality

This document outlines the core Kademlia/DHT-like functionality for Trikeshed.

## 1. Core DHT Operations

The DHT will support the following fundamental operations:

*   **`PUT(key, value)`**: Stores a key-value pair in the DHT. The node responsible for storing the data will be determined by the key's proximity to its Node ID.
*   **`GET(key)`**: Retrieves a value associated with a given key from the DHT. The lookup process will involve iteratively querying nodes closer to the key.
*   **`FIND_NODE(nodeId)`**: Finds a set of nodes in the DHT that are closest to the given `nodeId`. This is a core mechanism for routing and peer discovery.
*   **(Optional) `STORE_PROVIDER(contentId, providerNodeId)`**: If IPFS-like content addressing and provider records are implemented, this operation would allow nodes to announce that they are providing content associated with a `contentId`.

## 2. Node Identity and Routing

### Node ID

*   **Structure:** Node IDs will be cryptographic hashes (e.g., SHA-256) of a node's public key. This ensures uniqueness and provides a basis for secure identification.
*   **Keyspace Proximity:** Node IDs determine a node's position in the DHT keyspace. The "distance" between Node IDs (typically calculated using XOR metric) is fundamental to Kademlia routing.

### Concentric Subnets

*   **Concept:** Nodes will also be part of one or more "concentric subnets." These subnets provide a way to group nodes based on various criteria, such as:
    *   Geographic location (e.g., "europe-west1", "us-east")
    *   Trust levels (e.g., "trusted-peers", "community-nodes")
    *   Application-specific needs (e.g., "file-sharing-subnet", "messaging-subnet")
*   **Routing Implications:**
    *   Routing algorithms may prioritize peers within the same subnet.
    *   Subnets could represent distinct DHT "rings" or partitions, with specific mechanisms for inter-subnet communication.
    *   The exact mechanics of how subnets influence routing (e.g., dedicated routing tables per subnet, weighted routing preferences) will be further defined. This is intended as a high-level concept for now.

### Routing Table

*   **Structure:** Each node will maintain a routing table, likely based on Kademlia's k-bucket structure. K-buckets organize known peers according to their distance in the Node ID keyspace.
*   **Subnet Awareness:** Routing tables should be enhanced to be subnet-aware. This could mean:
    *   Prioritizing or maintaining more connections with peers in the same subnet(s).
    *   Having dedicated k-buckets or routing information for specific subnets, facilitating efficient intra-subnet lookups and inter-subnet jumps.

## 3. Peer Discovery and Bootstrapping

### Bootstrapping

*   **Initial Contacts:** To join the DHT, a new node needs a list of initial contact points (bootstrap nodes).
*   **Methods:** These bootstrap nodes can be:
    *   Hardcoded into the application.
    *   Dynamically discovered through other mechanisms (e.g., DNS seeds, community servers).

### Peer Discovery

*   **Iterative Process:** Once connected to bootstrap nodes, a node will use operations like `FIND_NODE` iteratively.
*   **Populating Routing Tables:** By repeatedly asking known nodes for peers closer to its own ID or specific target IDs, a node discovers more peers and populates its routing table, gradually building a comprehensive view of the network.

## 4. Network Communication

*   **Transport Protocol:** Network communication for DHT operations should ideally leverage the existing QUIC implementation found in `Trikeshed/src/commonMain/kotlin/borg/trikeshed/net/quic/`. QUIC provides a secure, efficient, and multiplexed transport layer.
*   **Message Serialization:** Messages exchanged for DHT operations (e.g., `PUT`, `GET`, `FIND_NODE` requests and responses) should be serialized using an efficient binary format, similar to `prautobeans`. This minimizes bandwidth usage and processing overhead.
