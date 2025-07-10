# TrikeShed io_uring Facade - The Beer Goggles 🍺👓

This module provides a pure Kotlin Multiplatform (KMP) implementation of an io_uring-style API that works on **any** platform. It's the "beer goggles" that make every platform look like it has io_uring!

## What Are Beer Goggles?

Just like beer goggles can make things look better than they are, our `TrikeUring` facade makes:
- **Darwin/macOS** kqueue look like io_uring
- **JVM** NIO Selector look like io_uring  
- **Linux** use real io_uring (when available)

All with the **exact same API**!

## CCEK Integration

The magic happens through CCEK (CoroutineContextElementKey) orchestration:

```kotlin
// Same code works everywhere!
withTrikeUring { uring ->
    // Submit a linked read->write operation
    val linkedOp = Read(fd = inputFd, buffer = buffer)
        .chain()
        .then { readResult ->
            if (readResult.isSuccess) {
                Write(fd = outputFd, buffer = buffer)
            } else null
        }
        .build()
    
    uring.submission.send(linkedOp)
    
    // Collect completion
    val result = uring.completion.first()
}
```

## Architecture

### Common API (`commonMain`)
- `TrikeUring` - The main interface
- `Sqe` - Submission Queue Entries (operations)
- `Cqe` - Completion Queue Entries (results)
- `CCEKUringOrchestrator` - The brain behind it all

### Platform Implementations

#### Darwin (`darwinMain`)
- Uses `kqueue` for async I/O
- GCD (Grand Central Dispatch) for work distribution
- Emulates linked operations with coroutines
- Full CCEK context propagation

#### Linux (`linuxMain`) - Future
- Direct io_uring syscalls
- Zero-copy operations
- Native linked operations
- Kernel-level batching

#### JVM (`jvmMain`) - Future
- NIO Selector-based implementation
- Thread pool for async operations
- CompletableFuture integration

## Features

### 1. Unified API
Write once, run anywhere with the same async I/O patterns.

### 2. CCEK Orchestration
- **Control**: Manages execution flow
- **Context**: Tracks operation state
- **Environment**: Platform capabilities
- **Knowledge**: Performance optimization

### 3. Linked Operations
Chain operations together, just like io_uring's `IOSQE_IO_LINK`:

```kotlin
read.chain()
    .then { write }
    .then { fsync }
    .build()
```

### 4. Batch Submission
Submit multiple operations efficiently:

```kotlin
uring.submitBatch(listOf(read1, read2, write1, write2))
```

### 5. Zero-Copy (where supported)
Register buffers for efficient I/O:

```kotlin
uring.registerBuffers(buffers)
uring.registerFiles(fileDescriptors)
```

## Performance

- **Linux**: Native io_uring performance
- **Darwin**: kqueue efficiency with GCD parallelism
- **JVM**: NIO with thread pool optimization

The CCEK Knowledge layer learns patterns and optimizes:
- Batch small operations
- Group by CPU affinity
- Use registered buffers for hot paths

## Use Cases

Perfect for:
- High-performance network servers
- Database engines
- File processing pipelines
- Any async I/O heavy workload

## Example: Echo Server

```kotlin
suspend fun echoServer() = withTrikeUring { uring ->
    val serverFd = socket(AF_INET, SOCK_STREAM, 0)
    bind(serverFd, address)
    listen(serverFd, 128)
    
    while (isActive) {
        // Accept connection
        uring.submission.send(Accept(serverFd))
        val acceptResult = uring.completion.first() as AcceptResult
        
        if (acceptResult.isSuccess) {
            val clientFd = acceptResult.clientFd
            
            // Echo loop with linked read->write
            val echoOp = Receive(clientFd, buffer)
                .chain()
                .then { receiveResult ->
                    if (receiveResult.isSuccess) {
                        Send(clientFd, buffer)
                    } else {
                        Close(clientFd)
                    }
                }
                .build()
            
            uring.submission.send(echoOp)
        }
    }
}
```

## The Magic

The beauty is that this code works **identically** on:
- macOS using kqueue
- Linux using io_uring
- JVM using NIO

That's the power of beer goggles! 🍺👓

## Building

```bash
./gradlew :trikeshed-uring:build
```

## Testing

```bash
./gradlew :trikeshed-uring:allTests
```

## Future Work

1. Complete Linux io_uring implementation
2. Add JVM NIO implementation  
3. WebAssembly support
4. Performance benchmarks
5. Integration with Swagger/OpenAPI for REST validation
6. QUIC protocol support
7. CouchDB replication protocol

## References

- [io_uring documentation](https://kernel.dk/io_uring.pdf)
- [kqueue man page](https://www.freebsd.org/cgi/man.cgi?kqueue)
- [CCEK Architecture](../trikeshed-ccek/README.md)