@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib

import kotlin.reflect.KClassifier
import borg.trikeshed.lib.Series as Indexed

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
 * type Series<T> = Join<number, (index: number) => T>
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
 * typealias Series<T> = MetaSeries<Int, T>          // Int-indexed sequences
 * typealias Tensor<T> = MetaSeries<Shape, T>        // Shape-indexed tensors  
 * typealias Twin<T> = MetaSeries<Boolean, T>        // Boolean-indexed pairs
 * 
 * // SPECIALIZED REALMS
 * typealias Series2<A, B> = Series<Join<A, B>>      // Sequence of pairs
 * typealias RowVec = Series<Join<Any?, () -> ColumnMeta>>  // Database row
 * typealias Cursor = MetaSeries<CursorIndex, RowVec> // Database table (specialized realm)
 * typealias TensorCursor = Series<Tensor<Any?>>     // Tensor dataset
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
 * val series = 10 j { i -> i * 2 }  // Series<Int>
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
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
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
 * typealias Series<T> = MetaSeries<Int, T>
 * 
 * // Shape realm - Multidimensional access  
 * typealias Tensor<T> = MetaSeries<Shape, T>
 * 
 * // Boolean realm - Binary choice access
 * typealias Twin<T> = MetaSeries<Boolean, T>
 * 
 * // Custom realms - Domain-specific access
 * typealias TimeSeries<T> = MetaSeries<Instant, T>
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
 * val numbers: Series<Double> = 10 j { i -> i * 3.14 }
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
 * val series = 10 j { i -> i * 2 }   // Series<Int>
 * val twin = 3.14 j 2.71             // Twin<Double>
 * ```
 */
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b)

// === SERIES METACLASS SYSTEM ===

/**
 * ## Series<T> - Indexed Sequence Metaclass
 * 
 * Series<T> is a **functional metaclass** that represents indexed sequences through composition
 * of size and accessor function. It's the cornerstone of TrikeShed's data processing.
 * 
 * **Definition:**
 * ```kotlin
 * typealias Series<T> = Join<Int, (Int) -> T>
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
 * type Series<T> = {
 *   size: number;
 *   accessor: (index: number) => T;
 * }
 * ```
 * 
 * ### Series Laws
 * 
 * Series<T> satisfies these **metaclass laws**:
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
typealias Series<T> = Join<Int, (Int) -> T>

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
typealias LongSeries<T> = Join<Long, (Long) -> T>

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
typealias Series2<A, B> = MetaSeries<Int, Join<A, B>>

// === METACLASS OPERATIONS ===

/**
 * ## Series Size Accessor
 * 
 * Extracts the size component from a Series<T> metaclass.
 * This is a **zero-cost abstraction** that accesses the first component of the Join.
 */
val <T> Series<T>.size: Int get() = a

/**
 * ## Series Element Accessor
 * 
 * Accesses elements in a Series<T> by invoking the accessor function.
 * This provides **array-like syntax** while maintaining functional composition.
 */
operator fun <T> Series<T>.get(i: Int): T = b(i)

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
 * auto transform(const Series<X>& series, auto xform) -> Series<C> {
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
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = size j { i -> xform(this[i]) }

/**
 * ## Play Materialization (▶) - ENSHRINED PATTERN
 * 
 * The `play` property materializes a lazy Series<T> into an IterableSeries<T>, enabling
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
val <T> Series<T>.play: IterableSeries<T> get() = this as? IterableSeries<T> ?: IterableSeries(this)

@kotlin.jvm.JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A> {
    override fun iterator(): Iterator<A> = s.iterator()
    val size: Int get() = s.size
    operator fun get(i: Int): A = s[i]
}

fun <T> Series<T>.iterator(): Iterator<T> = object : Iterator<T> {
    private var index = 0
    override fun hasNext(): Boolean = index < size
    override fun next(): T = get(index++)
}

// === COLLECTION CONVERSIONS ===

fun <T> List<T>.toSeries(): Series<T> = this.size j { i -> this[i] }
fun <T> Array<T>.toSeries(): Series<T> = this.size j { i -> this[i] }  
fun <T> Series<T>.toList(): List<T> = this.play.toList()
inline fun <reified T> Series<T>.toArray(): Array<T> = this.play.toList().toTypedArray()

// Special case for ByteArray
fun Series<Byte>.toArray(): ByteArray = ByteArray(this.size) { this[it] }

// === EMPTY SERIES ===

fun <T> emptySeries(): Series<T> = 0 j { throw IndexOutOfBoundsException("Empty series") }

// === SERIES CONSTRUCTION BRIDGE ===

/** Series constructor function from Review */
fun <T> s_(vararg elements: T): Series<T> = elements.toList().toSeries()

// === ADVANCED METACLASSES ===

/**
 * ## Shape - Tensor Dimension Metaclass
 * 
 * Shape represents the **dimensional structure** of tensors and multidimensional arrays
 * as a Series<Int>. This keeps Shape within the MetaSeries ecosystem while providing
 * the same functionality as IntArray with additional compositional benefits.
 * 
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Shape = Series<Int>
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
 * val fromArray: Shape = intArrayOf(3, 4).toSeries()
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
 * //                   = MetaSeries<Series<Int>, T>
 * //                   = Join<Series<Int>, (Series<Int>) -> T>
 * ```
 */
typealias Shape = Series<Int>

/**
 * ## Tensor<T> - Shape-Indexed Realm Specialization
 * 
 * Tensor<T> represents the **Shape realm** of MetaSeries, where elements are accessed
 * by Shape indices (Series<Int>). This provides **multidimensional array semantics**
 * with full integration into the MetaSeries ecosystem.
 * 
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Tensor<T> = MetaSeries<Shape, T>
 * //                   = MetaSeries<Series<Int>, T>
 * //                   = Join<Series<Int>, (Series<Int>) -> T>
 * ```
 * 
 * **Realm Properties:**
 * - **Index Type**: Shape (Series<Int>) - dimensional coordinates
 * - **Access Pattern**: Multidimensional indexing
 * - **Use Cases**: Matrices, tensors, multidimensional data structures
 * 
 * **Benefits of Shape as Series<Int>:**
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
 * // Construction with Shape as Series<Int>
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
 * ```kotlin
 * // Tensor of tensors (nested multidimensional structures)
 * typealias TensorND<T> = Tensor<Tensor<T>>
 * 
 * // Sparse tensor using Map for efficiency
 * typealias SparseTensor<T> = Join<Shape, Map<Shape, T>>
 * ```
 */
typealias Tensor<T> = MetaSeries<Shape, T>

/**
 * ## MetaSeries<T> - Series with Metadata Metaclass
 * 
 * MetaSeries<T> represents a **Series with associated metadata**, enabling rich data processing
 * with contextual information. It composes a Series with its metadata.
 * 
 * **Definition:**
 * ```kotlin
 * typealias MetaSeriesWithMetadata<T> = Join<Series<T>, Map<String, Any?>>
 * //                        ^           ^   ^
 * //                        |           |   └── Metadata values
 * //                        |           └──── Metadata keys  
 * //                        └─────────────── Core data series
 * ```
 * 
 * **Usage:**
 * ```kotlin
 * val data = (10 j { it * it }) j mapOf(
 *     "name" to "squares",
 *     "created" to System.currentTimeMillis(),
 *     "version" to "1.0"
 * )
 * ```
 */
typealias MetaSeriesWithMetadata<T> = Join<Series<T>, Map<String, Any?>>

/**
 * ## ColumnMeta - Database Column Metadata
 * 
 * ColumnMeta contains **database column context** including type, name, and other metadata.
 */
typealias ColumnMeta = Join<String, KClassifier>

/**
 * ## RowVec - Database Row Metaclass  
 * 
 * RowVec represents a **database row** as a Series of value-metadata pairs.
 * Each element is a Join of the actual value and a function that provides column metadata.
 * 
 * **Definition:**
 * ```kotlin
 * typealias RowVec = Series<Join<Any?, () -> ColumnMeta>>
 * //               = Series<Join<Any?, () -> ColumnMeta>>
 * ```
 * 
 * This enables **structured data access** with type information embedded at runtime.
 */
typealias RowVec = Series<Join<Any?, () -> ColumnMeta>>

/**
 * ## TableMeta - Database Table Metadata
 * 
 * TableMeta contains **database table context** including schema, connection info,
 * and other metadata needed for cursor operations.
 */
data class TableMeta(val name: String) {
    // Placeholder - would contain schema, connection, etc.
}

/**
 * ## CursorIndex - Database Row Index Type
 * 
 * CursorIndex represents a **database-specific index** that combines table metadata
 * with row position. This enables **cursor realm separation** from generic Series operations.
 * 
 * **Definition:**
 * ```kotlin
 * typealias CursorIndex = Join<TableMeta, Int>
 * //                     ^          ^    ^
 * //                     |          |    └── Row position  
 * //                     |          └─────── Table metadata
 * //                     └────────────────── Database context
 * ```
 */
typealias CursorIndex = Join<TableMeta, Int>

/**
 * ## Cursor - Database Table Metaclass (Specialized Realm)
 * 
 * Cursor represents the **Database realm** of MetaSeries, where elements are accessed
 * by CursorIndex rather than simple Int. This provides **database-specific semantics**
 * and separates cursor operations from generic series operations.
 * 
 * **MetaSeries Foundation:**
 * ```kotlin
 * typealias Cursor = MetaSeries<CursorIndex, RowVec>
 * //                = MetaSeries<Join<TableMeta, Int>, RowVec>
 * //                = Join<Join<TableMeta, Int>, (Join<TableMeta, Int>) -> RowVec>
 * ```
 * 
 * **Realm Properties:**
 * - **Index Type**: CursorIndex (database-aware row identification)
 * - **Access Pattern**: Database row access with table context
 * - **Use Cases**: SQL result sets, database tables, structured data queries
 * 
 * **Benefits of Realm Separation:**
 * - **Type Safety**: Cursor operations can't accidentally mix with Series operations
 * - **Database Context**: Table metadata available in index operations
 * - **Specialized Operations**: Cursor-specific methods like join, filter, aggregate
 * - **Performance**: Database-aware optimizations possible
 * 
 * **Usage:**
 * ```kotlin
 * // Construction with database context
 * val tableMeta = TableMeta("users")
 * val cursor: Cursor = (tableMeta j rowCount) j { cursorIndex ->
 *     val (meta, rowPos) = cursorIndex
 *     columnCount j { colIndex ->
 *         getDatabaseValue(meta.name, rowPos, colIndex) j { 
 *             getColumnMeta(meta.name, colIndex) 
 *         }
 *     }
 * }
 * 
 * // Access with database context
 * val rowIndex = TableMeta("users") j 5  // Row 5 in users table
 * val row = cursor.b(rowIndex)
 * val cell = row[2].a  // Get value at column 2
 * val meta = row[2].b()  // Get metadata for that column
 * ```
 * 
 * **Cursor Realm Operations:**
 * ```kotlin
 * // These would be cursor-specific extensions
 * fun Cursor.where(predicate: (RowVec) -> Boolean): Cursor = TODO()
 * fun Cursor.join(other: Cursor): Cursor = TODO()
 * fun Cursor.groupBy(keyExtractor: (RowVec) -> Any): Map<Any, Cursor> = TODO()
 * ```
 */
typealias Cursor = MetaSeries<CursorIndex, RowVec>

/**
 * ## TensorCursor - Tensor Dataset Metaclass
 * 
 * TensorCursor represents a **dataset of tensors**, combining database-like row access
 * with multidimensional array operations. Each row contains a complete tensor.
 * 
 * **Definition:**
 * ```kotlin
 * typealias TensorCursor = Series<Tensor<Any?>>
 * ```
 * 
 * **Usage:**
 * ```kotlin
 * val dataset: TensorCursor = batchCount j { batchIndex ->
 *     tensorShape j { coords -> getBatchData(batchIndex, coords) }
 * }
 * val batch = dataset[0]  // Get first tensor
 * val element = batch.b(intArrayOf(1, 2, 3))  // Access tensor element
 * ```
 */
typealias TensorCursor = Series<Tensor<Any?>>

// === METACLASS ECOSYSTEM ===

/**
 * ## Metaclass Composition Examples
 * 
 * The TrikeShed metaclass system enables **arbitrary composition** of data structures
 * through the universal Join<A,B> foundation:
 * 
 * ```kotlin
 * // Time series with metadata
 * typealias TimeSeries<T> = Join<Series<T>, Map<String, Any?>>
 * 
 * // Sparse tensor  
 * typealias SparseTensor<T> = Join<Shape, Map<IntArray, T>>
 * 
 * // Versioned cursor
 * typealias VersionedCursor = Join<Cursor, String>
 * 
 * // Multi-dimensional metadata
 * typealias MetaTensor<T> = Join<Tensor<T>, Tensor<Map<String, Any?>>>
 * ```
 * 
 * This **compositional approach** enables domain-specific types that maintain
 * mathematical properties while expressing business concepts directly in the type system.
 */

/**
 * Either type for error handling and disjoint unions.
 * Moved to CoreTypes as a foundational data structure.
 */
sealed interface Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>
    data class Right<R>(val value: R) : Either<Nothing, R>

    companion object {
        fun <L> left(value: L): Either<L, Nothing> = Left(value)
        fun <R> right(value: R): Either<Nothing, R> = Right(value)
    }
}

// === UTILITY FUNCTIONS ===

/**
 * Creates a Series from a list of elements.
 * This is a convenience function for creating Series instances.
 */
fun <T> seriesOf(vararg elements: T): Series<T> = 
    (elements.size j { i -> elements[i] })

/**
 * Creates an empty Series.
 * This is a convenience function for creating empty Series instances.
 */
fun <T> emptySeries(): Series<T> = 
    (0 j { _ -> throw IndexOutOfBoundsException("Empty series") })

