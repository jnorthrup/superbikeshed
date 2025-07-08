# RTODO - Reactor Build Error Analysis

## Current Status
- trikeshed-reactor has 278 compilation errors
- k2script dependency on non-existent trikeshed-services module fixed

## Systematic Error Analysis Approach

### 1. Error Categories to Extract
- [ ] Unresolved reference errors
- [ ] Type mismatch errors
- [ ] Platform-specific implementation errors
- [ ] Missing expect/actual declarations
- [ ] Import resolution failures

### 2. Stacktrace Segmentation Plan
1. Run build with --stacktrace
2. Extract each unique error type
3. Group by source file
4. Identify common patterns
5. Create fix priority order

### 3. Known Issues
- SelectableChannel has duplicate definitions
- ByteBuffer/PlatformByteBuffer confusion
- IOOperation expect/actual missing implementations
- UnaryAsyncReaction using undefined types

### 4. Fix Order
1. Core type definitions (IOOperation, SelectableChannel)
2. Platform-specific implementations
3. Higher-level abstractions (UnaryAsyncReaction)
4. Integration points with other modules

## Next Steps
1. Get full reactor build output with stacktraces
2. Segment errors by type and file
3. Fix core type issues first
4. Test incremental fixes