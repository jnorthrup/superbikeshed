#!/bin/bash

# TrikeShed JSON Scanner Integration Tests Runner
# This script runs all integration tests for the JSON scanner

set -e

echo "=== TrikeShed JSON Scanner Integration Tests ==="
echo "Timestamp: $(date)"
echo "Platform: $(uname -s) $(uname -m)"
echo "Python version: $(python3 --version)"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run a test and track results
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo -e "${BLUE}Running: $test_name${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command"; then
        echo -e "${GREEN}✓ PASSED: $test_name${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ FAILED: $test_name${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo "Checking prerequisites..."

if ! command_exists python3; then
    echo -e "${RED}ERROR: python3 is required but not installed${NC}"
    exit 1
fi

if ! command_exists gradle; then
    echo -e "${YELLOW}WARNING: gradle not found, will use gradlew wrapper${NC}"
fi

# Check if we're in the right directory
if [ ! -f "gradlew" ] && [ ! -f "../gradlew" ]; then
    echo -e "${RED}ERROR: gradlew not found. Please run from project root.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"
echo

# Get the project root
if [ -f "gradlew" ]; then
    PROJECT_ROOT="."
else
    PROJECT_ROOT=".."
fi

# Test 1: Python benchmark tests
run_test "Python JSON Scanner Benchmarks" \
    "cd $PROJECT_ROOT && python3 tests/integration/trikeshed-json/test_json_scanner_benchmarks.py"

# Test 2: Python integration tests
run_test "Python JSON Scanner Integration" \
    "cd $PROJECT_ROOT && python3 tests/integration/trikeshed-json/test_json_scanner_integration.py"

# Test 3: Kotlin compilation
run_test "Kotlin Compilation" \
    "cd $PROJECT_ROOT && ./gradlew :trikeshed-json:compileKotlinJvm --console=plain --no-daemon"

# Test 4: Kotlin test compilation
run_test "Kotlin Test Compilation" \
    "cd $PROJECT_ROOT && ./gradlew :trikeshed-json:compileTestKotlinJvm --console=plain --no-daemon"

# Test 5: Run benchmarks with --benchmark flag
run_test "JSON Scanner Benchmarks (Detailed)" \
    "cd $PROJECT_ROOT && python3 tests/integration/trikeshed-json/test_json_scanner_benchmarks.py --benchmark"

# Test 6: Check for memory leaks (if psutil is available)
if python3 -c "import psutil" 2>/dev/null; then
    run_test "Memory Usage Tests" \
        "cd $PROJECT_ROOT && python3 -c \"
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'tests', 'integration', 'trikeshed-json'))
from test_json_scanner_benchmarks import JsonScannerBenchmarkTests
import unittest
unittest.main(argv=[''], exit=False, verbosity=0)
\""
else
    echo -e "${YELLOW}SKIPPED: Memory usage tests (psutil not available)${NC}"
    echo
fi

# Test 7: Vector API benchmarks (if Java 17+ is available)
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 17 ]; then
    echo -e "${BLUE}Running: Vector API Benchmarks${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ -f "$PROJECT_ROOT/tests/integration/trikeshed-json/run-vector-benchmarks.sh" ]; then
        if cd "$PROJECT_ROOT/tests/integration/trikeshed-json" && bash run-vector-benchmarks.sh >/dev/null 2>&1; then
            echo -e "${GREEN}✓ PASSED: Vector API Benchmarks${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo -e "${RED}✗ FAILED: Vector API Benchmarks${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        echo -e "${YELLOW}SKIPPED: Vector API Benchmarks (script not found)${NC}"
    fi
    echo
else
    echo -e "${YELLOW}SKIPPED: Vector API Benchmarks (Java 17+ required, found Java $JAVA_VERSION)${NC}"
    echo
fi

# Test 8: Performance regression test
run_test "Performance Regression Test" \
    "cd $PROJECT_ROOT && python3 -c \"
import sys
import os
import time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'tests', 'integration', 'trikeshed-json'))
from test_json_scanner_benchmarks import generate_json, SimpleJsonScanner, BitmapJsonScanner

# Generate test data
json_data = generate_json(100)

# Warm up
for _ in range(10):
    SimpleJsonScanner(json_data).scan_properties()
    scanner = BitmapJsonScanner(json_data)
    scanner.scan()
    scanner.get_properties()

# Performance test
start_time = time.time()
for _ in range(100):
    SimpleJsonScanner(json_data).scan_properties()
simple_time = time.time() - start_time

start_time = time.time()
for _ in range(100):
    scanner = BitmapJsonScanner(json_data)
    scanner.scan()
    scanner.get_properties()
bitmap_time = time.time() - start_time

# Assert performance thresholds
assert simple_time < 1.0, f'Simple scanner too slow: {simple_time:.4f}s'
assert bitmap_time < 1.0, f'Bitmap scanner too slow: {bitmap_time:.4f}s'
print(f'Performance test passed: Simple={simple_time:.4f}s, Bitmap={bitmap_time:.4f}s')
\""

# Summary
echo "=== Integration Test Summary ==="
echo "Total tests run: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ All integration tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some integration tests failed${NC}"
    exit 1
fi 