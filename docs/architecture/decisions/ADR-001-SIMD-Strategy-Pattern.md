# ADR-001: SIMD Strategy Pattern with C Interop

## Status
Accepted

## Context
High-performance parsing in bbcursive requires platform-specific SIMD optimization. We need to balance performance with maintainability across multiple platforms (macosArm64, linuxX64).

## Decision
Use Kotlin Multiplatform expect/actual pattern with C interop for native SIMD operations.

## Consequences

### Positive
- Platform-specific SIMD implementations (Apple NEON/AMX, Linux SSE/AVX/AVX2)
- C interop provides direct access to native SIMD intrinsics
- Common interface allows bbcursive to use SIMD transparently
- Performance-critical operations remain in native code

### Negative
- Requires C interop setup for each platform
- More complex build configuration
- Need to maintain C stubs alongside Kotlin code

## Implementation Details
- `SimdStrategy` interface in `trikeshed-lib/src/commonMain/`
- Platform-specific actuals in `macosArm64Main` and `linuxX64Main`
- C interop headers in `cinterop/include/simd.h`
- C implementations in `cinterop/simd.c`

## Related
- BBCursiveSimdAutovec
- SimdStrategy interface
- cinterop/simd.h headers
- trikeshed-lib build.gradle.kts cinterop configuration

## DO NOT CHANGE
- Replace C interop with pure Kotlin implementations
- Add more platform targets without C interop
- Remove platform-specific actuals in favor of common implementation 