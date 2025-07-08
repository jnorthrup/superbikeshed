# Platform Detection

## Overview

The Trikeshed platform detection system provides comprehensive platform information and feature detection, with special focus on Linux kernel version detection for io_uring support (kernel 5.1+).

## Features

### Core Capabilities

- **Operating System Detection**: Linux, macOS, Windows, BSD variants, Android, iOS
- **Architecture Detection**: x86_64, ARM64, ARM32, RISC-V, WebAssembly
- **Linux Kernel Version Detection**: Parse `/proc/version` for accurate kernel version information
- **io_uring Support Detection**: Check for kernel 5.1+ support with advanced feature detection
- **CPU Feature Detection**: SIMD instructions, vector operations, specialized instructions
- **Cross-Platform Support**: Native, JVM, and JavaScript implementations

### io_uring Feature Detection

The system detects io_uring support and advanced features based on kernel version:

| Kernel Version | Feature | Description |
|----------------|---------|-------------|
| 5.1+ | `IO_URING` | Basic io_uring support |
| 5.6+ | `BUFFER_RING` | Buffer ring support |
| 5.7+ | `MULTI_SHOT` | Multi-shot operations |
| 5.8+ | `SQPOLL` | Submission queue polling |
| 5.11+ | `CQPOLL` | Completion queue polling |
| 5.12+ | `DEFER_TASKRUN` | Defer task run |
| 5.13+ | `SINGLE_ISSUER` | Single issuer optimization |
| 5.15+ | `ATTACH_WQ` | Attach work queue |

## Usage

### Basic Platform Detection

```kotlin
import borg.trikeshed.lib.platform.*

// Get comprehensive platform information
val info = PlatformDetection.getPlatformInfo()
println("OS: ${info.os}")
println("Architecture: ${info.arch}")
println("Kernel Version: ${info.kernelVersion}")
println("Features: ${info.features}")
```

### io_uring Support Check

```kotlin
// Check if io_uring is supported
if (PlatformDetection.hasIoUringSupport()) {
    println("io_uring is available for high-performance I/O")
    
    // Check for specific features
    if (PlatformDetection.hasFeature(PlatformFeature.BUFFER_RING)) {
        println("Buffer ring support available")
    }
    if (PlatformDetection.hasFeature(PlatformFeature.MULTI_SHOT)) {
        println("Multi-shot operations available")
    }
} else {
    println("io_uring not supported")
}
```

### Convenience Functions

```kotlin
// Quick platform checks
if (isLinux()) {
    println("Running on Linux")
}

if (isLinuxWithUring()) {
    println("Linux with io_uring support")
}

if (isMacOS()) {
    println("Running on macOS")
}

// Feature checks
if (hasFeature(PlatformFeature.ZERO_COPY)) {
    println("Zero-copy I/O available")
}
```

## Implementation Details

### Platform-Specific Implementations

#### Native (Linux/macOS)
- Direct access to `/proc/version` and `/proc/cpuinfo`
- Native POSIX API calls
- Real-time kernel version parsing
- CPU feature detection from `/proc/cpuinfo`

#### JVM
- Java system properties for OS/arch detection
- File I/O for kernel version parsing
- Fallback mechanisms for feature detection

#### JavaScript
- Browser navigator API for platform detection
- Limited system information access
- Basic feature availability

### Kernel Version Parsing

The system parses kernel version strings from `/proc/version`:

```
Linux version 5.15.0-91-generic (buildd@lgw01-amd64-001) 
(gcc (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0, 
GNU ld (GNU Binutils for Ubuntu) 2.38) 
#102-Ubuntu SMP Thu Nov 16 14:22:28 UTC 2023
```

Extracts:
- Major version: 5
- Minor version: 15
- Patch version: 0
- Build string: "91-generic"

### Feature Detection Logic

```kotlin
// io_uring support check
if (kernelVersion != null && kernelVersion >= KernelVersion.IO_URING_MINIMUM) {
    features.add(PlatformFeature.IO_URING)
    
    // Advanced features based on kernel version
    if (kernelVersion >= KernelVersion(5, 6, 0)) {
        features.add(PlatformFeature.BUFFER_RING)
    }
    // ... more feature checks
}
```

## Integration Examples

### IO Context Selection

```kotlin
// In IOContext.kt
private fun createOptimalContext(required: CapabilitySet): IOContext {
    val requiredSet: Set<IOCapability> = (0 until required.a.toInt()).map { 
        required.b(it) 
    }.toSet()
    
    return when {
        requiredSet.contains(IOCapability.Kernel.ZeroCopy) && isLinuxWithUring() -> {
            IOContext.UringContext(id = "uring-${System.nanoTime()}", ringSize = 512)
        }
        requiredSet.contains(IOCapability.Kernel.EdgeTriggered) && isMacOS() -> {
            IOContext.KqueueContext(id = "kqueue-${System.nanoTime()}")
        }
        requiredSet.contains(IOCapability.Kernel.EdgeTriggered) && isLinux() -> {
            IOContext.EpollContext(id = "epoll-${System.nanoTime()}")
        }
        else -> {
            IOContext.NioContext(id = "nio-${System.nanoTime()}")
        }
    }
}
```

### Torrent Kettle Strategy Selection

```kotlin
// In TorrentKettle.kt
private fun detectOptimalIOStrategy(): TorrentKettle.IOStrategy {
    return when {
        isLinuxWithUring() -> TorrentKettle.IOStrategy.UringIO()
        isMacOS() -> TorrentKettle.IOStrategy.KqueueIO()
        else -> TorrentKettle.IOStrategy.NioIO()
    }
}
```

## Testing

### Unit Tests

```kotlin
@Test
fun testIoUringDetection() {
    val hasUring = PlatformDetection.hasIoUringSupport()
    val info = PlatformDetection.getPlatformInfo()
    
    if (info.os == OperatingSystem.LINUX) {
        if (info.kernelVersion != null) {
            val expected = info.kernelVersion >= KernelVersion.IO_URING_MINIMUM
            assertEquals(expected, hasUring)
        }
    } else {
        assertFalse(hasUring)
    }
}
```

### Demo Script

Run the platform detection demo:

```bash
./scripts/test-platform-detection.kts
```

## Performance Considerations

- **Caching**: Platform information is cached after first detection
- **Lazy Evaluation**: Features are detected only when needed
- **Minimal I/O**: Single read of `/proc/version` for kernel detection
- **Cross-Platform**: No unnecessary system calls on non-Linux platforms

## Error Handling

- **Graceful Degradation**: Returns `UNKNOWN` for undetectable platforms
- **Null Safety**: Kernel version is `null` when not available
- **Exception Handling**: File I/O errors are caught and handled gracefully
- **Fallback Mechanisms**: Multiple detection methods for robustness

## Future Enhancements

- **Runtime Feature Testing**: Test actual io_uring syscalls
- **Hardware Detection**: GPU, network card capabilities
- **Container Awareness**: Detect running in containers
- **Cloud Platform Detection**: AWS, GCP, Azure specific optimizations 