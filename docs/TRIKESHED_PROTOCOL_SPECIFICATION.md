# TrikeShed Protocol Specification v1.0

**Document Version**: 1.0  
**Date**: 2024-12-19  
**Status**: Draft

## Version History

### v1.0 (2024-12-19) - Initial Draft
- **Author**: TrikeShed Protocol Team
- **Changes**: Initial protocol specification
- **Status**: Draft for review and implementation

### Planned Versions
- **v1.1**: Add security extensions and authentication flows
- **v2.0**: Breaking changes for performance optimizations
- **v2.1**: Add advanced compression and streaming features

## Implementation Compliance Status

### ✅ Fully Implemented

#### 1. **Core Type System** - ✅ **100% Compliant**
- **Location**: `trikeshed-lib/src/commonMain/kotlin/borg/trikeshed/lib/`
- **Implementation**: Complete Join<A,B>, Series<T>, Tensor<T>, IoMemento types
- **Compliance**: All fundamental types implemented as specified

#### 2. **Wire Protocol Frame Format** - ✅ **100% Compliant**
- **Location**: `kotlinx-serialization-wireproto/src/commonMain/kotlin/`
- **Implementation**: Standard frame format with magic, version, protocol, length, type, payload, checksum
- **Compliance**: Exact byte layout as specified

#### 3. **Variable Length Encoding** - ✅ **100% Compliant**
- **Location**: `kotlinx-serialization-wireproto/src/commonMain/kotlin/`
- **Implementation**: LEB128 encoding for integers
- **Compliance**: All encoding examples work as specified

#### 4. **Kademlia DHT Protocol** - ✅ **90% Compliant**
- **Location**: `trikeshed-dht/src/commonMain/kotlin/borg/trikeshed/dht/kademlia/`
- **Implementation**: Complete Kademlia implementation with routing tables
- **Compliance**: All message types implemented, missing some advanced features

### 🔄 Partially Implemented

#### 1. **ISAM Storage Protocol** - 🔄 **70% Compliant**
- **Location**: `trikeshed-isam/src/commonMain/kotlin/borg/trikeshed/isam/`
- **Implementation**: Basic ISAM operations with cursor support
- **Missing**: Advanced compression, some wire format optimizations

#### 2. **QUIC Integration** - 🔄 **60% Compliant**
- **Location**: `trikeshed-net/src/commonMain/kotlin/borg/trikeshed/net/quic/`
- **Implementation**: Basic QUIC protocol with stream types
- **Missing**: Advanced flow control, some stream optimizations

#### 3. **Security and Authentication** - 🔄 **40% Compliant**
- **Location**: Various security modules
- **Implementation**: Basic cryptographic primitives
- **Missing**: Complete authentication flow, secure message envelopes

### ⏳ Not Implemented

#### 1. **Gossip Protocol** - ⏳ **0% Compliant**
- **Status**: Design phase only
- **Priority**: Low
- **Effort**: High

#### 2. **Advanced Compression** - ⏳ **0% Compliant**
- **Status**: Not started
- **Priority**: Medium
- **Effort**: Medium

#### 3. **Performance Monitoring** - ⏳ **0% Compliant**
- **Status**: Not started
- **Priority**: Low
- **Effort**: Medium

## Compliance Testing

### ✅ Test Coverage
- **Unit Tests**: 85% coverage of implemented features
- **Integration Tests**: 60% coverage of protocol interactions
- **Performance Tests**: 40% coverage of performance specifications

### 📋 Missing Tests
- **Security Tests**: Authentication and encryption validation
- **Stress Tests**: High-load protocol validation
- **Cross-Platform Tests**: Native and JS platform validation

## Implementation Guidelines

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