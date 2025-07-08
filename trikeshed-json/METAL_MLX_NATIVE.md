# Native M3 Metal/MLX JSON Acceleration

## Overview

This implementation leverages Apple Silicon's unique capabilities for JSON parsing WITHOUT the JVM. It runs as native code using Kotlin/Native with direct access to:

- **Metal**: GPU compute shaders for massive parallelism
- **NEON**: 128-bit SIMD built into all Apple Silicon
- **AMX**: Apple Matrix Extension (512-bit operations via Accelerate)
- **Neural Engine**: 16-core ML accelerator
- **MLX**: Apple's ML framework optimized for unified memory

## Architecture

### No JVM Required
- Kotlin/Native compiles to native ARM64 code
- Direct C interop with Apple frameworks
- Zero JNI overhead
- Unified memory architecture (CPU/GPU/NE share memory)

### Implementation Layers

1. **Accelerate.framework** (Easiest)
   - vDSP functions for vectorized operations
   - Automatically uses NEON and AMX
   - No explicit SIMD code needed

2. **Metal Compute** (Maximum Throughput)
   - Write compute shaders for JSON scanning
   - Process GB/s of JSON on GPU
   - Best for large files (10MB+)

3. **BNNS** (Experimental)
   - Basic Neural Network Subroutines
   - Can leverage Neural Engine
   - Useful for pattern learning

4. **MLX** (Future)
   - Apple's NumPy-like framework
   - Lazy evaluation
   - Automatic device selection

## Performance Expectations

### M3 Family Specifications
- **M3**: 8-core CPU, 10-core GPU, 100 GB/s memory
- **M3 Pro**: 12-core CPU, 18-core GPU, 150 GB/s memory  
- **M3 Max**: 16-core CPU, 40-core GPU, 400 GB/s memory

### JSON Parsing Performance
- **NEON (CPU)**: 500-800 MB/s
- **Accelerate (AMX)**: 1-3 GB/s
- **Metal GPU**: 5-20 GB/s (scales with GPU cores)
- **Combined**: Limited by memory bandwidth

## Building and Running

```bash
# Build native executable
./gradlew :trikeshed-json:linkDebugExecutableNative

# Run benchmarks
./trikeshed-json/build/bin/native/debugExecutable/trikeshed-json.kexe

# Or use the build script
./trikeshed-json/src/nativeMain/kotlin/borg/trikeshed/json/build-native-m3.sh
```

## Key Advantages over JVM

1. **No JVM overhead**: Direct native execution
2. **Unified memory**: Zero-copy between processors
3. **Power efficiency**: 10x better perf/watt
4. **Framework access**: Direct Metal/CoreML/Accelerate APIs
5. **Startup time**: Instant (no JVM warmup)

## Code Example

```kotlin
// Direct Accelerate.framework usage from Kotlin/Native
jsonData.usePinned { pinned ->
    val dataPtr = pinned.addressOf(0)
    // Call vDSP functions directly
    // These use NEON/AMX automatically
}

// Metal compute shader dispatch
val device = MTLCreateSystemDefaultDevice()
val commandQueue = device.newCommandQueue()
// Dispatch parallel GPU computation
```

## MLX Potential

MLX offers unique capabilities for JSON:
- **Lazy evaluation**: Only compute what's needed
- **Automatic optimization**: CPU vs GPU vs Neural Engine
- **Learned patterns**: Train models to understand your JSON schemas
- **Unified API**: Same code works on all Apple Silicon

This native implementation shows how to achieve maximum performance on Apple Silicon without any JVM overhead.