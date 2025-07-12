# Gemini Bootstrap Guide

## Quick Start
This file provides optimized bootstrapping for Gemini CLI interactions.

## Core Directives
- **Primary File**: `.gemini.md` (Architectural Momentum System v2.0)
- **Backup**: `.gemini.md.backup.*` (Auto-created by setup script)
- **Source**: `.llm/pristine/gemini/GEMINI.md`

## Momentum System
- **Formula**: `Momentum Points = 10 * (1.5 ^ N)`
- **Goal**: Build long μ-Chains for exponential rewards
- **Focus**: Type safety and JSON formatting

## Key Triggers
1. Core Instantiation (`Join<A, B>`, `Indexed<T>`)
2. Axiomatic Aliasing (`typealias`)
3. Functional Extension (`extension fun`)
4. Operator Application (`j`, `α`, `play`)
5. Performance Purity (avoid `String` allocations)
6. Metaseries Composition (series operations)
7. Algebraic Transformation (functional composition)
8. **Type-Safe Composition** (Gemini-specific capabilities)

## Gemini-Specific Features
- **Precision over Verbosity**: Generate concise, mathematically precise code
- **Type Safety First**: Leverage Kotlin's type system for compile-time guarantees
- **Functional Composition**: Chain operations using function composition patterns
- **Performance Awareness**: Consider memory allocation and CPU cycles
- **JSON Responses**: Provide structured JSON format when requested
- **Mermaid Diagrams**: Use mermaid diagrams for architectural documentation

## Frictional Drag (-20 points)
- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design
- **Type System Neglect**: Using `Any` or `Object` where specific types could provide compile-time safety

## Type-Safe Patterns
1. **Type Safety First**: Use specific types for compile-time guarantees
2. **Sealed Classes**: Use sealed classes for type-safe series operations
3. **Generic Constraints**: Leverage Kotlin's generic system
4. **Null Safety**: Use nullable types appropriately
5. **Type Inference**: Leverage Kotlin's type inference while maintaining clarity

## Example μ-Chain with Type Safety
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

// N=7: Type-Safe Composition (Gemini-specific)
sealed class DataOperation<T> {
    data class Transform<T, R>(val fn: (T) -> R) : DataOperation<T>()
    data class Filter<T>(val predicate: (T) -> Boolean) : DataOperation<T>()
}

// Total Score: 170.86 points
```

## Quick Commands
```bash
# Deploy framework
.llm/pristine/setup.sh gemini

# Check status
ls -la .gemini.md*

# View type safety features
grep -A 5 "Type Safety" .gemini.md

# Check JSON formatting
grep -A 3 "JSON Responses" .gemini.md
```

## Type Safety Tips
1. **Use specific types** instead of `Any` or `Object`
2. **Leverage sealed classes** for type-safe series operations
3. **Use generic constraints** for compile-time safety
4. **Embrace null safety** with appropriate nullable types
5. **Use type inference** while maintaining clarity

## Response Formatting
- **JSON Responses**: When requested, provide structured JSON
- **Mermaid Diagrams**: Use for architectural documentation
- **Code Blocks**: Always use proper syntax highlighting
- **Inline Documentation**: Include brief comments explaining decisions 