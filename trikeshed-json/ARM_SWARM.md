# ARM SIMD & Swarm Parallel Processing Implementation

## Branch: `feature/arm-simd-native`

This branch provides ARM-optimized SIMD implementations for JSON parsing, with special focus on:

1. **ARM NEON** - 128-bit SIMD available on all ARM64 processors
2. **ARM SVE** - Scalable Vector Extension (128-2048 bits) on newer ARM
3. **macOS Swarm** - Multi-core parallel processing using GCD
4. **Apple Silicon** - Optimized for M1/M2/M3 unified memory architecture

## Architecture

### SIMD Strategy Hierarchy
```
SimdStrategy (interface)
├── ArmNeonSimdStrategy    - ARM NEON 128-bit SIMD
├── ArmSveSimdStrategy     - ARM SVE scalable vectors
├── NativeSimdStrategy     - x86 SSE/AVX fallback
└── FallbackSimdStrategy   - Pure scalar implementation
```

### Platform Detection
The implementation automatically detects:
- CPU architecture (ARM64 vs x64)
- Operating system (macOS, iOS, Linux)
- SIMD capabilities (NEON, SVE)
- Core count for parallel processing

## Key Components

### 1. ARM NEON Implementation
```kotlin
// Processes 16 bytes per instruction
class ArmNeonSimdStrategy : SimdStrategy {
    override fun findByte(data: ByteArray, target: Byte, offset: Int): IntArray {
        // Uses NEON intrinsics:
        // - vld1q_u8: Load 16 bytes
        // - vceqq_u8: Compare 16 bytes
        // - vmovmaskq_u8: Extract results
    }
}
```

### 2. macOS Swarm
```kotlin
// Distributes work across all CPU cores
class MacOSSwarmJsonScanner {
    fun swarmParse(): SwarmResult {
        // Uses Grand Central Dispatch
        // Each core processes a chunk with NEON
        // Merges results efficiently
    }
}
```

### 3. Performance Characteristics

#### Apple M3 Family
- **M3**: 8 cores, 100 GB/s memory bandwidth
- **M3 Pro**: 12 cores, 150 GB/s bandwidth
- **M3 Max**: 16 cores, 400 GB/s bandwidth

#### Expected Performance
- **Single-core NEON**: 500-800 MB/s
- **Swarm (8 cores)**: 4-6 GB/s
- **Swarm (16 cores)**: 8-12 GB/s

## Usage

### Basic SIMD
```kotlin
val simd = createSimdStrategy() // Auto-detects ARM NEON
val positions = simd.findByte(data, '{'.code.toByte())
```

### Swarm Processing
```kotlin
val scanner = MacOSSwarmJsonScanner(largeJson)
val result = scanner.swarmParse()
println("Used ${result.coresUsed} cores")
println("Throughput: ${result.throughputMBps} MB/s")
```

### Coroutine-based Swarm
```kotlin
val scanner = CoroutineSwarmJsonScanner(json)
val result = scanner.swarmParseAsync()
```

## Building

```bash
# Build native executable
./gradlew :trikeshed-json:linkDebugExecutableNative

# Run benchmarks
./trikeshed-json/build/bin/native/debugExecutable/trikeshed-json.kexe
```

## Advantages

1. **No JVM overhead** - Native ARM64 code
2. **Unified memory** - Zero-copy on Apple Silicon
3. **Power efficient** - ARM's efficiency advantage
4. **Scalable** - From embedded (1 core) to server (128+ cores)

## Future Enhancements

1. **SVE2** - When available on more ARM processors
2. **SME** - Scalable Matrix Extension for ML workloads
3. **Metal integration** - GPU acceleration for huge files
4. **Cross-platform** - Linux ARM (Graviton), Android