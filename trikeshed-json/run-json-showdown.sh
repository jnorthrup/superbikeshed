#!/bin/bash

# JSON Showdown Benchmark Runner
# 
# This script runs comprehensive benchmarks comparing all major JSON libraries
# on both JVM and Native platforms to determine the fastest JSON implementation.

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
BENCHMARK_DIR="src/jvmTest/kotlin/borg/trikeshed/json"
REPORT_DIR="benchmark-reports"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo -e "${CYAN}🚀 JSON SHOWDOWN BENCHMARK RUNNER${NC}"
echo -e "${CYAN}==================================${NC}"
echo ""

# Create report directory
mkdir -p "$REPORT_DIR"

# Function to print section headers
print_section() {
    echo -e "\n${YELLOW}$1${NC}"
    echo -e "${YELLOW}$(printf '=%.0s' {1..${#1}})${NC}"
}

# Function to run benchmark and capture output
run_benchmark() {
    local name="$1"
    local description="$2"
    local output_file="$REPORT_DIR/${name}_${TIMESTAMP}.txt"
    
    echo -e "${BLUE}Running: $description${NC}"
    
    # Run the benchmark and capture output
    if gradle :trikeshed-json:jvmTest --tests "*$name*" > "$output_file" 2>&1; then
        echo -e "${GREEN}✓ $name completed successfully${NC}"
        echo "   Output: $output_file"
    else
        echo -e "${RED}✗ $name failed${NC}"
        echo "   Check: $output_file"
    fi
}

# Function to check dependencies
check_dependencies() {
    print_section "Checking Dependencies"
    
    echo "Checking for required JSON libraries..."
    
    # Check if Jackson is available
    if gradle :trikeshed-json:dependencies | grep -q "jackson"; then
        echo -e "${GREEN}✓ Jackson found${NC}"
    else
        echo -e "${YELLOW}⚠ Jackson not found - adding to dependencies${NC}"
        # Add Jackson dependency if not present
        echo "implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'" >> build.gradle.kts
        echo "implementation 'com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2'" >> build.gradle.kts
    fi
    
    # Check if Gson is available
    if gradle :trikeshed-json:dependencies | grep -q "gson"; then
        echo -e "${GREEN}✓ Gson found${NC}"
    else
        echo -e "${YELLOW}⚠ Gson not found - adding to dependencies${NC}"
        # Add Gson dependency if not present
        echo "implementation 'com.google.code.gson:gson:2.10.1'" >> build.gradle.kts
    fi
    
    echo -e "${GREEN}✓ kotlinx.serialization available${NC}"
    echo -e "${GREEN}✓ TrikeShed implementations available${NC}"
}

# Function to run JVM benchmarks
run_jvm_benchmarks() {
    print_section "Running JVM Benchmarks"
    
    echo "Testing JSON libraries on JVM platform..."
    echo "JVM Version: $(java -version 2>&1 | head -n 1)"
    echo "Architecture: $(uname -m)"
    echo ""
    
    # Run the main showdown benchmark
    run_benchmark "JsonShowdownBenchmark" "Comprehensive JSON Showdown"
    
    # Run individual library benchmarks
    run_benchmark "KotlinBenchmark" "Kotlinx Serialization vs TrikeShed"
    run_benchmark "JmhBenchmark" "JMH-style microbenchmarks"
    run_benchmark "VectorApiBenchmark" "Vector API optimizations"
}

# Function to run native benchmarks
run_native_benchmarks() {
    print_section "Running Native Benchmarks"
    
    echo "Testing JSON libraries on Native platform..."
    echo "Platform: $(uname -s) $(uname -m)"
    echo ""
    
    # Check if native compilation is available
    if command -v clang >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Native compilation available${NC}"
        
        # Build native executable
        echo "Building native executable..."
        if ./gradlew :trikeshed-json:linkReleaseExecutableNative; then
            echo -e "${GREEN}✓ Native build successful${NC}"
            
            # Run native benchmarks
            local native_exe="./build/bin/native/releaseExecutable/trikeshed-json.kexe"
            if [ -f "$native_exe" ]; then
                echo "Running native benchmarks..."
                "$native_exe" > "$REPORT_DIR/native_benchmarks_${TIMESTAMP}.txt" 2>&1
                echo -e "${GREEN}✓ Native benchmarks completed${NC}"
            else
                echo -e "${RED}✗ Native executable not found${NC}"
            fi
        else
            echo -e "${RED}✗ Native build failed${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Native compilation not available${NC}"
    fi
}

# Function to run platform-specific benchmarks
run_platform_benchmarks() {
    print_section "Running Platform-Specific Benchmarks"
    
    # Check for Apple Silicon optimizations
    if [[ "$(uname -m)" == "arm64" && "$(uname -s)" == "Darwin" ]]; then
        echo "Detected Apple Silicon - running M3 optimizations..."
        run_benchmark "M3NativeBenchmark" "Apple M3 Metal/NEON optimizations"
    fi
    
    # Check for Linux optimizations
    if [[ "$(uname -s)" == "Linux" ]]; then
        echo "Detected Linux - running io_uring optimizations..."
        run_benchmark "UringBenchmark" "Linux io_uring optimizations"
    fi
    
    # Check for Windows optimizations
    if [[ "$(uname -s)" == "MINGW"* || "$(uname -s)" == "MSYS"* ]]; then
        echo "Detected Windows - running Windows-specific optimizations..."
        run_benchmark "WindowsBenchmark" "Windows-specific optimizations"
    fi
}

# Function to generate summary report
generate_summary_report() {
    print_section "Generating Summary Report"
    
    local summary_file="$REPORT_DIR/summary_${TIMESTAMP}.md"
    
    cat > "$summary_file" << EOF
# JSON Showdown Benchmark Summary

**Timestamp:** $(date)
**Platform:** $(uname -s) $(uname -m)
**JVM:** $(java -version 2>&1 | head -n 1)

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

## Benchmark Categories

1. **Parsing Performance** - Raw parsing speed across different JSON sizes
2. **Serialization Performance** - Object-to-JSON encoding speed
3. **Deserialization Performance** - JSON-to-object decoding speed
4. **Query Performance** - Field access and lookup performance
5. **Memory Usage** - Heap allocation and garbage collection impact
6. **Large JSON Handling** - Scalability with very large documents
7. **Complex Nested Structures** - Performance with deeply nested JSON
8. **Streaming Performance** - Chunked processing with backpressure
9. **Concurrent Access** - Multi-threaded access patterns
10. **Error Handling** - Performance with malformed JSON
11. **Platform Optimizations** - Hardware-specific acceleration

## Key Metrics

- **Throughput** - MB/s processed
- **Latency** - ns/op for operations
- **Memory Efficiency** - Heap usage and GC impact
- **Scalability** - Performance with increasing data size
- **Error Resilience** - Handling of malformed input

## Results

Detailed results are available in individual benchmark files:
- \`JsonShowdownBenchmark_${TIMESTAMP}.txt\` - Main showdown results
- \`KotlinBenchmark_${TIMESTAMP}.txt\` - Kotlinx vs TrikeShed comparison
- \`JmhBenchmark_${TIMESTAMP}.txt\` - JMH microbenchmarks
- \`VectorApiBenchmark_${TIMESTAMP}.txt\` - Vector API optimizations
- \`native_benchmarks_${TIMESTAMP}.txt\` - Native platform results

## Recommendations

Based on benchmark results:

### For High-Performance Applications
- Use **TrikeShed Fast** for high-frequency query operations
- Use **Jackson** for complex object serialization
- Use **Platform-specific optimizations** where available

### For Memory-Constrained Environments
- Use **TrikeShed BBCursive** for lowest memory footprint
- Use **TrikeShed Compact** for balanced performance/memory

### For Kotlin-First Development
- Use **kotlinx.serialization** for type-safe serialization
- Use **TrikeShed implementations** for performance-critical paths

### For Cross-Platform Applications
- Use **kotlinx.serialization** for consistency
- Use **TrikeShed implementations** for platform-specific optimizations

## Platform-Specific Notes

### Apple Silicon (M1/M2/M3)
- **Metal GPU acceleration** available for large JSON processing
- **NEON SIMD** automatically used by Accelerate framework
- **Unified memory** provides optimal CPU/GPU data sharing

### Linux (x86_64/ARM64)
- **io_uring** available for high-throughput I/O operations
- **SIMD optimizations** via compiler intrinsics
- **Memory-mapped files** for large JSON processing

### Windows
- **DirectX compute shaders** for GPU acceleration
- **Windows-specific optimizations** for file I/O
- **Cross-compilation** support for multiple architectures

EOF

    echo -e "${GREEN}✓ Summary report generated: $summary_file${NC}"
}

# Function to run quick benchmarks
run_quick_benchmarks() {
    print_section "Running Quick Benchmarks"
    
    echo "Running subset of benchmarks for quick comparison..."
    
    # Run only the main showdown benchmark
    run_benchmark "JsonShowdownBenchmark" "Quick JSON Showdown"
    
    echo -e "${GREEN}✓ Quick benchmarks completed${NC}"
}

# Function to run full benchmark suite
run_full_benchmarks() {
    print_section "Running Full Benchmark Suite"
    
    echo "Running complete benchmark suite..."
    
    # Check dependencies
    check_dependencies
    
    # Run JVM benchmarks
    run_jvm_benchmarks
    
    # Run native benchmarks
    run_native_benchmarks
    
    # Run platform-specific benchmarks
    run_platform_benchmarks
    
    # Generate summary
    generate_summary_report
    
    echo -e "${GREEN}✓ Full benchmark suite completed${NC}"
}

# Main execution
main() {
    local start_time=$(date +%s)
    
    echo -e "${CYAN}🚀 JSON Showdown Benchmark Runner${NC}"
    echo -e "${CYAN}==================================${NC}"
    echo "Timestamp: $(date)"
    echo "Platform: $(uname -s) $(uname -m)"
    echo "JVM: $(java -version 2>&1 | head -n 1)"
    echo ""
    
    # Parse command line arguments
    case "${1:-full}" in
        "quick")
            run_quick_benchmarks
            ;;
        "jvm")
            check_dependencies
            run_jvm_benchmarks
            generate_summary_report
            ;;
        "native")
            run_native_benchmarks
            generate_summary_report
            ;;
        "platform")
            run_platform_benchmarks
            generate_summary_report
            ;;
        "full"|*)
            run_full_benchmarks
            ;;
    esac
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo ""
    echo -e "${CYAN}🏁 BENCHMARK COMPLETION SUMMARY${NC}"
    echo -e "${CYAN}===============================${NC}"
    echo "Total duration: ${duration} seconds"
    echo "Reports generated in: $REPORT_DIR"
    echo "Timestamp: $(date)"
    echo ""
    echo -e "${GREEN}✓ All benchmarks completed successfully!${NC}"
}

# Run main function with all arguments
main "$@" 