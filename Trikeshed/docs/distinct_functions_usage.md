# Distinct Functions Usage Guide

**Document Version**: 1.0  
**Date**: 2024-12-19  
**Status**: Implementation Complete  

## Overview

The Distinct Functions implementation provides a comprehensive solution for deduplication across multiple storage backends, starting from low-level io_uring contexts and working up to distributed fabric coordination.

## Architecture Layers

### 1. IO_URING Context Layer
- **Purpose**: High-performance, low-latency I/O operations
- **Platforms**: JVM (AsynchronousFileChannel), Native (POSIX), WASM/JS (IndexedDB)
- **Features**: Content hashing, deduplication strategies, batch operations

### 2. Slab Layer
- **Purpose**: Memory management and bitmap operations
- **Features**: Content addressing, similarity search, metadata tracking

### 3. Storage Layer
- **Purpose**: Backend-specific implementations
- **Backends**: AWS S3, Filecoin/IPFS, Alibaba Cloud

### 4. Fabric Layer
- **Purpose**: Distributed coordination and consensus
- **Features**: Multi-backend operations, consensus strategies, result deduplication

## Quick Start

### Basic Usage

```kotlin
import borg.trikeshed.storage.*

// Create a storage fabric with multiple backends
val fabric = DistinctFunctions.createFabric(
    s3Config = S3Config(s3Client, "my-bucket", "us-west-2"),
    filecoinConfig = FilecoinConfig(ipfsClient, filecoinClient),
    alibabaConfig = AlibabaConfig(ossClient, slabClient, "cn-hangzhou"),
    consensusStrategy = DistinctFabric.ConsensusStrategy.QUORUM
)

// Store data with automatic deduplication
val data = "Hello, World!".toByteArray().let { Indexed(it.size) { i -> it[i] } }
val result = data.storeDistinct(fabric, "my-namespace", "hello.txt")

// Find similar data
val similar = data.findSimilar(fabric, "my-namespace", similarityThreshold = 0.8)
```

### Local Slab Operations

```kotlin
// Create a local slab context for high-performance operations
val slabContext = DistinctFunctions.createSlabContext(slabSize = 4096, maxSlabs = 1000)

// Store data with deduplication
val data = "Test data".toByteArray().let { Indexed(it.size) { i -> it[i] } }
val result = slabContext.storeDistinct(data, DeduplicationStrategy.CONTENT_HASH)

// Check if data is distinct
val isDistinct = slabContext.isDistinct(data)

// Find similar data
val similar = slabContext.findSimilar(data, similarityThreshold = 0.8)
```

### Batch Operations

```kotlin
// Create batch operations
val operations = listOf(
    DistinctOperation.Write(1, 0L, data1, DeduplicationStrategy.CONTENT_HASH),
    DistinctOperation.Write(2, 0L, data2, DeduplicationStrategy.COMPRESSION_BASED),
    DistinctOperation.Hash(data3, HashAlgorithm.SHA2_256),
    DistinctOperation.Compare(data1, data2, tolerance = 0.1)
).let { Indexed(it.size) { i -> it[i] } }

// Execute batch operations
val results = fabric.batchDistinctDistributed(operations)
```

## Deduplication Strategies

### 1. EXACT_MATCH
- **Description**: Byte-for-byte comparison
- **Use Case**: When exact duplicates must be detected
- **Performance**: Fast, but no compression benefits

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key", DeduplicationStrategy.EXACT_MATCH)
```

### 2. CONTENT_HASH
- **Description**: SHA-256 content addressing
- **Use Case**: Content-based deduplication (most common)
- **Performance**: Good balance of speed and effectiveness

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key", DeduplicationStrategy.CONTENT_HASH)
```

### 3. SIMILARITY_HASH
- **Description**: Locality-sensitive hashing
- **Use Case**: Finding similar but not identical content
- **Performance**: Slower but finds near-duplicates

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key", DeduplicationStrategy.SIMILARITY_HASH)
```

### 4. DELTA_ENCODING
- **Description**: Store only differences
- **Use Case**: Version control, incremental backups
- **Performance**: Good compression for similar data

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key", DeduplicationStrategy.DELTA_ENCODING)
```

### 5. COMPRESSION_BASED
- **Description**: Use compression for similarity detection
- **Use Case**: Large datasets with natural compression
- **Performance**: Slower but excellent compression ratios

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key", DeduplicationStrategy.COMPRESSION_BASED)
```

## Consensus Strategies

### 1. QUORUM
- **Description**: Require majority agreement
- **Use Case**: High availability with consistency
- **Formula**: `(backends + 1) / 2` backends must succeed

```kotlin
val fabric = DistinctFunctions.createFabric(
    consensusStrategy = DistinctFabric.ConsensusStrategy.QUORUM
)
```

### 2. ALL
- **Description**: Require all backends to agree
- **Use Case**: Maximum consistency
- **Trade-off**: Lower availability

```kotlin
val fabric = DistinctFunctions.createFabric(
    consensusStrategy = DistinctFabric.ConsensusStrategy.ALL
)
```

### 3. ANY
- **Description**: Accept any backend response
- **Use Case**: Maximum availability
- **Trade-off**: Lower consistency

```kotlin
val fabric = DistinctFunctions.createFabric(
    consensusStrategy = DistinctFabric.ConsensusStrategy.ANY
)
```

### 4. WEIGHTED
- **Description**: Weighted voting based on backend reliability
- **Use Case**: Heterogeneous backend reliability
- **Requires**: Backend reliability metrics

```kotlin
val fabric = DistinctFunctions.createFabric(
    consensusStrategy = DistinctFabric.ConsensusStrategy.WEIGHTED
)
```

## Platform-Specific Features

### JVM Platform
- **I/O**: AsynchronousFileChannel for high-performance I/O
- **Hashing**: Java Security MessageDigest
- **Compression**: Java Deflater/Inflater
- **Concurrency**: AtomicLong for operation IDs

### Native Platform (macOS ARM64)
- **I/O**: POSIX pread/pwrite for atomic operations
- **Hashing**: CommonCrypto (when available)
- **File Management**: Temporary files with proper cleanup
- **Error Handling**: POSIX error codes

### WASM/JS Platform
- **Storage**: IndexedDB for persistent storage
- **Hashing**: Web Crypto API (when available)
- **Compression**: Web Compression API (when available)
- **Async**: Promise-based operations

## Performance Considerations

### Memory Usage
- **Slab Size**: Default 4096 bytes, tune based on data patterns
- **Max Slabs**: Default 1000, increase for larger datasets
- **Bitmap Operations**: Efficient similarity search with O(n) complexity

### I/O Performance
- **Batch Operations**: Use batch operations for multiple items
- **Async I/O**: All operations are non-blocking
- **Platform Optimization**: Each platform uses optimal I/O primitives

### Storage Efficiency
- **Content Addressing**: Automatic deduplication reduces storage
- **Compression**: Built-in compression strategies
- **Metadata Packing**: Efficient metadata storage with bit packing

## Error Handling

### Common Error Patterns

```kotlin
try {
    val result = data.storeDistinct(fabric, "namespace", "key")
    if (result.success) {
        println("Stored successfully: ${result.hash}")
    } else {
        println("Storage failed: ${result.metadata["error"]}")
    }
} catch (e: Exception) {
    println("Exception: ${e.message}")
}
```

### Backend-Specific Errors

```kotlin
val result = data.storeDistinct(fabric, "namespace", "key")
when {
    result.metadata["backend"] == "S3" && !result.success -> {
        // Handle S3-specific errors
        println("S3 error: ${result.metadata["error"]}")
    }
    result.metadata["backend"] == "Filecoin" && !result.success -> {
        // Handle Filecoin-specific errors
        println("Filecoin error: ${result.metadata["error"]}")
    }
    result.metadata["backend"] == "Alibaba" && !result.success -> {
        // Handle Alibaba-specific errors
        println("Alibaba error: ${result.metadata["error"]}")
    }
}
```

## Advanced Usage

### Custom Backend Implementation

```kotlin
class CustomDistinctStorage : DistinctStorageBackend {
    override suspend fun storeDistinct(
        namespace: String,
        key: String,
        data: Indexed<Byte>,
        strategy: DeduplicationStrategy
    ): DistinctResult {
        // Custom implementation
        return DistinctResult(
            operationId = System.nanoTime(),
            success = true,
            data = data,
            hash = computeHash(data),
            metadata = mapOf("backend" to "Custom")
        )
    }
    
    // Implement other methods...
}

// Use custom backend
val customBackend = CustomDistinctStorage()
val backends = listOf(customBackend).let { Indexed(it.size) { i -> it[i] } }
val fabric = DistinctFabric(backends, DistinctFabric.ConsensusStrategy.ANY)
```

### Similarity Search

```kotlin
// Find similar data across all backends
val similar = data.findSimilar(fabric, "namespace", similarityThreshold = 0.8)

// Process similar results
for (i in 0 until similar.a) {
    val result = similar.b(i)
    println("Similar data found: ${result.hash}")
    println("Similarity: ${result.metadata["similarity"]}")
    println("Backend: ${result.metadata["backend"]}")
}
```

### Monitoring and Metrics

```kotlin
// Track operation metrics
val result = data.storeDistinct(fabric, "namespace", "key")
val metrics = mapOf(
    "operationId" to result.operationId.toString(),
    "success" to result.success.toString(),
    "isDuplicate" to result.isDuplicate.toString(),
    "backendCount" to result.backendResults.a.toString(),
    "consensusStrategy" to fabric.consensusStrategy.name
)

// Log metrics
println("Metrics: $metrics")
```

## Best Practices

### 1. Choose Appropriate Deduplication Strategy
- Use `CONTENT_HASH` for general-purpose deduplication
- Use `EXACT_MATCH` when byte-perfect comparison is required
- Use `SIMILARITY_HASH` for finding near-duplicates
- Use `DELTA_ENCODING` for versioned data
- Use `COMPRESSION_BASED` for large datasets

### 2. Configure Consensus Strategy
- Use `QUORUM` for balanced availability and consistency
- Use `ALL` when maximum consistency is required
- Use `ANY` when maximum availability is required
- Use `WEIGHTED` when backends have different reliability

### 3. Optimize for Your Use Case
- Tune slab size based on data patterns
- Use batch operations for multiple items
- Monitor performance metrics
- Handle errors gracefully

### 4. Platform Considerations
- JVM: Best for server-side applications
- Native: Best for high-performance desktop applications
- WASM/JS: Best for web applications

## Troubleshooting

### Common Issues

1. **Operation Fails on All Backends**
   - Check network connectivity
   - Verify backend credentials
   - Check backend-specific error messages

2. **High Memory Usage**
   - Reduce slab size
   - Reduce max slabs
   - Use streaming for large data

3. **Slow Performance**
   - Use batch operations
   - Choose appropriate deduplication strategy
   - Monitor platform-specific optimizations

4. **Inconsistent Results**
   - Check consensus strategy
   - Verify backend configurations
   - Monitor backend health

### Debug Mode

```kotlin
// Enable debug logging
val fabric = DistinctFunctions.createFabric(
    // ... config ...
).also { fabric ->
    // Add debug logging
    fabric.debugMode = true
}
```

## Conclusion

The Distinct Functions implementation provides a comprehensive, platform-agnostic solution for deduplication across multiple storage backends. By starting from low-level io_uring contexts and working up to distributed fabric coordination, it offers both high performance and flexibility for various use cases.

For more information, see the [Storage Specifications and Standards](storage_specifications_standards.md) document. 