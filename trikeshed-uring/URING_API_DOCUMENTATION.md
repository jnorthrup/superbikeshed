# io_uring API Documentation

**Last Updated**: 2025-01-27  
**Status**: Complete Cross-Platform Implementation  
**Architecture**: CommonLiburingEmulator with NioEngine Abstraction

## Overview

The `trikeshed-uring` module provides a complete cross-platform io_uring abstraction that works on JVM, Native, and other platforms. It implements the full io_uring API surface through a common emulator that maps to platform-specific NIO engines.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
├─────────────────────────────────────────────────────────────┤
│              CommonLiburingEmulator                         │
├─────────────────────────────────────────────────────────────┤
│                NioEngine Interface                          │
├─────────────────────────────────────────────────────────────┤
│  JVM NIO  │  Native kqueue  │  Native epoll  │  Other...   │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### CommonLiburingEmulator
- **Purpose**: Cross-platform io_uring emulator
- **Interface**: Implements `LiburingEmulator`
- **Features**: Complete io_uring API surface with coroutine integration

### NioEngine
- **Purpose**: Platform-specific I/O engine abstraction
- **Implementation**: Platform-specific (JVM NIO, Native kqueue/epoll)
- **Features**: Async I/O operations with completion handling

## API Reference

### Ring Management

#### `io_uring_queue_init(entries, ring, flags)`
Initialize an io_uring ring with specified parameters.

```kotlin
val ring = IoUring()
val emulator = CommonLiburingEmulator()
val result = emulator.io_uring_queue_init(32u, ring, 0u)
if (result == 0) {
    println("Ring initialized successfully")
}
```

#### `io_uring_queue_exit(ring)`
Clean up ring resources.

```kotlin
emulator.io_uring_queue_exit(ring)
```

### Submission Queue Operations

#### `io_uring_get_sqe(ring)`
Get a submission queue entry for configuration.

```kotlin
val sqe = emulator.io_uring_get_sqe(ring)
if (sqe != null) {
    // Configure the SQE
    emulator.io_uring_prep_read(sqe, fd, buffer, buffer.size.toUInt(), 0L)
}
```

#### `io_uring_submit(ring)`
Submit all pending SQEs to the ring.

```kotlin
val submitted = emulator.io_uring_submit(ring)
println("Submitted $submitted operations")
```

#### `io_uring_submit_and_wait(ring, wait_nr)`
Submit operations and wait for completions.

```kotlin
val result = emulator.io_uring_submit_and_wait(ring, 1u)
if (result >= 0) {
    println("Submitted and completed $result operations")
}
```

### Completion Queue Operations

#### `io_uring_wait_cqe(ring, cqe_ptr)`
Wait for a completion queue entry.

```kotlin
val cqePtr = 0u j { 0 }
val result = emulator.io_uring_wait_cqe(ring, cqePtr)
if (result == 0) {
    val cqe = cqePtr.b(0)
    println("Received completion: ${cqe?.res}")
}
```

#### `io_uring_for_each_cqe(ring)`
Iterate through all available completions.

```kotlin
val completions = emulator.io_uring_for_each_cqe(ring)
for (i in 0 until completions.a) {
    val cqe = completions.b(i)
    println("Completion $i: ${cqe.res}")
}
```

### Operation Preparation

#### Read Operations
```kotlin
// Simple read
emulator.io_uring_prep_read(sqe, fd, buffer, buffer.size.toUInt(), offset)

// Vectored read
val iovecs = listOf(IoVec(buffer1, buffer1.size.toUInt()), IoVec(buffer2, buffer2.size.toUInt()))
val iovecList = IoVecList(iovecs)
emulator.io_uring_prep_readv(sqe, fd, iovecList, iovecs.size.toUInt(), offset)
```

#### Write Operations
```kotlin
// Simple write
emulator.io_uring_prep_write(sqe, fd, buffer, buffer.size.toUInt(), offset)

// Vectored write
emulator.io_uring_prep_writev(sqe, fd, iovecList, iovecs.size.toUInt(), offset)
```

#### Network Operations
```kotlin
// Accept connection
emulator.io_uring_prep_accept(sqe, listenFd, null, 0u, 0)

// Connect to server
val addr = SocketAddress()
emulator.io_uring_prep_connect(sqe, fd, addr, 16u)

// Send data
emulator.io_uring_prep_send(sqe, sockfd, buffer, buffer.size.toULong(), 0)

// Receive data
emulator.io_uring_prep_recv(sqe, sockfd, buffer, buffer.size.toULong(), 0)
```

#### File Operations
```kotlin
// Close file descriptor
emulator.io_uring_prep_close(sqe, fd)

// File synchronization
emulator.io_uring_prep_fsync(sqe, fd, 0u)

// No-operation (for testing)
emulator.io_uring_prep_nop(sqe)
```

### Data Association

#### `io_uring_sqe_set_data(sqe, data)`
Associate arbitrary data with an SQE.

```kotlin
val requestId = "req-123"
emulator.io_uring_sqe_set_data(sqe, requestId)
```

#### `io_uring_cqe_get_data(cqe)`
Retrieve associated data from a CQE.

```kotlin
val data = emulator.io_uring_cqe_get_data(cqe)
if (data is String) {
    println("Request completed: $data")
}
```

### Buffer and File Registration

#### Buffer Registration
```kotlin
val iovecs = listOf(IoVec(buffer1, buffer1.size.toUInt()))
val iovecList = IoVecList(iovecs)
emulator.io_uring_register_buffers(ring, iovecList, 1u)

// Use registered buffers in operations
// ...

// Unregister when done
emulator.io_uring_unregister_buffers(ring)
```

#### File Registration
```kotlin
val files = listOf(fd1, fd2, fd3)
val fdList = FdList(files)
emulator.io_uring_register_files(ring, fdList, files.size.toUInt())

// Use registered files in operations
// ...

// Unregister when done
emulator.io_uring_unregister_files(ring)
```

## Usage Examples

### Basic File Read
```kotlin
runBlocking {
    val ring = IoUring()
    val emulator = CommonLiburingEmulator()
    
    // Initialize ring
    emulator.io_uring_queue_init(32u, ring, 0u)
    
    // Open file (platform-specific)
    val fd = openFile("/path/to/file")
    val buffer = ByteArray(1024)
    
    // Prepare read operation
    val sqe = emulator.io_uring_get_sqe(ring)
    emulator.io_uring_prep_read(sqe, fd, buffer, buffer.size.toUInt(), 0L)
    
    // Submit and wait
    val result = emulator.io_uring_submit_and_wait(ring, 1u)
    
    if (result >= 0) {
        val cqePtr = 0u j { 0 }
        emulator.io_uring_wait_cqe(ring, cqePtr)
        val cqe = cqePtr.b(0)
        
        if (cqe?.res != null && cqe.res > 0) {
            println("Read ${cqe.res} bytes: ${String(buffer, 0, cqe.res)}")
        }
    }
    
    // Cleanup
    emulator.io_uring_queue_exit(ring)
}
```

### Network Server
```kotlin
runBlocking {
    val ring = IoUring()
    val emulator = CommonLiburingEmulator()
    emulator.io_uring_queue_init(32u, ring, 0u)
    
    val listenFd = createServerSocket(8080)
    
    while (true) {
        // Accept connection
        val sqe = emulator.io_uring_get_sqe(ring)
        emulator.io_uring_prep_accept(sqe, listenFd, null, 0u, 0)
        
        emulator.io_uring_submit_and_wait(ring, 1u)
        
        val cqePtr = 0u j { 0 }
        emulator.io_uring_wait_cqe(ring, cqePtr)
        val cqe = cqePtr.b(0)
        
        if (cqe?.res != null && cqe.res >= 0) {
            val clientFd = cqe.res
            // Handle client connection
            handleClient(emulator, ring, clientFd)
        }
    }
}
```

### Batch Operations
```kotlin
runBlocking {
    val ring = IoUring()
    val emulator = CommonLiburingEmulator()
    emulator.io_uring_queue_init(32u, ring, 0u)
    
    // Prepare multiple operations
    repeat(5) { i ->
        val sqe = emulator.io_uring_get_sqe(ring)
        emulator.io_uring_prep_nop(sqe)
        emulator.io_uring_sqe_set_data(sqe, "operation-$i")
    }
    
    // Submit all operations
    val submitted = emulator.io_uring_submit(ring)
    
    // Wait for all completions
    repeat(submitted) {
        val cqePtr = 0u j { 0 }
        emulator.io_uring_wait_cqe(ring, cqePtr)
        val cqe = cqePtr.b(0)
        val data = emulator.io_uring_cqe_get_data(cqe)
        println("Completed: $data")
    }
    
    emulator.io_uring_queue_exit(ring)
}
```

## Platform-Specific Considerations

### JVM Platform
- Uses Java NIO for underlying I/O operations
- Supports all io_uring operations through NIO emulation
- Good performance for most use cases

### Native Platform (Linux)
- Can use actual io_uring when available
- Falls back to epoll/kqueue for older kernels
- Best performance for high-throughput I/O

### Native Platform (macOS)
- Uses kqueue for underlying I/O operations
- Full io_uring API compatibility
- Good performance for network operations

## Best Practices

### Ring Sizing
- Choose ring size based on expected concurrency
- Larger rings support more concurrent operations
- Default 32 entries work well for most applications

### Error Handling
```kotlin
val result = emulator.io_uring_submit(ring)
if (result < 0) {
    println("Submit failed: $result")
    return
}

val cqe = // ... get completion
if (cqe.res < 0) {
    println("Operation failed: ${cqe.res}")
    // Handle error appropriately
}
```

### Resource Management
- Always call `io_uring_queue_exit()` to clean up
- Unregister buffers and files when done
- Close file descriptors properly

### Performance Optimization
- Use vectored I/O (readv/writev) for multiple buffers
- Register frequently used buffers and files
- Batch operations when possible
- Use appropriate timeout values

## Testing

The module includes comprehensive tests in `CommonNioTest.kt`:

```kotlin
@Test
fun `should submit and wait for completion`() = runBlocking {
    val ring = IoUring()
    val emulator = CommonLiburingEmulator()
    
    emulator.io_uring_queue_init(32u, ring, 0u)
    
    val sqe = emulator.io_uring_get_sqe(ring)
    emulator.io_uring_prep_nop(sqe)
    
    val result = emulator.io_uring_submit_and_wait(ring, 1u)
    assertTrue(result >= 0)
    
    emulator.io_uring_queue_exit(ring)
}
```

## Migration from Native io_uring

If migrating from native io_uring:

1. Replace direct io_uring calls with `CommonLiburingEmulator`
2. Update initialization code to use the emulator
3. Replace platform-specific error handling with cross-platform equivalents
4. Test thoroughly on all target platforms

## Future Enhancements

- **Performance Monitoring**: Add metrics collection
- **Advanced Features**: Support for more io_uring operations
- **Platform Optimization**: Platform-specific performance tuning
- **Integration**: Better integration with other trikeshed modules

---

*This documentation covers the complete io_uring API implementation. For specific use cases or advanced features, refer to the test suite or contact the development team.* 