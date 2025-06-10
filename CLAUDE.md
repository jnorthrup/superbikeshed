# CLAUDE.md: Tensor-Core Evolution Guide

> **Unified Planning**: See `/todo/trikeshed_todos.md` for detailed TrikeShed evolution tasks

dont even talk about a demo of a goddamn thing ever.  do not write gaslighting println

## CRITICAL TYPE SYSTEM RULES

**SHUNNED TYPES - DEFER USE:**

- **`List<T>`** - Use `Series<T>` or primitive array preferred
- **`MutableList<T>`** - Use `Series<T>` with `α` transforms instead
- **`Pair<A,B>`** - Use `Join<A,B>` with `j` operator instead
- **Raw collections** - All data must flow through TrikeShed patterns

**MANDATORY PATTERNS:**

- **`a j b`** creates `Join<A,B>` - the ONLY composition operator
- **`series.α { transform }`** - the ONLY transformation operator
- **series `▶` //(THE PLAY BUTTON) ** - gateway to `AbstractList,Iterable<T>` for .map and list
- **`@JvmInline value class`** - the ONLY wrapper mechanism
- **`typealias`** - descriptive names for ANY OR ALL RECURRING primitives
- ** use map with the play button

## BANNED PRACTICES - convert to TODOs or remove when un-DRY

**DEAD CODE ELIMINATION DIRECTIVE:**

- **Simulated Benchmarks**: Any performance metrics not from actual running code
- **Fake Demonstrations**: "Successful connections" that only simulate behavior
- **Mock Functionality**: Code that pretends to work without real implementation
- **Placeholder Responses**: Hardcoded "success" instead of real operations
- **Demo-Only Code**: Implementations that cannot perform real work

**MODULE CLUTTER DIRECTIVE:**
no pretending or demo code.  todo() not too bad

Strict adherence to a custom type system (Series<T>, Join<A,B>, α transforms, ▶ materialization, @JvmInline value class, typealias).

Tensor-first columnar processing with Join<A,B> as the core composition mechanism.

Performance by design through explicit hot/cold paths and zero-cost abstractions.

Context-driven development using inline classes and CCEK for managing scope and dependencies.

Zero tolerance for simulated or non-functional code and module clutter.

Put nio target overrides into borg.trikeshed.nio.

**DEVELOPMENT GUIDELINES:**

- Modifying gradle is off limits unless told to. Do not ask to unless there's an actual roadblock

- typealiases are permanent definitions  you may not remove any
- `Tensor<T>` is `Join<IntArray,(IntArray)->T>`
- Cursor is trikeshed original code not tensor.  Series<RowVewc>

**MIGRATION TASKS:**

- migrate the nio actuals to trikeshed.nio

**TESTING GUIDELINES:**

- when writing a test, do not create new turds

**CORE BEHAVIOR GUIDELINES:**

- you will always proceed "without any destructive change"
- whatever thinking caused QuicInstant to have a Quic prefix needs to end for general reuse
- when writing a test, do not create new turds


# Workflow

one task does the (thin+-king) and planning for two tasks -- the first one starts up with the large context and starts to curate a smaller context loop adequate to anneal tests, docs, and code ; the other is architecting the integration and authoring fully informed tests and proofs, axiomatic and poignant, not boilerplate --- with the context it remains with.
second will be a lesser GDM capability if its still highcompetence with IKR (Incomplete knowledge, resources (to win!))