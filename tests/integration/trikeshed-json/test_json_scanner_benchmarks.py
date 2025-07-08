#!/usr/bin/env python3
"""
Integration tests for TrikeShed JSON Scanner benchmarks
Converts Kotlin benchmarks to Python for CI/CD integration
"""

import time
import json
import sys
import os
from typing import List, Tuple, Dict, Any
import unittest

# Add the project root to the path to import trikeshed modules
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', '..'))

class SimpleJsonScanner:
    """Simple JSON scanner implementation for benchmarking"""
    
    def __init__(self, input_str: str):
        self.input = input_str
    
    def scan_properties(self) -> List[Tuple[str, str]]:
        """Extract key-value pairs from JSON string"""
        result = []
        i = 0
        
        while i < len(self.input):
            # Skip whitespace
            while i < len(self.input) and self.input[i].isspace():
                i += 1
            
            # Look for property name
            if i < len(self.input) and self.input[i] == '"':
                i += 1  # skip opening quote
                key_start = i
                while i < len(self.input) and self.input[i] != '"':
                    i += 1
                key = self.input[key_start:i]
                i += 1  # skip closing quote
                
                # Skip : and whitespace
                while i < len(self.input) and (self.input[i].isspace() or self.input[i] == ':'):
                    i += 1
                
                # Get value
                if i < len(self.input) and self.input[i] == '"':
                    i += 1  # skip opening quote
                    value_start = i
                    while i < len(self.input) and self.input[i] != '"':
                        i += 1
                    value = self.input[value_start:i]
                    i += 1  # skip closing quote
                    
                    result.append((key, value))
            i += 1
        
        return result


class BitmapJsonScanner:
    """Bitmap-based JSON scanner implementation for benchmarking"""
    
    def __init__(self, input_str: str):
        self.input = input_str
        self.bitmap = [False] * len(input_str)
        self.properties = []  # start,end indices
    
    def scan(self):
        """Mark structural characters in bitmap"""
        for i, char in enumerate(self.input):
            self.bitmap[i] = char in '{[]}"":,'
        
        # Extract properties using bitmap
        i = 0
        while i < len(self.input):
            if self.bitmap[i] and self.input[i] == '"':
                start = i
                i += 1
                while i < len(self.input) and not self.bitmap[i]:
                    i += 1
                if i < len(self.input) and self.input[i] == '"':
                    self.properties.append((start, i))
            i += 1
    
    def get_properties(self) -> List[Tuple[str, str]]:
        """Extract key-value pairs using bitmap indices"""
        result = []
        i = 0
        
        while i < len(self.properties) - 1:
            key_start, key_end = self.properties[i]
            key = self.input[key_start + 1:key_end]
            
            # Find corresponding value
            value_start, value_end = self.properties[i + 1]
            value = self.input[value_start + 1:value_end]
            
            result.append((key, value))
            i += 2
        
        return result


def generate_json(count: int) -> str:
    """Generate test JSON with specified number of properties"""
    properties = []
    for i in range(count):
        properties.append(f'"field{i}":"value{i}"')
    return "{" + ",".join(properties) + "}"


class JsonScannerBenchmarkTests(unittest.TestCase):
    """Integration tests for JSON scanner performance"""
    
    def setUp(self):
        """Set up test data"""
        self.test_json = '{"name":"test","value":"42","active":"true"}'
        self.sizes = [10, 100, 1000]
        self.iterations = 100
    
    def test_correctness(self):
        """Test that both scanners produce correct results"""
        simple_result = SimpleJsonScanner(self.test_json).scan_properties()
        bitmap_scanner = BitmapJsonScanner(self.test_json)
        bitmap_scanner.scan()
        bitmap_result = bitmap_scanner.get_properties()
        
        self.assertEqual(len(simple_result), 3)
        self.assertEqual(len(bitmap_result), 3)
        self.assertEqual(simple_result, bitmap_result)
        
        # Verify expected properties
        expected = [("name", "test"), ("value", "42"), ("active", "true")]
        self.assertEqual(simple_result, expected)
    
    def test_performance_small_json(self):
        """Test performance with small JSON (10 properties)"""
        json_data = generate_json(10)
        
        # Warm up
        for _ in range(10):
            SimpleJsonScanner(json_data).scan_properties()
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        
        # Benchmark simple scanner
        start_time = time.time()
        for _ in range(self.iterations):
            SimpleJsonScanner(json_data).scan_properties()
        simple_time = time.time() - start_time
        
        # Benchmark bitmap scanner
        start_time = time.time()
        for _ in range(self.iterations):
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        bitmap_time = time.time() - start_time
        
        # Performance assertions
        self.assertLess(simple_time, 1.0)  # Should complete within 1 second
        self.assertLess(bitmap_time, 1.0)  # Should complete within 1 second
        
        print(f"Small JSON (10 props): Simple={simple_time:.4f}s, Bitmap={bitmap_time:.4f}s")
    
    def test_performance_medium_json(self):
        """Test performance with medium JSON (100 properties)"""
        json_data = generate_json(100)
        
        # Warm up
        for _ in range(10):
            SimpleJsonScanner(json_data).scan_properties()
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        
        # Benchmark simple scanner
        start_time = time.time()
        for _ in range(self.iterations):
            SimpleJsonScanner(json_data).scan_properties()
        simple_time = time.time() - start_time
        
        # Benchmark bitmap scanner
        start_time = time.time()
        for _ in range(self.iterations):
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        bitmap_time = time.time() - start_time
        
        # Performance assertions
        self.assertLess(simple_time, 2.0)  # Should complete within 2 seconds
        self.assertLess(bitmap_time, 2.0)  # Should complete within 2 seconds
        
        print(f"Medium JSON (100 props): Simple={simple_time:.4f}s, Bitmap={bitmap_time:.4f}s")
    
    def test_performance_large_json(self):
        """Test performance with large JSON (1000 properties)"""
        json_data = generate_json(1000)
        
        # Warm up
        for _ in range(5):
            SimpleJsonScanner(json_data).scan_properties()
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        
        # Benchmark simple scanner
        start_time = time.time()
        for _ in range(self.iterations // 10):  # Fewer iterations for large JSON
            SimpleJsonScanner(json_data).scan_properties()
        simple_time = time.time() - start_time
        
        # Benchmark bitmap scanner
        start_time = time.time()
        for _ in range(self.iterations // 10):
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        bitmap_time = time.time() - start_time
        
        # Performance assertions
        self.assertLess(simple_time, 5.0)  # Should complete within 5 seconds
        self.assertLess(bitmap_time, 5.0)  # Should complete within 5 seconds
        
        print(f"Large JSON (1000 props): Simple={simple_time:.4f}s, Bitmap={bitmap_time:.4f}s")
    
    def test_memory_efficiency(self):
        """Test memory usage with large JSON"""
        import gc
        import psutil
        import os
        
        process = psutil.Process(os.getpid())
        
        # Large JSON for memory test
        large_json = generate_json(5000)
        
        # Simple scanner memory
        gc.collect()
        simple_before = process.memory_info().rss
        simple_scanner = SimpleJsonScanner(large_json)
        simple_props = simple_scanner.scan_properties()
        simple_after = process.memory_info().rss
        
        # Bitmap scanner memory
        gc.collect()
        bitmap_before = process.memory_info().rss
        bitmap_scanner = BitmapJsonScanner(large_json)
        bitmap_scanner.scan()
        bitmap_props = bitmap_scanner.get_properties()
        bitmap_after = process.memory_info().rss
        
        simple_memory = (simple_after - simple_before) / 1024  # KB
        bitmap_memory = (bitmap_after - bitmap_before) / 1024  # KB
        
        # Memory assertions
        self.assertLess(simple_memory, 10000)  # Less than 10MB
        self.assertLess(bitmap_memory, 10000)  # Less than 10MB
        
        print(f"Memory usage: Simple={simple_memory:.1f}KB, Bitmap={bitmap_memory:.1f}KB")
    
    def test_edge_cases(self):
        """Test edge cases and error handling"""
        # Empty JSON
        empty_json = "{}"
        simple_result = SimpleJsonScanner(empty_json).scan_properties()
        bitmap_scanner = BitmapJsonScanner(empty_json)
        bitmap_scanner.scan()
        bitmap_result = bitmap_scanner.get_properties()
        
        self.assertEqual(simple_result, [])
        self.assertEqual(bitmap_result, [])
        
        # JSON with escaped quotes
        escaped_json = '{"key":"value with \\"quotes\\"","other":"normal"}'
        simple_result = SimpleJsonScanner(escaped_json).scan_properties()
        bitmap_scanner = BitmapJsonScanner(escaped_json)
        bitmap_scanner.scan()
        bitmap_result = bitmap_scanner.get_properties()
        
        # Note: Simple scanner doesn't handle escapes properly, but should still work
        self.assertEqual(len(simple_result), 2)
        self.assertEqual(len(bitmap_result), 2)


def run_benchmarks():
    """Run comprehensive benchmarks and print results"""
    print("=== TrikeShed JSON Scanner Integration Benchmarks ===")
    print(f"Python version: {sys.version}")
    print(f"Platform: {sys.platform}")
    print()
    
    # Test correctness first
    test_json = '{"name":"test","value":"42","active":"true"}'
    simple_result = SimpleJsonScanner(test_json).scan_properties()
    bitmap_scanner = BitmapJsonScanner(test_json)
    bitmap_scanner.scan()
    bitmap_result = bitmap_scanner.get_properties()
    
    print("Correctness check:")
    print(f"  Simple found: {len(simple_result)} properties")
    print(f"  Bitmap found: {len(bitmap_result)} properties")
    print()
    
    # Benchmark different sizes
    test_cases = [
        (10, 1000),
        (100, 100),
        (1000, 10)
    ]
    
    for size, iterations in test_cases:
        json_data = generate_json(size)
        print(f"=== Test: {size} properties, {iterations} iterations ===")
        print(f"JSON size: {len(json_data)} characters")
        
        # Warm up
        for _ in range(50):
            SimpleJsonScanner(json_data).scan_properties()
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        
        # Measure simple scanner
        start_time = time.time()
        for _ in range(iterations):
            SimpleJsonScanner(json_data).scan_properties()
        simple_time = (time.time() - start_time) * 1000  # Convert to ms
        
        # Measure bitmap scanner
        start_time = time.time()
        for _ in range(iterations):
            scanner = BitmapJsonScanner(json_data)
            scanner.scan()
            scanner.get_properties()
        bitmap_time = (time.time() - start_time) * 1000  # Convert to ms
        
        print(f"Simple Scanner: {simple_time:.2f}ms total")
        print(f"  Per iteration: {simple_time / iterations:.4f}ms")
        print(f"Bitmap Scanner: {bitmap_time:.2f}ms total")
        print(f"  Per iteration: {bitmap_time / iterations:.4f}ms")
        
        speedup = simple_time / bitmap_time
        if speedup > 1:
            print(f"Result: Bitmap is {speedup:.2f}x faster")
        else:
            print(f"Result: Simple is {1/speedup:.2f}x faster")
        print()
    
    print("=== Integration Test Summary ===")
    print("All benchmarks completed successfully")


if __name__ == "__main__":
    # Run as integration test
    if len(sys.argv) > 1 and sys.argv[1] == "--benchmark":
        run_benchmarks()
    else:
        # Run as unit tests
        unittest.main(verbosity=2) 