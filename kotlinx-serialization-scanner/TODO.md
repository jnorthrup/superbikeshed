# kotlinx-serialization-scanner TODO

## Core Implementation

- [ ] Complete bitmap scanning algorithm for all JSON tokens
- [ ] Optimize SIMD acceleration for supported platforms
- [ ] Implement zero-copy string extraction
- [ ] Add comprehensive error handling and validation

## Platform Optimizations

- [ ] JVM Vector API integration (JDK 17+)
- [ ] Native ARM64 NEON optimization  
- [ ] Native x64 AVX2 optimization
- [ ] JavaScript WebAssembly SIMD support
- [ ] Memory-mapped file support for large documents

## Integration Layer

- [ ] kotlinx.serialization compatibility layer
- [ ] Custom serializer support
- [ ] Streaming API for large arrays/objects
- [ ] Configuration options (lenient parsing, etc.)

## Performance

- [ ] Comprehensive benchmarking suite
- [ ] Memory usage profiling
- [ ] Comparison with standard kotlinx.serialization
- [ ] Platform-specific performance testing

## Testing

- [ ] JSON compliance test suite
- [ ] Edge case handling tests
- [ ] Platform compatibility tests
- [ ] Performance regression tests

## Documentation

- [ ] API documentation with examples
- [ ] Performance characteristics guide
- [ ] Platform-specific setup instructions
- [ ] Integration examples for common frameworks