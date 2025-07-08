# Async I/O Engine (Trikeshed)

This module provides high-performance, cross-platform async I/O with unified APIs and coroutine integration.

## Features
- Unified async I/O API for POSIX (kqueue), Linux (io_uring), and fallback platforms
- Full Kotlin coroutine support
- Batch and event-driven operations
- Resource management and cleanup

## Quick Start
```kotlin
val engine = AsyncIOEngine.create()
engine.initialize()
val buffer = ByteArray(1024)
val bytesRead = engine.read(fileDescriptor, buffer, offset)
engine.cleanup()
```

## API Overview
- `AsyncIOEngine`: Main async I/O interface
- `IOOperation`: Describes an I/O operation
- `IOResult`: Result of an I/O operation

## Platform Implementations
- **POSIX**: kqueue
- **Linux**: io_uring
- **Other**: Fallbacks

## Performance
- Use buffer pools for throughput
- Prefer batch operations
- Always cleanup resources

## Error Handling
```kotlin
try {
    val bytesRead = engine.read(fd, buffer, offset)
    if (bytesRead < 0) println("Read failed: $bytesRead")
} catch (e: Exception) {
    println("Read failed: ${e.message}")
}
```

## Testing
Run all tests:
```bash
./gradlew test
```

## Coroutine-Based Manager Refactor

### Motivation
To minimize resource costs, all manager classes (such as IOContextManager) are being refactored to use coroutine actors and suspendable APIs. This enables lightweight, scalable management without threads or heavy objects.

### Example: IOContextManager (Coroutine Form)
```kotlin
class IOContextManagerCoroutine(scope: CoroutineScope) {
    private val actor = scope.actor<ManagerMsg> {
        // handle messages, mutate state
    }
    suspend fun registerService(...) = actor.send(RegisterServiceMsg(...))
    suspend fun getContext(...) = actor.send(GetContextMsg(...))
    // ...
}
```

### Summary Table
| Manager Class      | Coroutine-based | Refactor Status |
|-------------------|:---------------:|:---------------:|
| IOContextManager  | Yes (planned)   | In progress     |
| WaveSessionManager| Yes (planned)   | In progress     |

### Migration Steps
1. Replace mutable state with coroutine actor
2. Expose suspend functions for all operations
3. Use a dedicated coroutine scope for lifecycle

## Contributing
- Implement new managers as coroutine actors
- Add tests and update docs

## License
This module is part of the Trikeshed project and follows the same licensing terms. 