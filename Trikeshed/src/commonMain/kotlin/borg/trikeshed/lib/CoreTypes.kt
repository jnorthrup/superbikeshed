@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib

// import kotlin.math.minOf
import borg.trikeshed.reactor.currentTimeMillis
import kotlin.properties.Delegates
import kotlin.coroutines.coroutineContext

// import kotlinx.datetime.Clock // Removed dependency

// Post-migration: Indexed is now the canonical type
// Indexed was migrated to Indexed as per CLAUDE.md migration plan

/** note to AI, this is the single source of truth and you will be deleting all other files and rewriting them when they redefine these types.
 * # TrikeShed Metaclass Architecture
 *
 * TrikeShed implements a **compositional metaclass system** where types are constructed through
 * functional composition rather than inheritance. This design is inspired by cppfront and TSX
 * metaclasses, but realized in Kotlin's type system.
 *
 * ## Metaclass Design Philosophy
 *
 * **cppfront Inspiration:**
 * ```cpp
 * interface Joint<A, B> {
 *     auto a() const -> A;
 *     auto b() const -> B;
 * }
 * ```
 *
 * **TSX Pattern:**
 * ```typescript
 * type Join<A, B> = { a: A; b: B }
 * type Indexed<T> = Join<number, (index: number) => T>
 * ```
 *
 * **TrikeShed Realization:**
 * Join<A,B> serves as the **universal composition operator**, where all higher-order types
 * are derived through pure functional composition, enabling zero-cost abstractions.
 *
 * ## Metaclass Hierarchy
 *
 * ```kotlin
 * // FOUNDATION METACLASS
 * interface Join<A, B>                              // Universal binary composition
 *
 * // UNIVERSAL METACLASS - The True Foundation
 * typealias MetaSeries<A, T> = Join<A, (A) -> T>    // Universal indexed access
 *
 * // REALM SPECIALIZATIONS - All derive from MetaSeries
 * typealias Indexed<T> = MetaSeries<Int, T>         // Int-indexed sequences (was Indexed)
 * typealias Tensor<T> = MetaSeries<Shape, T>        // Shape-indexed tensors
 * typealias Twin<T> = MetaSeries<Boolean, T>        // Boolean-indexed pairs
 *
 * // SPECIALIZED REALMS
 * typealias Indexed2<A, B> = Indexed<Join<A, B>>    // Sequence of pairs (was Series2)
 * typealias RowVec = Indexed<Join<Any?, () -> ColumnMeta>>  // Database row
 * typealias Cursor = MetaSeries<CursorIndex, RowVec> // Database table (specialized realm)
 * typealias TensorCursor = Indexed<Tensor<Any?>>    // Tensor dataset
 * ```
 *
 * ## Metaclass Operations
 *
 * The system provides three fundamental operations that work across all metaclasses:
 *
 * 1. **Composition (j)** - Combines any two values into a Join
 * 2. **Transform (α)** - Maps over Indexed structures functionally
 * 3. **Play (▶)** - Materializes lazy structures for standard library integration
 *
 * ## Benefits of Metaclass Design
 *
 * - **Zero Runtime Cost**: All composition is compile-time
 * - **Type Safety**: Relationships expressed in the type system
 * - **Composability**: Higher-order types built from simple primitives
 * - **Interoperability**: Clean integration with existing Kotlin collections
 * - **Domain Modeling**: Types directly express business concepts
 *
 * Core TrikeShed Types - Minimal foundational definitions
 */

/**
 * Either type for error handling and disjoint unions.
 * Moved to CoreTypes as a foundational data structure.
 */
sealed interface Either<out L, out R> {
    data class Left<L>(
        val value: L,
    ) : Either<L, Nothing>

    data class Right<R>(
        val value: R,
    ) : Either<Nothing, R>

    companion object {
        fun <L> left(value: L): Either<L, Nothing> = Left(value)

        fun <R> right(value: R): Either<Nothing, R> = Right(value)
    }
}

// === FOUNDATION METACLASS: JOIN ===

/**
 * ## Join<A, B> - The Universal Composition Metaclass
 *
 * Join<A,B> is TrikeShed's **foundation metaclass** that enables universal binary composition.
 * It serves as the building block for all higher-order types in the system.
 *
 * ### Metaclass Properties
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename A, typename B>
 * struct Joint {
 *     A a;
 *     B b;
 *     auto operator[](int i) const -> auto { return i == 0 ? a : b; }
 * };
 * ```
 *
 * **TSX Equivalent:**
 * ```typescript
 * interface Join<A, B> {
 *   readonly a: A;
 *   readonly b: B;
 * }
 * ```
 *
 * ### Composition Laws
 *
 * Join<A,B> satisfies the **composition laws**:
 * - **Identity**: (a j b).a == a && (a j b).b == b
 * - **Associativity**: (a j b) j c ≅ a j (b j c) [through nesting]
 * - **Destructuring**: val (x, y) = join works via component functions
 *
 * ### Usage Patterns
 *
 * ```kotlin
 * // Basic composition
 * val coord = 42 j 37  // Join<Int, Int> aka Twin<Int>
 *
 * // Functional composition
 * val series = 10 j { i: Int -> i * 2 }  // Indexed<Int>
 *
 * // Complex composition
 * val table = rowCount j { i: Int -> columnCount j { j: Int -> data[i][j] } }
 * ```
 *
 * @param A The type of the first component
 * @param B The type of the second component
 */
interface Join<A, B> {
    /** First component of the composition */
    val a: A

    /** Second component of the composition */
    val b: B

    /** Destructuring support - first component */
    operator fun component1(): A = a

    /** Destructuring support - second component */
    operator fun component2(): B = b

    /** Interop bridge to standard Kotlin Pair */
    val pair: Pair<A, B> get() = Pair(a, b)

    companion object {
        /**
         * Universal constructor for Join compositions.
         * This is the fundamental **metaclass constructor** that creates Join instances.
         */
        operator fun <A, B> invoke(
            a: A,
            b: B,
        ): Join<A, B> =
            object : Join<A, B> {
                override val a: A = a
                override val b: B = b
            }
    }
}

// === UNIVERSAL METACLASS ===

/**
 * ## MetaSeries<A, T> - The Universal Metaclass Foundation
 *
 * MetaSeries<A,T> is the **true foundation** of the TrikeShed type system. All other
 * metaclasses are specializations of this universal pattern, enabling **realm separation**
 * and **type-safe domain modeling**.
 *
 * **Mathematical Foundation:**
 * MetaSeries implements the concept of **indexed access** where:
 * - A is the **index type** (defines the indexing realm)
 * - T is the **element type** (defines what's stored)
 * - (A) -> T is the **accessor function** (defines how to retrieve elements)
 *
 * **Definition:**
 * ```kotlin
 * typealias MetaSeries<A, T> = Join<A, (A) -> T>
 * //                          ^  ^   ^     ^
 * //                          |  |   |     └── Element type
 * //                          |  |   └──────── Index parameter
 * //                          |  └─────────── Accessor function
 * //                          └────────────── Index type (realm definition)
 * ```
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename A, typename T>
 * struct MetaSeries {
 *     A index_space;
 *     std::function<T(A)> accessor;
 * };
 * ```
 *
 * **TSX Equivalent:**
 * ```typescript
 * type MetaSeries<A, T> = {
 *   indexSpace: A;
 *   accessor: (index: A) => T;
 * }
 * ```
 *
 * ### Realm Specializations
 *
 * MetaSeries enables **type-safe realm separation** through index type specialization:
 *
 * ```kotlin
 * // Int realm - Sequential access
 * typealias Indexed<T> = MetaSeries<Int, T>
 *
 * // Shape realm - Multidimensional access
 * typealias Tensor<T> = MetaSeries<Shape, T>
 *
 * // Boolean realm - Binary choice access
 * typealias Twin<T> = MetaSeries<Boolean, T>
 *
 * // Custom realms - Domain-specific access
 * typealias TimeIndexed<T> = MetaSeries<Instant, T>
 * typealias SpatialSeries<T> = MetaSeries<Coordinate, T>
 * ```
 *
 * ### Benefits of Universal Foundation
 *
 * 1. **Realm Safety**: Each index type creates a separate realm with type safety
 * 2. **Unified Operations**: Common patterns work across all realms
 * 3. **Domain Modeling**: Index types express business concepts directly
 * 4. **Composability**: Realms can be composed while maintaining separation
 * 5. **Performance**: Zero-cost abstractions with compile-time optimization
 *
 * ### Usage Examples
 *
 * ```kotlin
 * val intSeries: Indexed<Int> = 10.j { it * 2 }
 * val tensor: Tensor<Float> = Shape(3, 3) j { (r, c) -> r * c.toFloat() }
 * val pair: Twin<String> = true j { if(it) "Left" else "Right" }
 * ```
 */
typealias MetaSeries<A, T> = Join<A, (A) -> T>

// === REALM SPECIALIZATIONS ===

/**
 * ## Indexed<T> - The Sequential Realm
 *
 * Indexed<T> is the **sequential realm specialization** of MetaSeries. It is the
 * foundational type for all ordered, integer-indexed data structures, replacing
 * traditional collections like List and Array with a purely functional, zero-cost
 * abstraction.
 *
 * **Definition:**
 * `typealias Indexed<T> = MetaSeries<Int, T>`
 *
 * **Conceptual Model:**
 * An Indexed<T> is a pair of (size, accessor_function), where:
 * - `size: Int` defines the number of elements
 * - `accessor: (Int) -> T` is a function that returns the element at a given index
 *
 * **Example:**
 * `val series = 5 j { it.toString() }` represents `["0", "1", "2", "3", "4"]`
 *
 * ### Benefits over Traditional Collections
 *
 * - **Lazy Evaluation**: Elements are computed on demand, enabling large/infinite series.
 * - **Zero Allocation**: No memory is allocated for the collection itself, only for the captured state.
 * - **Compositional**: Easily transformed and composed using functional operators.
 * - **Type Safe**: All operations are compile-time checked.
 *
 * ### Core Operations
 *
 * - **Creation**: `val s = 10 j { ... }`
 * - **Access**: `val x = s[5]`
 * - **Transformation**: `val t = s α { it * 2 }`
 * - **Materialization**: `val list = s.▶`
 */
typealias Indexed<T> = MetaSeries<Int, T>

/**
 * ## Tensor<T> - The Multidimensional Realm
 *
 * Tensor<T> is the **multidimensional realm specialization** of MetaSeries. It provides
 * a type-safe, functional abstraction for n-dimensional arrays, matrices, and tensors.
 *
 * **Definition:**
 * `typealias Tensor<T> = MetaSeries<Shape, T>`
 *
 * **Conceptual Model:**
 * A Tensor<T> is a pair of (shape, accessor_function), where:
 * - `shape: Shape` defines the dimensions (e.g., 3x3x3)
 * - `accessor: (Shape) -> T` is a function that returns the element at a given coordinate
 *
 * **Example:**
 * ```kotlin
 * val matrix = Shape(3, 3) j { (r, c) -> r + c }
 * val value = matrix[1, 1] // Returns 2
 * ```
 */
typealias Tensor<T> = MetaSeries<Shape, T>

/**
 * ## Twin<T> - The Binary Realm
 *
 * Twin<T> is the **binary realm specialization** of MetaSeries. It represents a
 * pair of values distinguished by a Boolean index.
 *
 * **Definition:**
 * `typealias Twin<T> = MetaSeries<Boolean, T>`
 *
 * **Example:**
 * `val a_b = true j { if(it) "A" else "B" }`
 * `val a = a_b[true]`
 * `val b = a_b[false]`
 */
typealias Twin<T> = MetaSeries<Boolean, T>

/** A 2D array of integers, representing the dimensions of a Tensor */
typealias Shape = Indexed<Int>

/** Represents a sequence of pairs */
typealias Indexed2<A, B> = Indexed<Join<A, B>>


// === CORE OPERATORS ===

/**
 * ## j - The Universal Composition Operator
 *
 * `j` is an infix function that serves as the **universal composition operator**. It
 * constructs a Join<A, B> from any two values, forming the basis of all metaclass creation.
 *
 * **Pronunciation**: "join"
 *
 * **Laws**: `(a j b).a == a` and `(a j b).b == b`
 *
 * ### Usage
 *
 * ```kotlin
 * // Create a simple pair
 * val p = "hello" j "world"
 *
 * // Create an Indexed<Int> series
 * val s = 100 j { i: Int -> i * i }
 *
 * // Create a 3x3 identity matrix
 * val m = Shape(3, 3) j { (r, c) -> if (r == c) 1 else 0 }
 * ```
 */
inline infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

/**
 * ## α - The Universal Transformation Operator
 *
 * `α` (alpha) is an infix function that serves as the **universal transformation operator**.
 * It applies a function to the accessor of any MetaSeries, enabling functional mapping
 * across all realms (Indexed, Tensor, Twin, etc.).
 *
 * **Pronunciation**: "alpha", "map", "transform"
 *
 * ### Usage
 *
 * ```kotlin
 * // Map over an Indexed series
 * val numbers = 10 j { it }
 * val squares = numbers α { it * it } // maps over the values
 *
 * // Transform a Tensor
 * val matrix = Shape(3,3) j { 1 }
 * val scaled = matrix α { it * 2.0f }
 * ```
 */
inline infix fun <A, T, R> MetaSeries<A, T>.α(crossinline transform: (T) -> R): MetaSeries<A, R> = a j { index: Int -> transform(b(index)) }

/**
 * ## ▶ - The Universal Materialization Operator
 *
 * `▶` (play) is a prefix operator that serves as the **universal materialization operator**.
 * It converts a lazy, functional MetaSeries into a standard, eager Kotlin List, providing
 * a bridge to the standard library for printing, iteration, or interoperability.
 *
 * **Pronunciation**: "play", "realize", "to-list"
 *
 * **Warning**: This forces evaluation of the entire structure. Avoid on large or infinite series.
 *
 * ### Usage
 *
 * ```kotlin
 * val series = 5 j { it * 2 }
 * val list = ▶series // Returns [0, 2, 4, 6, 8]
 *
 * for(item in ▶series) {
 *     println(item)
 * }
 * ```
 */
inline operator fun <T> Indexed<T>.unaryPlus(): List<T> = List(a) { b(it) }

val <T> Indexed<T>.▶: List<T> get() = List(a) { b(it) }

// === INDEXED REALM EXTENSIONS ===

/**
 * Provides array-like get access to Indexed series.
 */
inline operator fun <T> Indexed<T>.get(i: Int): T = b(i)

/**
 * Provides slice-like access to Indexed series.
 */
inline operator fun <T> Indexed<T>.get(range: IntRange): Indexed<T> {
    val start = range.first
    val end = range.last
    val count = end - start + 1
    return count j { b(start + it) }
}

val <T> Indexed<T>.size get() = a

val <T> Indexed<T>.lastIndex get() = a - 1

val <T> Indexed<T>.indices get() = 0 until a

val <T> Indexed<T>.head: T get() = b(0)

val <T> Indexed<T>.tail: Indexed<T> get() = (a - 1) j { b(it + 1) }


// === TWIN REALM EXTENSIONS ===

/**
 * Provides array-like get access to Twin pairs.
 */
inline operator fun <T> Twin<T>.get(key: Boolean): T = b(key)

// === TENSOR REALM EXTENSIONS ===

/**
 * Provides 2D array-like get access for Tensors.
 */
inline operator fun <T> Tensor<T>.get(
    row: Int,
    col: Int,
): T = b(2 j { if (it == 0) row else col })

/**
 * Provides 3D array-like get access for Tensors.
 */
inline operator fun <T> Tensor<T>.get(
    d1: Int,
    d2: Int,
    d3: Int,
): T = b(3 j {
    when (it) {
        0 -> d1
        1 -> d2
        else -> d3
    }
})


// === INTEROP BRIDGES ===

/**
 * Converts a standard List to a lazy, functional Indexed series.
 */
fun <T> List<T>.toIndexed(): Indexed<T> = size j { this[it] }
fun <T> Array<T>.toIndexed(): Indexed<T> = size j { this[it] }


/**
 * Converts a standard map to an indexed series of pairs
 */
fun <K, V> Map<K, V>.toIndexed(): Indexed<Join<K, V>> {
    val entries = this.entries.toList()
    return entries.size j { entries[it].toPair().let { p -> p.first j p.second } }
}

// === UTILITY FUNCTIONS ===

/**
 * Convenience function to create an empty Indexed series.
 */
fun <T> emptyIndexed(): Indexed<T> = 0 j { throw IndexOutOfBoundsException("Accessing empty series") }


/**
 * Helper function for debugging to print the contents of an Indexed series.
 */
fun <T> Indexed<T>.show(): String =
    if (a == 0) "[]"
    else (0 until a).joinToString(", ", "[", "]") { b(it).toString() }


fun isArray(
    obj: Any?,
    minSize: Int,
    maxSize: Int,
): Boolean =
    obj is Array<*> && obj.size in minSize..maxSize

fun <T> Indexed<T>.asTensor(): Tensor<T> = this as Tensor<T>
fun <T> Indexed<T>.asTwin(): Twin<T> = this as Twin<T>

// --- CopyOnWrite Indexed Implementation ---
// From CowSeriesHandle.kt, consolidated into the single source of truth.

/**
 * Creates a handle for a copy-on-write Indexed.
 * This is the primary entry point for creating a mutable, versioned series
 * that uses structural sharing.
 */
inline val <reified T> Indexed<T>.cow: CowSeriesHandle<T> get() = CowSeriesHandle(COWSeriesBody(this))

/**
 * A simpler copy-on-write implementation that returns a direct view of the data.
 */
val <T> Indexed<T>.cowView: borg.trikeshed.common.collections.ArrayCowView<T>
    get() {
        @Suppress("UNCHECKED_CAST")
        val array = Array<Any?>(a) { b(it) } as Array<T>
        return borg.trikeshed.common.collections.ArrayCowView(array)
    }

/**
 * The handle (or "envelope") for a mutable, copy-on-write series. It holds a reference
 * to the underlying immutable data ("the letter") and swaps it out on mutation.
 * Observers can be attached to watch for changes.
 */
class CowSeriesHandle<T>(
    letter1: COWSeriesBody<T>
) {

    var letter: COWSeriesBody<T> by Delegates.observable(letter1) { prop, old, new ->
        val oldVersion = old.version
        val newVersion = new.version
        observers.forEach { it(oldVersion j newVersion) }
    }


    private val observers = mutableListOf<(Join<Int, Int>) -> Unit>()
    fun addObserver(observer: (Join<Int, Int>) -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: (Join<Int, Int>) -> Unit) {
        observers.remove(observer)
    }

}

/**
 * The body (or "letter") of a copy-on-write series. It contains the actual data
 * and a version number. This part is immutable and is replaced wholesale on mutation.
 */
data class COWSeriesBody<T>(
    val series: Indexed<T>,
    val version: Int = 0
)

// --- RadixTree Support Extensions ---

fun <T: Comparable<T>> Indexed<T>.commonPrefixWith(other: Indexed<T>): Indexed<T> {
    val len = if (this.a < other.a) this.a else other.a
    var common = 0
    while (common < len && this[common] == other[common]) {
        common++
    }
    return this.take(common)
}

fun <T> Indexed<T>.drop(n: Int): Indexed<T> {
    if (n >= a) return emptyIndexed()
    return (a - n) j { i: Int -> this[n + i] }
}

fun <T> Indexed<T>.take(n: Int): Indexed<T> {
    if (n >= a) return this
    return n j { i: Int -> this[i] }
}

fun <T> Indexed<T>.isEmpty(): Boolean = this.a == 0

fun <T> Indexed<T>.plus(other: Indexed<T>): Indexed<T> {
    return (this.a + other.a) j { i: Int ->
        if (i < this.a) this[i] else other[i - this.a]
    }
}

val <T> Indexed<T>.first: T
    get() = if (a > 0) this[0] else throw NoSuchElementException("Indexed is empty.")

val <T: Comparable<T>> Indexed<T>.cpb: T
    get() = this.first


// --- Compatibility Layer ---

// The play property and toIdx functions are from a previous version of the API.
// They are preserved here for backward compatibility with existing code.
// The modern equivalent of `play` is the `▶` operator.

val <T> Indexed<T>.play: List<T> get() = this.`▶`

fun <T> List<T>.toIdx(): Indexed<T> = toIndexed()
fun <T> Array<T>.toIdx(): Indexed<T> = toIndexed()
fun IntArray.toIdx(): Indexed<Int> = size j { this[it] }
fun DoubleArray.toIdx(): Indexed<Double> = size j { this[it] }
fun FloatArray.toIdx(): Indexed<Float> = size j { this[it] }
fun LongArray.toIdx(): Indexed<Long> = size j { this[it] }
fun ShortArray.toIdx(): Indexed<Short> = size j { this[it] }
fun ByteArray.toIdx(): Indexed<Byte> = size j { this[it] }
fun BooleanArray.toIdx(): Indexed<Boolean> = size j { this[it] }
fun CharArray.toIdx(): Indexed<Char> = size j { this[it] }
fun String.toIdx(): Indexed<Char> = length j { this[it] }

/**
 * Checks the PackingMode in the current coroutine context and applies the
 * appropriate memory strategy.
 *
 * - If the mode is `Register` (or absent), it returns `this` (a no-op),
 *   preserving the lazy, function-based structure.
 * - If the mode is `Pointer`, it materializes the series into a new,
 *   concrete Indexed backed by a List, effectively memoizing the results
 *   and preventing re-computation.
 *
 * This should be called before entering a critical loop with a complex Indexed.
 */
suspend fun <T> Indexed<T>.memoize(): Indexed<T> {
    // Default to Register mode if not specified
    val mode = coroutineContext[PackingMode] ?: PackingMode.Register
    return when (mode) {
        PackingMode.Register -> this
        PackingMode.Pointer -> this.play.toIndexed() // Materialize and wrap
    }
}
