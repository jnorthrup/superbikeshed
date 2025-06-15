# CLAUDE.md: Tensor-Core Evolution Guide

> **Unified Planning**: See `/todo/trikeshed_todos.md` for detailed TrikeShed evolution tasks

Avoid any discussion or implementation of 'demo' functionality. Do not include 'println' statements that simulate or 'gaslight' successful operations.

## CRITICAL TYPE SYSTEM RULES

**SHUNNED TYPES - DEFER USE:**

- **`List<T>`** - Use `Series<T>` or primitive array preferred
- **`MutableList<T>`** - Use `Series<T>` with `α` transforms
- **`Pair<A,B>`** - Use `Join<A,B>` with `j` operator instead
Avoid the `trikeshed.collections.*` package due to its unconstrained algorithmic designs and potential for mutability/nullability. Refactor its usage slowly.

**MANDATORY PATTERNS:**

- the first step is to create and replace the model with taxonomical typealiases and enums down to the leaf attributes  
- **a j b** creates Join<A,B> - the ONLY composition operator
  //   e.g., val combinedSeries = seriesA j seriesB
  //        (conceptual: combines two Series into a Join structure)
- **series.α { transform }** - the ONLY transformation operator  
  //   e.g., val transformedSeries = mySeries.α { it * 2 }
  //        (conceptual: applies a transformation to elements, producing a new Series)
- **series.\`▶\`** (THE PLAY BUTTON) - gateway to AbstractList,Iterable<T> for .map and list
  **DO NOT CHANGE THE BACKTICKS - THEY ARE KOTLIN IDENTIFIER SYNTAX NOT MARKDOWN**
  //   e.g., val listRepresentation = mySeries.\`▶\`.toList()
  //        val mappedList = mySeries.\`▶\`.map { it.toString() }
  //        (conceptual: materializes a Series to a standard collection for specific operations)
- **@JvmInline value class** - the ONLY wrapper mechanism
- **typealias** - descriptive names for ANY OR ALL RECURRING primitives
- To use standard collection operations like `.map()`, first materialize the `Series` using `▶`, e.g., `mySeries.▶.map { ... }`

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

## BANNED PRACTICES - convert to TODOs or remove when un-DRY

**DEAD CODE ELIMINATION DIRECTIVE:**

- **Simulated Benchmarks**: Any performance metrics not from actual running code
- **Fake Demonstrations**: "Successful connections" that only simulate behavior
- **Mock Functionality**: Code that pretends to work without real implementation
- **Placeholder Responses**: Hardcoded "success" instead of real operations
- **Demo-Only Code**: Implementations that cannot perform real work

**MODULE CLUTTER DIRECTIVE:**
Avoid all forms of 'pretending' or 'demo' code. Use `TODO()` for incomplete implementations rather than placeholder logic.

**CORE ARCHITECTURE PRINCIPLES:**

- Strict adherence to a custom type system (Series<T>, Join<A,B>, α transforms, ▶ materialization, @JvmInline value class, typealias)
- Tensor-first columnar processing with Join<A,B> as the core composition mechanism
- Performance by design through explicit hot/cold paths and zero-cost abstractions
- Context-driven development using inline classes and CCEK for managing scope and dependencies
- Zero tolerance for simulated or non-functional code and module clutter
- Put nio target overrides into borg.trikeshed.nio

## DEVELOPMENT GUIDELINES

**Gradle and Build Management:**
Gradle modifications are prohibited unless explicitly instructed or a critical roadblock necessitates it.

- we only show multiplatform plugin which includes all others , and benmanes plugin versions
- our gradle should lack non-kotlin deps except by very crucial active development anchors
- No kotlin deps except moneyfan has xchange right now
- Versions and specifics should be in the top level gradle to reduce updates to the lower over time
- Multiplatform projects have only that plugin, and benmanes top-level
- We do not know of compose dependencies to include at this time

**Type System Enforcement:**

- typealiases are permanent definitions - you may not remove any
- `Tensor<T>` is `Join<IntArray,(IntArray)->T>`
- Cursor is trikeshed original code not tensor. Series<RowVewc>

**Kotlin Versioning and Platform Targets:**

- we are going to go with java 21, kotlin 2.1.21, and benmanes latest versions of the non-2.1.21 unlocked versions

**Serialization and Annotation Libs:**

- we dont use _kotlin_ serialization or the annotation libs yet and dont plan to

**Migration Tasks:**

- migrate the nio actuals to trikeshed.nio (DONE, verified via todo/trikeshed_todos.md and code structure)

**Testing Guidelines:**
When writing tests, avoid introducing unnecessary or poorly designed code ('turds').
Mock tests are permissible and should be designed to ensure code stability without falling into the 'gaslighting' category of simulated functionality.

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

- you will always proceed "without any destructive change"
- whatever thinking caused QuicInstant to have a Quic prefix needs to end for general reuse

## Workflow

one task does the (thin+-king) and planning for two tasks -- the first one starts up with the large context and starts to curate a smaller context loop adequate to anneal tests, docs, and code ; the other is architecting the integration and authoring fully informed tests and proofs, axiomatic and poignant, not boilerplate --- with the context it remains with.
second will be a lesser GDM capability if its still highcompetence with IKR (Incomplete knowledge, resources (to win!))

- when comments and unused decl are reasonably accurate specifications or designs, they are not dead

## Project Notes

**Dependencies:**

- trikeshed-core has been re-absorbed into Triekshed. The webpack alias 'trikeshed-ts' should be updated to point to the correct location in the Triekshed repository.

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

- no more unplanned  DCE, ever or version changes