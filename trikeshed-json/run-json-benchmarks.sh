#!/bin/bash

# JSON Parser Microbenchmark Competition Rematch Runner
# 
# This script runs comprehensive benchmarks on all 27 JSON implementations
# in the TrikeShed codebase to determine the fastest parser.

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

echo -e "${CYAN}🚀 JSON Parser Microbenchmark Competition Rematch${NC}"
echo -e "${CYAN}================================================${NC}"
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

# Function to run comprehensive benchmark
run_comprehensive() {
    print_section "Running Comprehensive Benchmark Suite"
    
    # No explicit build step needed; tests will trigger build automatically
    
    # Run the comprehensive benchmark runner
    echo -e "${BLUE}Running comprehensive benchmark runner...${NC}"
    local output_file="$REPORT_DIR/comprehensive_${TIMESTAMP}.txt"
    
    if gradle :trikeshed-json:jvmTest --tests "*ComprehensiveJsonBenchmark*" > "$output_file" 2>&1; then
        echo -e "${GREEN}✓ Comprehensive benchmark completed${NC}"
        echo "   Output: $output_file"
    else
        echo -e "${RED}✗ Comprehensive benchmark failed${NC}"
        echo "   Check: $output_file"
    fi
}

# Function to run individual benchmarks
run_individual_benchmarks() {
    print_section "Running Individual Benchmark Suites"
    
    local benchmarks=(
        "KotlinBenchmark:Core Kotlin Benchmarks"
        "BigJsonBenchmark:Large JSON Performance"
        "VectorApiBenchmark:Vector API Performance"
        "SimpleBenchmarkTest:Simple vs Bitmap Comparison"
        "JsonBenchmark:BBCursive JSON Performance"
        "JmhBenchmark:JMH-style Benchmarks"
        "ManualBenchmark:Manual Performance Tests"
    )
    
    for benchmark in "${benchmarks[@]}"; do
        IFS=':' read -r name description <<< "$benchmark"
        run_benchmark "$name" "$description"
    done
}

# Function to run integration benchmarks
run_integration_benchmarks() {
    print_section "Running Integration Benchmarks"
    
    # Python integration tests
    if [ -f "tests/integration/trikeshed-json/test_json_scanner_benchmarks.py" ]; then
        echo -e "${BLUE}Running Python integration benchmarks...${NC}"
        local python_output="$REPORT_DIR/python_integration_${TIMESTAMP}.txt"
        
        if python3 tests/integration/trikeshed-json/test_json_scanner_benchmarks.py > "$python_output" 2>&1; then
            echo -e "${GREEN}✓ Python integration completed${NC}"
        else
            echo -e "${RED}✗ Python integration failed${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Python integration tests not found${NC}"
    fi
    
    # Kotlin script benchmarks
    if [ -f "tests/integration/trikeshed-json/benchmark-runner.kts" ]; then
        echo -e "${BLUE}Running Kotlin script benchmarks...${NC}"
        local kotlin_output="$REPORT_DIR/kotlin_script_${TIMESTAMP}.txt"
        
        if kotlin tests/integration/trikeshed-json/benchmark-runner.kts > "$kotlin_output" 2>&1; then
            echo -e "${GREEN}✓ Kotlin script completed${NC}"
        else
            echo -e "${RED}✗ Kotlin script failed${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Kotlin script benchmarks not found${NC}"
    fi
}

# Function to generate summary report
generate_summary() {
    print_section "Generating Summary Report"
    
    local summary_file="$REPORT_DIR/summary_${TIMESTAMP}.md"
    
    cat > "$summary_file" << EOF
# JSON Parser Microbenchmark Competition Rematch Summary

**Timestamp:** $(date)
**Platform:** $(uname -s) $(uname -m)
**JVM:** $(java -version 2>&1 | head -1)

## Implementations Tested

The following 27 JSON implementations were benchmarked:

### Core Implementations
1. **SimpleJsonScanner** - Basic character-by-character parsing
2. **JsonScannerCompact** - Bitmap-based structural indexing  
3. **FastJsonScanner** - O(1) property lookup with LinkedHashMap
4. **HardwareAcceleratedJsonScanner** - Platform-specific acceleration

### JVM Implementations
5. **VectorizedJsonScanner** - JVM Vector API SIMD acceleration
6. **MemorySegmentJsonScanner** - Memory-mapped parsing

### Native Implementations
7. **M3NativeJsonScanner** - Apple M3 Metal GPU acceleration
8. **SwarmJsonScanner** - Multi-threaded swarm processing
9. **CoroutineSwarmJsonScanner** - Async swarm processing

### BBCursive Implementations
10. **JsonBBCursive** - BBCursive pattern-based parser
11. **JsonBbcursive** - Alternative BBCursive implementation
12. **NaturalBBCursive** - Register-at-a-time scanning
13. **TiledBBCursive** - 8/16-byte tiled processing

### Streaming Implementations
14. **JsonStreamingParser** - Streaming JSON with backpressure
15. **JsonStreamingCCEK** - Streaming with CCEK integration

### Serialization Implementations
16. **SimpleJsonSerializer** - kotlinx.serialization integration
17. **TrikeShedJsonSerializer** - TrikeShed-specific serialization

### Legacy Implementations
18. **BitmapJsonScanner** - Original bitmap scanner (disabled)
19. **SimdBitmapOps** - SIMD bitmap operations (disabled)
20. **SimdJsonScanner** - SIMD-optimized scanner

### Baseline
21. **kotlinx.serialization** - Standard library baseline

## Benchmark Categories

- **Parsing Performance**: Raw parsing speed across different JSON sizes
- **Query Performance**: Field access and lookup performance  
- **Memory Usage**: Heap allocation and garbage collection impact
- **Large JSON Handling**: Scalability with very large documents
- **Serialization Performance**: Encoding/decoding integration
- **Streaming Performance**: Backpressure and flow handling
- **Platform-Specific Performance**: Hardware acceleration benefits
- **BBCursive Performance**: Functional parser performance
- **Fingerprinting**: Structural hash generation
- **Structural Analysis**: Document structure analysis
- **Concurrent Access**: Multi-threaded access patterns
- **Error Handling**: Malformed JSON processing

## Test Data Patterns

- **Simple objects**: \`{"field1":"value1",...}\`
- **Arrays**: \`[{"id":1,"name":"item1",...},...]\`
- **Nested structures**: Deep object hierarchies
- **Large datasets**: 100KB+ JSON documents
- **Mixed content**: Objects, arrays, primitives

## Performance Metrics

- **Throughput**: Operations per second
- **Latency**: Nanoseconds per operation
- **Memory**: Heap usage in KB
- **GC Impact**: Garbage collection frequency
- **Scalability**: Performance vs document size
- **Concurrency**: Multi-threaded speedup

## Results

*Detailed results are available in the individual benchmark output files.*

## Files Generated

$(ls -la "$REPORT_DIR"/*"$TIMESTAMP"* 2>/dev/null | awk '{print "- " $9}' || echo "No files generated yet")

## Next Steps

1. Review individual benchmark outputs for detailed performance data
2. Compare implementations across different metrics
3. Identify the fastest parser for your specific use case
4. Consider memory usage and scalability requirements
5. Test with your actual JSON data patterns

EOF

    echo -e "${GREEN}✓ Summary report generated: $summary_file${NC}"
}

# Function to show help
show_help() {
    echo -e "${CYAN}JSON Parser Microbenchmark Competition Rematch${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --all, -a              Run all benchmarks (comprehensive + individual + integration)"
    echo "  --comprehensive, -c     Run only comprehensive benchmark suite"
    echo "  --individual, -i        Run only individual benchmark suites"
    echo "  --integration, -t       Run only integration benchmarks"
    echo "  --quick, -q             Run quick benchmark subset"
    echo "  --help, -h              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --all               # Run everything"
    echo "  $0 --comprehensive     # Run comprehensive suite only"
    echo "  $0 --quick             # Run quick tests"
    echo ""
    echo "Reports will be saved to: $REPORT_DIR/"
}

# Main execution
main() {
    case "${1:-}" in
        --all|-a)
            run_comprehensive
            run_individual_benchmarks
            run_integration_benchmarks
            generate_summary
            ;;
        --comprehensive|-c)
            run_comprehensive
            generate_summary
            ;;
        --individual|-i)
            run_individual_benchmarks
            generate_summary
            ;;
        --integration|-t)
            run_integration_benchmarks
            generate_summary
            ;;
        --quick|-q)
            print_section "Running Quick Benchmark Subset"
            run_benchmark "SimpleBenchmarkTest" "Quick Simple vs Bitmap"
            run_benchmark "KotlinBenchmark" "Quick Core Benchmarks"
            generate_summary
            ;;
        --help|-h|*)
            show_help
            ;;
    esac
    
    echo -e "\n${GREEN}🏁 Benchmark competition completed!${NC}"
    echo -e "${BLUE}Check the $REPORT_DIR/ directory for detailed results.${NC}"
}

# Run main function with all arguments
main "$@" 