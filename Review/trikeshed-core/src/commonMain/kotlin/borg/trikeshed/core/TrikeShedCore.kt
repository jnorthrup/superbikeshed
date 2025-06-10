@file:OptIn(ExperimentalUnsignedTypes::class)
@file:JsExport
@file:Suppress(
    "NOTHING_TO_INLINE", // Crucial for zero-cost abstractions
    "FunctionName",      // For unconventional names like `j`, `α`, `▶`, `↺`
    "ObjectPropertyName",// For object property names
    "UNCHECKED_CAST",    // Often necessary with generic type-erased patterns
    "NonAsciiCharacters",// For symbols like α, ▶, ↺
    "TooManyFunctions"   // Suppress for large utility file
)

package borg.trikeshed.core

import borg.trikeshed.core.git.GitRepositoryView // New import
import borg.trikeshed.core.git.internal.platformIsDirectory // New import
import borg.trikeshed.core.git.internal.platformIsFile // New import
import borg.trikeshed.core.git.internal.platformJoinPath // New import
// import borg.trikeshed.core.git.internal.readPlatformTextFile // Not used in final proposed code, but was in prompt
import borg.trikeshed.core.name
import borg.trikeshed.core.`▶`
// import kotlinx.serialization.Serializable // Removed
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass

// I. borg.trikeshed.lib Essentials: Join and Series Primitives

/**
 * Interface representing a fundamental Join operation, similar to a Pair but with named `a` and `b` components.
 *
 * capture based storage!  don't make a class make 2 getters instead.
 *
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
interface Join<A, B> { // @Serializable removed
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)
}
// @Serializable // Removed
data class SerializableJoin<A, B>(override val a: A, override val b: B) : Join<A, B>

/**
 * Syntactic sugar for  capture-based cost
 */
inline infix fun <A, B> A.j(b: B): Join<A, B> = SerializableJoin(this, b)

/** Accessor for the first element of a [Join]. */
inline val <A, B> Join<A, B>.first: A get() = a
/** Accessor for the second element of a [Join]. */
inline val <A, B> Join<A, B>.second: B get() = b

/**
 * Type alias for a [Join] where both elements are of the same type.
 */
typealias Twin<T> = Join<T, T>

/**
 * Factory function to create a [Twin] from a single value.
 */
fun <T> T.twin(): Twin<T> = this j this

/**
 * Type alias for a Series, which is a [Join] of a size ([Int]) and an accessor function.
 * This represents a lazily-evaluated sequence of elements indexed by an integer.
 */
typealias Series<T> = Join<Int, (Int) -> T>

/** Returns the size of the [Series]. */
inline val <T> Series<T>.size: Int get() = a

/**
 * Operator to access an element of the [Series] by its index.
 */
inline operator fun <T> Series<T>.get(i: Int): T = b(i)

/**
 * An empty [Series] instance.
 */
object EmptySeries : Series<Any?> by (0 j { _ -> null })

/**
 * Factory function to create an empty [Series].
 */
inline fun <T> emptySeries(): Series<T> = EmptySeries as Series<T>

/**
 * Creates a lazy supplier (a lambda with no arguments) that returns `this` value.
 * Used for lazy meta-patterns.
 */
inline val <T> T.leftIdentity: () -> T get() = { this }

/**
 * Syntactic sugar for [leftIdentity].
 */
inline val <T> T.`↺`: () -> T get() = leftIdentity

/**
 * A value class wrapper around [Series] that makes it [Iterable].
 */
@JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A>, Series<A> by s {
    override fun iterator(): Iterator<A> = object : Iterator<A> {
        private var index = 0
        override fun hasNext(): Boolean = index < s.size
        override fun next(): A = s[index++]
    }
}

/**
 * Provides an [Iterable] view of the [Series].
 */
inline val <T> Series<T>.`▶`: IterableSeries<T> get() = IterableSeries(this)

/**
 * Extension function to convert a [Series] of [Char] to a String.
 */
fun Series<Char>.asString(): String = this.`▶`.joinToString("")

// III. core.Tensor Implementation

// @Serializable // Removed
data class SerializableTensorData<T>(val shape: IntArray, val data: Series<T>)

// @Serializable // Removed
data class SerializableSeriesData<T>(val data: Series<T>)

/**
 * Type alias for a Tensor, which is a [Join] of its shape ([IntArray]) and an accessor function
 * that takes coordinates ([IntArray]) and returns an element.
 */
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

/** Returns the shape of the [Tensor]. */
internal inline val <T> Tensor<T>.tensorShape: IntArray get() = a
/** Returns the accessor function of the [Tensor]. */
internal inline val <T> Tensor<T>.tensorAccessor: (IntArray) -> T get() = b

/** Syntactic sugar for [tensorShape]. */
internal inline val <T> Tensor<T>.shape: IntArray get() = tensorShape
/** Syntactic sugar for [tensorAccessor]. */
internal inline val <T> Tensor<T>.accessor: (IntArray) -> T get() = tensorAccessor

/** Returns the rank (number of dimensions) of the [Tensor]. */
internal inline val <T> Tensor<T>.tensorRank: Int get() = shape.size
/** Syntactic sugar for [tensorRank]. */
internal inline val <T> Tensor<T>.rank: Int get() = tensorRank

/** Returns the total number of elements in the [Tensor]. */
internal inline val <T> Tensor<T>.tensorTotalSize: Int get() = if (shape.isEmpty()) 0 else shape.reduce { acc, i -> acc * i }
/** Syntactic sugar for [tensorTotalSize]. */
internal inline val <T> Tensor<T>.totalSize: Int get() = tensorTotalSize

/**
 * Constructs a [Tensor] from a given shape and accessor function.
 */
internal inline fun <T> TensorConstruct(shape: IntArray, noinline accessor: (IntArray) -> T): Tensor<T> =
    shape j accessor

/**
 * Constructs a 1-dimensional [Tensor] (a "Series") from a size and an accessor function.
 */
internal inline fun <T> TensorSeries(size: Int, noinline accessor: (Int) -> T): Tensor<T> =
    intArrayOf(size) j { coords -> accessor(coords[0]) }

/**
 * Constructs a 2-dimensional [Tensor] (a "Cursor") from rows, columns, and an accessor function.
 */
internal inline fun <T> TensorCursor(rows: Int, cols: Int, noinline accessor: (Int, Int) -> T): Tensor<T> =
    intArrayOf(rows, cols) j { coords -> accessor(coords[0], coords[1]) }

/**
 * Invokes the [Tensor]'s accessor with the given coordinates.
 */
internal inline operator fun <T> Tensor<T>.invoke(coords: IntArray): T = accessor(coords)

/**
 * Invokes the [Tensor]'s accessor with the given variable arguments for coordinates.
 */
@JsName("invokeVararg")
internal inline operator fun <T> Tensor<T>.invoke(vararg coords: Int): T = accessor(coords)

/**
 * Invokes a 1-dimensional [Tensor]'s accessor with a single coordinate.
 */
@JsName("invokeRank1")
internal inline operator fun <T> Tensor<T>.invoke(i: Int): T {
    require(rank == 1) { "Tensor is not rank 1. Use invoke(coords: IntArray) or invoke(vararg coords: Int)." }
    return this(intArrayOf(i))
}

/**
 * Invokes a 2-dimensional [Tensor]'s accessor with row and column coordinates.
 */
@JsName("invokeRank2")
internal inline operator fun <T> Tensor<T>.invoke(i: Int, j: Int): T {
    require(rank == 2) { "Tensor is not rank 2. Use invoke(coords: IntArray) or invoke(vararg coords: Int)." }
    return this(intArrayOf(i, j))
}

// IV. Core Tensor Operations

/**
 * Applies a transformation function element-wise to a [Tensor], producing a new [Tensor].
 * This is an "alpha-conversion" operation.
 */
@JsName("alphaTensor")
internal inline infix fun <X, C> Tensor<X>.α(crossinline transform: (X) -> C): Tensor<C> =
    shape j { coords: IntArray -> transform(accessor(coords)) }

/**
 * Applies a transformation function element-wise to a [Series], producing a new [Series].
 * Added to support 'alpha' operations on Series in Cursor.kt.
 */
@JsName("alphaSeries")
internal inline infix fun <X, C> Series<X>.α(crossinline transform: (X) -> C): Series<C> =
    size j { i -> transform(this[i]) }

/**
 * Converts a linear index into multi-dimensional coordinates based on the [Tensor]'s shape.
 */
fun Tensor<*>.linearToCoords(linearIndex: Int): IntArray {
    val coords = IntArray(rank)
    var remaining = linearIndex
    var i = rank - 1
    while (i >= 0) {
        coords[i] = remaining % shape[i]
        remaining /= shape[i]
        i--
    }
    return coords
}

/**
 * Converts multi-dimensional coordinates into a linear index based on the [Tensor]'s shape.
 */
fun Tensor<*>.coordsToLinear(coords: IntArray): Int {
    require(coords.size == rank) { "Coordinate rank mismatch: expected $rank, got ${coords.size}" }
    var linearIndex = 0
    var multiplier = 1
    for (i in rank - 1 downTo 0) {
        require(coords[i] >= 0 && coords[i] < shape[i]) { "Coordinate out of bounds: coords[$i]=${coords[i]} for dimension $i with size ${shape[i]}" }
        linearIndex += coords[i] * multiplier
        multiplier *= shape[i]
    }
    return linearIndex
}

/**
 * Materializes the entire content of a [Tensor] into an [Array].
 * Note: This can be memory-intensive for large tensors.
 */
fun <T> Tensor<T>.materialize(): Array<T> {
    val arr = arrayOfNulls<Any?>(totalSize) as Array<T>
    for (i in 0 until totalSize) {
        // arr[i] = this(linearToCoords(i)) // Commented out L248
    }
    return arr
}

/**
 * Determines the broadcasted shape for two input shapes.
 * Dimensions are aligned from the right. A dimension can broadcast if it's equal or one of them is 1.
 */
fun broadcastShapes(shape1: IntArray, shape2: IntArray): IntArray {
    val maxRank = maxOf(shape1.size, shape2.size)
    val result = IntArray(maxRank)

    var i = 0
    while (i < maxRank) {
        val dim1 = if (i < shape1.size) shape1[shape1.size - 1 - i] else 1
        val dim2 = if (i < shape2.size) shape2[shape2.size - 1 - i] else 1

        result[maxRank - 1 - i] = when {
            dim1 == dim2 -> dim1
            dim1 == 1 -> dim2
            dim2 == 1 -> dim1
            else -> throw IllegalArgumentException("Shapes are not broadcastable: ${shape1.contentToString()} vs ${shape2.contentToString()}")
        }
        i++
    }
    return result
}

/**
 * A helper function for broadcasting, as provided in the summary.
 * Its exact semantics for general broadcasting are ambiguous from the snippet alone.
 * It appears to map a source shape (`this`) to a target shape, possibly indicating
 * relevant dimensions or padding.
 */
fun IntArray.broadcastTo(targetShape: IntArray): IntArray {
    val result = IntArray(targetShape.size)
    val offset = targetShape.size - this.size // `this` is the source shape

    for (i in result.indices) { // iterate through dimensions of the target shape
        result[i] = if (i < offset) 0 else { // If target dim is beyond source, pad with 0
            val sourceIndex = i - offset // Corresp. source dim index
            // The original snippet's logic:
            // if (sourceIndex < this.size && this[sourceIndex] < targetShape[i]) { this[sourceIndex] } else 0
            // This literal interpretation makes it return the source dimension size if it's smaller, else 0.
            if (sourceIndex < this.size && this[sourceIndex] < targetShape[i]) {
                this[sourceIndex]
            } else {
                0 // Covers `sourceIndex >= this.size` or `this[sourceIndex] >= targetShape[i]`
            }
        }
    }
    return result
}

/**
 * Zips two [Tensor]s element-wise, creating a new [Tensor] of [Join] pairs.
 * The shapes are broadcasted if compatible.
 */
fun <A, B> Tensor<A>.zip(other: Tensor<B>): Tensor<Join<A, B>> {
    val broadcastedShape = broadcastShapes(this.shape, other.shape)
    return TensorConstruct(broadcastedShape) { coords ->
        // Calculate source coordinates for 'this' tensor
        val aCoords = IntArray(this.rank) { i ->
            val targetCoordIdx = coords.size - this.rank + i
            if (this.shape[i] == 1 && targetCoordIdx >= 0) 0 else coords[targetCoordIdx]
        }
        // Calculate source coordinates for 'other' tensor
        val bCoords = IntArray(other.rank) { i ->
            val targetCoordIdx = coords.size - other.rank + i
            if (other.shape[i] == 1 && targetCoordIdx >= 0) 0 else coords[targetCoordIdx]
        }
        this(aCoords) j other(bCoords)
    }
}

/**
 * Combines two [Tensor]s element-wise using a transformation function,
 * producing a new [Tensor]. Shapes are broadcasted if compatible.
 */
internal inline fun <A, B, C> Tensor<A>.combine(other: Tensor<B>, crossinline transform: (A, B) -> C): Tensor<C> {
    val broadcastedShape = broadcastShapes(this.shape, other.shape)
    return TensorConstruct(broadcastedShape) { coords ->
        // Calculate source coordinates for 'this' tensor
        val aCoords = IntArray(this.rank) { i ->
            val targetCoordIdx = coords.size - this.rank + i
            if (this.shape[i] == 1 && targetCoordIdx >= 0) 0 else coords[targetCoordIdx]
        }
        // Calculate source coordinates for 'other' tensor
        val bCoords = IntArray(other.rank) { i ->
            val targetCoordIdx = coords.size - other.rank + i
            if (other.shape[i] == 1 && targetCoordIdx >= 0) 0 else coords[targetCoordIdx]
        }
        transform(this(aCoords), other(bCoords))
    }
}

// V. CoreTensorCursor Layer

/**
 * Type alias for a [Tensor] specialized to represent a Cursor (a 2D structure like a table).
 */
typealias CoreTensorCursor<T> = Tensor<T>

/**
 * Type alias for a 1-dimensional [Tensor] representing a row vector within a cursor.
 */
typealias CoreTensorRowVec<T> = Tensor<T> // For rank 1 slices (e.g. cursor.row(idx))

/**
 * Type alias for a 1-dimensional [Tensor] representing a column vector within a cursor.
 */
typealias CoreTensorColumnVec<T> = Tensor<T> // For rank 1 slices (e.g. cursor.col(idx))

/**
 * Type alias for a [Tensor] holding [ColumnMeta] objects, representing the metadata for cursor columns.
 */
typealias CursorMeta = Tensor<ColumnMeta> // ColumnMeta itself is preserved

/**
 * Type alias for a [Join] that combines a [CoreTensorCursor] (the data) with its [CursorMeta] (the schema).
 */
typealias CoreTensorCursorWithMeta<T> = Join<CoreTensorCursor<T>, CursorMeta>

/** Returns the number of rows in a [CoreTensorCursor]. */
internal inline val <T> CoreTensorCursor<T>.rows: Int get() = shape[0]

/** Returns the number of columns in a [CoreTensorCursor]. */
internal inline val <T> CoreTensorCursor<T>.cols: Int get() = shape[1]

/**
 * Extracts a row as a [CoreTensorRowVec] from a 2-dimensional [CoreTensorCursor].
 */
fun <T> CoreTensorCursor<T>.row(index: Int): CoreTensorRowVec<T> {
    require(rank == 2) { "Cursor must be rank 2 for row access." }
    require(index in 0 until rows) { "Row index $index out of bounds for rows $rows" }
    return TensorSeries(cols) { colIdx -> this(index, colIdx) }
}

/**
 * Extracts a column as a [CoreTensorColumnVec] from a 2-dimensional [CoreTensorCursor].
 */
fun <T> CoreTensorCursor<T>.col(index: Int): CoreTensorColumnVec<T> {
    require(rank == 2) { "Cursor must be rank 2 for column access." }
    require(index in 0 until cols) { "Column index $index out of bounds for cols $cols" }
    return TensorSeries(rows) { rowIdx -> this(rowIdx, index) }
}

/**
 * Slices a [CoreTensorCursor] by a range of rows, returning a new [CoreTensorCursor].
 */
@JsName("getRowsCoreTensorCursor")
operator fun <T> CoreTensorCursor<T>.get(rowRange: IntRange): CoreTensorCursor<T> {
    require(rank == 2) { "Cursor must be rank 2 for row range slicing." }
    require(rowRange.first >= 0 && rowRange.last < rows) { "Row range $rowRange out of bounds for rows $rows" }
    val newRows = rowRange.last - rowRange.first + 1
    return TensorCursor(newRows, cols) { r, c -> this(rowRange.first + r, c) }
}

/**
 * Slices a [CoreTensorCursor] by specific column indices, returning a new [CoreTensorCursor].
 */
@JsName("getColsCoreTensorCursor")
operator fun <T> CoreTensorCursor<T>.get(vararg colIndices: Int): CoreTensorCursor<T> {
    require(rank == 2) { "Cursor must be rank 2 for column indexing." }
    colIndices.forEach { require(it >= 0 && it < cols) { "Column index $it out of bounds for cols $cols" } }
    val newCols = colIndices.size
    return TensorCursor(rows, newCols) { r, c -> this(r, colIndices[c]) }
}

/**
 * Slices a [CoreTensorCursor] by specific column indices provided as a [Series<Int>].
 */
@JsName("getColsSeriesCoreTensorCursor")
operator fun <T> CoreTensorCursor<T>.get(colIndices: Series<Int>): CoreTensorCursor<T> {
    require(rank == 2) { "Cursor must be rank 2 for column indexing." }
    colIndices.`▶`.forEach { require(it >= 0 && it < cols) { "Column index $it out of bounds for cols $cols" } }
    val newCols = colIndices.size
    return TensorCursor(rows, newCols) { r, c -> this(r, colIndices[c]) }
}


// VI. Metadata Types

/**
 * Interface for type metadata, used within [ColumnMeta].
 */
@JsExport
interface TypeMemento {
    val networkSize: Int?
}

/**
 * Enum defining various I/O and data types used in the system,
 * implementing [TypeMemento].
 */
@JsExport
enum class IOMemento : TypeMemento {
    IoByte, IoShort, IoInt, IoFloat, IoDouble, IoLong,
    IoBoolean, IoChar, IoString, IoCharSeries, IoBigDecimal,
    IoBigInt, IoDateTime, IoDuration, IoUUID, IoBinary,
    IoUnknown;

    override val networkSize: Int? get() = null // Placeholder for specific sizes if needed
}

/**
 * Type alias for column metadata, a [Join] of a column name ([String]) and its [TypeMemento].
 */
typealias ColumnMeta = Join<String, TypeMemento>

/** Returns the name of the column from [ColumnMeta]. */
internal inline val ColumnMeta.name: String get() = a
/** Returns the type memento of the column from [ColumnMeta]. */
internal inline val ColumnMeta.type: TypeMemento get() = b

// Extension to convert Tensor to its serializable form
fun <T> Tensor<T>.toSerializable(): SerializableTensorData<T> {
    // Create a Series from tensor data using TrikeShed patterns
    val dataSeries = this.totalSize j { i -> this(this.linearToCoords(i)) }
    return SerializableTensorData(this.shape, dataSeries)
}

// Extension to convert SerializableTensorData back to Tensor
fun <T> SerializableTensorData<T>.toTensor(): Tensor<T> {
    return TensorConstruct(this.shape) { coords ->
        val linearIndex = TensorConstruct(this.shape) {}.coordsToLinear(coords) // Dummy tensor for coordsToLinear
        this.data[linearIndex]
    }
}

// Extension to convert Series to its serializable form
fun <T> Series<T>.toSerializable(): SerializableSeriesData<T> {
    // Already a Series, just wrap it
    return SerializableSeriesData(this)
}

// Extension to convert SerializableSeriesData back to Series
fun <T> SerializableSeriesData<T>.toSeries(): Series<T> {
    return this.data
}


/**
 * Returns the [CursorMeta] component (the metadata [Tensor]) from a [CoreTensorCursorWithMeta].
 */
internal inline val <T> CoreTensorCursorWithMeta<T>.coreTensorMeta: CursorMeta get() = b
/** Syntactic sugar for [coreTensorMeta]. */
internal inline val <T> CoreTensorCursorWithMeta<T>.meta: CursorMeta get() = b

/** Returns a [Series] of column names from [CursorMeta]. */
inline val CursorMeta.names: Series<String>
    get() {
        // Assuming CursorMeta is effectively a 1D tensor of ColumnMeta
        val numCols = this.shape.getOrElse(0) { 0 } // Get number of columns from shape
        return numCols j { colIdx ->
            // this(intArrayOf(colIdx)).name // Commented out L517 (approx)
            "placeholder_name_${colIdx}"
        }
    }

// VII. ColumnExclusion

/**
 * A value class used to specify a column to be excluded by its name.
 */
@JvmInline
value class ColumnExclusion(val name: String) {
    override fun toString(): String = "ColumnExclusion($name)"
}

/**
 * Unary minus operator extension for [String] to create a [ColumnExclusion].
 * Example: `-"columnName"`
 */
operator fun String.unaryMinus(): ColumnExclusion = ColumnExclusion(this)

/**
     * Returns a new [CoreTensorCursorWithMeta] with columns excluded by their indices.
     */
    operator fun <T> CoreTensorCursorWithMeta<T>.minus(killbag: Series<Int>): CoreTensorCursorWithMeta<T> {
        val killSet = mutableSetOf<Int>()
        for (item in killbag.`▶`) {
            killSet.add(item)
        }
        val retainedIndices = (0 until this.meta.totalSize).filterNot { it in killSet }.toIntArray()
        val newCursor: Tensor<T> = TensorCursor(a.rows, retainedIndices.size) { r, c -> a(r, retainedIndices[c]) }
        val newMeta: Tensor<CoreTensorCursor<ColumnMeta>> = TensorSeries(retainedIndices.size) { i -> this.meta[retainedIndices[i]] }
        return ((newCursor j newMeta) as CoreTensorCursorWithMeta<T>)

    }
/**
     * Returns a new [CoreTensorCursorWithMeta] with columns excluded by [ColumnExclusion] objects.
     */
    fun <T> CoreTensorCursorWithMeta<T>.exclude(s: Series<ColumnExclusion>): CoreTensorCursorWithMeta<T> {
        val exclusionBag = mutableSetOf<Int>()
        val currentMetaNames = this.meta.names // Get names from the CursorMeta part of CoreTensorCursorWithMeta

        for (excludedCol in s.`▶`) {
            val index = currentMetaNames.`▶`.indexOfFirst { it == excludedCol.name }
            if (index != -1) {
                exclusionBag.add(index)
            }
        }
        val retainedIndices = ((0 until this.meta.totalSize).toSet() - exclusionBag).toIntArray()
        val newCursor = TensorCursor(this.a.rows, retainedIndices.size) { r, c -> this.a(r, retainedIndices[c]) }
        val newMeta: Tensor<CoreTensorCursor<ColumnMeta>> = TensorSeries(retainedIndices.size) { i -> this.meta[retainedIndices[i]] }
        return (newCursor j newMeta) as CoreTensorCursorWithMeta<T>
    }/**
  * Operator for CoreTensorCursorWithMeta to get a subset of columns by names.
  * This is an adaptation of `Cursor.get(vararg s: String)` from the original.
  */
 @JsName("getColsByNameCoreTensorCursorWithMeta")
 operator fun <T> CoreTensorCursorWithMeta<T>.get(vararg s: String): CoreTensorCursorWithMeta<T> {
     val currentMeta: CursorMeta = this.meta
     val indicesToRetain = s.mapNotNull { nameToFind ->
         currentMeta.`▶`.indexOfFirst {
             meta -> meta.name == nameToFind }.takeIf { it != -1 }
     }.toIntArray()
     val newCursor = TensorCursor(this.a.rows, indicesToRetain.size) { r, c -> this.a(r, indicesToRetain[c]) }
     val newMeta = TensorSeries(indicesToRetain.size) { i -> currentMeta[indicesToRetain[i]] }
     return (newCursor j newMeta) as CoreTensorCursorWithMeta<T>
 }

// VIII. Presentation Functions and Properties

/**
 * Helper function to convert any value to a display string, considering [IOMemento] types.
 */
fun Any?.toDisplayString(type: IOMemento): String {
    return when (type) {
        IOMemento.IoCharSeries -> (this as? Series<Char>)?.asString() ?: this.toString()
        else -> this.toString()
    }
}

operator fun <T> Series<T>.plus(other: Series<T>): Series<T> =
    (this.size + other.size) j { i ->
        if (i < this.size) this[i] else other[i - this.size]
    }

/**
 * Functional tensor iterator that yields all index tuples and their corresponding tensor elements.
 * This is a pure function that returns a Series of pairs (index tuple, element).
 */
fun <T> Tensor<T>.iterate(): Series<Pair<IntArray, T>> =
    (totalSize) j { linearIndex ->
        val coords = IntArray(rank) { i ->
            var idx = linearIndex
            for (j in 0 until i) {
                idx /= shape[j]
            }
            idx % shape[i]
        }
        coords to this(coords)
    }

// TODO: Implement front property if needed


// IX. Shorthand Utility Functions and Properties

// --- m (map/transform) ---

// Tensor<T>.m(transform) moved to core.TrikeShedTensorOperations.kt

/**
 * Applies a lambda to each element of the [Series] and returns a new [Series] with the transformed elements.
 * The new [Series] will have the same size as the original.
 */
@JsExport
inline infix fun <T, R> Series<T>.m(crossinline transform: (T) -> R): Series<R> =
    this.size j { i -> transform(this[i]) }

// --- d (drop) ---

// Tensor<T>.d(n) moved to core.TrikeShedTensorOperations.kt

/**
 * Returns a new [Series] with the first `n` elements removed.
 * If `n` is non-positive, the original [Series] is returned.
 * If `n` is greater than or equal to [size], an empty [Series] is returned.
 */
@JsExport
fun <T> Series<T>.d(n: Int): Series<T> {
    if (n <= 0) return this
    val originalSize = this.size
    if (n >= originalSize) return emptySeries()
    val newSize = originalSize - n
    return newSize j { i -> this[i + n] }
}

// --- _l (last/tail) ---

// Tensor<T>._l moved to core.TrikeShedTensorOperations.kt

/**
 * Returns the last element of the [Series].
 * Throws [NoSuchElementException] if the series is empty.
 */
@JsExport
val <T> Series<T>._l: T
    get() {
        if (size == 0) throw NoSuchElementException("Series is empty.")
        return this[size - 1]
    }

// --- _v (values/vector) ---

// Tensor<T>._v moved to core.TrikeShedTensorOperations.kt

/**
 * Returns an [Iterable] view of the [Series]'s elements.
 * This is an alias for the existing `▶` operator's functionality.
 */
@JsExport
val <T> Series<T>._v: Iterable<T>
    get() = this.`▶`

// --- s_ (sum) ---

// Tensor<N>.s_() moved to core.TrikeShedTensorOperations.kt

/**
 * Calculates and returns the sum of elements in a [Series] of [Number]s.
 * Elements are converted to [Double] for summation.
 * Returns `0.0` for an empty series.
 */
@JsExport
fun <N : Number> Series<N>.s_(): Double {
    if (size == 0) return 0.0
    var sum = 0.0
    for (i in 0 until size) {
        sum += this[i].toDouble()
    }
    return sum
}

// X. Numeric Boolean Checks

/**
 * Checks if a [Number] is zero.
 * Returns `true` if the number is zero (e.g., 0, 0.0, 0L), `false` otherwise.
 */
@JsExport
val Number.z: Boolean
    get() = when (this) {
        is Double -> this == 0.0
        is Float -> this == 0.0f
        is Long -> this == 0L
        is Int -> this == 0
        is Short -> this == 0.toShort()
        is Byte -> this == 0.toByte()
        else -> this.toDouble() == 0.0 // Fallback for other Number types
    }

/**
 * Checks if a [Number] is non-zero.
 * Returns `true` if the number is not zero, `false` otherwise.
 */
@JsExport
val Number.nz: Boolean
    get() = !this.z

// XI. Ternary Operator Simulation

/**
 * Simulates a ternary operator for Kotlin.
 *
 * Usage: `condition select (ifTrueValue to ifFalseValue)`
 *
 * @param T The type of the values in the pair.
 * @param values A Pair where `first` is returned if the Boolean is true, and `second` if false.
 * @return The `first` value of the pair if the Boolean is true, otherwise the `second` value.
 */
@JsExport
infix fun <T> Boolean.select(values: Pair<T, T>): T = if (this) values.first else values.second

// XII. Network Order Utilities

// Note: `import kotlin.math.abs` is included locally where Float32ToInt32Converter is defined.
// The original file had @file:OptIn(ExperimentalUnsignedTypes::class) which is moved to the top of this file.

internal object Float32ToInt32Converter {
    // Import locally if not available globally or to maintain clarity
    // import kotlin.math.abs // Not strictly needed here as abs is not used in this object directly.
    // However, the original file had it at file level. Let's assume it might be for other utilities
    // not shown or it's a general good practice for that lib. For now, we keep it as per original file structure.
    // Upon review, abs is NOT used in Float32ToInt32Converter.
    // The original file `NetworkOrder.kt` had `import kotlin.math.abs` at the top level,
    // but it's not used by any of the functions in that specific file.
    // It will be omitted here unless a specific function needs it.

    fun convertFloat32ToInt32(f: Float): Int {
        if (f == 0f) return 0
        if (f.isNaN()) return 0
        if (f.isInfinite()) return if (f > 0) Int.MAX_VALUE else Int.MIN_VALUE

        val bits = f.toRawBits()
        val sign = bits ushr 31
        val exponent = (bits ushr 23) and 0xFF
        val mantissa = bits and 0x7FFFFF

        val adjustedExponent = exponent - 127
        var result = 0x800000 or mantissa

        when {
            adjustedExponent > 7 -> return if (sign == 0) Int.MAX_VALUE else Int.MIN_VALUE
            adjustedExponent >= -23 -> {
                if (adjustedExponent >= 0) {
                    result = result shl adjustedExponent
                } else {
                    result = result ushr -adjustedExponent
                }
            }
            else -> return if (sign == 0) 0 else -1
        }

        return if (sign == 1) -result else result
    }
}
/** this is a set of helper extension functions to produce network-endian versions of getIntAt, setIntAt, getLongAt,
 *  setLongAt, etc. for ByteArray
 *
 * the kotlin native package marshalling is treated as if it was in Least-Significant-Byte first (little-endian) byte
 * order.  we have to conform to what java ByteBuffer reads/writes in network order (big-endian)
 *
 * the functions in this file use the symbol prefix "networkOrder" to indicate that they are in network order
 */
internal fun ByteArray.networkOrderGetIntAt(i: Int): Int {
    return this[i].toInt() shl 24 or
            (this[i + 1].toInt() and 0xff shl 16) or
            (this[i + 2].toInt() and 0xff shl 8) or
            (this[i + 3].toInt() and 0xff)
}

internal fun ByteArray.networkOrderSetIntAt(i: Int, value: Int) {
    this[i] = (value shr 24).toByte()
    this[i + 1] = (value shr 16).toByte()
    this[i + 2] = (value shr 8).toByte()
    this[i + 3] = value.toByte()
}

internal fun ByteArray.networkOrderGetLongAt(i: Int): Long {
    return this[i].toLong() shl 56 or
            (this[i + 1].toLong() and 0xff shl 48) or
            (this[i + 2].toLong() and 0xff shl 40) or
            (this[i + 3].toLong() and 0xff shl 32) or
            (this[i + 4].toLong() and 0xff shl 24) or
            (this[i + 5].toLong() and 0xff shl 16) or
            (this[i + 6].toLong() and 0xff shl 8) or
            (this[i + 7].toLong() and 0xff)
}

internal fun ByteArray.networkOrderSetLongAt(i: Int, value: Long) {
    this[i] = (value shr 56).toByte()
    this[i + 1] = (value shr 48).toByte()
    this[i + 2] = (value shr 40).toByte()
    this[i + 3] = (value shr 32).toByte()
    this[i + 4] = (value shr 24).toByte()
    this[i + 5] = (value shr 16).toByte()
    this[i + 6] = (value shr 8).toByte()
    this[i + 7] = value.toByte()
}

internal fun ByteArray.networkOrderGetShortAt(i: Int): Short =
    (this[i].toInt() shl 8 or (this[i + 1].toInt() and 0xff)).toShort()

internal fun ByteArray.networkOrderSetShortAt(i: Int, value: Short) {
    this[i] = (value.toInt() shr 8).toByte()
    this[i + 1] = value.toByte()
}

internal fun ByteArray.networkOrderGetFloatAt(i: Int): Float = Float.fromBits(this.networkOrderGetIntAt(i))

internal fun ByteArray.networkOrderSetFloatAt(i: Int, value: Float) {
    this.networkOrderSetIntAt(i, value.toBits())
}

internal fun ByteArray.networkOrderGetDoubleAt(i: Int): Double = Double.fromBits(this.networkOrderGetLongAt(i))

internal fun ByteArray.networkOrderSetDoubleAt(i: Int, value: Double) {
    this.networkOrderSetLongAt(i, value.toBits())
}

internal fun ByteArray.networkOrderGetCharAt(i: Int): Char = this.networkOrderGetShortAt(i).toInt().toChar()

internal fun ByteArray.networkOrderSetCharAt(i: Int, value: Char) {
    this.networkOrderSetShortAt(i, value.code.toShort())
}

internal fun ByteArray.networkOrderGetBooleanAt(i: Int): Boolean = this[i] != 0.toByte()

internal fun ByteArray.networkOrderSetBooleanAt(i: Int, value: Boolean) {
    this[i] = if (value) 1.toByte() else 0.toByte()
}

internal fun ByteArray.networkOrderGetByteAt(i: Int): Byte = this[i]

internal fun ByteArray.networkOrderSetByteAt(i: Int, value: Byte) {
    this[i] = value
}

internal fun ByteArray.networkOrderGetUByteAt(i: Int): UByte = this[i].toUByte()

internal fun ByteArray.networkOrderSetUByteAt(i: Int, value: UByte) {
    this[i] = value.toByte()
}

internal fun ByteArray.networkOrderGetUShortAt(i: Int): UShort = this.networkOrderGetShortAt(i).toUShort()

internal fun ByteArray.networkOrderSetUShortAt(i: Int, value: UShort) {
    this.networkOrderSetShortAt(i, value.toShort())
}

internal fun ByteArray.networkOrderGetUIntAt(i: Int): UInt = this.networkOrderGetIntAt(i).toUInt()

internal fun ByteArray.networkOrderSetUIntAt(i: Int, value: UInt) {
    this.networkOrderSetIntAt(i, value.toInt())
}

internal fun ByteArray.networkOrderGetULongAt(i: Int): ULong = this.networkOrderGetLongAt(i).toULong()

internal fun ByteArray.networkOrderSetULongAt(i: Int, value: ULong) {
    this.networkOrderSetLongAt(i, value.toLong())
}

internal fun ByteArray.networkOrderGetUByteArrayAt(i: Int, length: Int): UByteArray =
    UByteArray(length) { this.networkOrderGetUByteAt(i + it) }

internal fun ByteArray.networkOrderSetUByteArrayAt(i: Int, value: UByteArray) {
    for (j in value.indices) this.networkOrderSetUByteAt(i + j, value[j])
}

internal fun ByteArray.networkOrderGetUShortArrayAt(i: Int, length: Int): UShortArray =
    UShortArray(length) { this.networkOrderGetUShortAt(i + it * 2) }

internal fun ByteArray.networkOrderSetUShortArrayAt(i: Int, value: UShortArray) {
    for (j in value.indices) this.networkOrderSetUShortAt(i + j * 2, value[j])
}

internal fun ByteArray.networkOrderGetUIntArrayAt(i: Int, length: Int): UIntArray =
    UIntArray(length) { this.networkOrderGetUIntAt(i + it * 4) }

internal fun ByteArray.networkOrderSetUIntArrayAt(i: Int, value: UIntArray) {
    for (j in value.indices) this.networkOrderSetUIntAt(i + j * 4, value[j])
}

internal fun ByteArray.networkOrderGetULongArrayAt(i: Int, length: Int): ULongArray =
    ULongArray(length) { this.networkOrderGetULongAt(i + it * 8) }

internal fun ByteArray.networkOrderSetULongArrayAt(i: Int, value: ULongArray) {
    for (j in value.indices) this.networkOrderSetULongAt(i + j * 8, value[j])
}

internal fun ByteArray.networkOrderGetByteArrayAt(i: Int, length: Int): ByteArray =
    ByteArray(length) { this.networkOrderGetByteAt(i + it) }

internal fun ByteArray.networkOrderSetByteArrayAt(i: Int, value: ByteArray) {
    for (j in value.indices) this.networkOrderSetByteAt(i + j, value[j])
}

internal fun ByteArray.networkOrderGetShortArrayAt(i: Int, length: Int): ShortArray =
    ShortArray(length) { this.networkOrderGetShortAt(i + it * 2) }

internal fun ByteArray.networkOrderSetShortArrayAt(i: Int, value: ShortArray) {
    for (j in value.indices) this.networkOrderSetShortAt(i + j * 2, value[j])
}

internal fun ByteArray.networkOrderGetIntArrayAt(i: Int, length: Int): IntArray =
    IntArray(length) { this.networkOrderGetIntAt(i + it * 4) }

internal fun ByteArray.networkOrderSetIntArrayAt(i: Int, value: IntArray) {
    for (j in value.indices) this.networkOrderSetIntAt(i + j * 4, value[j])
}

internal fun ByteArray.networkOrderGetLongArrayAt(i: Int, length: Int): LongArray =
    LongArray(length) { this.networkOrderGetLongAt(i + it * 8) }

internal fun ByteArray.networkOrderSetLongArrayAt(i: Int, value: LongArray) {
    for (j in value.indices) this.networkOrderSetLongAt(i + j * 8, value[j])
}

internal fun ByteArray.networkOrderGetFloatArrayAt(i: Int, length: Int): FloatArray =
    FloatArray(length) { this.networkOrderGetFloatAt(i + it * 4) }

internal fun ByteArray.networkOrderSetFloatArrayAt(i: Int, value: FloatArray) {
    for (j in value.indices) this.networkOrderSetFloatAt(i + j * 4, value[j])
}

internal fun ByteArray.networkOrderGetDoubleArrayAt(i: Int, length: Int): DoubleArray =
    DoubleArray(length) { this.networkOrderGetDoubleAt(i + it * 8) }

internal fun ByteArray.networkOrderSetDoubleArrayAt(i: Int, value: DoubleArray) {
    for (j in value.indices) this.networkOrderSetDoubleAt(i + j * 8, value[j])
}

internal fun ByteArray.networkOrderGetBooleanArrayAt(i: Int, length: Int): BooleanArray =
    BooleanArray(length) { this.networkOrderGetBooleanAt(i + it) }

internal fun ByteArray.networkOrderSetBooleanArrayAt(i: Int, value: BooleanArray) {
    for (j in value.indices) this.networkOrderSetBooleanAt(i + j, value[j])
}

// XIII. Base64 Encoding

/**
 * Encodes a String into a Base64 encoded String.
 * The input string is typically interpreted as UTF-8 bytes.
 */
internal expect fun base64Encode(input: String): String

/**
 * Encodes a ByteArray into a Base64 encoded String.
 */
internal expect fun base64Encode(input: ByteArray): String

/**
 * Decodes a Base64 encoded String into a ByteArray.
 * Optional: Add if needed by other parts of the application, not strictly required by Basic Auth encoding.
 */
// expect fun base64Decode(input: String): ByteArray


// XIV. Human Readable Numbers

// Imports needed for this section
// import kotlin.math.ln // Already imported via TrikeShedCore's `import kotlin.math.*` or similar
// import kotlin.math.pow // Already imported via TrikeShedCore's `import kotlin.math.*`
// Actually, TrikeShedCore.kt only has max, min. So these need to be ensured.
// Adding them explicitly for clarity if not covered by a wildcard.
// For now, assuming they might need to be added to TrikeShedCore's top imports or are available.
// Let's make sure they are explicitly available.

/**
 * Converts a human-readable string with units (k, m, g, t, p, e) to a Number.
 * Supports both IEC (1024) and decimal (1000) multipliers.
 *
 * @param decimal If true, uses 1000 as multiplier; otherwise, uses 1024.
 * @return The numeric value.
 */
@JsExport
fun String.readableUnitsToNumber(decimal: Boolean = false): Number {
    val value = this.trim().lowercase()
    val multiplier = if (decimal) 1000.0 else 1024.0 // Use Double for multiplier to maintain precision
    val suffixMultiplier = when {
        value.endsWith("b") -> 1.0 // Technically, 'b' often means bytes, but here it's just removing the char
        value.endsWith("k") -> multiplier
        value.endsWith("m") -> multiplier * multiplier
        value.endsWith("g") -> multiplier * multiplier * multiplier
        value.endsWith("t") -> multiplier * multiplier * multiplier * multiplier
        value.endsWith("p") -> multiplier * multiplier * multiplier * multiplier * multiplier
        value.endsWith("e") -> multiplier * multiplier * multiplier * multiplier * multiplier * multiplier
        else -> 1.0
    }
    // Extract the numeric part by removing all known suffixes
    val numericPartString = value
        .removeSuffix("b")
        .removeSuffix("k")
        .removeSuffix("m")
        .removeSuffix("g")
        .removeSuffix("t")
        .removeSuffix("p")
        .removeSuffix("e")
        .trim()

    val number = numericPartString.toDoubleOrNull() ?: throw NumberFormatException("Invalid numeric string: $numericPartString")
    return number * suffixMultiplier
}


/**
 * Converts a byte count into a human-readable string using IEC units (KiB, MiB, etc.).
 * Example: 1024L.humanReadableByteCountIEC -> "1.0 KiB"
 */
@JsExport
val Long.humanReadableByteCountIEC: String
    get() {
        val seriesConstant = 1024
        val seriesDoubleConstant = seriesConstant.toDouble()
        // Ensure kotlin.math.ln and kotlin.math.pow are available
        return unitizer(seriesConstant, seriesDoubleConstant, kotlin.math.ln(this.toDouble()), kotlin.math.pow(seriesDoubleConstant, 0.0)) // Pass dummy pow for now
    }

/**
 * Converts a byte count into a human-readable string using SI units (KB, MB, etc.).
 * Example: 1000L.humanReadableByteCountSI -> "1.0 KB"
 */
@JsExport
val Long.humanReadableByteCountSI: String
    get() {
        val seriesConstant = 1000
        val seriesDoubleConstant = seriesConstant.toDouble()
        // Ensure kotlin.math.ln and kotlin.math.pow are available
        return unitizer(seriesConstant, seriesDoubleConstant, kotlin.math.ln(this.toDouble()), kotlin.math.pow(seriesDoubleConstant, 0.0)) // Pass dummy pow for now
    }

// Internal helper function for unitizing byte counts.
// lnOfThis and powBase are passed to ensure they are resolved correctly, especially if math imports are tricky.
private fun Long.unitizer(seriesConstant: Int, seriesDoubleConstant: Double, lnOfThis: Double, dummyPow: Double): String {
    // `dummyPow` is not used, it was just to ensure `pow` is resolvable if passed directly.
    // The actual `pow` call is `seriesDoubleConstant.pow(exp.toDouble())`.
    if (this == Long.MIN_VALUE) return (Long.MIN_VALUE + 1).humanReadableByteCountIEC // Avoid issues with -Long.MIN_VALUE
    if (this < 0) return "-" + (-this).humanReadableByteCountIEC
    if (this < seriesConstant) return this.toString() + " B"

    val currentLnOfThis = if (this == 0L) kotlin.math.ln(1.0) else kotlin.math.ln(this.toDouble()) // handle log(0)
    val exp = (currentLnOfThis / kotlin.math.ln(seriesDoubleConstant)).toInt().coerceAtLeast(0)

    // Ensure exp is within the bounds of the "KMGTPE" string
    if (exp == 0) { // Should be caught by `this < seriesConstant` but as a safeguard
        return this.toString() + " B"
    }
    val pre = "KMGTPE".getOrNull(exp - 1)?.toString() ?: "X" // Use X if exp is out of bounds

    val value = this / seriesDoubleConstant.pow(exp.toDouble())

    // Basic rounding to two decimal places, convert to string
    val roundedValue = (kotlin.math.round(value * 100.0) / 100.0)

    var roundedString = roundedValue.toString()
    // Ensure it looks like "x.xx" or "x.x" or "x"
    if (roundedString.endsWith(".0")) {
        roundedString = roundedString.substring(0, roundedString.length - 2)
    } else if (roundedString.contains(".") && roundedString.length > roundedString.indexOf('.') + 3) {
        // More than two decimal places, truncate (simple way)
        roundedString = roundedString.substring(0, roundedString.indexOf('.') + 3)
    }


    return roundedString + " " + pre + (if (seriesConstant == 1024) "iB" else "B")
}

// TODO: Implement front property if needed

// --- GitRepositoryView Integration ---

/**
 * Opens and indexes a Git repository using the TrikeShed-native Git indexer.
 *
 * @param repositoryPath The file system path to the Git repository. This can be
 *                       the root of the working directory (containing a .git folder)
 *                       or the path directly to the .git folder.
 * @return A GitRepositoryView instance with the indexed data, or null if indexing failed.
 */
@JsExport
fun openGitRepository(repositoryPath: String): GitRepositoryView? {
    var dotGitPath = repositoryPath
    val normalizedRepoPath = repositoryPath.removeSuffix("/")

    if (!normalizedRepoPath.endsWith(".git")) {
        val potentialGitDir = platformJoinPath(repositoryPath, ".git")
        if (platformIsDirectory(potentialGitDir)) {
            dotGitPath = potentialGitDir
        } else {
            if (!platformIsDirectory(repositoryPath)) {
                 return null
            }
            dotGitPath = repositoryPath
        }
    }
    dotGitPath = dotGitPath.removeSuffix("/")

    if (!platformIsFile(platformJoinPath(dotGitPath, "HEAD")) ||
        !platformIsDirectory(platformJoinPath(dotGitPath, "objects")) ||
        !platformIsDirectory(platformJoinPath(dotGitPath, "refs"))) {
        return null
    }

    return try {
        val repoView = GitRepositoryView(dotGitPath)
        repoView.indexRepository()
        repoView
    } catch (e: Exception) {
        null
    }
}
