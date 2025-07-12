# ADR-005: Single-Line Kotlin Architecture

## Status
Accepted

## Context
Across relaxfactory, columnar, mpsuperproject, and trikeshed projects, a consistent single-line Kotlin architecture has emerged that leverages Kotlin's concise syntax to create highly readable, functional code with minimal visual noise.

## Decision
Use single-line Kotlin patterns that avoid braces/parens, leverage heavy function references, and maintain type alias transparency. This creates a distinctive architectural style that maximizes expressiveness while minimizing syntactic overhead.

## Consequences

### Positive
- **Minimal visual noise** - single-line expressions reduce cognitive load
- **Heavy function references** enable powerful composition patterns
- **Type alias transparency** maintains type safety with concise syntax
- **Consistent architectural style** across all projects
- **Functional composition** through infix operators and extensions

### Negative
- **Learning curve** for developers unfamiliar with the patterns
- **May be less readable** for complex expressions
- **Requires discipline** to maintain single-line style

## Implementation Patterns

### 1. Single-Line Function Definitions
```kotlin
// ✅ RIGHT - Single-line function with infix
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

// ✅ RIGHT - Extension function single-line
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// ✅ RIGHT - Type alias with single-line definition
typealias Indexed<T> = Join<Int, (Int) -> T>
```

### 2. Heavy Function References
```kotlin
// ✅ RIGHT - Function reference composition
fun ByteArray.toIndexed(): Indexed<Byte> = size j { this[it] }

// ✅ RIGHT - Lambda with function reference
fun Indexed<Int>.toIntArray(): IntArray = IntArray(a) { b(it) }

// ✅ RIGHT - Extension with function reference
inline infix fun <X, C> Array<X>.α(crossinline xform: (X) -> C): Indexed<C> = 
    size j { i: Int -> xform(this[i]) }
```

### 3. Type Alias Transparency
```kotlin
// ✅ RIGHT - Transparent type aliases
typealias MetaSeries<A, T> = Join<A, (A) -> T>
typealias Indexed<T> = Join<Int, (Int) -> T>
typealias Twin<T> = Join<T, T>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>

// ✅ RIGHT - Usage maintains transparency
fun <T> Indexed<T>.toList(): AbstractList<T> = object : AbstractList<T>() {
    override val size: Int = a
    override fun get(index: Int): T = b(index)
}
```

### 4. Infix Operators for Composition
```kotlin
// ✅ RIGHT - Infix for composition
infix fun <T, R> Indexed<T>.map(crossinline transform: (T) -> R): Indexed<R> = 
    this α transform

// ✅ RIGHT - Infix for joining
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

// ✅ RIGHT - Infix for alpha conversion
inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = 
    this.a j { index: Int -> xform(this.b(index)) }
```

### 5. Object Factories for Concise Creation
```kotlin
// ✅ RIGHT - Object factories
object _a {
    operator fun get(vararg t: Int): IntArray = t
    operator fun get(vararg t: Byte): ByteArray = t
}

object _i {
    operator fun <T> get(vararg t: T) = t.size j { i: Int -> t[i] }
}

// Usage: _a[1, 2, 3] or _i["a", "b", "c"]
```

### 6. Extension Properties for Access
```kotlin
// ✅ RIGHT - Extension properties
val <T> Indexed<T>.size: Int get() = a
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// ✅ RIGHT - Operator extensions
operator fun <T> Indexed<T>.get(index: Int): T = b(index)
```

## Architectural Principles

### 1. Single-Line Preference
- **Prefer single-line expressions** over multi-line blocks
- **Use infix operators** to reduce parentheses
- **Leverage extension functions** for fluent APIs

### 2. Function Reference Composition
- **Heavy use of function references** in lambdas
- **Compose functions** through infix operators
- **Minimize intermediate variables**

### 3. Type Alias Transparency
- **Type aliases should be transparent** to usage
- **Maintain type safety** through aliases
- **Use aliases for domain concepts**

### 4. Object Factories
- **Use object factories** for concise creation
- **Operator overloading** for natural syntax
- **Consistent naming patterns** (_a, _i, _l, _s, _m)

## Examples from CoreTypes

```kotlin
// Single-line function definitions
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// Heavy function references
fun ByteArray.toIndexed(): Indexed<Byte> = size j { this[it] }
fun Indexed<Int>.toIntArray(): IntArray = IntArray(a) { b(it) }

// Type alias transparency
typealias Indexed<T> = Join<Int, (Int) -> T>
typealias MetaSeries<A, T> = Join<A, (A) -> T>

// Infix composition
inline infix fun <T, R> Indexed<T>.map(crossinline transform: (T) -> R): Indexed<R> = 
    this α transform

// Object factories
object _i {
    operator fun <T> get(vararg t: T) = t.size j { i: Int -> t[i] }
}
```

## Related
- CoreTypes.kt - Primary implementation
- AlphaConversion.kt - Function composition patterns
- Type system patterns: ADR-003
- String performance war: ADR-002 