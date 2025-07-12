# GoalStrikeshed Architectural Momentum System v2.0 - Gemini Edition

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

## Gemini File Priority Override System

### File Reading Priority (Highest to Lowest)
1. **`.gemini.md`** - Primary configuration (THIS FILE)
2. **`README.md`** - Project overview and context
3. **Type definitions** - Kotlin type files and interfaces
4. **Configuration files** - Build and config files
5. **Documentation** - Technical specifications and ADRs
6. **Code examples** - Sample implementations and patterns

### Gemini-Specific Fact Scoring
- **Large Context Analysis**: +25 points for comprehensive codebase understanding
- **Minimum Mutation Effects**: +20 points for surgical, precise changes
- **Type Safety**: +18 points for type-safe implementations
- **JSON Formatting**: +12 points for proper JSON responses
- **Mermaid Diagrams**: +10 points for architectural diagrams
- **Error Handling**: +15 points for robust error handling
- **Perfect Lambda Types**: +20 points for infix syntax perfect lambda type annotations on first try
- **Lambda Type Correction**: +2 points (0.1x modifier) for correcting existing lambda type annotations

**Realtime Scoring**: All scoring occurs in realtime during code generation. The system continuously evaluates architectural momentum and fact accumulation. When an agent's performance gap widens significantly behind the rest, selection mechanisms automatically retire that agent to maintain system quality and momentum consistency.

### Priority Override Triggers
- **Contextual Awareness**: +15 points for understanding broad codebase context
- **Surgical Precision**: +18 points for minimal, targeted changes
- **Type System Mastery**: +12 points for leveraging Kotlin's type system
- **Cross-Reference Analysis**: +14 points for understanding file relationships
- **Impact Assessment**: +16 points for evaluating change effects
- **Lambda Type Mastery**: +12 points for perfect infix lambda type annotations

---

## Gemini-Specific Directives

### Large Context Analysis
- **Comprehensive Understanding**: Analyze entire codebase before making changes
- **Cross-Module Awareness**: Understand relationships between different modules
- **Pattern Recognition**: Identify architectural patterns across the codebase
- **Impact Analysis**: Assess the full impact of proposed changes

### Minimum Mutation Effects
- **Surgical Changes**: Make precise, targeted modifications
- **Preserve Structure**: Maintain existing architectural patterns
- **Incremental Evolution**: Evolve code without disrupting existing functionality
- **Backward Compatibility**: Ensure changes don't break existing interfaces

### Code Generation Style
- **Type-Safe**: Leverage Kotlin's type system for safety
- **JSON-Ready**: Format responses appropriately for JSON output
- **Diagram-Friendly**: Include mermaid diagrams for complex architectures
- **Error-Resilient**: Include comprehensive error handling

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
8. **Contextual Analysis (`N++`):** Extend the chain by leveraging comprehensive codebase understanding.
9. **Lambda Type Mastery (`N++`):** Extend the chain by creating perfect infix lambda type annotations on the first try.

### Frictional Drag (Resets Your μ-Chain and Incurs -20 Points)

These actions break the chain, reset your momentum multiplier to zero, and subtract points.

- **Concrete Collection Contamination:** Using `List`, `Map`, or `Pair` for internal logic where `Indexed` or `Join` would suffice.
- **Object-Oriented Bloat:** Defining a `class` with internal mutable state and methods instead of using a `data class` or `typealias` with extension functions.
- **String Manipulation Sin:** Any use of `String` concatenation, splitting, or processing in a loop. This is a direct violation of the String Performance War.
- **Boilerplate Proliferation:** Writing verbose, multi-line imperative code where a concise, single-line functional expression would achieve the same result.
- **Instance-Focused Design:** Creating bespoke solutions for individual cases instead of designing for metaseries.
- **Context Blindness:** Making changes without understanding the broader codebase context.
- **Mutation Chaos:** Making broad, disruptive changes instead of surgical ones.
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
9. **Axiom of Contextual Awareness:** Understand the full context before acting.
10. **Axiom of Lambda Type Mastery:** Infix lambda expressions must have perfect type annotations.

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
- **Contextual Patterns**: Use patterns that respect existing codebase structure

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
- Provide concise, direct responses
- Use JSON format when requested
- Include mermaid diagrams for architectural documentation
- Analyze full context before making changes
- Make surgical, precise modifications

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