# TrikeShed JSON Scanner Integration Tests

This directory contains comprehensive integration tests for the TrikeShed JSON Scanner, including performance benchmarks, memory usage tests, and Kotlin compilation verification.

## Overview

The integration tests ensure that:
- The JSON scanner compiles correctly
- Performance meets acceptable thresholds
- Memory usage is reasonable
- Edge cases are handled properly
- The complete workflow functions as expected

## Test Files

### Python Tests

- **`test_json_scanner_benchmarks.py`** - Performance benchmarks and correctness tests
  - Tests both simple and bitmap-based JSON scanners
  - Measures performance across different JSON sizes
  - Validates memory usage
  - Handles edge cases

- **`test_json_scanner_integration.py`** - Kotlin integration tests
  - Tests Kotlin compilation
  - Verifies basic functionality
  - Tests performance characteristics
  - Validates memory usage
  - Tests edge cases
  - Tests complete integration workflow

### Kotlin Benchmarks (Moved from trikeshed-json/)

- **`benchmark-runner.kts`** - Original Kotlin benchmark runner
- **`detailed-benchmark.kts`** - Detailed performance analysis
- **`run-vector-benchmarks.sh`** - Vector API benchmarks (requires Java 17+)

### Test Runner

- **`run_integration_tests.sh`** - Comprehensive test runner script

## Running the Tests

### Quick Test
```bash
# Run Python benchmark tests
python3 tests/integration/trikeshed-json/test_json_scanner_benchmarks.py

# Run with detailed benchmarks
python3 tests/integration/trikeshed-json/test_json_scanner_benchmarks.py --benchmark
```

### Full Integration Test Suite
```bash
# Run all integration tests
./tests/integration/trikeshed-json/run_integration_tests.sh
```

### Individual Tests
```bash
# Python integration tests
python3 tests/integration/trikeshed-json/test_json_scanner_integration.py

# Kotlin compilation test
./gradlew :trikeshed-json:compileKotlinJvm

# Vector API benchmarks (Java 17+)
cd trikeshed-json && bash run-vector-benchmarks.sh
```

## Test Categories

### 1. Performance Tests
- **Small JSON (10 properties)**: < 1 second for 100 iterations
- **Medium JSON (100 properties)**: < 2 seconds for 100 iterations  
- **Large JSON (1000 properties)**: < 5 seconds for 10 iterations

### 2. Memory Tests
- **Memory usage**: < 10MB for 5000 properties
- **Memory leaks**: No significant memory growth over iterations

### 3. Correctness Tests
- **Property extraction**: Correct key-value pairs
- **Edge cases**: Empty JSON, null values, escaped characters
- **Type handling**: Strings, numbers, booleans, null

### 4. Integration Tests
- **Kotlin compilation**: Code compiles without errors
- **Workflow**: Complete scan → extract → query → evidence flow
- **API compatibility**: All public APIs work as expected

## Performance Benchmarks

The benchmarks compare two JSON scanner implementations:

### Simple Scanner
- Direct character-by-character parsing
- Minimal memory allocation
- Good for small to medium JSON

### Bitmap Scanner  
- Pre-processes structural characters
- Uses bitmap for fast navigation
- Better for large JSON with complex structure

### Expected Results
- **Small JSON**: Simple scanner typically faster
- **Large JSON**: Bitmap scanner may be faster
- **Memory**: Bitmap scanner uses more memory but enables faster queries

## Prerequisites

- Python 3.7+
- Java 8+ (for Kotlin compilation)
- Java 17+ (for Vector API benchmarks)
- Gradle (or gradlew wrapper)

### Optional Dependencies
- `psutil` - For detailed memory usage tracking
- `jdk.incubator.vector` - For SIMD acceleration benchmarks

## CI/CD Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run JSON Scanner Integration Tests
  run: |
    ./tests/integration/trikeshed-json/run_integration_tests.sh
```

## Troubleshooting

### Common Issues

1. **Gradle not found**: Use `./gradlew` instead of `gradle`
2. **Java version issues**: Vector API requires Java 17+
3. **Memory tests fail**: Install `psutil` with `pip install psutil`
4. **Compilation errors**: Check Kotlin dependencies in `build.gradle.kts`

### Debug Mode
```bash
# Run with verbose output
python3 -v tests/integration/trikeshed-json/test_json_scanner_benchmarks.py

# Run individual test methods
python3 -c "
from tests.integration.trikeshed_json.test_json_scanner_benchmarks import JsonScannerBenchmarkTests
import unittest
unittest.main(argv=[''], exit=False, verbosity=2)
"
```

## Contributing

When adding new tests:

1. Follow the existing test structure
2. Include both performance and correctness assertions
3. Add appropriate error handling
4. Update this README with new test descriptions
5. Ensure tests pass in CI environment

## Performance Thresholds

The tests enforce these performance thresholds:

- **Compilation**: < 60 seconds
- **Small JSON processing**: < 1 second for 100 iterations
- **Medium JSON processing**: < 2 seconds for 100 iterations
- **Large JSON processing**: < 5 seconds for 10 iterations
- **Memory usage**: < 10MB for 5000 properties

These thresholds ensure the JSON scanner remains performant across different use cases. 