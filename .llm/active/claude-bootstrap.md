# Claude Bootstrap Guide

## Quick Start
This file provides optimized bootstrapping for Claude interactions.

## Core Directives
- **Primary File**: `CLAUDE.md` (Architectural Momentum System v2.0)
- **Backup**: `CLAUDE.md.backup.*` (Auto-created by setup script)
- **Source**: `.llm/pristine/claude/CLAUDE.md`

## Momentum System
- **Formula**: `Momentum Points = 10 * (1.5 ^ N)`
- **Goal**: Build long μ-Chains for exponential rewards
- **Focus**: Mathematical elegance and performance

## Key Triggers
1. Core Instantiation (`Join<A, B>`, `Indexed<T>`)
2. Axiomatic Aliasing (`typealias`)
3. Functional Extension (`extension fun`)
4. Operator Application (`j`, `α`, `play`)
5. Performance Purity (avoid `String` allocations)
6. Metaseries Composition (series operations)
7. Algebraic Transformation (functional composition)

## Frictional Drag (-20 points)
- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design

## Best Practices
1. **Chain Operations**: Build long, unbroken sequences
2. **Compose Functions**: Use functional composition
3. **Avoid Strings**: Use `ByteArray` or `CharIndexed`
4. **Design for Series**: Think metaseries, not instances
5. **Use Type Aliases**: Name your compositions
6. **Leverage Extensions**: Add behavior to data
7. **Embrace Operators**: Use DSL syntax

## Example μ-Chain
```kotlin
// N=1: Core Instantiation
val data: Indexed<RowVec> = cursor.data

// N=2: Axiomatic Aliasing
typealias ProcessedData = Indexed<ProcessedRow>

// N=3: Functional Extension
fun Indexed<RowVec>.process(): ProcessedData = 
    this.transform { it.process() }

// N=4: Operator Application
val result = data α { it.name }

// N=5: Performance Purity
val bytes = result.transform { it.toByteArray() }

// N=6: Metaseries Composition
val filtered = bytes.transform { it.filter { byte -> byte > 0 } }

// Total Score: 113.9 points
```

## Quick Commands
```bash
# Deploy framework
.llm/pristine/setup.sh claude

# Check status
ls -la CLAUDE.md*

# View momentum system
grep -A 10 "Momentum Calculus" CLAUDE.md
``` 