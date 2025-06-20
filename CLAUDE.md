# CLAUDE.md: TrikeShed Production Infrastructure Specification

> **Engineering Excellence**: TrikeShed represents production-grade distributed systems infrastructure

**CANDIDATE PROMISE**: I commit to building real, working implementations. No simulations, no placeholders disguised as functionality, no architectural modifications to existing working systems. I will add TODO() stubs for missing features and implement them systematically without touching proven code.

## CRITICAL TYPE SYSTEM RULES

**VERSION MANAGEMENT RULE:**

- NEVER change versions to fix build or code bugs
- Use ONLY the specified versions in this document
- If a build fails, fix the code to work with the specified versions
- Version changes require explicit approval and documentation

**SHUNNED TYPES - DEFER USE:**

- **`List<T>`** - Use `Series<T>` or primitive array preferred
- **`MutableList<T>`** - Use `Series<T>` with `α` transforms
- **`Pair<A,B>`** - Use `Join<A,B>` with `j` operator instead
- **trikeshed  '*.collections.*' package is inhernetly unconstrained algorithmic designs and ports, consume them slowly when refactoring out mutability and nullable** -

**MANDATORY PATTERNS:**

- the first step is to create and replace the model with taxonomical typealiases and enums down to the leaf attributes (2-ary tuples)
- **a j b** creates Join<A,B> - the ONLY composition operator
- **series.α { transform }** - the ONLY transformation operator  
- **series.\`▶\`** (THE PLAY BUTTON) - gateway to AbstractList,Iterable<T> for .map and list
  **DO NOT CHANGE THE BACKTICKS - THEY ARE KOTLIN IDENTIFIER SYNTAX NOT MARKDOWN**
- **@JvmInline value class** - the ONLY wrapper mechanism
- **typealias** - descriptive names for ANY OR ALL RECURRING primitives
- To use standard collection operations like `.map()`, first materialize the `Series` using `.play`, e.g., `mySeries.play.map { ... }`

## Ontological Typealiases and Value Classes

A cornerstone of TrikeShed's expressive and type-safe DSLs is the precise use of `@JvmInline value class` and `typealias`. This approach, which we term "Ontological Typealiases," aims to elevate primitive types or simple wrappers into meaningful domain-specific concepts.

**Principles:**

1. **Clarity of Intent:** Instead of passing raw `String` or `Int` values, we use types that explicitly state their purpose. For example, `HttpHeaderName` is more descriptive than `String` when representing an HTTP header's name.
2. **Type Safety:** `value class` provides compile-time type safety over raw primitives, preventing accidental misuse (e.g., passing a `HttpHeaderValue` where a `HttpHeaderName` is expected). While `typealias` provides weaker safety (it's just a name), it significantly improves readability.
3. **Zero-Cost Abstraction:** `@JvmInline value class` typically incurs no runtime overhead compared to using the underlying primitive type directly.
4. **Domain Modeling:** This allows us to model concepts from specifications (like IETF RFCs for networking protocols) directly in our type system, making the code a more accurate reflection of the domain.

**Examples from `borg.trikeshed.net.http.types`:**

- **Value Classes for Fundamental HTTP Types:**
  - `@JvmInline value class HttpVersion(val value: String)` - e.g., `HttpVersion("HTTP/1.1")`
  - `@JvmInline value class HttpRequestPath(val value: String)` - e.g., `HttpRequestPath("/index.html?q=foo")`
  - `@JvmInline value class HttpStatusCode(val value: Int)` - e.g., `HttpStatusCode(200)`
  - `@JvmInline value class HttpReasonPhrase(val value: String)` - e.g., `HttpReasonPhrase("OK")`
  - `@JvmInline value class HttpHeaderName(val value: String)` - e.g., `HttpHeaderName("Content-Type")`
  - `@JvmInline value class HttpHeaderValue(val value: String)` - e.g., `HttpHeaderValue("application/json")`

This disciplined approach ensures that TrikeShed's APIs are not just performant but also highly readable and robust against common errors.

## PRODUCTION IMPLEMENTATION REQUIREMENTS

**REAL IMPLEMENTATION MANDATE:**

- **Working Infrastructure**: TrikeShed contains production-quality QUIC, HTTP, Kademlia DHT, ISAM storage, and trading systems
- **Functional Protocols**: Network protocols implement actual RFC specifications, not simulations
- **Live Data Processing**: Market data, cursors, and series operations handle real data streams
- **Cryptographic Security**: Key management and secure messaging use genuine cryptographic implementations
- **Performance Optimized**: Zero-cost abstractions and tensor-first processing for production workloads

**DEVELOPMENT INTEGRITY DIRECTIVE:**
Honor existing working implementations. Add TODO() stubs for missing features only. Implement TODO() systematically without modifying proven code.

**CORE ARCHITECTURE PRINCIPLES:**

- Strict adherence to a custom type system (Series<T>, Join<A,B>, α transforms, play materialization, @JvmInline value class, typealias)
- Tensor-first columnar processing with Join<A,B> as the core composition mechanism
- Performance by design through explicit hot/cold paths and zero-cost abstractions
- Context-driven development using inline classes and CCEK for managing scope and dependencies
- Preserve and extend existing working implementations
- Put nio target overrides into borg.trikeshed.nio

## DEVELOPMENT GUIDELINES

**Gradle and Build Management:**
Gradle modifications are prohibited unless explicitly instructed or a critical roadblock necessitates it. Spotless is not used as it is not multiplatform compatible.

- we only show multiplatform plugin which includes all others , and benmanes plugin versions
- our gradle should lack non-kotlin deps except by very crucial active development anchors
- No kotlin deps except moneyfan has xchange right now
- Versions and specifics should be in the top level gradle to reduce updates to the lower over time
- Multiplatform projects have only that plugin, and benmanes top-level
- We do not know of compose dependencies to include at this time

**Dependencies:**

```
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
}
```

**Type System Enforcement:**

- typealiases are permanent definitions - you may not remove any
- `Tensor<T>` is `Join<IntArray,(IntArray)->T>`
- Cursor is trikeshed original code not tensor. Series<RowVewc>

**Kotlin Versioning and Platform Targets:**

- we are going to go with java 21, kotlin 2.1.21, and benmanes latest versions of the non-2.1.21 unlocked versions

**Serialization and Annotation Libs:**

- we dont use *kotlin* serialization or the annotation libs yet and dont plan to

**Migration Tasks:**

- migrate the nio actuals to trikeshed.nio (DONE, verified via todo/trikeshed_todos.md and code structure)

**Testing Guidelines:**
Write tests that verify actual functionality of working systems. Test abstractions serve legitimate architectural purposes - they are not "turds" but essential components of the production infrastructure. Focus tests on validating real behavior, not mocking away the systems being tested.

**Coding Style Preferences:**

- **null as elvis conditional**: short-circuit early and often
- **1-liners, no braces**: Golf expressions preferred, concise elegant code
- **Typealiased lambdas**: Must always type lambdas in Series and throughout
- **Ternary Kotlin hack**: `yes!` love it, embrace creative shortcuts
- **Null safety shortcuts**: `nz,z` - can't live without it
- **Kotlin native**: `d, l, m, or n` - tiny bit of peek and poke
- **Debug calls**: `-ea` only debug calls preferred in logging (they get inlined out)
- **Immaculate specification**: Fully articulated taxonomical typealias models
- **DSEL architecture**: Immaculate DSELs with CCEK and enum clusters of expertise
- **Domain clustering**: Enum clusters organize domain code and expertise
- **Dynamic capture**: Most dynamic capture-based anonymous inner classes as subject of iterative JIT counters for locality
- **Inline class preference**: If a class isn't an inline class, why not?
- **Gossip tool**: Will use `gossip_about` llm tool as soon as we can write it and loft the Jetsam (Note: As of 2024-07-26, this tool does not appear to be implemented in dgm/langchain_tools.py or documented elsewhere.)

**Core Behavior Guidelines:**

- **Additive Development Only**: Build upon existing working systems without modification
- **Preserve All Working Code**: Existing implementations represent proven solutions to complex problems
- **Respect Architectural Decisions**: Domain-specific naming and abstractions serve important purposes

## Workflow

one task does the (thin+-king) and planning for two tasks -- the first one starts up with the large context and starts to curate a smaller context loop adequate to anneal tests, docs, and code ; the other is architecting the integration and authoring fully informed tests and proofs, axiomatic and poignant, not boilerplate --- with the context it remains with.
second will be a lesser GDM capability if its still highcompetence with IKR (Incomplete knowledge, resources (to win!))

- **Preserve Design Documentation**: Comments and declarations contain valuable design specifications and architectural knowledge

## Project Notes

**Dependencies:**

- trikeshed-core has been re-absorbed into Triekshed. The webpack alias 'trikeshed-ts' should be updated to point to the correct location in the Triekshed repository.

## Migration Memories

- all of the superbikeshed code is migrating to trikeshed and so  it all needs taxonomical typealiases down to the leafnode.  we no longer have  a DCE policy, anthropic can sell bridges all day long again

# important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.# Project Notes

## Dependencies

- trikeshed-core has been re-absorbed into Triekshed. The webpack alias 'trikeshed-ts' should be updated to point to the correct location in the Triekshed repository.

- dont use apply diff when a tool that has coordinate based insertion is available for edit
- `apply_diff` is a banned tool

# Gradle tree

we follow the kotlin KMP 2.1.21 example which can be browsed

our top level superbikeshed builds the lower projects and holds the benmanes version plugin in false mode. nothing else welcome.

for our second tier projects we use a 1-target native from the env CPU/PLATOFORM we run kotlin now.  we should adopt default source hierarchy for native asnd posix middle but test for local platform.

we care about wasm node+browser,jvm,native targets occasionally demoing in typescript  
plugins {
    kotlin("multiplatform") version "2.1.21"
}
-- mandatory gradle subproject base
kotlin {
    jvm()
    wasmJs {
        browser()
        nodejs()
    }
    // Example test for platform tuple
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows && isArm64 -> mingwArm64()
        isWindows -> mingwX64()
    } 
}

## CORE MEMORY

**UN-ALTERABLE CORE TYPE SYSTEM** - Always Available in Every Context:

**FOUNDATION JOIN INTERFACE** (`borg.trikeshed.lib.Join`):

```kotlin
package borg.trikeshed.lib

interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)
    
    companion object {
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A get() = a
            override val b: B get() = b
        }
    }
}

typealias Twin<T> = Join<T, T>
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b)
```

**SERIES TYPE SYSTEM** (`borg.trikeshed.lib.Series`):

```kotlin
package borg.trikeshed.lib

typealias Series<T> = Join<Int, (Int) -> T>
typealias Series2<A, B> = Series<Join<A, B>>

// Essential operators
val <T> Series<T>.size: Int get() = a
operator fun <T> Series<T>.get(i: Int): T = b(i)
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = size j { i -> xform(this[i]) }

// Play materialization - ENSHRINED PATTERN
val <T> Series<T>.play: IterableSeries<T> get() = this as? IterableSeries ?: IterableSeries(this)

@JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A> {
    override fun iterator(): Iterator<A> = s.iterator()
    val size: Int get() = s.size
    operator fun get(i: Int): A = s[i]
}
```

**CURSOR TYPE SYSTEM** (`borg.trikeshed.cursor.Cursor`):

```kotlin
package borg.trikeshed.cursor

typealias RowVec = Series2<Any?, () -> ColumnMeta>
typealias Cursor = Series<RowVec>
```

**TOP 10 PRODUCTION TYPEALIASES:**

```kotlin
// Core data abstractions
typealias RowVec = Series2<Any?, () -> ColumnMeta>               // borg.trikeshed.cursor
typealias Cursor = Series<RowVec>                                // borg.trikeshed.cursor  
typealias Twin<T> = Join<T, T>                                   // borg.trikeshed.lib
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>            // borg.trikeshed.lib

// Network and routing
typealias Series<T> =  Join<Int,(Int)->T>                   
typealias Route<TNum> = Join<NUID<TNum>, Address>               // gk.kademlia.include

// Data serialization  
typealias EncoderDispatch = DoubleDispatchTable<RecordMeta, ColumnData, ByteArray>  // borg.trikeshed.isam

// JSON parsing (TrikeShed native)
typealias JsElement = Join<Twin<Int>, Series<Int>>               // borg.trikeshed.parse.json
typealias JsIndex = Join<Twin<Int>, Series<Char>>               // borg.trikeshed.parse.json  
typealias JsContext = Join<JsElement, Series<Char>>             // borg.trikeshed.parse.json

// Tensor shapes (TODO: implement)
typealias Shape = IntArray                                       // borg.trikeshed.lib (MISSING)

// Taxonomy and AI
typealias ContentId = String                                     // borg.trikeshed.taxonomy
typealias ConceptVector = List<Float>                           // borg.trikeshed.taxonomy
typealias AttentionScore = Double                               // borg.trikeshed.taxonomy
```

- **No unplanned version changes** - Preserve working configurations
- **Import packages with star** in TrikeShed - Established convention
- **TrikeShed serialization only** - Use JsonScanner, not external libraries

## RUNTIME MEMORY

- remove all kotlin serialization references as soon as you see one
