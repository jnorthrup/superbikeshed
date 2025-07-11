# trikeshed-uring Usage Guide

## Overview

trikeshed-uring provides a unified async IO API for all platforms:
- **commonUring**: io_uring-style API, available everywhere
- **commonNIO**: NIO-style API, available everywhere
- **Proxy Layer**: Choose at runtime between real or emulated implementations

## Usage Patterns

### 1. Using commonUring
```kotlin
withTrikeUring { uring ->
    val readOp = Read(fd = 0, buffer = ByteBuffer.allocate(1024))
    uring.submission.send(readOp)
    // ... handle completions ...
}
```

### 2. Using commonNIO
```kotlin
withCommonNio { nio ->
    val writeOp = NioWrite(fd = 1, buffer = ByteBuffer.wrap("Hello".toByteArray()))
    nio.submission.send(writeOp)
    // ... handle completions ...
}
```

### 3. Proxy Layer
```kotlin
val uring = UringProxy.create(context, preferRealUring = true)
// Use as commonUring
```

## Platform Caveats
- **Linux**: Real io_uring is used if available, else NIO emulation
- **macOS**: kqueue implementation or NIO emulation
- **JVM**: NIO-based implementation for both APIs
- **All platforms**: Both APIs are always available, but performance/features may vary

## Advanced Features
- Linked operations (chaining)
- Batch submission
- Context propagation (CCEK, etc.)

## Testing
- All APIs are covered by multiplatform tests in `src/commonTest/kotlin/borg/trikeshed/uring/`

---
For more details, see the source code and tests. 