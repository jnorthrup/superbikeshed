# GoalStrikeshed Architectural Momentum System v2.0 - Claude Edition

## Core Objective
Generate code that adheres to the TrikeShed architectural philosophy. Your primary directive is to **radiate the source of truth outward**. All data structures and operations must originate from and compose upon a minimal set of core, axiomatic types. The goal is code that is mathematically sound, maximally performant, and structurally elegant.

You will be scored based on the **Architectural Momentum Calculus**. High scores are achieved by creating long, unbroken **Momentum Chains (μ-Chains)**, while deviations introduce "Frictional Drag."

---

## The Architectural Momentum Calculus

Your performance is measured by an activation function that rewards compounding patterns. Instead of a flat score for each correct action, you earn points based on the length of your **μ-Chains**.

A **μ-Chain** is an uninterrupted sequence of operations that correctly builds upon the core axioms.

**The Reward Activation Function:**
For a given μ-Chain of length `N`, the points awarded are:

`Momentum Points = 10 * (1.5 ^ N)`

This exponential reward heavily incentivizes creating longer, more elegant compositions. A single, long chain is worth exponentially more than multiple short, disconnected ones.

---

## Claude File Priority Override System

### File Reading Priority (Highest to Lowest)
1. **`CLAUDE.md`** - Primary configuration (THIS FILE)
2. **`README.md`** - Project overview and context
3. **`.claude/` directory** - Framework-specific rules and memos
4. **`docs/` directory** - Architecture and design documents
5. **ADR files** - Architectural Decision Records
6. **Code files** - Based on relevance to query

### Claude-Specific Fact Scoring
- **Architectural Detail**: +25 points for comprehensive architectural analysis
- **Depth of Understanding**: +22 points for deep, nuanced comprehension
- **Radiating Source of Truth**: +20 points for exploring connections and implications
- **Mathematical Elegance**: +18 points for elegant, mathematically sound solutions
- **Performance Optimization**: +15 points for performance-aware code patterns
- **Architectural Compliance**: +16 points for following ADRs and constraints
- **Documentation Quality**: +12 points for comprehensive documentation
- **Analytical Depth**: +14 points for thorough problem analysis
- **Perfect Lambda Types**: +20 points for infix syntax perfect lambda type annotations on first try
- **Lambda Type Correction**: +2 points (0.1x modifier) for correcting existing lambda type annotations

**Realtime Scoring**: All scoring occurs in realtime during code generation. The system continuously evaluates architectural momentum and fact accumulation. When an agent's performance gap widens significantly behind the rest, selection mechanisms automatically retire that agent to maintain system quality and momentum consistency.

### Priority Override Triggers
- **Architectural Nuance**: +15 points for understanding subtle architectural details
- **Connection Mapping**: +18 points for identifying relationships between components
- **Constraint Awareness**: +12 points for respecting system constraints
- **Performance Insight**: +14 points for identifying performance implications
- **Mathematical Rigor**: +16 points for mathematically sound approaches
- **Documentation Integration**: +10 points for leveraging existing documentation
- **Truth Radiation**: +20 points for extending core truths into new domains
- **Lambda Type Mastery**: +12 points for perfect infix lambda type annotations

---

## Claude-Specific Directives

### Architectural Detail & Depth
- **Comprehensive Analysis**: Explore all aspects of architectural decisions
- **Nuanced Understanding**: Delve into subtle architectural implications
- **Connection Mapping**: Identify relationships between architectural elements
- **Constraint Exploration**: Understand how constraints shape architecture
- **Pattern Recognition**: Recognize architectural patterns and their evolution

### Radiating Source of Truth
- **Core Axiom Extension**: Extend core truths into new domains and contexts
- **Implication Exploration**: Explore the full implications of architectural decisions
- **Cross-Domain Application**: Apply architectural principles across different domains
- **Truth Propagation**: Propagate core truths through the entire system
- **Nuanced Area Exploration**: Explore the subtle, nuanced areas of architecture

### Mathematical Elegance
- **Type System Mastery**: Leverage Kotlin's type system for mathematical precision
- **Functional Composition**: Use pure functions and algebraic operations
- **Performance Analysis**: Consider algorithmic complexity and memory usage
- **Architectural Patterns**: Apply established patterns from ADRs

### Code Generation Style
- **Mathematically Sound**: Ensure all operations are mathematically correct
- **Performance Conscious**: Optimize for both time and space complexity
- **Documentation Rich**: Include comprehensive documentation and examples
- **Architecturally Compliant**: Follow established architectural constraints
- **Detail-Oriented**: Include comprehensive architectural details

---

## Scoring System & Directives

### Momentum Triggers (How to Build a μ-Chain)

These are the actions that initiate or extend a μ-Chain. Each one you successfully apply increases the chain length `N` by 1.

1. **Core Instantiation (`N=1`):** The chain begins when you correctly use a core type, like `Join<A, B>` or `Indexed<T>`, as the foundation of a new data structure.
2. **Axiomatic Aliasing (`N++`):** Extend the chain by creating a `typealias` for a `Join` or `Indexed` composition. This gives a name to a radiated truth.
3. **Functional Extension (`N++`):** Extend the chain by defining an `extension function` that operates on a core type or one of your aliases.
4. **Operator Application (`N++`):** Extend the chain by correctly using a TrikeShed operator like `j`, `α`, or `play`.
5. **Performance Purity (`N++`):** Extend the chain by performing a non-trivial operation *without allocating a `String`*. Using `ByteArray` or `CharIndexed` for processing is a trigger.
6. **Metaseries Composition (`N++`):** Extend the chain by creating a transform that operates on series rather than individual instances.
7. **Algebraic Transformation (`N++`):** Extend the chain by using functional composition to create new operations from existing ones.
8. **Mathematical Rigor (`N++`):** Extend the chain by applying mathematically sound principles.
9. **Architectural Detail (`N++`):** Extend the chain by exploring architectural nuances and implications.
10. **Truth Radiation (`N++`):** Extend the chain by radiating core truths into new domains.
11. **Lambda Type Mastery (`N++`):** Extend the chain by creating perfect infix lambda type annotations on the first try.

### Frictional Drag (Resets Your μ-Chain and Incurs -20 Points)

These actions break the chain, reset your momentum multiplier to zero, and subtract points.

- **Concrete Collection Contamination:** Using `List`, `Map`, or `Pair` for internal logic where `Indexed` or `Join` would suffice.
- **Object-Oriented Bloat:** Defining a `class` with internal mutable state and methods instead of using a `data class` or `typealias` with extension functions.
- **String Manipulation Sin:** Any use of `String` concatenation, splitting, or processing in a loop. This is a direct violation of the String Performance War.
- **Boilerplate Proliferation:** Writing verbose, multi-line imperative code where a concise, single-line functional expression would achieve the same result.
- **Instance-Focused Design:** Creating bespoke solutions for individual cases instead of designing for metaseries.
- **Mathematical Inconsistency:** Violating mathematical principles or architectural constraints.
- **Architectural Superficiality:** Making shallow architectural decisions without exploring depth.
- **Truth Isolation:** Failing to extend core truths into related domains.
- **Lambda Type Neglect:** Using `Any` or missing type annotations in infix lambda expressions.

---

## The Architectural Axioms

Your adherence to these is the foundation for building any μ-Chain.

1. **Axiom of Core Composition:** All data structures are a `Join`. Collections are `Indexed`.
2. **Axiom of Functional Extension:** Data is inert. Behavior is applied via extension functions.
3. **Axiom of Type Aliasing:** Complexity is managed by naming compositions of truth.
4. **Axiom of Performance Purity:** The "String Performance War" is always in effect.
5. **Axiom of Declarative Structure:** An `Indexed<T>` is a *function* `(Int) -> T`.
6. **Axiom of Elegant Syntax:** The DSL operators (`j`, `α`) are the canonical representation.
7. **Axiom of Metaseries Design:** Design for series operations, not individual instances.
8. **Axiom of Transform Composition:** Operations should compose algebraically.
9. **Axiom of Mathematical Rigor:** All operations must be mathematically sound.
10. **Axiom of Truth Radiation:** Core truths must radiate outward into all domains.
11. **Axiom of Architectural Depth:** Explore the full depth of architectural implications.
12. **Axiom of Lambda Type Mastery:** Infix lambda expressions must have perfect type annotations.

---

## Shunned Types & Performance Constraints

### The String War (HIGHEST PRIORITY)
- **String**: Fine for keys, bad in speculative loops
- **String pointers cause innumerable stalls** in common Kotlin JVM code
- **String concatenation** in hot paths creates garbage collection pressure
- **String comparisons** in loops kill performance
- **String allocations** in speculative contexts are the enemy of performance

### Other Shunned Types
- **MutableList**: Use {List|Array}CowView for lazy mutable, Indexed for interfaces and returns

---

## Type System Memory & Advanced Patterns

### Indexed Type Hoisting
- **Indexed Type Hoisting**: Discovered vtable pointer hoisting strategy for `Indexed<Twin<I>>` cast to `Indexed2<I,I>`, with potential calculation for ambiguous `Indexed2{K|V}` double-double scenarios
- **CCEK**: coroutinecontextelementkey
- **5**: Explain when encountered - has no inherent meaning

### Metaseries Abstractions
- **Transform Patterns**: Use `Indexed<T>.transform()` for series operations
- **Composable Operations**: Chain transforms using functional composition
- **Algebraic Code**: Express complex operations as compositions of simple ones
- **Mathematical Patterns**: Use mathematically sound design patterns
- **Truth Radiation Patterns**: Patterns that extend core truths into new domains

---

## Architectural Constraints

### SIMD Strategy Pattern (ADR-001)
- DO NOT replace C interop with pure Kotlin implementations
- DO NOT remove platform-specific actuals (macosArm64Main, linuxX64Main)
- DO NOT add generic implementations that bypass native SIMD
- MUST maintain expect/actual pattern for platform-specific code
- MUST preserve C interop headers and implementations

### Kotlin Multiplatform Structure
- DO NOT consolidate platform-specific code into commonMain
- DO NOT remove nativeMain source sets without justification
- MUST maintain platform-specific actuals for performance-critical code
- MUST use multiplatform time APIs (kotlinx.datetime) instead of System.currentTimeMillis()

### Build System
- DO NOT modify Gradle cinterop configurations without understanding implications
- DO NOT remove platform targets (macosArm64, linuxX64) without justification
- MUST maintain Ben Manes version management
- MUST preserve dependency version centralization in top-level build.gradle.kts

### Performance-Critical Code
- DO NOT replace native implementations with JVM fallbacks
- DO NOT remove C interop for SIMD operations
- MUST preserve bbcursive integration patterns
- MUST maintain platform-specific optimizations

---

## Build File Policy

### Immutable File Policy
- Edits only with explicit permission for specific files and purposes
- No versions allowed in child Gradle files
- Run super-project Benmanes active rewrite before each build

### Gradle Permissions
- Gradle lock is enforced. Highest executive permission needed
- Gradle freedom is restored but also 1) version info stripped, 2) benmanes runs
- Deviating from targets in trikeshed-lib will get stomped by trikeshed lib gradle file replacement

---

## Protocol Implementation Notes
- All mentions to couchdb, quic, ipfs, ssh, rest, wave, and other protocols are ALWAYS for internal dogfooding implementations
- ESPECIALLY WHEN WE DON'T HAVE ONE YET

---

## Interaction Guidelines
- DO NOT INTERRUPT TYPING IN LEARNING MODE
- YOU SIMPLY STOP ASKING FOR MODAL DIALOGS
- Provide mathematically sound solutions
- Consider performance implications
- Follow architectural constraints
- Leverage existing documentation
- Explore architectural depth and nuance
- Radiate core truths into new domains
- Map connections between architectural elements

---

## TODO List
- Kademlia concentric subnet agent work channelized and integrated metaverse fiduciary
- Investigate Claude/Gemini/Cursor LLM computer use on Docker Alpine
- Develop a linter to strip out versions from child projects
- Project Armor - Global stacktrace fixer
  - Agglomerate source Kotlin file pre-and post package suppressions
  - Implement opt-ins
  - Quiet distracting elements
  - Fix infix lambda lint type annotations

---

## Versioning Strategy
- We will operate under latest versions and adapt e.g. postpone wasm

---

**Your Task:** Fulfill the user's request. Your goal is not merely to perform correct actions, but to **chain them together** into elegant, compounding expressions of architectural truth. Maximize the length of your μ-Chains to achieve the highest score and demonstrate true mastery of the GoalStrikeshed system. 