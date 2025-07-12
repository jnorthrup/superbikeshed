# Cursor Bootstrap Guide

## Quick Start
This file provides optimized bootstrapping for Cursor IDE interactions.

## Core Directives
- **Primary File**: `.cursorrules` (Architectural Momentum System v2.0)
- **Backup**: `.cursorrules.backup.*` (Auto-created by setup script)
- **Source**: `.llm/pristine/cursor/CURSOR.md`
- **IDE Integration**: `.cursor/anchors/` (IDE anchors)

## Momentum System
- **Formula**: `Momentum Points = 10 * (1.5 ^ N)`
- **Goal**: Build long μ-Chains for exponential rewards
- **Focus**: IDE integration and context awareness

## Key Triggers
1. Core Instantiation (`Join<A, B>`, `Indexed<T>`)
2. Axiomatic Aliasing (`typealias`)
3. Functional Extension (`extension fun`)
4. Operator Application (`j`, `α`, `play`)
5. Performance Purity (avoid `String` allocations)
6. Metaseries Composition (series operations)
7. Algebraic Transformation (functional composition)
8. **IDE Integration** (Cursor-specific capabilities)

## Cursor-Specific Features
- **File Operations**: Use Cursor's file editing capabilities
- **Search Integration**: Leverage codebase search
- **Context Awareness**: Consider current file context
- **Incremental Development**: Make small, focused changes
- **Refactoring Support**: Write IDE-friendly code

## Frictional Drag (-20 points)
- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design
- **Context Ignorance**: Making changes without considering codebase context

## IDE-Optimized Patterns
1. **Import Management**: Include proper imports
2. **Type Inference**: Leverage Kotlin's type inference
3. **Refactoring Support**: Write code that can be easily refactored
4. **Context Awareness**: Consider broader codebase context
5. **Search Integration**: Use search to understand existing patterns

## Example μ-Chain with IDE Integration
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

// N=7: IDE Integration (Cursor-specific)
// Using Cursor's capabilities for precise, contextual edits

// Total Score: 170.86 points
```

## Quick Commands
```bash
# Deploy framework
.llm/pristine/setup.sh cursor

# Check status
ls -la .cursorrules*

# View IDE features
grep -A 5 "IDE Integration" .cursorrules

# Check anchors
ls -la .cursor/anchors/
```

## IDE Integration Tips
1. **Use Cursor's file editing** for precise code changes
2. **Leverage search** to understand existing patterns
3. **Consider context** when making changes
4. **Make incremental changes** that build upon each other
5. **Use refactoring tools** for code improvements 