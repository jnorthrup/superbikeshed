# TrikeShed io_uring Integration

This directory contains non-functional placeholder code for Linux io_uring integration using TrikeShed patterns.

## Source

Ported from columnar repository commit `45dd83c413786d1bd4ea8c51c2d4fabaf253b5d4` which trimmed the working io_uring implementation.

## TrikeShed Patterns Applied

- **Series<T>** instead of raw C arrays and pointers
- **Join<A,B>** with `j` operator for composition 
- **α transforms** for data transformations
- **▶ operator** for materialization to standard collections when needed
- **@JvmInline value class** for zero-cost wrappers

## Current Status

⚠️ **NON-FUNCTIONAL**: These files contain TODO placeholders and will not compile or run.

## Future Implementation

When io_uring integration is implemented, it should:

1. Use Kotlin/Native C interop for Linux kernel syscalls
2. Map C structures to TrikeShed Series/Join patterns  
3. Implement async operations using TrikeShed composition
4. Provide zero-copy buffer management through Series abstractions

## Files

- `IoUringTest.kt` - Main io_uring abstraction using TrikeShed patterns
- `UringHelpers.kt` - Helper functions for memory management and setup
- `README.md` - This documentation

## Original Implementation

The original working implementation was removed in columnar commit 45dd83c. 
It included:

- Full Linux io_uring C interop
- cat_main.kt - file reading using io_uring
- helpers.kt - memory allocation and ring setup
- Extensive test suite with various io_uring operations

## Integration Goals

Future integration should maintain TrikeShed principles:

- **Lazy by Default**: Operations should be lazy until materialized
- **Join Everywhere**: No Pair/Tuple types, only Join with j operator  
- **Alpha Transforms**: Use α for all data transformations
- **Zero-Cost Abstractions**: Performance through inlining and value classes
- **Tensor-First**: Build toward columnar processing patterns

## Testing

Tests should follow TrikeShed guidelines:
- No simulated/mock functionality
- Real operations only  
- Use Series/Join patterns throughout
- Verify through materialization with ▶ operator when necessary