@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib

// import kotlinx.datetime.Clock // Removed dependency

// Post-migration: Indexed is now the canonical type
// Series was migrated to Indexed as per CLAUDE.md migration plan

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
 * typealias Indexed<T> = MetaSeries<Int, T>         // Int-indexed sequences (was Series)
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
 * 2. **Transform (α)** - Maps over Series structures functionally
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
 * val series = 10 j { i -> i * 2 }  // Indexed<Int>
 *
 * // Complex composition
 * val table = rowCount j { i -> columnCount j { j -> data[i][j] } }
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
 * // Series realm (Int-indexed)
 * val numbers: Indexed<Double> = 10 j { i -> i * 3.14 }
 *
 * // Tensor realm (Shape-indexed)
 * val matrix: Tensor<Float> = intArrayOf(3, 4) j { coords ->
 *     coords[0] * 4.0f + coords[1]
 * }
 *
 * // Twin realm (Boolean-indexed)
 * val choice: Twin<String> = true j { if (it) "yes" else "no" }
 *
 * // Custom realm (Time-indexed)
 * val events: MetaSeries<Instant, Event> = startTime j { time ->
 *     getEventAt(time)
 * }
 * ```
 */
typealias MetaSeries<A, T> = Join<A, (A) -> T>

// === REALM SPECIALIZATIONS ===

/**
 * ## Twin<T> - Boolean-Indexed Realm Specialization
 *
 * Twin<T> represents the **Boolean realm** of MetaSeries, where elements are accessed
 * by Boolean indices (true/false). This provides **binary choice semantics** with
 * type safety and functional composition.
 *
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Twin<T> = MetaSeries<Boolean, T>
 * //                 = Join<Boolean, (Boolean) -> T>
 * ```
 *
 * **Realm Properties:**
 * - **Index Type**: Boolean (true/false)
 * - **Access Pattern**: Binary choice
 * - **Use Cases**: Either/or values, min/max bounds, coordinate pairs
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename T>
 * using Twin = MetaSeries<bool, T>;
 * ```
 *
 * **Usage Examples:**
 * ```kotlin
 * // Boolean-indexed access
 * val coordinate: Twin<Int> = true j { if (it) 42 else 37 }  // x=42, y=37
 * val bounds: Twin<Float> = true j { if (it) 1.0f else 0.0f }  // max=1.0, min=0.0
 *
 * // Access by boolean index
 * val x = coordinate.b(true)   // 42
 * val y = coordinate.b(false)  // 37
 * ```
 *
 * **Legacy Compatibility:**
 * For backward compatibility with Join<T,T> patterns:
 * ```kotlin
 * val legacy: Join<Int, Int> = 42 j 37  // Still works
 * ```
 */
typealias Twin<T> = Join<T, T>

/**
 * ## Composition Operator (j) - Universal Metaclass Constructor
 *
 * The `j` operator is TrikeShed's **universal composition operator** that creates Join instances
 * from any two values. It serves as the syntactic foundation for the entire metaclass system.
 *
 * **TSX Inspiration:**
 * ```typescript
 * const j = <A, B>(a: A, b: B): Join<A, B> => ({ a, b })
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val basic = 42 j "hello"           // Join<Int, String>
 * val series = 10 j { i -> i * 2 }   // Indexed<Int>
 * val twin = 3.14 j 2.71             // Twin<Double>
 * ```
 */
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b)

/**
 * ## Series Construction Operator (j) - Indexed Sequence Builder
 *
 * The `j` operator for Int creates Indexed sequences from size and accessor function.
 * This is the primary constructor for Indexed<T> metaclasses.
 *
 * **Usage:**
 * ```kotlin
 * val numbers = 10 j { i -> i * i }  // Indexed<Int> with squares
 * val strings = 5 j { i -> "item$i" }  // Indexed<String>
 * ```
 */
inline infix fun <T> Int.j(noinline getter: (index: Int) -> T): Indexed<T> = Join.invoke(this, getter)

// === SERIES METACLASS SYSTEM ===

/**
 * ## Indexed<T> - Indexed Sequence Metaclass
 *
 * Indexed<T> is a **functional metaclass** that represents indexed sequences through composition
 * of size and accessor function. It's the cornerstone of TrikeShed's data processing.
 *
 * **Definition:**
 * ```kotlin
 * typealias Indexed<T> = Join<Int, (Int) -> T>
 * //                   ^     ^    ^
 * //                   |     |    └── Element accessor function
 * //                   |     └───────── Index parameter
 * //                   └─────────────── Series size
 * ```
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename T>
 * struct Series {
 *     int size;
 *     std::function<T(int)> accessor;
 * };
 * ```
 *
 * **TSX Equivalent:**
 * ```typescript
 * type Indexed<T> = {
 *   size: number;
 *   accessor: (index: number) => T;
 * }
 * ```
 *
 * ### Series Laws
 *
 * Indexed<T> satisfies these **metaclass laws**:
 * - **Bounds**: 0 <= i < series.size for valid access
 * - **Consistency**: series[i] always returns the same value for the same i
 * - **Laziness**: Elements computed on-demand via accessor function
 *
 * ### Usage Patterns
 *
 * ```kotlin
 * // Construction
 * val numbers = 10 j { i -> i * i }  // Squares: [0, 1, 4, 9, 16, ...]
 *
 * // Access
 * val third = numbers[3]  // 9
 * val size = numbers.size // 10
 *
 * // Transformation
 * val strings = numbers α { it.toString() }  // ["0", "1", "4", "9", ...]
 * ```
 */
typealias Indexed<T> = Join<Int, (Int) -> T>

/**
 * ## LongSeries<T> - Long-Indexed Series Metaclass
 *
 * LongSeries<T> is a **functional metaclass** that represents long-indexed sequences.
 * This is useful for large datasets that exceed Int.MAX_VALUE in size.
 *
 * **Definition:**
 * ```kotlin
 * typealias LongSeries<T> = Join<Long, (Long) -> T>
 * //                      ^     ^    ^
 * //                      |     |    └── Element accessor function
 * //                      |     └───────── Index parameter
 * //                      └─────────────── Series size
 * ```
 */
typealias LongIndexed<T> = Join<Long, (Long) -> T>

/**
 * ## Series2<A, B> - MetaSeries with Join Elements
 *
 * Series2<A,B> represents a **MetaSeries where the element type is a Join<A,B>**.
 * This provides structured data processing with paired elements while maintaining
 * the full MetaSeries foundation.
 *
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Series2<A, B> = MetaSeries<Int, Join<A, B>>
 * //                       = Join<Int, (Int) -> Join<A, B>>
 * ```
 *
 * **Benefits:**
 * - **Structured Elements**: Each position contains a typed pair
 * - **Type Safety**: A and B types are preserved and enforced
 * - **Functional Operations**: Can transform either component independently
 * - **MetaSeries Integration**: Full access to α transforms and other operations
 *
 * **Usage Examples:**
 * ```kotlin
 * // Construction
 * val coordinates: Series2<Int, Int> = 10 j { i -> i j (i * 2) }
 * val keyValues: Series2<String, Any?> = data.size j { i ->
 *     data.keys[i] j data.values[i]
 * }
 *
 * // Access
 * val point = coordinates[3]        // Join<Int, Int>
 * val x = coordinates[3].a          // Int (first component)
 * val y = coordinates[3].b          // Int (second component)
 *
 * // Functional operations
 * val scaled = coordinates α { (x, y) -> (x * 2) j (y * 2) }
 * val xValues = coordinates α { it.a }  // Extract x coordinates
 * val yValues = coordinates α { it.b }  // Extract y coordinates
 * ```
 *
 * **Advanced Patterns:**
 * ```kotlin
 * // MetaSeries2 for custom index types
 * typealias MetaSeries2<I, A, B> = MetaSeries<I, Join<A, B>>
 *
 * // Tensor2 for paired tensor elements
 * typealias Tensor2<A, B> = MetaSeries<Shape, Join<A, B>>
 * ```
 */
typealias Indexed2<A, B> = Indexed<Join<A, B>>

/**
 * ## Indexed2 Left Projection
 *
 * Extracts the left component of an Indexed2<A,B> as an Indexed<A>.
 * This provides a view of just the first elements from each pair.
 */
val <A, B> Indexed2<A, B>.left: Indexed<A>
    get() = this.a j { i -> this.b(i).a }

/**
 * ## Indexed2 Right Projection
 *
 * Extracts the right component of an Indexed2<A,B> as an Indexed<B>.
 * This provides a view of just the second elements from each pair.
 */
val <A, B> Indexed2<A, B>.right: Indexed<B>
    get() = this.a j { i -> this.b(i).b }

// === CZERO UTILITIES ===

/**
 * CZero utilities for C-style zero/non-zero checks
 * Provides idiomatic Kotlin extensions for C interop
 */
object CZero {
    /** Check if value is zero */
    val Int.z: Boolean get() = this == 0
    val UInt.z: Boolean get() = this == 0u
    val Long.z: Boolean get() = this == 0L
    val ULong.z: Boolean get() = this == 0uL

    /** Check if value is non-zero */
    val Int.nz: Boolean get() = this != 0
    val UInt.nz: Boolean get() = this != 0u
    val Long.nz: Boolean get() = this != 0L
    val ULong.nz: Boolean get() = this != 0uL
}

// === HELPER FUNCTIONS ===

/**
 * Empty Indexed collection
 * Returns an empty indexed collection of the specified type
 */
fun <T> emptyIndex(): Indexed<T> = 0 j { _ -> throw IndexOutOfBoundsException("Empty index") }

/**
 * Empty Indexed collection (alternate name)
 */
fun <T> emptyIndexed(): Indexed<T> = emptyIndex()

// === METACLASS OPERATIONS ===

/**
 * ## Series Size Accessor
 *
 * Extracts the size component from a Indexed<T> metaclass.
 * This is a **zero-cost abstraction** that accesses the first component of the Join.
 */
val <T> Indexed<T>.size: Int get() = a

/**
 * ## Series Element Accessor
 *
 * Accesses elements in a Indexed<T> by invoking the accessor function.
 * This provides **array-like syntax** while maintaining functional composition.
 */
operator fun <T> Indexed<T>.get(i: Int): T = b(i)

/**
 * ## Transform Operator (α) - Series Metaclass Functor
 *
 * The α (alpha) operator is TrikeShed's **universal transformation operator** for Series.
 * It maps over a Series functionally, creating a new Series with transformed elements.
 *
 * **Mathematical Foundation:**
 * This implements the **Functor law** for Series:
 * - `series α { it } == series` (identity)
 * - `series α f α g == series α { g(f(it)) }` (composition)
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename X, typename C>
 * auto transform(const Indexed<X>& series, auto xform) -> Indexed<C> {
 *     return { series.size, [=](int i) { return xform(series[i]); } };
 * }
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val numbers = 10 j { it }                    // [0, 1, 2, ..., 9]
 * val squares = numbers α { it * it }          // [0, 1, 4, ..., 81]
 * val strings = squares α { it.toString() }    // ["0", "1", "4", ..., "81"]
 * ```
 */
inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = size j { i -> xform(this[i]) }

/**
 * ## Play Materialization (▶) - ENSHRINED PATTERN
 *
 * The `play` property materializes a lazy Indexed<T> into an IterableSeries<T>, enabling
 * integration with Kotlin's standard library collections operations.
 *
 * **Design Philosophy:**
 * TrikeShed maintains **lazy evaluation** by default, but provides seamless materialization
 * when needed for standard library integration. The ▶ (play) operator serves as the
 * **bridge between lazy and eager evaluation**.
 *
 * **Usage:**
 * ```kotlin
 * val series = 1000 j { it * it }
 * val eager = series.play.map { it.toString() }.filter { it.length > 2 }
 * ```
 */
val <T> Indexed<T>.play: IterableSeries<T> get() = this as? IterableSeries<T> ?: IterableSeries(this)

@kotlin.jvm.JvmInline
value class IterableSeries<A>(
    val s: Indexed<A>,
) : Iterable<A> {
    override operator fun iterator(): Iterator<A> = s.iterator()

    val size: Int get() = s.size

    operator fun get(i: Int): A = s[i]
}

operator fun <T> Indexed<T>.iterator(): Iterator<T> =
    object : Iterator<T> {
        private var index = 0

        override fun hasNext(): Boolean = index < size

        override fun next(): T = get(index++)
    }

// === COLLECTION CONVERSIONS ===

// Note: toSeries() is obsolete - use toIdx() instead
fun <T> Indexed<T>.toList(): List<T> = this.play.toList()

inline fun <reified T> Indexed<T>.toArray(): Array<T> = this.play.toList().toTypedArray()

// Special case for ByteArray
fun Indexed<Byte>.toArray(): ByteArray = ByteArray(this.size) { this[it] }

// === SERIES CONSTRUCTION BRIDGE ===

/** Series constructor function from Review - moved to BrokeShed compatibility section */

// === DATABASE CURSOR TYPES ===

/**
 * ColumnMeta - Database column metadata
 * Represents the metadata for a database column including name and type information
 */
data class ColumnMeta(
    val a: String,
    val b: kotlin.reflect.KClass<*>,
) {
    companion object {
        fun create(
            name: String,
            typeName: String,
        ): ColumnMeta {
            val kClass =
                when (typeName) {
                    "String" -> String::class
                    "Int" -> Int::class
                    "Long" -> Long::class
                    "Float" -> Float::class
                    "Double" -> Double::class
                    "Boolean" -> Boolean::class
                    "Byte" -> Byte::class
                    "Short" -> Short::class
                    "Char" -> Char::class
                    else -> String::class // Default to String
                }
            return ColumnMeta(name, kClass)
        }
    }
}

/**
 * Database row vector - represents a single row in a database cursor
 */
typealias RowVec = Indexed<Join<Any?, () -> ColumnMeta>>

/**
 * Database cursor - represents a database table/query result
 */
typealias DatabaseCursor = Indexed<RowVec>

// === ADVANCED METACLASSES ===

/**
 * ## Shape - Tensor Dimension Metaclass
 *
 * Shape represents the **dimensional structure** of tensors and multidimensional arrays
 * as a Indexed<Int>. This keeps Shape within the MetaSeries ecosystem while providing
 * the same functionality as IntArray with additional compositional benefits.
 *
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Shape = Indexed<Int>
 * //              = MetaSeries<Int, Int>
 * //              = Join<Int, (Int) -> Int>
 * ```
 *
 * **Benefits over IntArray:**
 * - **Functional composition**: Can use α transforms and other Series operations
 * - **Lazy evaluation**: Dimensions computed on-demand if needed
 * - **Unified ecosystem**: Works with all MetaSeries operations
 * - **Type safety**: Part of the realm-separated type system
 *
 * **cppfront Equivalent:**
 * ```cpp
 * using Shape = MetaSeries<int, int>;  // Not std::vector<int>
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // Construction (similar to IntArray)
 * val matrixShape: Shape = 2 j { i -> if (i == 0) 3 else 4 }  // [3, 4]
 * val tensorShape: Shape = 4 j { i -> i + 2 }  // [2, 3, 4, 5]
 *
 * // From existing IntArray
 * val fromArray: Shape = intArrayOf(3, 4).toList().toIdx()
 *
 * // Functional operations
 * val doubled: Shape = matrixShape α { it * 2 }  // [6, 8]
 * val volume = matrixShape.play.fold(1) { acc, dim -> acc * dim }  // 12
 *
 * // Access
 * val width = matrixShape[0]   // 3
 * val height = matrixShape[1]  // 4
 * val rank = matrixShape.size  // 2
 * ```
 *
 * **Integration with Tensor:**
 * ```kotlin
 * typealias Tensor<T> = MetaSeries<Shape, T>
 * //                   = MetaSeries<Indexed<Int>, T>
 * //                   = Join<Indexed<Int>, (Indexed<Int>) -> T>
 * ```
 */
typealias Shape = Indexed<Int>

/**
 * ## Tensor<T> - Shape-Indexed Realm Specialization
 *
 * Tensor<T> represents the **Shape realm** of MetaSeries, where elements are accessed
 * by Shape indices (Indexed<Int>). This provides **multidimensional array semantics**
 * with full integration into the MetaSeries ecosystem.
 *
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Tensor<T> = MetaSeries<Shape, T>
 * //                   = MetaSeries<Indexed<Int>, T>
 * //                   = Join<Indexed<Int>, (Indexed<Int>) -> T>
 * ```
 *
 * **Realm Properties:**
 * - **Index Type**: Shape (Indexed<Int>) - dimensional coordinates
 * - **Access Pattern**: Multidimensional indexing
 * - **Use Cases**: Matrices, tensors, multidimensional data structures
 *
 * **Benefits of Shape as Indexed<Int>:**
 * - **Functional operations**: Shape can be transformed with α operator
 * - **Lazy evaluation**: Coordinates computed on-demand
 * - **Type safety**: Shape operations are type-checked
 * - **Composability**: Shapes can be composed and manipulated functionally
 *
 * **cppfront Equivalent:**
 * ```cpp
 * template<typename T>
 * using Tensor = MetaSeries<MetaSeries<int, int>, T>;
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // Construction with Shape as Indexed<Int>
 * val matrixShape: Shape = 2 j { i -> if (i == 0) 3 else 4 }  // [3, 4]
 * val matrix: Tensor<Double> = matrixShape j { coords ->
 *     coords[0] * 4.0 + coords[1]
 * }
 *
 * // Access using Shape coordinates
 * val coord: Shape = 2 j { i -> if (i == 0) 1 else 2 }  // [1, 2]
 * val element = matrix.b(coord)  // Access element at [1,2]
 *
 * // Shape operations
 * val doubledShape: Shape = matrixShape α { it * 2 }  // [6, 8]
 * val reshapedTensor: Tensor<Double> = doubledShape j { coords ->
 *     // New tensor with doubled dimensions
 *     matrix.b(2 j { i -> coords[i] / 2 })
 * }
 *
 * // Integration with Series operations
 * val volume = matrix.a.play.fold(1) { acc, dim -> acc * dim }  // Shape volume
 * ```
 *
 * **Advanced Patterns:**
 *
 * **Shape Composition:**
 * ```kotlin
 * val batchShape: Shape = 1 j { 32 }  // [32]
 * val imageShape: Shape = 3 j { i -> if (i == 0) 224 else 224 }  // [224, 224, 224]
 * val fullShape: Shape = batchShape.play + imageShape.play  // [32, 224, 224, 224]
 * ```
 *
 * **Tensor Broadcasting:**
 * ```kotlin
 * val scalar: Tensor<Double> = 1 j { _ -> 42.0 }
 * val vector: Tensor<Double> = 10 j { i -> i.toDouble() }
 * val result: Tensor<Double> = scalar + vector  // Broadcasting
 * ```
 */
typealias Tensor<T> = MetaSeries<Shape, T>

// === TENSOR CONSTRUCTION UTILITIES ===

/**
 * ## toIdx - Indexed Conversion Helper
 *
 * Converts various collection types to Indexed<T> (formerly Indexed<T>).
 * This provides a bridge between standard Kotlin collections and the MetaSeries ecosystem.
 *
 * **Usage:**
 * ```kotlin
 * val list = listOf(1, 2, 3, 4, 5)
 * val series: Indexed<Int> = list.toIdx()
 * ```
 */
fun <T> List<T>.toIdx(): Indexed<T> = size j { this[it] }

fun <T> Array<T>.toIdx(): Indexed<T> = size j { this[it] }

fun IntArray.toIdx(): Indexed<Int> = size j { this[it] }

fun DoubleArray.toIdx(): Indexed<Double> = size j { this[it] }

fun FloatArray.toIdx(): Indexed<Float> = size j { this[it] }

fun LongArray.toIdx(): Indexed<Long> = size j { this[it] }

fun ShortArray.toIdx(): Indexed<Short> = size j { this[it] }

fun ByteArray.toIdx(): Indexed<Byte> = size j { this[it] }

fun BooleanArray.toIdx(): Indexed<Boolean> = size j { this[it] }

fun CharArray.toIdx(): Indexed<Char> = size j { this[it] }

// === PLATFORM-SPECIFIC BRIDGES ===

/**
 * ## Platform-specific bridges for common operations
 *
 * These provide platform-agnostic interfaces for operations that need
 * platform-specific implementations.
 */

// === COMPATIBILITY ALIASES ===

// For backward compatibility with existing code
@Deprecated("Use toIdx() instead", ReplaceWith("toIdx()"))
fun <T> List<T>.toIndexed(): Indexed<T> = toIdx()

// === INDEXED EXTENSIONS ===

typealias Score = Double

fun <T : Comparable<T>> Indexed<T>.best(): T? {
    if (this.a == 0) return null
    var best = this.b(0)
    for (i in 1 until this.a) {
        val current = this.b(i)
        if (current > best) {
            best = current
        }
    }
    return best
}

fun <T> Indexed<T>.scoreWith(scorer: (T) -> Score): Indexed<Join<Score, T>> = this.a j { i -> scorer(this.b(i)) j this.b(i) }

fun <T> Indexed<Join<Score, T>>.rankByScore(): Indexed<Join<Score, T>> {
    val materialized = this.play.toMutableList()
    materialized.sortByDescending { it.a }
    return materialized.toIdx()
}

fun <T> Indexed<T>.take(n: Int): Indexed<T> {
    if (n <= 0) return emptyIndex()
    val actualTake = minOf(n, this.a)
    return actualTake j { i -> this.b(i) }
}

fun <T, R> Indexed<T>.evolveWith(transform: (T) -> R): Indexed<R> = this.a j { i -> transform(this.b(i)) }

fun <T> Indexed<T>.selectTop(ratio: Double): Indexed<T> {
    val count = (this.a * ratio).toInt()
    return this.take(count)
}

// === OPERATOR EXTENSIONS ===

/**
 * Division operator for Int
 */
operator fun Int.div(other: Int): Int = this / other

/**
 * Remainder operator for Int  
 */
operator fun Int.rem(other: Int): Int = this % other

/**
 * Division operator for Long
 */
operator fun Long.div(other: Long): Long = this / other

/**
 * Remainder operator for Long
 */
operator fun Long.rem(other: Long): Long = this % other

/**
 * Division operator for Double
 */
operator fun Double.div(other: Double): Double = this / other

/**
 * Remainder operator for Double
 */
operator fun Double.rem(other: Double): Double = this % other

/**
 * Division operator for Float
 */
operator fun Float.div(other: Float): Float = this / other

/**
 * Remainder operator for Float
 */
operator fun Float.rem(other: Float): Float = this % other

// === UTILITY FUNCTIONS ===

/**
 * Check if an object is an array
 */
fun isArray(obj: Any?): Boolean = obj is Array<*>

/**
 * Check if an object is an array with specific size
 */
fun isArray(obj: Any?, size: Int): Boolean = obj is Array<*> && obj.size == size

/**
 * Check if an object is an array with size range
 */
fun isArray(obj: Any?, minSize: Int, maxSize: Int): Boolean = 
    obj is Array<*> && obj.size in minSize..maxSize
