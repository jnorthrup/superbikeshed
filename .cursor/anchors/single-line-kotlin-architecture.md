# Single-Line Kotlin Architecture Anchor

## The Pattern: Minimal Visual Noise Architecture

### Core Philosophy
- **Single-line expressions** over multi-line blocks
- **Avoid braces/parens** through infix operators and extensions
- **Heavy function references** for composition
- **Type alias transparency** for domain concepts
- **Object factories** for concise creation

## Single-Line Patterns

### 1. Function Definitions
```kotlin
// ✅ RIGHT - Single-line with infix
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

// ✅ RIGHT - Extension property single-line
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// ✅ RIGHT - Type alias single-line
typealias Indexed<T> = Join<Int, (Int) -> T>
```

### 2. Function Reference Composition
```kotlin
// ✅ RIGHT - Heavy function references
fun ByteArray.toIndexed(): Indexed<Byte> = size j { this[it] }
fun Indexed<Int>.toIntArray(): IntArray = IntArray(a) { b(it) }

// ✅ RIGHT - Lambda with function reference
inline infix fun <X, C> Array<X>.α(crossinline xform: (X) -> C): Indexed<C> = 
    size j { i: Int -> xform(this[i]) }
```

### 3. Infix Operators for Composition
```kotlin
// ✅ RIGHT - Infix for joining
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

// ✅ RIGHT - Infix for transformation
inline infix fun <T, R> Indexed<T>.map(crossinline transform: (T) -> R): Indexed<R> = 
    this α transform

// ✅ RIGHT - Infix for alpha conversion
inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = 
    this.a j { index: Int -> xform(this.b(index)) }
```

### 4. Object Factories
```kotlin
// ✅ RIGHT - Object factories for arrays
object _a {
    operator fun get(vararg t: Int): IntArray = t
    operator fun get(vararg t: Byte): ByteArray = t
}

// ✅ RIGHT - Object factories for Indexed
object _i {
    operator fun <T> get(vararg t: T) = t.size j { i: Int -> t[i] }
}

// ✅ RIGHT - Object factories for lists
object _l {
    operator fun <T> get(vararg t: T): List<T> = listOf(*t)
}

// Usage: _a[1, 2, 3] or _i["a", "b", "c"] or _l[1, 2, 3]
```

### 5. Extension Properties
```kotlin
// ✅ RIGHT - Extension properties for access
val <T> Indexed<T>.size: Int get() = a
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// ✅ RIGHT - Operator extensions
operator fun <T> Indexed<T>.get(index: Int): T = b(index)
```

## Type Alias Transparency

### Domain Concepts
```kotlin
// ✅ RIGHT - Transparent type aliases
typealias MetaSeries<A, T> = Join<A, (A) -> T>
typealias Indexed<T> = Join<Int, (Int) -> T>
typealias Twin<T> = Join<T, T>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>
typealias ByteSeries = Indexed<Byte>
typealias CharSeries = Indexed<Char>
```

### Usage Maintains Transparency
```kotlin
// ✅ RIGHT - Type alias usage is transparent
fun <T> Indexed<T>.toList(): AbstractList<T> = object : AbstractList<T>() {
    override val size: Int = a
    override fun get(index: Int): T = b(index)
}
```

## Forbidden Patterns

### Multi-Line When Single-Line Possible
```kotlin
// ❌ WRONG - Multi-line when single-line possible
fun <A, B> join(a: A, b: B): Join<A, B> {
    return Join(a, b)
}

// ✅ RIGHT - Single-line
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)
```

### Unnecessary Braces/Parens
```kotlin
// ❌ WRONG - Unnecessary braces
fun <T> Indexed<T>.map(transform: (T) -> R): Indexed<R> {
    return this.α(transform)
}

// ✅ RIGHT - No braces needed
inline infix fun <T, R> Indexed<T>.map(crossinline transform: (T) -> R): Indexed<R> = 
    this α transform
```

### Missing Function References
```kotlin
// ❌ WRONG - No function reference
fun ByteArray.toIndexed(): Indexed<Byte> = size j { byte -> this[byte] }

// ✅ RIGHT - Function reference
fun ByteArray.toIndexed(): Indexed<Byte> = size j { this[it] }
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

## The Single-Line Mantra
**"Single-line expressions, heavy function references, type alias transparency, object factories for creation."** 