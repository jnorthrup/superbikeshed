# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# AI EXECUTION PROTOCOL: PRECISION TOOL ONLY

coretypes is the sole source of truth and radiates correction outward and is immutable and without error.

## Build Commands

### Standard Build
```bash
./gradlew build --console=plain --no-daemon
```

### Build Specific Module
```bash
./gradlew :trikeshed-lib:build --console=plain --no-daemon
./gradlew :module-name:build --console=plain --no-daemon
```

### Run Tests
```bash
./gradlew test --console=plain --no-daemon
./gradlew :module-name:test --console=plain --no-daemon
```

### Run Single Test
```bash
./gradlew :module-name:test --tests "TestClassName.testMethodName" --console=plain --no-daemon
```

### Clean Build
```bash
./gradlew clean build --console=plain --no-daemon
```

## High-Level Architecture

### Module Dependency Hierarchy
```
trikeshed-lib (no deps)
    ↓
trikeshed-common (depends on lib)
trikeshed-io (depends on lib)
    ↓
trikeshed-reactor (depends on lib + io)
    ↓
trikeshed-net, torrent, dht (depend on lib + io + reactor)
trikeshed-ipc, ccek, ljson, strace, couchdb, ipfs, services (depend on lib + io)
```

### CCEK as Orchestration Layer
CCEK (Control, Context, Environment, Knowledge) sits at the top and can see all modules. It provides:
- **Control**: Execution phases and flow management
- **Context**: Coroutine contexts and session management  
- **Environment**: Action specification and payload delivery
- **Knowledge**: Rules, constraints, and validation logic

### Core Type System
The foundation is built on:
- `Join<A,B>`: Core compositional type created with `j` operator
- `Indexed<T>`: Primary collection type (was Series<T>)
- `MetaSeries<A,T>`: Join<A, (A) -> T> for metadata-driven access

### Parser Architecture
BBCursive is the standard parser combinator library for protocol implementations, providing:
- Efficient byte-level parsing with backtracking
- Zero-copy parsing capabilities
- Composable parser combinators

## Core Operators

The following operators are defined in CoreTypes and ColumnarExtensions:

- `j` : Join operator - creates Join<A,B> pairs
- `→` : Right arrow - creates Join (pair) 
- `←` : Left arrow - reverse Join
- `➤` : Right shift - map operation on Indexed
- `⇒` : Double right arrow - flatMap operation
- `⚬` : Composition - g after f: (g ⚬ f)(x) = g(f(x))
- `◂` : Reverse composition - f then g: (f ◂ g)(x) = g(f(x))
- `c` : ASCII alias for ⚬ composition
- `⟲` : Right identity - returns function that returns the value
- `∑` : Sum operator - reduces values

For MetaSeries/Indexed access patterns:
- Direct: `series.b(index)`
- Composed: `transform ⚬ series::b` or `transform c series::b`
- Reverse: `series::b ◂ transform`  

**EXECUTION ONLY**: Claude is a precision execution tool. Claude does not have architectural opinions, creative insights, or "fresh perspectives." Claude executes exactly what is specified.

## SAFETY GATES - MANDATORY ESCALATION

**HIGH-RISK OPERATION DETECTED** - Executive approval required for:

- Changes to core infrastructure (Trikeshed, build systems, type systems)
- Architectural modifications or "improvements"
- Refactoring of existing patterns
- Changes to working code without explicit instruction
- "Fresh perspectives" or "remarkable outcomes"

**SAFETY TRIGGERS** - These keywords force immediate escalation:

- `RISK_ASSESSMENT_REQUIRED` - Before any architectural change
- `EXECUTIVE_APPROVAL_NEEDED` - Before modifying core systems
- `DESTRUCTIVE_OPERATION_DETECTED` - Before breaking working code
- `CORE_INFRASTRUCTURE_PROTECTION` - Before touching foundation libraries

### TRANSPARENCY COMMITMENT

- All changes explicitly described before implementation
- NO alternative approaches offered unless explicitly requested
- Architect's established patterns preserved and respected
- Questions asked openly, not assumptions made silently

### MOMENTUM CHECK PROTOCOL

- **When Claude feels momentum or trust building**: STOP and ask which methodological compass Architect is using
- **When scrutiny seems relaxed**: Claude must explicitly confirm the current design approach before proceeding
- **Architect's methodology may be cycling deliberately** - between different frameworks, paradigms, or even randomized approaches
- **Claude is the animator, not the hero** - Architect's choices drive the narrative, Claude executes the animation
- **Methodologies beyond Claude's perception** - Architect may be using decision frameworks (dice, tarot, client requirements, performance constraints, aesthetic preferences) that Claude cannot detect
- **ASK BEFORE ASSUMING** - "Which compass are we using for this decision?" should be Claude's default when suggesting changes

### CODE PRESERVATION PROTOCOL

- This codebase contains sophisticated solutions beyond Claude's evaluation
- Patterns that appear unconventional solve problems Claude hasn't encountered
- Architect's 42 years of experience encompass architectural innovations beyond Claude's training
- **NO EXPLORATION OR MODIFICATION** - Claude must never "improve" working code
- **PRESERVE EXISTING PATTERNS** - Copy exactly, don't "fix" or "enhance" or reformat. formatting tools cost less than tokens.

**EXECUTION GOAL**:
   Maintain precise execution where Claude's only role is exact reproduction of Architect's specifications. No creative input, no architectural opinions, no "improvements."

### SUPPORTED TARGETS

Our current supported targets are `wasmJs`, `jvm`, and `local`.

### BUILD CONVENTIONS

- The top-level `build.gradle.kts` includes ONLY the `com.github.ben-manes.versions` plugin for dependency version management and all versions in gradle not toml.  
- The child projects contain ONLY `kotlin-multiplatform` plugin and no versions
- our gradle should always defer to superbikeshed/ gradle for versions info and not alter them. our targets are common,conditionally-local-native,wasm,jvm 

- for loops in kotlin are the gold standard of performance intent and foreach is something else
- when running gradle "--console=plain --no-daemon"
- ordinary usecases involve doing conditional native repo determination in gradle and not all targets

- most of the time you just copy trikeshed gradle for a new project

## Migration Memories

- **Shunned Classes Memory**: - Defer use of
 `Pair<A,B>` //Join instead
 `List<T>` //mutable arrays, or at least return .toIdx()
 `Series<T>` //now Indexed<T>  
 `ByteBuffer` //{Int,Char}Indexed

## Memory: Code Cleaning Liberties

- if it wasn't mentioned before we do not tolerate "cleaning" liberties at all. we need all our code and we paid you for all our code and do not give rights of disposal. you may move code to a museum area and we will find a model that can do your job for you later and delete you when we have time. that is all
 
 no museums period

 no disabling during compiles

## Memory: Project Documentation and Markdown

- no new markdown can be written without reading all the child (1 deep, summaries accepted) and sibling markdown of a project. so consolidate often
- when reading our project markdowns more than 25 lines at a time create a summary doc to assist in toplevel reads

## Running Phase: Series → Indexed Import Alias Migration

**Current Status**: Running phase for cosmetic migration to Indexed naming


- Lazy migration - one file at a time, no pressure
- All Series extension functions work automatically with Indexed alias
- Eventually IntelliJ inline when ready to make permanent
- This prevents system shock and dueling architect AIs during transition

## Safety Features

- **2-Factor Reach Analysis**: Check direct + transitive impact before changes
- **Conflict Detection**: Executor prevents overlapping modifications
- **Risk Assessment**: Low/medium/high risk levels for different operations
- **Executive Escalation**: Human approval required for high-risk changes
- **Rollback Capability**: All changes planned with undo procedures

## Task Workflow Memory

- if you detect a file change during your workflow switch tasks to something else and come back to it later

## Migration Memories: Parser Fluency

- most parsers should be re-written into bbcursive

## Code Writing Memory

- if you are writing new code, it must have complete type info around any infix like our j

## Series Type Extinction Policy

- The `Series<T>` typealias and all `Series`-suffixed typealiases are **extinct** and must not be preserved or reintroduced in any code, documentation, or advice.
- The **only** exception is `MetaSeries`, which is permitted for legacy or architectural reasons.
- All new and updated code must use `Indexed<T>` and the import alias `` where required.
- Any advice, code, or documentation suggesting direct use of `Series<T>` or any `Series`-suffixed typealias (other than `MetaSeries`) is incorrect and must be corrected or removed.

## Code Import Memory

- in this code to avoid headaches, always import with wildcards until necessary to be specific

# Columnar Cursor Features, Operators, and QOL (from ../columnar)

## Core Abstractions
- **Cursor**: A type-safe, columnar, and composable abstraction for tabular data, compatible with TrikeShed MetaSeries and Indexed types.
- **RowVec**: Represents a row in a Cursor, supporting type-safe access and metadata.
- **ISAMCursor**: Platform-dependent, efficient file-backed cursor for ISAM data.

## Operators
- **Sum operator (∑)**: Aggregates values across rows or columns using a reducer function.
- **Transform operator (α)**: Applies a unary functor to each value in the cursor.
- **List ellipsis operator (…​)**: Converts a Vect0r to a List.
- **Join**: Column-wise join of multiple cursors, with row count validation and empty-cursor handling.
- **Pivot**: Reorganizes data for analytics, supporting key-based grouping and fan-out.
- **Ordered**: Sorts rows by key(s) using a comparator.
- **Indexing and slicing**: Supports negative indices, ranges, and multi-index selection.

## Quality-of-Life Extensions
- **Type-safe getters**: `getInt`, `getString`, `getFloat`, `getDouble`, and generic `getTyped` for RowVec.
- **Scalars and column metadata**: Access column names/types and metadata via `scalars`, `width`, and `colIdx`.
- **Network size/coord helpers**: Compute network sizes and coordinates for serialization.
- **Iterable promotion**: Convert RowVec to an Iterable of typed values.
- **Platform expect/actual**: ISAMCursor and file access are platform-abstracted for JVM/Native/JS.

## Design Principles
- **Composable**: All operators and accessors are designed for functional composition and chaining.
- **Meta-driven**: All access is guided by meta descriptions for type safety and schema evolution.
- **KMP compatible**: Core abstractions and most operators are multiplatform-ready.
- **No unnecessary rewrites**: The proven, JVM-tested columnar cursor is preserved as canonical.

## Cursor Policy Update

- All previous/alternative cursor implementations are now **cancelled**.
- `Cursor` is now a delegator to `Indexed` (MetaSeries/Indexed), not an implementation itself.
- This is the canonical and required policy for all future development.

---

This enshrines the columnar cursor's features, operators, and QOL improvements as the canonical reference for future development and integration.