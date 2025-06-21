# TrikeShed Project Instructions

## Memory: CCEK Meaning

- CCEK means nothing other than CoroutineContextElementKey

## Memory: Typealias Taxonomical Ontology Design

- Before adding new design to the code, design the Typealias taxonomical ontology according to the specification language
- Leverage `Join`, and `MetaSeries` derived types like `Series` and `LongSeries`
- **PRIORITY**: MetaSeries → Indexed refactoring (see below)

## Memory: Project Setup

- before creating new code open trikeshed CoreTypes.kt first so its in the context
#memory   
-use borg.trikeshed.lib.z and nz for cmpz brevity 

## MetaSeries → Indexed Refactoring Plan

You've pinpointed a crucial and subtle point of conceptual friction in the TrikeShed type system. Your association of "MetaSeries" with a "ring architecture" or specific "access geometries" (like linear/associative) is exactly why the name, while functional, is architecturally misleading.

It imposes a **linear** connotation ("Series") onto a structure that is fundamentally more abstract. The power of `Join<A, (A) -> T>` is that it's a pure, functional, indexed collection where the index type `A` can define *any* access geometry—be it the linear geometry of an `Int`, the associative geometry of a `String` key, or the spatial geometry of a `Shape`.

This is a "so bad it needs to switch" situation, not because the implementation is wrong, but because the **name itself is a conceptual liability** that pollutes the otherwise pure architecture.

### The Problem: A Misleading Name

-   **The Thing:** `typealias MetaSeries<A, T> = Join<A, (A) -> T>`
-   **The Flaw (The "Bad"):** The name "MetaSeries" incorrectly implies that all specializations are inherently sequential or "series-like." This creates confusion when defining non-linear structures like associative maps or tensors, forcing a mental leap that contradicts the name. It constrains thinking and muddies the architectural purity.

### The Better Thing You Offer: A More Abstract, Truthful Name

The codebase needs a more fundamental name that captures the essence of this universal metaclass: **an indexed collection defined by its index type.**

-   **The Better Thing:** A new name that sheds the linear connotation. I propose `Indexed<A, T>`.
-   **The Refined Definition:**
    ```kotlin
    /**
     * ## Indexed<A, T> - The Universal Indexed Collection Metaclass
     *
     * Indexed<A,T> is the true foundation of the TrikeShed type system. It represents
     * a functionally-defined collection of elements of type `T` that are accessed
     * via an index of type `A`.
     *
     * This metaclass enables type-safe "realm separation," where the index type `A`
     * defines the access geometry (e.g., linear, associative, spatial).
     */
    typealias Indexed<A, T> = Join<A, (A) -> T>
    ```

### Applying the Switch: Refactoring the Realms

By replacing `MetaSeries` with `Indexed`, the "realm specializations" become conceptually clear and precise.

#### **Before (Confusing):**

-   `typealias Series<T> = MetaSeries<Int, T>` *(An integer-indexed "meta-series"?)*
-   `typealias Tensor<T> = MetaSeries<Shape, T>` *(A shape-indexed "meta-series"? How is a tensor a series?)*
-   `typealias Dictionary<T> = MetaSeries<String, T>` *(An associative-keyed "meta-series"? This is the most confusing.)*

#### **After (Clear and Awesome):**

-   `typealias Series<T> = Indexed<Int, T>`
    -   **Meaning:** A collection indexed by an Integer. Perfect.
-   `typealias Tensor<T> = Indexed<Shape, T>`
    -   **Meaning:** A collection indexed by a Shape. Perfect.
-   `typealias Twin<T> = Indexed<Boolean, T>`
    -   **Meaning:** A collection indexed by a Boolean. Perfect.
-   `typealias Dictionary<T> = Indexed<String, T>`
    -   **Meaning:** A collection indexed by a String. Perfect.

### Proposal: The Weights Calculus In Action

**Replace the definition in `Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt`.**

**Old Code (The "Bad" Abstraction):**
```kotlin
// In CoreTypes.kt

typealias MetaSeries<A, T> = Join<A, (A) -> T>
typealias Series<T> = MetaSeries<Int, T>
typealias Tensor<T> = MetaSeries<Shape, T>
typealias Twin<T> = MetaSeries<Boolean, T>
// ... and so on
```

**New Code (The "Better Thing You Offer"):**
```kotlin
// In Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt

/**
 * Universal functional collection indexed by type `A` containing elements of type `T`.
 * This is the true foundation metaclass, defining an "access realm" via type `A`.
 */
typealias Indexed<A, T> = Join<A, (A) -> T>

/**
 * ## Series<T> - The Integer Realm
 * A collection indexed by an Int. The classic linear series.
 */
typealias Series<T> = Indexed<Int, T>

/**
 * ## Tensor<T> - The Shape Realm
 * A collection indexed by a Shape (a Series<Int>). A multi-dimensional structure.
 */
typealias Tensor<T> = Indexed<Shape, T>

/**
 * ## Twin<T> - The Boolean Realm
 * A collection indexed by a Boolean. A pair of values.
 */
typealias Twin<T> = Indexed<Boolean, T>

// ... all other definitions that used MetaSeries now use Indexed
```

This single, strategic change clarifies the entire type system, making it more intuitive, honest, and powerful without altering any of the underlying mechanics. You have correctly identified a point of leverage where a small change yields a massive improvement in architectural clarity.

## Core Types Consolidation Priority

**Critical Issue**: Multiple, conflicting definitions of core data structures exist across the codebase (e.g., `borg.trikeshed.lib.Series`, `moneyfan.trikeshed.Series`, `com.google.trike.series.Series`). This is a classic architectural code smell leading to confusion and bugs.

### **CRITICAL Proposal: Establish Single Source of Truth**

**Establish `Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt` as the canonical source** for `Series`, `Join`, and all foundational types.

**Action Items**:
1. **Remove all duplicate definitions** from other modules (`moneyfan`, `k2script`, etc.)
2. **Have all modules import directly from `Trikeshed`**
3. **Benefits**: Drastically improves maintainability and consistency across entire project

This refactoring is **foundational** to architectural integrity and must be prioritized.

---

# TrikeShed Modular Architecture - Updated Implementation Status

## New Modular Architecture (Post-Refactoring)

The codebase has been refactored into a clean, layered architecture to resolve the "Big Ball of Mud" anti-pattern:

```
superbikeshed/
├── trikeshed-kernel/           # 🏗️  FOUNDATIONAL LAYER (Zero Dependencies)
│   ├── src/commonMain/kotlin/borg/trikeshed/
│   │   ├── lib/                # Core data structures
│   │   │   ├── series/         # Series.kt - Canonical Series type
│   │   │   └── join/           # Join.kt - Canonical Join type
│   │   ├── num/                # Core numeric types
│   │   │   ├── BigInt.kt       # BigInt implementation
│   │   │   └── BigDecimal.kt   # BigDecimal implementation
│   │   ├── parse/              # Parsing utilities
│   │   │   ├── json/           # JsonParser.kt
│   │   │   └── csv/            # CSVUtil.kt
│   │   ├── isam/               # Storage formats
│   │   │   └── meta/           # IOMemento.kt, TypeEvidence.kt
│   │   ├── tilting/            # Compression utilities
│   │   │   └── zran/           # kzran.kt
│   │   ├── common/             # Common utilities
│   │   │   └── collections/    # s_.kt, BinarySearch.kt
│   │   └── cursor/             # Cursor abstractions
│   └── build.gradle.kts        # Zero internal Trikeshed deps
│
├── Trikeshed/                  # 🚀 APPLICATION LAYER (Depends on kernel)
│   ├── src/commonMain/kotlin/borg/trikeshed/
│   │   ├── reactor/            # Concurrent components
│   │   ├── net/                # Network abstractions
│   │   ├── services/           # Application services
│   │   ├── acapulco/           # High-level orchestrators
│   │   ├── io/                 # I/O abstractions
│   │   ├── nio/                # NIO utilities
│   │   ├── git/                # Git integration
│   │   ├── storage/            # Storage management
│   │   ├── rl/                 # Reinforcement learning
│   │   ├── qol/                # Quality of life utilities
│   │   ├── taxonomy/           # Classification systems
│   │   ├── reflection/         # Reflection utilities
│   │   └── [shared components] # parse/, num/, isam/, tilting/, common/
│   └── build.gradle.kts        # Depends on :trikeshed-kernel
│
├── nexus/                      # 🤖 AGENTIC INTEGRATION
├── k2script/                   # 📜 SCRIPTING FRAMEWORK
├── ta4k/                       # 📊 TECHNICAL ANALYSIS
├── moneyfan/                   # 💰 TRADING BOT
├── rtsgame/                    # 🎮 REAL-TIME STRATEGY GAME
├── spacegraph/                 # 🌐 GRAPH DATABASE
└── [other modules...]
```

## Architecture Principles

### 1. **trikeshed-kernel** (Foundation Layer)
- **Rule:** Zero dependencies on any other Trikeshed module
- **Contents:** Canonical data types, core algorithms, parsing utilities
- **Dependencies:** Only Kotlin stdlib and minimal external libraries (kotlinx-datetime)
- **Purpose:** Provides the foundational building blocks for all higher-level components

### 2. **Trikeshed** (Application Layer)
- **Rule:** One-way dependency on trikeshed-kernel
- **Contents:** Concurrent components, application logic, high-level orchestrators
- **Dependencies:** trikeshed-kernel + external libraries
- **Purpose:** Implements the application-specific functionality using kernel primitives

## Implementation Status by Component

### ✅ **trikeshed-kernel** (Foundation Layer)
- [x] **Core Data Structures**
  - [x] Series.kt - Canonical Series type implementation
  - [x] Join.kt - Canonical Join type implementation
  - [x] BinarySearch.kt - Search algorithms
  - [x] s_.kt - Utility functions

- [x] **Numeric Types**
  - [x] BigInt.kt - Arbitrary precision integers
  - [x] BigDecimal.kt - Arbitrary precision decimals
  - [x] RoundingMode.kt - Rounding mode implementations

- [x] **Parsing Utilities**
  - [x] JsonParser.kt - JSON parsing (core implementation)
  - [x] CSVUtil.kt - CSV parsing utilities

- [x] **Storage Formats**
  - [x] IOMemento.kt - Storage format definitions
  - [x] TypeEvidence.kt - Type system components

- [x] **Compression**
  - [x] kzran.kt - Zran compression utilities

- [x] **Platform Support**
  - [x] JVM implementations (BigInt.jvm.kt, BigDecimal.jvm.kt)
  - [x] JS implementations (BigInt.js.kt, BigDecimal.js.kt)
  - [x] WASM support configured
  - [x] Native platform detection

### 🔄 **Trikeshed** (Application Layer)
- [x] **Concurrent Components**
  - [x] reactor/ - Reactor pattern implementation
  - [x] net/ - Network abstractions and protocols

- [x] **Application Services**
  - [x] services/ - Service layer implementations
  - [x] acapulco/ - High-level application orchestrators

- [x] **I/O and Storage**
  - [x] io/ - I/O abstractions
  - [x] nio/ - NIO utilities
  - [x] storage/ - Storage management

- [x] **Specialized Components**
  - [x] git/ - Git integration
  - [x] rl/ - Reinforcement learning
  - [x] qol/ - Quality of life utilities
  - [x] taxonomy/ - Classification systems
  - [x] reflection/ - Reflection utilities

- [ ] **Remaining Refactoring**
  - [ ] Move remaining foundational components from Trikeshed to kernel
  - [ ] Remove duplicate implementations across modules
  - [ ] Update all imports to use kernel types
  - [ ] Ensure clean dependency boundaries

### 🚧 **Integration Modules**
- [x] **nexus/** - Agentic integration framework
- [x] **k2script/** - Scripting framework
- [x] **ta4k/** - Technical analysis toolkit
- [x] **moneyfan/** - Trading bot implementation
- [x] **rtsgame/** - Real-time strategy game
- [x] **spacegraph/** - Graph database implementation

## Build System Enforcement

### ✅ **Gradle Configuration**
- [x] `settings.gradle.kts` - Includes both modules
- [x] `trikeshed-kernel/build.gradle.kts` - Zero internal dependencies
- [x] `Trikeshed/build.gradle.kts` - Depends on trikeshed-kernel
- [x] Platform-specific source sets configured
- [x] WASM target support added

### 🔄 **Dependency Management**
- [x] Clean one-way dependency enforced
- [x] Kernel has minimal external dependencies
- [x] Application layer can depend on kernel
- [ ] Remove any remaining circular dependencies

## Next Steps for Complete Modularization

### Phase 1: Clean Up Remaining Issues
1. **Fix Build Errors**
   - [ ] Resolve any remaining unresolved references
   - [ ] Fix syntax errors in moved files
   - [ ] Ensure all imports are correct

2. **Complete Component Migration**
   - [ ] Move any remaining foundational components to kernel
   - [ ] Remove duplicate implementations
   - [ ] Update all references to use kernel types

### Phase 2: Validation and Testing
1. **Architecture Validation**
   - [ ] Verify no circular dependencies exist
   - [ ] Confirm kernel has zero internal dependencies
   - [ ] Test that application layer can access all kernel types

2. **Build System Testing**
   - [ ] Test clean builds on all platforms
   - [ ] Verify WASM compilation works
   - [ ] Test dependency resolution

### Phase 3: Documentation and Cleanup
1. **Update Documentation**
   - [ ] Update API documentation to reflect new structure
   - [ ] Create migration guides for existing code
   - [ ] Document the new architecture principles

2. **Code Cleanup**
   - [ ] Remove any BROKEN/ files once issues are resolved
   - [ ] Clean up any temporary files
   - [ ] Update any hardcoded paths or references

## Benefits of New Architecture

1. **Clean Dependencies**: No more circular dependencies or "Big Ball of Mud"
2. **Testability**: Kernel can be tested independently
3. **Reusability**: Kernel can be used by other projects
4. **Maintainability**: Clear separation of concerns
5. **Build Performance**: Parallel compilation of independent modules
6. **Type Safety**: Canonical types prevent conflicts

## Legacy Components (Pre-Refactoring)

The following components existed before the modularization and may need updates:

### HTTP/1.1 Implementation
- Found in `trikeshed-core/src/commonMain/kotlin/borg/trikeshed/net/http/client/HttpClientConnection.kt`
- Basic HTTP/1.1 client connection handler
- Supports request serialization and response parsing
- Has error handling and connection management
- Uses NIO for socket operations

### HTTP/2 Implementation
- Found in `quic_http3_server/http2_protocol.py`
- Basic HTTP/2 server implementation
- Supports TLS
- Has basic request handling

### HTTP/3 (QUIC) Implementation
- Found in `quic_http3_server/` directory
- Has server implementation with TLS support
- Includes testing capabilities
- Has WebTransport support

### Download Management
- Found in `ta4k/bin/fetchtrades.sh`
- Uses aria2c for downloads
- Supports concurrent downloads
- Has basic error handling

### Testing Capabilities
- Found in `quic_http3_server/abusive_tests/`
- Has performance testing
- Includes protocol testing
- Supports concurrent session testing

## Implementation Priorities (Updated)

1. **Phase 1 (Current - Modularization)**
   - [x] Create trikeshed-kernel module
   - [x] Move foundational components
   - [x] Set up clean dependency structure
   - [ ] Complete migration and fix build issues

2. **Phase 2 (Next - Integration)**
   - [ ] Update all modules to use kernel types
   - [ ] Implement missing foundational components
   - [ ] Add comprehensive testing
   - [ ] Performance optimization

3. **Phase 3 (Advanced Features)**
   - [ ] Advanced protocol support
   - [ ] Enhanced security features
   - [ ] Comprehensive monitoring
   - [ ] Complete documentation

4. **Phase 4 (Ecosystem)**
   - [ ] Plugin system
   - [ ] Custom protocol support
   - [ ] Advanced integrations
   - [ ] Performance optimizations

The new modular architecture provides a solid foundation for future development while maintaining backward compatibility and improving code organization.
