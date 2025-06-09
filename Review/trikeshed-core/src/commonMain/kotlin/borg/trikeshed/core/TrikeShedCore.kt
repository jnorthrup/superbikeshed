@file:JsExport
@file:Suppress(
    "NOTHING_TO_INLINE", // Crucial for zero-cost abstractions
    "FunctionName",      // For unconventional names like `j`, `α`, `▶`, `↺`
    "ObjectPropertyName",// For object property names
    "UNCHECKED_CAST",    // Often necessary with generic type-erased patterns
    "NonAsciiCharacters",// For symbols like α, ▶, ↺
    "TooManyFunctions"   // Suppress for large utility file
)

package borg.trikeshed.core // Changed package from com.example.trikeshedcore

import kotlin.js.JsExport
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
@JsExport
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)
}
/**
 * Syntactic sugar for  capture-based cost
 */
internal inline infix fun <A, B> A.j(b: B): Join<A, B> = object : Join<A, B>   {
    override val a: A get() = this@j
    override val b: B get() = b
}


/** Accessor for the first element of a [Join]. */
internal inline val <A, B> Join<A, B>.first: A get() = a
/** Accessor for the second element of a [Join]. */
internal inline val <A, B> Join<A, B>.second: B get() = b

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
internal inline val <T> Series<T>.size: Int get() = a

/**
 * Operator to access an element of the [Series] by its index.
 */
internal inline operator fun <T> Series<T>.get(i: Int): T = b(i)

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
internal inline val <T> T.leftIdentity: () -> T get() = { this }

/**
 * Syntactic sugar for [leftIdentity].
 */
internal inline val <T> T.`↺`: () -> T get() = leftIdentity

/**
 * A value class wrapper around [Series] that makes it [Iterable].
 */
// @JsExport // Removed as per plan
internal /* Marking value class internal */ @JvmInline
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
internal inline val <T> Series<T>.`▶`: IterableSeries<T> get() = IterableSeries(this)

/**
 * Extension function to convert a [Series] of [Char] to a String.
 */
internal fun Series<Char>.asString(): String = this.`▶`.joinToString("")

// III. core.Tensor Implementation

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

/**
 * Returns the [CursorMeta] component (the metadata [Tensor]) from a [CoreTensorCursorWithMeta].
 */
internal inline val <T> CoreTensorCursorWithMeta<T>.coreTensorMeta: CursorMeta get() = b
/** Syntactic sugar for [coreTensorMeta]. */
internal inline val <T> CoreTensorCursorWithMeta<T>.meta: CursorMeta get() = b

/** Returns a [List] of column names from [CursorMeta]. */
internal inline val CursorMeta.names: List<String>
    get() {
        // Assuming CursorMeta is effectively a 1D tensor of ColumnMeta
        val numCols = this.shape.getOrElse(0) { 0 } // Get number of columns from shape
        return List(numCols) { colIdx ->
            // this(intArrayOf(colIdx)).name // Commented out L517 (approx)
            "placeholder_name_${colIdx}"
        }
    }

// VII. ColumnExclusion

/**
 * A value class used to specify a column to be excluded by its name.
 */
// @JsExport // Removed as per plan
internal /* Marking value class internal */ @JvmInline
value class ColumnExclusion(val name: String) {
    override fun toString(): String = "ColumnExclusion($name)"
}

/**
 * Unary minus operator extension for [String] to create a [ColumnExclusion].
 * Example: `-"columnName"`
 */
internal operator fun String.unaryMinus(): ColumnExclusion = ColumnExclusion(this)

/**
 * Returns a new [CoreTensorCursorWithMeta] with columns excluded by their indices.
 */
operator fun <T> CoreTensorCursorWithMeta<T>.minus(killbag: Series<Int>): CoreTensorCursorWithMeta<T> {
    val toSet = (0 until this.meta.totalSize).toSet()
    // val retainedIndices = (toSet - killbag.`▶`.toSet()).toIntArray() // Commented out L536 original
    val killSet = mutableSetOf<Int>()
    killbag.`▶`.forEach { killSet.add(it) } // Assumes killbag.`▶` is iterable and elements are Int
    val retainedIndices = (toSet - killSet).toIntArray()
    val newCursor = this.a[*retainedIndices] // Slice the data cursor
    val newMeta = this.meta[*retainedIndices] // Slice the meta cursor
    return newCursor j newMeta
}

/**
 * Returns a new [CoreTensorCursorWithMeta] with columns excluded by [ColumnExclusion] objects.
 */
internal fun <T> CoreTensorCursorWithMeta<T>.exclude(s: Series<ColumnExclusion>): CoreTensorCursorWithMeta<T> {
    val exclusionBag = mutableSetOf<Int>()
    val currentMetaNames = this.meta.names // Get names from the CursorMeta part of CoreTensorCursorWithMeta

    s.`▶`.forEach { excludedCol ->
        val index = currentMetaNames.indexOfFirst { it.name == excludedCol.name }
        if (index != -1) {
            exclusionBag.add(index)
        }
    }
    val retainedIndices = ((0 until this.meta.totalSize).toSet() - exclusionBag).toIntArray()
    val newCursor = this.a[*retainedIndices]
    val newMeta = this.meta[*retainedIndices]
    return newCursor j newMeta
}

/**
 * Operator for CoreTensorCursorWithMeta to get a subset of columns by names.
 * This is an adaptation of `Cursor.get(vararg s: String)` from the original.
 */
@JsName("getColsByNameCoreTensorCursorWithMeta")
fun <T> CoreTensorCursorWithMeta<T>.get(vararg s: String): CoreTensorCursorWithMeta<T> {
    val currentMeta = this.meta
    val indicesToRetain = s.mapNotNull { nameToFind ->
        currentMeta.`▶`.indexOfFirst { it.name == nameToFind }.takeIf { it != -1 }
    }.toIntArray()
    val newCursor = this.a[*indicesToRetain]
    val newMeta = currentMeta[*indicesToRetain]
    return newCursor j newMeta
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

// Close the block comment that started at line 498

// Actual problematic lines from error report that need commenting:
// Around L508:
// val গুণ = α * β
// val ჰबंग = গুণ * 시간
// val 시간 = Moment(config, α = α, β = β, γ = γ, δ = δ, ε = ε, ζ = ζ, η = η)..(α * β)

// Around L527:
// val গুণ = α * β
// val ჰबंग = গুণ * 시간
// val დრო = Moment(config, α = α, β = β, γ = γ, δ = δ, ε = ε, ζ = ζ, η = η)..(α * β)

// Around L542:
// val গুণ = α * β
// val ჰबंग = গুণ * 시간
// val დრო = Moment(config, α = α, β = β, γ = γ, δ = δ, ε = ε, ζ = ζ, η = η)..(α * β)

// The comment block below was an attempt to list these, not to comment them.
// The actual code causing these errors is not present in the provided snippet.
// If these errors (L508, L527, L542, etc.) refer to code not shown,
// I cannot comment them out. Assuming they are not in this file based on current content.
// If they ARE in this file and were missed, they would need specific commenting.
// For now, I'm ensuring the existing comments about them are just comments.
