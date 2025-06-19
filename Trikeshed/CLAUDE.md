# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

**Core Build Commands:**
```bash
# Build with native access flags (required for NIO operations)
./gradlew build --console=plain --no-daemon

# JVM compilation only
./gradlew compileKotlinJvm --console=plain --no-daemon

# Run tests
./gradlew test --console=plain --no-daemon

# Clean build
./gradlew clean build --console=plain --no-daemon
```

**CRITICAL: Always use `--console=plain --no-daemon` with Gradle commands**

**Environment Setup:**
- Export `JAVA_OPTS="--enable-native-access=ALL-UNNAMED"` for JVM native access
- Use Java 21, Kotlin 2.1.21
- Multiplatform targets: JVM, JS, WASM, Native (platform-specific)

## Architecture Overview

**TrikeShed Core Type System:**
TrikeShed is built around a custom type system with three fundamental abstractions:

1. **Join<A,B>** - The only composition operator (`a j b`)
2. **Series<T>** - Defined as `Join<Int, (Int) -> T>` for tensor-first processing
3. **@JvmInline value class** - Zero-cost domain modeling

**Core Pattern Examples:**
```kotlin
// Composition with j operator
val pair = "name" j "value"  // Join<String, String>

// Series operations
val series = 10 j { i -> i * 2 }  // Series<Int>
val transformed = series.α { it + 1 }  // Transform with α

// Materialization for standard operations
val list = series.play.toList()  // Convert to List when needed
```

**Lightning SIMD JSON:**
- Uses `JsonBitmapSimd` with expect/actual multiplatform structure
- `JsonTensorFactory` provides Series<T> integration
- Replace broken JSON imports with `LightningJson` implementation

**Package Structure:**
- `borg.trikeshed.lib.*` - Core types (Join, Series, bridge patterns)
- `borg.trikeshed.net.*` - Network protocols (HTTP, QUIC)
- `borg.trikeshed.parse.*` - Parsing (Lightning JSON)
- `borg.trikeshed.reactor.*` - Async I/O reactor pattern
- `borg.trikeshed.isam.*` - Storage systems

## Critical Development Rules

**Type System Enforcement:**
- **NEVER use** `List<T>` or `MutableList<T>` - use `Series<T>`
- **NEVER use** `Pair<A,B>` - use `Join<A,B>` with `j` operator
- **ALWAYS use** `@JvmInline value class` for wrappers
- **ALWAYS use** `series.α { transform }` for transformations
- **ALWAYS use** `series.play` before standard collection operations

**Version Management:**
- **NEVER change versions** to fix build/code bugs
- Fix code to work with specified versions: Kotlin 2.1.21, Java 21
- Only kotlinx dependencies: coroutines-core:1.10.2, datetime:0.6.2, collections-immutable:0.4.0, atomicfu:0.27.0

**Bridge Pattern for Missing Symbols:**
When encountering unresolved references, add them to bridge files:
- Common interfaces/types in `src/commonMain/kotlin/borg/trikeshed/lib/bridge/`
- Platform-specific implementations in corresponding `actual` files
- Use `@JvmInline value class` for type safety

**Multiplatform expect/actual Pattern:**
```kotlin
// commonMain
expect object PlatformType {
    fun operation(): Result
}

// jvmMain  
actual object PlatformType {
    actual fun operation(): Result = /* JVM implementation */
}
```

## Development Integrity

**Real Implementation Mandate:**
- TrikeShed contains production-quality infrastructure (QUIC, HTTP, Kademlia DHT, ISAM storage)
- Honor existing working implementations - add TODO() stubs for missing features only
- Build upon existing systems without modification
- Performance-optimized with zero-cost abstractions

**Coding Style:**
- Concise, golf-style expressions preferred
- Type lambdas explicitly in Series operations
- Use `null` as elvis conditional for early returns
- Prefer 1-liners without braces
- Import packages with star (`import borg.trikeshed.lib.*`)

**Error Resolution Strategy:**
1. **Inline class violations** - Use single parameter or convert to `data class`
2. **Missing symbols** - Add to bridge with proper expect/actual structure  
3. **Type mismatches** - Use conversion functions (`toByteArray()`, `toBytesSeries()`)
4. **JSON issues** - Replace with Lightning SIMD JSON implementation

## Project Context

**Gradle Project Structure:**
- Top-level `superbikeshed` builds all subprojects
- Each subproject uses only `kotlin("multiplatform")` plugin
- Platform detection for native targets based on host OS/architecture
- No Spotless (not multiplatform compatible)

**Related Projects:**
- `ta4k/` - Trading analytics moved from TrikeShed core
- `nexus/` - AI/taxonomy features moved from TrikeShed core  
- `brokeshed/` - Alien types and external integrations
- `moneyfan/` - Financial data processing

**Key Dependencies:**
- No Kotlin serialization libraries (use TrikeShed native JSON)
- Minimal external dependencies except for critical development anchors
- Coroutines, datetime, and collections-immutable from kotlinx

This architecture prioritizes performance, type safety, and clean separation of concerns while maintaining production-grade reliability.