# Async I/O Engine

This module provides high-performance async I/O capabilities across different platforms using platform-specific optimizations:

- **POSIX Systems (macOS, BSD)**: Uses kqueues for efficient event-driven I/O
- **Linux**: Uses liburing (io_uring) for maximum performance
- **Other Platforms**: Provides a consistent API with platform-appropriate fallbacks

## Features

- **Cross-platform compatibility** with a unified API
- **High-performance async I/O** using platform-specific optimizations
- **Coroutine integration** with full Kotlin coroutines support
- **Batch operations** for high-throughput scenarios
- **Event-driven completion** via Kotlin Flow
- **Resource management** with proper cleanup

## Quick Start

### Basic Usage

```kotlin
// Create and initialize the async I/O engine
val engine = AsyncIOEngine.create()
engine.initialize()

// Perform async read
val buffer = ByteArray(1024)
val bytesRead = engine.read(fileDescriptor, buffer, offset)

// Perform async write
val data = "Hello, World!".toByteArray()
val bytesWritten = engine.write(fileDescriptor, data, offset)

// Submit batch operations
val operations = listOf(
    IOOperation(1L, IOOperation.IOType.READ, fd, buffer1, 0L),
    IOOperation(2L, IOOperation.IOType.WRITE, fd, data, 0L)
)
val results = engine.submitBatch(operations)

// Listen for completed operations
engine.completedOperations().collect { result ->
    println("Operation ${result.operationId} completed: ${result.bytesTransferred} bytes")
}

// Clean up resources
engine.cleanup()
```

### Advanced Usage

```kotlin
// Create multiple engines for different purposes
val readEngine = AsyncIOEngine.create()
val writeEngine = AsyncIOEngine.create()

readEngine.initialize()
writeEngine.initialize()

// Use engines concurrently
val readJob = launch {
    val bytesRead = readEngine.read(fd, buffer, offset)
    println("Read $bytesRead bytes")
}

val writeJob = launch {
    val bytesWritten = writeEngine.write(fd, data, offset)
    println("Wrote $bytesWritten bytes")
}

// Wait for completion
readJob.join()
writeJob.join()

// Clean up
readEngine.cleanup()
writeEngine.cleanup()
```

## API Reference

### AsyncIOEngine

The main interface for async I/O operations.

#### Methods

- `initialize()`: Initialize the async I/O engine
- `cleanup()`: Clean up resources
- `read(fd: Int, buffer: ByteArray, offset: Long): Int`: Perform async read
- `write(fd: Int, data: ByteArray, offset: Long): Int`: Perform async write
- `submitBatch(operations: List<IOOperation>): List<IOResult>`: Submit multiple operations
- `completedOperations(): Flow<IOResult>`: Get flow of completed operations

#### Companion Object

- `create(): AsyncIOEngine`: Create a new async I/O engine instance

### IOOperation

Represents an I/O operation to be performed.

#### Properties

- `id: Long`: Unique operation identifier
- `type: IOType`: Type of operation (READ or WRITE)
- `fd: Int`: File descriptor
- `buffer: ByteArray`: Data buffer
- `offset: Long`: Offset for the operation

#### IOType Enum

- `READ`: Read operation
- `WRITE`: Write operation

### IOResult

Represents the result of an I/O operation.

#### Properties

- `operationId: Long`: ID of the completed operation
- `bytesTransferred: Int`: Number of bytes transferred
- `error: Int`: Error code (0 for success)

#### Computed Properties

- `isSuccess: Boolean`: True if operation succeeded
- `isError: Boolean`: True if operation failed

## Platform-Specific Implementations

### POSIX (Native)

Uses kqueues for efficient event-driven I/O on POSIX systems (macOS, BSD, etc.).

**Features:**
- Event-driven I/O using kqueue
- Non-blocking operations
- Efficient resource management

### Linux

Uses liburing (io_uring) for maximum performance on Linux systems.

**Features:**
- High-performance io_uring integration
- Batch operation support
- Advanced I/O scheduling

### Other Platforms

Provides a consistent API with platform-appropriate fallbacks.

## Performance Considerations

### Memory Management

- Buffers are managed by the caller
- Large buffers should be reused when possible
- Consider using buffer pools for high-throughput scenarios

### Batch Operations

- Use `submitBatch()` for multiple operations to reduce overhead
- Batch size should be tuned based on your workload
- Monitor completion flow for backpressure

### Resource Cleanup

- Always call `cleanup()` when done with an engine
- Engines can be reinitialized after cleanup
- Multiple engines can coexist

## Error Handling

```kotlin
try {
    val bytesRead = engine.read(fd, buffer, offset)
    if (bytesRead < 0) {
        // Handle error
        println("Read failed with error: $bytesRead")
    }
} catch (e: Exception) {
    // Handle exception
    println("Read operation failed: ${e.message}")
}
```

## Testing

The module includes comprehensive tests:

- **Unit tests**: Test individual components
- **Integration tests**: Test complete workflows
- **Performance tests**: Benchmark performance characteristics
- **Platform-specific tests**: Test platform-specific implementations

Run tests with:

```bash
./gradlew test
```

## Examples

See the test files for complete examples:

- `AsyncIOEngineTest.kt`: Basic functionality tests
- `AsyncIOEngineIntegrationTest.kt`: Integration examples
- `AsyncIOEngineBenchmarkTest.kt`: Performance examples
- `AsyncIOEngineNativeTest.kt`: Native platform tests
- `LiburingAsyncIOEngineTest.kt`: Linux-specific tests

## Contributing

When adding new platform support:

1. Implement the `AsyncIOEngine` interface
2. Add platform-specific tests
3. Update documentation
4. Ensure proper resource management
5. Add performance benchmarks

## License

This module is part of the Trikeshed project and follows the same licensing terms. 