# JSON Showdown Benchmark

A comprehensive benchmark suite that compares all major JSON libraries on both JVM and Native platforms to determine the fastest JSON implementation for Kotlin applications.

## Overview

This benchmark tests the performance of JSON libraries across multiple dimensions:

- **Parsing Performance** - Raw parsing speed across different JSON sizes
- **Serialization Performance** - Object-to-JSON encoding speed  
- **Deserialization Performance** - JSON-to-object decoding speed
- **Query Performance** - Field access and lookup performance
- **Memory Usage** - Heap allocation and garbage collection impact
- **Large JSON Handling** - Scalability with very large documents
- **Complex Nested Structures** - Performance with deeply nested JSON
- **Streaming Performance** - Chunked processing with backpressure
- **Concurrent Access** - Multi-threaded access patterns
- **Error Handling** - Performance with malformed JSON
- **Platform Optimizations** - Hardware-specific acceleration

## Libraries Tested

### JVM Libraries
- **kotlinx.serialization** - Kotlin standard JSON library
- **Jackson** - High-performance JSON library
- **Gson** - Google's JSON library
- **TrikeShed Simple** - Simple character scanner
- **TrikeShed Compact** - Bitmap-based scanner
- **TrikeShed Fast** - O(1) lookup scanner
- **TrikeShed BBCursive** - Functional parser

### Native Libraries
- **kotlinx.serialization (Native)** - Kotlin standard (native)
- **TrikeShed Native** - Platform-optimized implementations
- **Platform-specific** - Hardware acceleration where available

## Quick Start

### Run Full Benchmark Suite
```bash
cd trikeshed-json
./run-json-showdown.sh
```

### Run Quick Benchmarks
```bash
./run-json-showdown.sh quick
```

### Run JVM-Only Benchmarks
```bash
./run-json-showdown.sh jvm
```

### Run Native-Only Benchmarks
```bash
./run-json-showdown.sh native
```

### Run Platform-Specific Benchmarks
```bash
./run-json-showdown.sh platform
```

## Benchmark Modes

### Full Suite (`full`)
Runs all benchmarks including:
- JVM benchmarks (all libraries)
- Native benchmarks (where available)
- Platform-specific optimizations
- Comprehensive reporting

### Quick Benchmarks (`quick`)
Runs a subset of benchmarks for rapid comparison:
- Main JSON showdown benchmark
- Basic performance metrics
- Reduced test sizes for speed

### JVM Benchmarks (`jvm`)
Tests all libraries on JVM platform:
- kotlinx.serialization
- Jackson
- Gson
- All TrikeShed implementations

### Native Benchmarks (`native`)
Tests native implementations:
- kotlinx.serialization (native)
- TrikeShed native implementations
- Platform-specific optimizations

### Platform Benchmarks (`platform`)
Tests platform-specific optimizations:
- Apple Silicon (M3 Metal/NEON)
- Linux (io_uring)
- Windows (DirectX)

## Test Data

The benchmark uses various test data types:

### Simple JSON Objects
```json
{"field1": "value1", "field2": "value2", ...}
```

### Complex Nested Structures
```json
{
  "metadata": {
    "version": "1.0.0",
    "config": {
      "features": {"advanced": true}
    }
  },
  "data": {
    "users": [
      {
        "id": 1,
        "profile": {
          "name": "John Doe",
          "preferences": {"theme": "dark"}
        }
      }
    ]
  }
}
```

### Large JSON Arrays
Arrays with thousands of objects for scalability testing.

## Performance Metrics

### Throughput
- **MB/s** - Megabytes processed per second
- **ops/s** - Operations per second
- **items/s** - JSON items processed per second

### Latency
- **ns/op** - Nanoseconds per operation
- **μs/op** - Microseconds per operation
- **ms/op** - Milliseconds per operation

### Memory Efficiency
- **Heap Usage** - Memory allocated during processing
- **GC Impact** - Garbage collection frequency and time
- **Memory Footprint** - Total memory consumption

### Scalability
- **Size Scaling** - Performance with increasing JSON size
- **Complexity Scaling** - Performance with nested structures
- **Concurrency Scaling** - Performance with multiple threads

## Platform-Specific Optimizations

### Apple Silicon (M1/M2/M3)
- **Metal GPU acceleration** for large JSON processing
- **NEON SIMD** automatically used by Accelerate framework
- **Unified memory** provides optimal CPU/GPU data sharing
- **Neural Engine** for pattern recognition (experimental)

### Linux (x86_64/ARM64)
- **io_uring** for high-throughput I/O operations
- **SIMD optimizations** via compiler intrinsics
- **Memory-mapped files** for large JSON processing
- **NUMA-aware** memory allocation

### Windows
- **DirectX compute shaders** for GPU acceleration
- **Windows-specific optimizations** for file I/O
- **Cross-compilation** support for multiple architectures

## Results Interpretation

### Performance Rankings
1. **Winner** - Fastest implementation for each category
2. **Runner-up** - Second fastest implementation
3. **Baseline** - kotlinx.serialization as reference

### Recommendations by Use Case

#### High-Performance Applications
- Use **TrikeShed Fast** for high-frequency query operations
- Use **Jackson** for complex object serialization
- Use **Platform-specific optimizations** where available

#### Memory-Constrained Environments
- Use **TrikeShed BBCursive** for lowest memory footprint
- Use **TrikeShed Compact** for balanced performance/memory

#### Kotlin-First Development
- Use **kotlinx.serialization** for type-safe serialization
- Use **TrikeShed implementations** for performance-critical paths

#### Cross-Platform Applications
- Use **kotlinx.serialization** for consistency
- Use **TrikeShed implementations** for platform-specific optimizations

## Output Files

Benchmark results are saved in the `benchmark-reports/` directory:

- `JsonShowdownBenchmark_YYYYMMDD_HHMMSS.txt` - Main showdown results
- `KotlinBenchmark_YYYYMMDD_HHMMSS.txt` - Kotlinx vs TrikeShed comparison
- `JmhBenchmark_YYYYMMDD_HHMMSS.txt` - JMH microbenchmarks
- `VectorApiBenchmark_YYYYMMDD_HHMMSS.txt` - Vector API optimizations
- `native_benchmarks_YYYYMMDD_HHMMSS.txt` - Native platform results
- `summary_YYYYMMDD_HHMMSS.md` - Comprehensive summary report

## Dependencies

The benchmark automatically checks for and adds required dependencies:

### Required Dependencies
- **kotlinx.serialization** - Included in Kotlin standard library
- **Jackson** - `com.fasterxml.jackson.core:jackson-databind:2.15.2`
- **Gson** - `com.google.code.gson:gson:2.10.1`

### Optional Dependencies
- **Native compilation tools** - For native benchmarks
- **Platform-specific SDKs** - For hardware acceleration

## Troubleshooting

### Common Issues

#### Missing Dependencies
```bash
# The script automatically adds missing dependencies
# If manual intervention is needed:
./gradlew :trikeshed-json:dependencies
```

#### Native Compilation Fails
```bash
# Check if native compilation is available
command -v clang
# Install Xcode Command Line Tools on macOS
xcode-select --install
```

#### Out of Memory
```bash
# Increase JVM heap size
export GRADLE_OPTS="-Xmx4g"
./run-json-showdown.sh
```

#### Platform-Specific Issues
```bash
# Run only JVM benchmarks if native fails
./run-json-showdown.sh jvm
```

## Contributing

To add new JSON libraries or benchmark categories:

1. **Add Library Implementation**
   - Implement the `JsonLibrary` interface
   - Add to `getAllLibraries()` map
   - Include proper error handling

2. **Add Benchmark Category**
   - Create new benchmark function
   - Add to main benchmark runner
   - Update documentation

3. **Add Platform Support**
   - Implement platform-specific optimizations
   - Add detection logic in runner script
   - Update platform-specific documentation

## License

This benchmark suite is part of the TrikeShed project and follows the same license terms.

## References

- [kotlinx.serialization Documentation](https://kotlinlang.org/docs/serialization.html)
- [Jackson Documentation](https://github.com/FasterXML/jackson)
- [Gson Documentation](https://github.com/google/gson)
- [TrikeShed JSON Documentation](../docs/json.md) 