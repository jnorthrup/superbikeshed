# TrikeShed Wire Protocol Specification

## Overview

The TrikeShed Wire Protocol provides efficient binary serialization for TrikeShed's core types with a focus on performance, platform independence, and data integrity. This specification defines the wire format, serialization rules, and performance requirements.

## Core Types

### IoMemento
Metadata serialization for ISAM and cursor operations. IoMemento contains column metadata including name, type, width, nullability, encoding, and format information.

### Series<T>
High-performance columnar data serialization for arbitrary types. Series provides efficient storage and retrieval of homogeneous data collections.

## Wire Protocol Format

### Message Structure

All wire protocol messages follow this binary format:

```
[Version:1] [MessageType:varint+string] [PayloadLength:varint] [Payload:bytes] [CRC32:4]
```

#### Field Descriptions

- **Version**: Single byte protocol version (currently 1)
- **MessageType**: Variable-length string identifying content type
- **PayloadLength**: Variable-length integer specifying payload size
- **Payload**: Serialized data content
- **CRC32**: 4-byte checksum for integrity verification

### Data Type Encoding

The wire protocol supports these primitive types with specific encoding rules:

| Type | Marker | Encoding |
|------|--------|----------|
| String | 1 | Length-prefixed UTF-8 |
| Int | 2 | Variable-length integer |
| Long | 3 | Two 32-bit fixed values |
| Boolean | 4 | Single byte (0/1) |
| Double | 5 | IEEE 754 as two 32-bit values |
| Generic | 255 | toString() representation |

### Optional Fields

Optional fields use a presence marker system:
- `0`: Field is null/absent
- `1`: Field is present, followed by value

## IoMemento Wire Format

### Binary Layout

IoMemento serialization preserves all metadata fields:

```
[name:optional_string] [type:optional_string] [width:optional_int] 
[nullable:optional_boolean] [encoding:optional_string] [format:optional_string]
```

### Example

```kotlin
val memento = IOMemento.create("id", "Int", 4, false)
// Serializes to: [1,"id"] [1,"Int"] [1,4] [1,false] [0] [0]
```

### Field Specifications

- **name**: Column name as UTF-8 string
- **type**: Data type identifier as UTF-8 string
- **width**: Fixed width in bytes as varint
- **nullable**: Boolean flag for nullability
- **encoding**: Encoding specification as UTF-8 string
- **format**: Format specification as UTF-8 string

## Series<T> Wire Format

### Binary Layout

Series serialization includes type information and element data:

```
[TypeName:string] [Size:varint] [Element1] [Element2] ... [ElementN]
```

### Example

```kotlin
val series = 3 j { i -> "item$i" }
// Serializes to: ["String"] [3] [1,"item0"] [1,"item1"] [1,"item2"]
```

### Type-Specific Encoding

#### Series<Int>
- Type marker: "Int"
- Elements: Variable-length integers
- Performance target: 2-5μs per 1000 elements

#### Series<String>
- Type marker: "String"
- Elements: Length-prefixed UTF-8 strings
- Performance target: 10-20μs per 1000 elements

#### Series<Boolean>
- Type marker: "Boolean"
- Elements: Single bytes (0/1)
- Performance target: 1-3μs per 1000 elements

## Performance Requirements

### Serialization Performance Targets

- **IoMemento**: ~50-100ns per object (small, fixed structure)
- **Series<Int>**: ~2-5μs per 1000 elements
- **Series<String>**: ~10-20μs per 1000 elements (depends on string length)
- **Series<Boolean>**: ~1-3μs per 1000 elements

### Wire Format Efficiency

- **Overhead**: 9-13 bytes per message (version + type + length + checksum)
- **Compression**: 60-80% size reduction vs. JSON for typical data
- **Varint encoding**: Efficient for small integers (1-5 bytes vs. fixed 4/8)

### Memory Usage

- **Zero-copy**: Direct UByteArray manipulation where possible
- **Streaming**: Can process data without loading entire payload
- **Minimal allocation**: Reuses builders and readers

## Platform Support

### Kotlin Multiplatform Compatibility

- Consistent binary format across all platforms
- Endianness-independent encoding
- Platform-agnostic type handling

### Supported Platforms

- JVM (Java 8+)
- Native (Linux, macOS, Windows)
- JavaScript (Node.js, Browser)
- WASM (WebAssembly)

## Error Handling

### Checksum Validation

All wire protocol messages include CRC32 checksums for data integrity. Corrupted data should be detected and reported with clear error messages.

### Type Safety

Type mismatches are caught at deserialization time. The system should provide clear error messages for incompatible type conversions.

### Graceful Degradation

- Invalid data should fail with descriptive error messages
- Empty or null data should be handled gracefully
- Unknown message types should be logged but not crash the system

## Integration Patterns

### ISAM Data File Integration

```kotlin
class IsamMetadataSerializer {
    fun serializeColumnMeta(columnMeta: ColumnMeta): UByteArray {
        return columnMeta.memento.toWireBytes()
    }
    
    fun deserializeColumnMeta(data: UByteArray): ColumnMeta {
        val memento = data.toIoMemento()
        return ColumnMeta(memento)
    }
}
```

### Network Protocol Integration

```kotlin
class TrikeShedNetworkProtocol {
    suspend fun sendMetadata(metadata: IOMemento, socket: Socket) {
        val wireBytes = metadata.toWireBytes()
        socket.send(wireBytes)
    }
    
    suspend fun receiveMetadata(socket: Socket): IOMemento {
        val wireBytes = socket.receive()
        return wireBytes.toIoMemento()
    }
}
```

### Cursor Serialization

```kotlin
// Serialize cursor metadata for caching
fun serializeCursorSchema(cursor: Cursor): UByteArray {
    val metadata = mutableListOf<UByteArray>()
    
    // Serialize each column's metadata
    for (i in 0 until cursor.size) {
        val rowVec = cursor[i]
        val columnMeta = rowVec.b() // Get ColumnMeta
        metadata.add(columnMeta.memento.toWireBytes())
    }
    
    // Combine into series
    val series = metadata.size j { i -> metadata[i] }
    return series.toWireBytes()
}
```

## Advanced Features

### Custom Message Types

```kotlin
// Define custom message type
data class CustomData(val id: Int, val value: String)

// Serialize with custom type marker
fun serializeCustom(data: CustomData): UByteArray {
    val payload = buildWirePayload {
        writeVarInt(data.id)
        writeString(data.value)
    }
    return TrikeShedWireMessage.create("CustomData", payload).let {
        TrikeShedWireSerializer.serializeMessage(it)
    }
}
```

### Streaming Large Series

```kotlin
// For very large Series, consider chunked processing
fun serializeLargeSeries(series: Series<Int>, chunkSize: Int = 1000): Sequence<UByteArray> = sequence {
    for (start in 0 until series.size step chunkSize) {
        val end = minOf(start + chunkSize, series.size)
        val chunk = (end - start) j { i -> series[start + i] }
        yield(chunk.toWireBytes())
    }
}
```

## Comparison with Alternatives

| Format | Size | Speed | Complexity | Schema Evolution |
|--------|------|-------|------------|------------------|
| TrikeShed Wire | 100% | 100% | Low | Manual |
| JSON | 250-300% | 30-50% | Low | Easy |
| Protocol Buffers | 80-120% | 70-90% | Medium | Good |
| MessagePack | 90-130% | 60-80% | Medium | Limited |
| Avro | 85-115% | 50-70% | High | Excellent |

### TrikeShed Wire Protocol Advantages

- Minimal dependencies (only TrikeShed core)
- Direct integration with Series<T> and IoMemento
- Simple implementation, easy to debug
- Optimized for TrikeShed's specific use cases

## Implementation Guidelines

### Development Principles

1. **Preserve type safety** - All serialization preserves TrikeShed's type system
2. **Minimize allocations** - Use zero-copy techniques where possible
3. **Keep it simple** - Avoid over-engineering, focus on actual use cases
4. **Test thoroughly** - Include round-trip tests and performance benchmarks

### Testing Requirements

- Round-trip serialization tests for all types
- Performance benchmarks against targets
- Error handling tests for corrupted data
- Platform compatibility tests
- Memory usage validation

### Dependencies

```kotlin
dependencies {
    implementation(project(":trikeshed-lib"))  // TrikeShed core types
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")
}
```

## Future Extensions

### Planned Features

- Compression support (LZ4, ZSTD)
- Encryption envelope support
- Schema evolution mechanisms
- Streaming serialization for large datasets
- Cross-platform performance optimizations

### Version Compatibility

- Protocol version 1: Current specification
- Future versions will maintain backward compatibility where possible
- Breaking changes will be clearly documented and versioned

## References

- [TrikeShed Core Library](../trikeshed-lib/README.md)
- [ISAM Storage Protocol](./ISAM_PROTOCOL.md)
- [Performance Benchmarks](./PERFORMANCE_BENCHMARKS.md)
- [Platform Compatibility Guide](./PLATFORM_COMPATIBILITY.md) 