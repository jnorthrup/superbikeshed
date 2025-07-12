# Architectural Context Anchor

## Critical Design Decisions

### SIMD Strategy Pattern (ADR-001)
**CONTEXT**: High-performance parsing requires platform-specific SIMD optimization
**DECISION**: Use expect/actual pattern with C interop for native SIMD
**CONSEQUENCES**: Platform-specific implementations (Apple NEON/AMX, Linux SSE/AVX)

**DO NOT CHANGE**: This interface must remain stable for bbcursive integration
**DO NOT REPLACE**: C interop with pure Kotlin implementations

**Related Files**:
- `trikeshed-lib/src/commonMain/kotlin/borg/trikeshed/lib/simd/SimdStrategy.kt`
- `trikeshed-lib/src/macosArm64Main/kotlin/borg/trikeshed/lib/simd/SimdStrategy.kt`
- `trikeshed-lib/src/linuxX64Main/kotlin/borg/trikeshed/lib/simd/SimdStrategy.kt`
- `trikeshed-lib/src/macosArm64Main/cinterop/include/simd.h`
- `trikeshed-lib/src/linuxX64Main/cinterop/include/simd.h`

### Type System Patterns
**Indexed Type Hoisting**: Discovered vtable pointer hoisting strategy for `Indexed<Twin<I>>` cast to `Indexed2<I,I>`
**CCEK**: coroutinecontextelementkey
**Shunned Types**: String (fine for keys, bad in speculative loops), MutableList (use Indexed/CowView instead)

### Build System Constraints
**Gradle Permissions**: Highest executive permission needed
**Version Management**: No versions in child Gradle files, Ben Manes runs before each build
**Platform Targets**: macosArm64, linuxX64 only - deviating will get stomped

## Forbidden Patterns
- Replacing C interop with pure Kotlin for SIMD
- Consolidating platform-specific code into commonMain
- Using String in speculative loops
- Using MutableList instead of Indexed/CowView patterns
- Modifying cinterop configurations without understanding implications

## Required Patterns
- Use expect/actual for platform-specific implementations
- Maintain C interop for native SIMD operations
- Use Indexed<T> for mutable list interfaces and returns
- Use CowView for lazy mutable operations
- Use kotlinx.datetime instead of System.currentTimeMillis() 