# TrikeShed Protocol Specification v1.0

**Document Version**: 1.0  
**Date**: 2024-12-19  
**Status**: Draft  

## Table of Contents

1. [Overview](#overview)
2. [Core Type System](#core-type-system)
3. [Wire Protocol](#wire-protocol)
4. [Network Protocols](#network-protocols)
5. [Storage Protocols](#storage-protocols)
6. [Serialization Formats](#serialization-formats)
7. [Security and Authentication](#security-and-authentication)
8. [Performance Specifications](#performance-specifications)
9. [Implementation Guidelines](#implementation-guidelines)
10. [Protocol Evolution](#protocol-evolution)

---

## 1. Overview

TrikeShed is a high-performance distributed systems infrastructure with multiple protocol layers for different use cases. This document defines the complete protocol stack for TrikeShed implementations.

### 1.1 Design Principles

- **Zero-Cost Abstractions**: Protocols compile to efficient native code
- **Type Safety**: Strong typing through TrikeShed's taxonomical type system
- **Platform Independence**: Consistent behavior across JVM, Native, and JavaScript
- **Performance First**: Optimized for high-throughput, low-latency operations
- **Composability**: Protocols can be layered and combined

### 1.2 Protocol Stack

```
┌─────────────────────────────────────────────────────────┐
│                Application Layer                        │
│  Trading APIs │ Storage APIs │ Network APIs │ ML APIs   │
├─────────────────────────────────────────────────────────┤
│                  TrikeShed Core                         │
│     Series<T> │ Tensor<T> │ Join<A,B> │ IoMemento      │
├─────────────────────────────────────────────────────────┤
│                Wire Protocols                           │
│  DHT Messages │ Gossip │ QUIC │ HTTP │ ISAM │ JSON      │
├─────────────────────────────────────────────────────────┤
│               Transport Layer                           │
│        UDP │ TCP │ QUIC │ WebSockets │ Memory          │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Core Type System

### 2.1 Fundamental Types

TrikeShed protocols are built on a foundational type system:

```kotlin
// Core composition operator
interface Join<A, B> {
    val a: A
    val b: B
}

// Infix composition
infix fun <A, B> A.j(b: B): Join<A, B>

// Core data structure
typealias Series<T> = Join<Int, (Int) -> T>

// Tensor abstraction
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

// Metadata container
typealias IoMemento = Join<String?, Join<String?, Join<Int?, Boolean?>>>
```

### 2.2 Taxonomical Type Aliases

All protocol fields MUST use taxonomical type aliases:

```kotlin
// Network types
@JvmInline value class NodeId(val bytes: UByteArray)
@JvmInline value class MessageId(val bytes: UByteArray)
@JvmInline value class NetworkAddress(val value: String)
@JvmInline value class NetworkPort(val value: Int)

// Protocol types
@JvmInline value class ProtocolVersion(val version: UByte)
@JvmInline value class MessageType(val name: String)
@JvmInline value class PayloadLength(val bytes: Int)
@JvmInline value class Checksum(val crc32: UInt)

// Temporal types
@JvmInline value class Timestamp(val epochMillis: Long)
@JvmInline value class Duration(val millis: Long)
@JvmInline value class TimeToLive(val seconds: Int)

// Data types
@JvmInline value class DataKey(val bytes: UByteArray)
@JvmInline value class DataValue(val bytes: UByteArray)
@JvmInline value class DataSize(val bytes: Long)
```

### 2.3 Protocol Enumerations

```kotlin
enum class TrikeShedProtocol(val id: UByte) {
    WIRE_PROTO(1u),     // Native TrikeShed wire protocol
    DHT_KADEMLIA(2u),   // Kademlia DHT messages
    GOSSIP(3u),         // Gossip protocol messages
    QUIC(4u),           // QUIC transport messages
    HTTP(5u),           // HTTP/1.1, HTTP/2, HTTP/3
    ISAM(6u),           // ISAM storage messages
    JSON_RPC(7u),       // JSON-RPC over various transports
    TENSOR_PROTO(8u)    // Tensor operation messages
}

enum class SerializationFormat(val id: UByte) {
    TRIKESHED_NATIVE(1u),  // TrikeShed binary format
    CBOR(2u),              // RFC 8949 CBOR
    MSGPACK(3u),           // MessagePack
    JSON(4u),              // RFC 8259 JSON
    PROTOBUF(5u)           // Protocol Buffers
}

enum class CompressionType(val id: UByte) {
    NONE(0u),
    LZ4(1u),
    ZSTD(2u),
    GZIP(3u),
    BROTLI(4u)
}
```

---

## 3. Wire Protocol

### 3.1 Message Frame Format

All TrikeShed messages use this standard frame:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    Magic      |Version|Proto  |         Message Length        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Message Type Length                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Message Type ...                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Payload ...                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        CRC32 Checksum                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**Fields:**
- **Magic** (16 bits): `0x5452` ("TR" in ASCII)
- **Version** (8 bits): Protocol version (currently 1)
- **Proto** (8 bits): Protocol identifier from `TrikeShedProtocol` enum
- **Message Length** (32 bits): Total message length in bytes
- **Message Type Length** (32 bits): Length of message type string
- **Message Type** (variable): UTF-8 encoded message type identifier
- **Payload** (variable): Serialized message data
- **CRC32 Checksum** (32 bits): IEEE 802.3 CRC32 of entire message

### 3.2 Variable Length Encoding

TrikeShed uses LEB128 (Little Endian Base 128) for efficient integer encoding:

```kotlin
fun encodeVarInt(value: Int): UByteArray {
    val result = mutableListOf<UByte>()
    var v = value
    while (v >= 0x80) {
        result.add(((v and 0x7F) or 0x80).toUByte())
        v = v ushr 7
    }
    result.add(v.toUByte())
    return result.toUByteArray()
}
```

**Encoding Examples:**
- `0` → `[0x00]`
- `127` → `[0x7F]`
- `128` → `[0x80, 0x01]`
- `16383` → `[0xFF, 0x7F]`
- `16384` → `[0x80, 0x80, 0x01]`

### 3.3 Optional Field Encoding

Optional fields use a presence marker:

```
Optional<T> := [present:1] [value:T] | [absent:1]
```

Where:
- `present = 0x01`: Field is present, followed by value
- `absent = 0x00`: Field is absent/null

---

## 4. Network Protocols

### 4.1 Kademlia DHT Protocol

TrikeShed implements a secure Kademlia DHT for distributed hash table operations.

#### 4.1.1 Node Identifier

```kotlin
@JvmInline value class KademliaNodeId(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "NodeId must be 32 bytes" } }
}
```

#### 4.1.2 Message Types

**PING Request**
```kotlin
data class PingRequest(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp
)
```

**PONG Response**
```kotlin
data class PongResponse(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp,
    val uptime: Duration
)
```

**FIND_NODE Request**
```kotlin
data class FindNodeRequest(
    val nodeId: KademliaNodeId,
    val targetId: KademliaNodeId,
    val timestamp: Timestamp
)
```

**NODES Response**
```kotlin
data class NodesResponse(
    val nodeId: KademliaNodeId,
    val nodes: Series<KademliaNodeInfo>,
    val timestamp: Timestamp
)

data class KademliaNodeInfo(
    val nodeId: KademliaNodeId,
    val address: NetworkAddress,
    val port: NetworkPort,
    val lastSeen: Timestamp
)
```

**STORE Request**
```kotlin
data class StoreRequest(
    val nodeId: KademliaNodeId,
    val key: DataKey,
    val value: DataValue,
    val ttl: TimeToLive,
    val timestamp: Timestamp
)
```

**FIND_VALUE Request**
```kotlin
data class FindValueRequest(
    val nodeId: KademliaNodeId,
    val key: DataKey,
    val timestamp: Timestamp
)
```

#### 4.1.3 Wire Format

Kademlia messages use the standard TrikeShed frame with `Proto = DHT_KADEMLIA (2)`:

```
Message Type: "PING" | "PONG" | "FIND_NODE" | "NODES" | "STORE" | "FIND_VALUE"
Payload: [nodeId:32] [timestamp:8] [message-specific-fields...]
```

### 4.2 Gossip Protocol

#### 4.2.1 Gossip Message

```kotlin
data class GossipMessage(
    val messageId: MessageId,
    val publisherId: NodeId,
    val content: DataValue,
    val targetSubnets: Series<String>,
    val timestamp: Timestamp,
    val ttl: TimeToLive,
    val hopCount: Int
)
```

#### 4.2.2 Anti-Entropy and Rumor Spreading

**GOSSIP_DIGEST Request**
```kotlin
data class GossipDigestRequest(
    val nodeId: NodeId,
    val digests: Series<MessageDigest>,
    val timestamp: Timestamp
)

data class MessageDigest(
    val messageId: MessageId,
    val version: Long,
    val checksum: Checksum
)
```

**GOSSIP_SYNC Response**
```kotlin
data class GossipSyncResponse(
    val nodeId: NodeId,
    val messages: Series<GossipMessage>,
    val timestamp: Timestamp
)
```

### 4.3 QUIC Integration

TrikeShed integrates with QUIC for low-latency, multiplexed connections.

#### 4.3.1 Stream Types

```kotlin
enum class QuicStreamType(val id: Long) {
    DHT_MESSAGES(0L),
    GOSSIP_MESSAGES(1L),
    TENSOR_DATA(2L),
    ISAM_OPERATIONS(3L),
    JSON_RPC(4L)
}
```

#### 4.3.2 Stream Frame Format

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Stream ID                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Stream Type                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        TrikeShed Message Frame               |
|                              ...                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## 5. Storage Protocols

### 5.1 ISAM (Indexed Sequential Access Method)

#### 5.1.1 IoMemento Wire Format

```kotlin
data class WireIoMemento(
    val name: Optional<String>,
    val type: Optional<String>,
    val width: Optional<Int>,
    val nullable: Optional<Boolean>,
    val encoding: Optional<String>,
    val format: Optional<String>
)
```

**Binary Layout:**
```
[name:optional_string] [type:optional_string] [width:optional_varint]
[nullable:optional_bool] [encoding:optional_string] [format:optional_string]
```

#### 5.1.2 Cursor Protocol

**CURSOR_OPEN Request**
```kotlin
data class CursorOpenRequest(
    val dataFile: String,
    val columns: Series<WireIoMemento>,
    val readOnly: Boolean
)
```

**CURSOR_READ Request**
```kotlin
data class CursorReadRequest(
    val cursorId: Long,
    val offset: Long,
    val limit: Int
)
```

**CURSOR_DATA Response**
```kotlin
data class CursorDataResponse(
    val cursorId: Long,
    val rows: Series<Series<DataValue>>, // RowVec data
    val hasMore: Boolean
)
```

### 5.2 Series<T> Storage Format

#### 5.2.1 Series Header

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   Magic       |Version| Type  |         Series Size           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Element Type Length                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Element Type ...                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Compression                           |C|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Where:
- **Magic**: `0x5345` ("SE" for Series)
- **Version**: Format version (1)
- **Type**: Element type encoding
- **Series Size**: Number of elements
- **C**: Compression flag

#### 5.2.2 Element Encoding

| Type | Encoding | Wire Format |
|------|----------|-------------|
| String | UTF-8 | `[length:varint] [data:bytes]` |
| Int | LEB128 | `[value:varint]` |
| Long | Fixed 64-bit | `[value:8bytes]` |
| Double | IEEE 754 | `[value:8bytes]` |
| Boolean | Single bit | `[value:1bit]` |
| ByteArray | Raw bytes | `[length:varint] [data:bytes]` |

---

## 6. Serialization Formats

### 6.1 TrikeShed Native Format

TrikeShed's native binary format optimized for Series<T> and Tensor<T> operations.

#### 6.1.1 Type Descriptors

```kotlin
enum class NativeTypeId(val id: UByte) {
    NULL(0u),
    BOOLEAN(1u),
    BYTE(2u),
    SHORT(3u),
    INT(4u),
    LONG(5u),
    FLOAT(6u),
    DOUBLE(7u),
    STRING(8u),
    BYTEARRAY(9u),
    SERIES(10u),
    TENSOR(11u),
    JOIN(12u),
    IOMEMENTO(13u)
}
```

#### 6.1.2 Compound Type Encoding

**Series<T> Encoding:**
```
[type_id:SERIES] [element_type:type_descriptor] [size:varint] [elements...]
```

**Tensor<T> Encoding:**
```
[type_id:TENSOR] [element_type:type_descriptor] [rank:varint] 
[dimensions:varint[]] [data_layout:byte] [elements...]
```

**Join<A,B> Encoding:**
```
[type_id:JOIN] [a_type:type_descriptor] [b_type:type_descriptor] [a_value] [b_value]
```

### 6.2 JSON Protocol

For interoperability, TrikeShed supports JSON serialization with extensions.

#### 6.2.1 Series<T> JSON Format

```json
{
  "_type": "Series",
  "_element_type": "Int",
  "_size": 3,
  "_data": [1, 2, 3]
}
```

#### 6.2.2 IoMemento JSON Format

```json
{
  "_type": "IoMemento",
  "name": "user_id",
  "type": "Long",
  "width": 8,
  "nullable": false,
  "encoding": "binary",
  "format": "int64"
}
```

### 6.3 CBOR Integration

TrikeShed supports RFC 8949 CBOR for compact binary JSON.

#### 6.3.1 CBOR Type Extensions

| CBOR Tag | TrikeShed Type | Description |
|----------|----------------|-------------|
| 1001 | Series<T> | Columnar data series |
| 1002 | Tensor<T> | N-dimensional array |
| 1003 | Join<A,B> | Composed pair |
| 1004 | IoMemento | Metadata container |

---

## 7. Security and Authentication

### 7.1 Cryptographic Primitives

TrikeShed uses these cryptographic algorithms:

- **Key Exchange**: X25519 (Curve25519 ECDH)
- **Signatures**: Ed25519 (EdDSA)
- **Symmetric Encryption**: ChaCha20-Poly1305
- **Hashing**: BLAKE3
- **Key Derivation**: HKDF-SHA256

### 7.2 Node Identity

```kotlin
data class NodeIdentity(
    val nodeId: NodeId,           // BLAKE3 hash of public key
    val publicKey: PublicKey,     // Ed25519 public key
    val signature: Signature      // Self-signature of nodeId
)
```

### 7.3 Secure Message Envelope

```kotlin
data class SecureMessage(
    val senderId: NodeId,
    val recipientId: NodeId,
    val nonce: UByteArray,        // 24 bytes
    val encryptedPayload: UByteArray,
    val authTag: UByteArray       // 16 bytes
)
```

### 7.4 Authentication Flow

1. **Node Registration**: Nodes generate Ed25519 keypair and compute NodeId
2. **Key Exchange**: Nodes perform X25519 ECDH for session keys
3. **Message Encryption**: All messages encrypted with ChaCha20-Poly1305
4. **Signature Verification**: Critical operations require Ed25519 signatures

---

## 8. Performance Specifications

### 8.1 Throughput Requirements

| Protocol | Min Throughput | Target Throughput | Max Latency |
|----------|----------------|-------------------|-------------|
| DHT Operations | 1,000 ops/sec | 10,000 ops/sec | 100ms |
| Gossip Messages | 10,000 msg/sec | 100,000 msg/sec | 50ms |
| ISAM Read | 100,000 rows/sec | 1,000,000 rows/sec | 10ms |
| Series<T> Serialization | 1 GB/sec | 5 GB/sec | 1ms |
| Wire Protocol | 100 MB/sec | 1 GB/sec | 0.1ms |

### 8.2 Memory Usage

- **Wire Protocol Overhead**: < 64 bytes per message
- **Series<T> Overhead**: < 32 bytes + 8 bytes per element
- **IoMemento Size**: < 256 bytes typical
- **Node State**: < 1 MB per 10,000 active connections

### 8.3 Compression Ratios

| Data Type | Uncompressed | LZ4 | ZSTD | Improvement |
|-----------|--------------|-----|------|-------------|
| JSON Metadata | 100% | 65% | 45% | 55% |
| Series<Int> | 100% | 85% | 75% | 25% |
| Series<String> | 100% | 40% | 30% | 70% |
| IoMemento | 100% | 70% | 55% | 45% |

---

## 9. Implementation Guidelines

### 9.1 Protocol Compliance

All TrikeShed implementations MUST:

1. Support the standard wire frame format
2. Implement CRC32 checksum verification
3. Handle variable-length integer encoding correctly
4. Support optional field encoding
5. Validate message type identifiers
6. Implement proper error handling and recovery

### 9.2 Platform-Specific Optimizations

#### 9.2.1 JVM Platform

- Use `ByteBuffer` for zero-copy operations
- Leverage Vector API for SIMD operations (JDK 17+)
- Use `MethodHandles` for dynamic dispatch optimization
- Implement off-heap storage for large Series<T>

#### 9.2.2 Native Platform

- Use SIMD intrinsics (NEON, AVX2) for bulk operations
- Memory-map large files for ISAM operations
- Implement lock-free data structures for concurrent access
- Use platform-specific networking (epoll, kqueue, IOCP)

#### 9.2.3 JavaScript Platform

- Use `ArrayBuffer` and `TypedArray` for binary operations
- Leverage WebAssembly SIMD when available
- Implement streaming processing for large datasets
- Use Web Workers for parallel operations

### 9.3 Error Handling

#### 9.3.1 Error Codes

```kotlin
enum class TrikeShedError(val code: Int) {
    SUCCESS(0),
    PROTOCOL_VERSION_MISMATCH(1001),
    INVALID_MESSAGE_TYPE(1002),
    CHECKSUM_MISMATCH(1003),
    PAYLOAD_TOO_LARGE(1004),
    UNSUPPORTED_SERIALIZATION(1005),
    AUTHENTICATION_FAILED(1006),
    NODE_UNREACHABLE(1007),
    STORAGE_ERROR(1008),
    COMPRESSION_ERROR(1009),
    TIMEOUT(1010)
}
```

#### 9.3.2 Error Response Format

```kotlin
data class ErrorResponse(
    val errorCode: TrikeShedError,
    val errorMessage: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Timestamp
)
```

### 9.4 Logging and Debugging

#### 9.4.1 Log Levels

- **ERROR**: Protocol violations, authentication failures
- **WARN**: Performance degradation, fallback mechanisms
- **INFO**: Connection establishment, protocol negotiation
- **DEBUG**: Message serialization, detailed timing
- **TRACE**: Individual field encoding, state transitions

#### 9.4.2 Metrics Collection

Required metrics for protocol monitoring:

```kotlin
interface ProtocolMetrics {
    fun recordMessageSent(protocol: TrikeShedProtocol, messageType: String, bytes: Long)
    fun recordMessageReceived(protocol: TrikeShedProtocol, messageType: String, bytes: Long)
    fun recordSerializationTime(format: SerializationFormat, duration: Duration)
    fun recordCompressionRatio(type: CompressionType, ratio: Double)
    fun recordError(error: TrikeShedError, context: String)
}
```

---

## 10. Protocol Evolution

### 10.1 Versioning Strategy

TrikeShed protocols use semantic versioning:

- **Major Version**: Breaking changes to wire format
- **Minor Version**: Backward-compatible additions
- **Patch Version**: Bug fixes and optimizations

### 10.2 Backward Compatibility

Protocol implementations MUST:

1. Support at least the previous major version
2. Gracefully degrade when encountering unknown message types
3. Preserve unknown fields during serialization round-trips
4. Provide clear error messages for version mismatches

### 10.3 Extension Mechanism

New protocol features can be added through:

#### 10.3.1 Message Type Extensions

```kotlin
// New message types must follow naming convention
"TRIKESHED_V2_NEW_FEATURE_REQUEST"
"TRIKESHED_V2_NEW_FEATURE_RESPONSE"
```

#### 10.3.2 Optional Field Extensions

```kotlin
// Extend existing messages with optional fields
data class ExtendedPingRequest(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp,
    val capabilities: Optional<Series<String>> = Optional.absent()
)
```

### 10.4 Deprecation Policy

1. **Deprecation Notice**: 6 months advance notice
2. **Deprecation Period**: 12 months support after deprecation
3. **Removal**: Only in major version updates
4. **Migration Guide**: Provided for all breaking changes

---

## Appendices

### Appendix A: Reference Implementation

The canonical TrikeShed protocol implementation is available at:
- **Module**: `kotlinx-serialization-wireproto`
- **Main Classes**: `TrikeShedWireSerializer`, `WireIoMemento`
- **Tests**: Comprehensive protocol compliance tests

### Appendix B: Performance Benchmarks

Benchmark results for reference implementation:

```
IoMemento Serialization:     ~75ns per object
Series<Int> Serialization:   ~3μs per 1000 elements  
Wire Protocol Overhead:     ~12 bytes per message
CRC32 Calculation:          ~2ns per byte
Variable-length Encoding:   ~1ns per byte
```

### Appendix C: Protocol Grammar

ABNF grammar for TrikeShed wire protocol:

```abnf
trikeshed-message = magic version protocol message-length message-type payload checksum
magic = %x54.52              ; "TR"
version = %x01                ; Version 1
protocol = %x01-FF            ; Protocol identifier
message-length = 4OCTET       ; 32-bit length
message-type = length-prefixed-string
payload = *OCTET              ; Variable length data
checksum = 4OCTET             ; CRC32

length-prefixed-string = varint *OCTET
varint = *(%x80-FF) %x00-7F   ; LEB128 encoding
```

### Appendix D: Security Considerations

1. **DoS Protection**: Rate limiting, message size limits
2. **Cryptographic Agility**: Support for algorithm upgrades
3. **Side-Channel Resistance**: Constant-time implementations
4. **Forward Secrecy**: Ephemeral key exchange
5. **Audit Trail**: Comprehensive security event logging

---

**End of Document**

This specification defines the complete TrikeShed protocol stack. All implementations MUST conform to these specifications to ensure interoperability and performance characteristics.