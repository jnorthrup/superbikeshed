# Message Formats and Serialization (prautobeans-inspired)

This document details the message formats for DHT and Gossip operations within Trikeshed, and the principles for their serialization, drawing inspiration from `prautobeans`.

## 1. General Serialization Principles (prautobeans-inspired)

The primary goal is to achieve efficient, compact, and fast serialization and deserialization of messages.

*   **Binary Serialization:** All network messages must be serialized into a binary format to minimize overhead compared to text-based formats like JSON or XML.
*   **Schema-Driven:**
    *   Messages will be based on predefined schemas. This ensures type safety and clear message structures.
    *   Potential candidates include Protocol Buffers (with Kotlin bindings) or a Kotlin-native solution like `kotlinx.serialization` configured for binary output (e.g., CBOR or Protobuf format). The choice should align with the project's existing technology stack and performance requirements.
*   **Compactness:** The serialization format should aim for:
    *   **Efficient Integer Encoding:** Use variable-length integers (varints) for integers to save space for smaller values (common for timestamps, counts, enums).
    *   **Bit-Packing:** Booleans and small sets of flags should be bit-packed where possible.
    *   **Efficient Null/Optional Field Representation:** Optional fields, if not set, should consume minimal to no space in the serialized output.
*   **Support for Directed Acyclic Graphs (DAGs):** As noted in `prautobeans` documentation, the serialization mechanism should ideally support object graphs that are DAGs, handling shared references efficiently to avoid redundant serialization of the same object multiple times within a single message. This is particularly relevant for complex nested structures.
*   **Zero-Copy/Minimal-Copy Byte Buffer Manipulation:**
    *   Aligning with `prautobeans`' DirectByteBuffer compatibility, the system should strive for zero-copy or minimal-copy operations when reading from or writing to network byte buffers.
    *   This might involve serializing directly to/from `kotlinx.io.Buffer`, `java.nio.ByteBuffer`, or similar platform-specific buffer types used by the QUIC implementation.

## 2. DHT Message Formats

These are conceptual structures. The final fields and types will be defined by the chosen schema language (e.g., `.proto` files).

*   **`PUT_MESSAGE`**:
    *   `key: bytes` (e.g., SHA-256 hash)
    *   `value: bytes` (the data to be stored)
    *   `originNodeId: bytes` (Node ID of the requester)
    *   `timestamp: long` (request timestamp)

*   **`GET_MESSAGE`**:
    *   `key: bytes` (e.g., SHA-256 hash)
    *   `originNodeId: bytes` (Node ID of the requester)
    *   `timestamp: long` (request timestamp)

*   **`FIND_NODE_MESSAGE`**:
    *   `targetNodeId: bytes` (the Node ID being searched for)
    *   `originNodeId: bytes` (Node ID of the requester)
    *   `timestamp: long` (request timestamp)

*   **`NODE_FOUND_MESSAGE`** (Response to `FIND_NODE`):
    *   `nodes: list<NodeInfo>` (a list of nodes closer to the `targetNodeId`)
    *   `originNodeId: bytes` (Node ID of the responder)
    *   `timestamp: long` (response timestamp)

*   **`NodeInfo` (auxiliary structure):**
    *   `nodeId: bytes`
    *   `ipAddress: string`
    *   `port: int`
    *   `subnets: list<string>` (list of concentric subnets the node belongs to)

**Note:** `key`, `value`, and `nodeId` fields are typically byte arrays. Timestamps are Unix epoch milliseconds or similar.

## 3. Gossip Message Formats

*   **`GOSSIP_MESSAGE`**:
    *   `messageId: bytes` (Unique identifier for the gossip message, e.g., hash of content + publisherId + timestamp)
    *   `publisherId: bytes` (Node ID of the original publisher)
    *   `targetSubnets: list<string>` (Optional: suggested subnets for initial propagation)
    *   `payload: bytes` (The actual content being gossiped)
    *   `timestamp: long` (Creation timestamp of the gossip)
    *   `signature: bytes` (Cryptographic signature of `hash(messageId + publisherId + payload + timestamp)` by the `publisherId`'s private key, to verify authenticity and integrity)

## 4. Type Proxies/Generated Code (Conceptual)

To translate these abstract message definitions into usable Kotlin code, a code generation step is envisioned, similar to `prautobeans`.

*   **Schema Definition:** Message structures (like those above) will be formally defined in a schema file (e.g., a `.proto` file if using Protocol Buffers, or Kotlin data classes annotated for `kotlinx.serialization`).
*   **Code Generation:**
    *   **Protocol Buffers:** If Protocol Buffers are chosen, the `protoc` compiler with Kotlin plugins will generate Kotlin data classes, builders, and (de)serialization methods for each message type. These generated classes serve as the "type proxies."
    *   **`kotlinx.serialization`:** If `kotlinx.serialization` is used, Kotlin data classes themselves, annotated appropriately (e.g., with `@Serializable`), will define the schema. The library provides plugins and methods to generate serializers for binary formats (like CBOR or its own Protobuf implementation).
    *   **Custom Solution (if necessary):** If a fully custom solution inspired by `prautobeans` were to be built (less likely given existing Kotlin options), it would involve writing a code generator that parses a custom schema definition language and outputs Kotlin data classes with efficient, hand-optimized serialization logic.
*   **Benefits:**
    *   **Type Safety:** Ensures that messages conform to the defined structures.
    *   **Ease of Use:** Provides a developer-friendly API for creating, accessing, and serializing/deserializing messages.
    *   **Efficiency:** Generated code from mature libraries like Protocol Buffers or `kotlinx.serialization` is typically highly optimized for performance and compactness, embodying many of the `prautobeans`-inspired principles.

The core idea is that developers will interact with these generated Kotlin data classes, and the underlying serialization mechanism (whether from Protobuf, `kotlinx.serialization`, or another library) will handle the efficient conversion to/from byte arrays for network transmission, guided by the principles of compactness and efficiency.
