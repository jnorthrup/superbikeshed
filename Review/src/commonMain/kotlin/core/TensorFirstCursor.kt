@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package core

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.name
import borg.trikeshed.cursor.type
import borg.trikeshed.lib.*
import borg.trikeshed.lib.j
import kotlin.jvm.*
import core.*
import borg.trikeshed.isam.meta.IOMemento

/**
 * TENSOR-FIRST CURSOR IMPLEMENTATION
 * 
 * Evolves TrikeShed's cursor abstraction to be tensor-first, eliminating the artificial
 * distinction between Series/Cursor and making everything a unified tensor operation.
 * 
 * Core Philosophy:
 * - CoreTensorCursor<T> = Tensor<T> where rank == 2 (2D tensor)
 * - No type escalation - cursors are just 2D tensor views
 * - All cursor operations preserved but operating on tensors
 * - Maintains TrikeShed's Join<Value, Meta> pattern for columnar data
 * - Enables 50k+ column scale through tensor optimization
 */

// ============================================================================
// TENSOR-FIRST CURSOR TYPEALIASES
// ============================================================================

/**
 * CoreTensorCursor - 2D tensor with columnar semantics (tensor-first)
 */
typealias CoreTensorCursor<T> = Tensor<T>

/**
 * CoreTensorRowVec - Single row as 1D tensor (tensor-first)
 */
typealias CoreTensorRowVec<T> = Tensor<T>

/**
 * CoreTensorColumnVec - Single column as 1D tensor 
 */
typealias CoreTensorColumnVec<T> = Tensor<T>

/**
 * CursorMeta - Metadata for cursor operations
 */
typealias CursorMeta = Tensor<ColumnMeta>

/**
 * CoreTensorCursorWithMeta - Cursor with attached metadata (Join pattern)
 */
typealias CoreTensorCursorWithMeta<T> = Join<CoreTensorCursor<T>, CursorMeta>

// ============================================================================
// CURSOR CONSTRUCTION (Tensor-First)
// ============================================================================

/**
 * Create CoreTensorCursor from 2D tensor
 */
inline fun <T> CoreTensorCursor(rows: Int, cols: Int, noinline accessor: (Int, Int) -> T): CoreTensorCursor<T> =
    TensorCursor(rows, cols, accessor)

/**
 * Create CoreTensorCursor from existing tensor (requires rank 2)
 */
inline fun <T> Tensor<T>.asCoreTensorCursor(): CoreTensorCursor<T> {
    require(rank == 2) { "Cursor requires 2D tensor, got rank $rank" }
    return this
}

/**
 * Create cursor with metadata
 */
inline fun <T> CoreTensorCursorWithMeta(data: CoreTensorCursor<T>, meta: CursorMeta): CoreTensorCursorWithMeta<T> = 
    data j meta

// ============================================================================
// CURSOR ACCESSORS (Tensor-Compatible)
// ============================================================================

// Cursor dimensions
inline val <T> CoreTensorCursor<T>.rows: Int get() = shape[0]
inline val <T> CoreTensorCursor<T>.cols: Int get() = shape[1]

// Row/column access
inline fun <T> CoreTensorCursor<T>.row(index: Int): CoreTensorRowVec<T> {
    require(index >= 0 && index < rows) { "Row index $index out of bounds [0, $rows)" }
    return TensorSeries(cols) { col -> this(index, col) }
}

inline fun <T> CoreTensorCursor<T>.col(index: Int): CoreTensorColumnVec<T> {
    require(index >= 0 && index < cols) { "Column index $index out of bounds [0, $cols)" }
    return TensorSeries(rows) { row -> this(row, index) }
}

// Element access (inherited from tensor)
inline operator fun <T> CoreTensorCursor<T>.get(row: Int, col: Int): T = this(row, col)

// ============================================================================
// TRIKESHED CURSOR OPERATORS (Tensor-Adapted)
// ============================================================================

/**
 * Cursor slicing by row range
 */
operator fun <T> CoreTensorCursor<T>.get(rowRange: IntRange): CoreTensorCursor<T> {
    require(rowRange.first >= 0) { "Row range start ${rowRange.first} out of bounds" }
    require(rowRange.last < rows) { "Row range end ${rowRange.last} out of bounds" }
    
    val newRows = rowRange.last - rowRange.first + 1
    return CoreTensorCursor(newRows, cols) { row, col -> 
        this(rowRange.first + row, col) 
    }
}

/**
 * Cursor slicing by column indices (preserves TrikeShed pattern)
 */
operator fun <T> CoreTensorCursor<T>.get(vararg colIndices: Int): CoreTensorCursor<T> {
    require(colIndices.all { it >= 0 && it < cols }) { "Column indices out of bounds" }
    
    return CoreTensorCursor(rows, colIndices.size) { row, col ->
        this(row, colIndices[col])
    }
}

/**
 * Cursor slicing by column names (requires metadata)
 */
fun <T> CoreTensorCursorWithMeta<T>.get(vararg columnNames: String): CoreTensorCursorWithMeta<T> {
    val cursor = a  // data
    val meta = b    // metadata
    
    val colIndices = mutableListOf<Int>()
    for (name in columnNames) {
        var index = -1
        var i = 0
        while (i < meta.totalSize) {
            if (meta(intArrayOf(i)).name == name) {
                index = i
                break
            }
            i++
        }
        require(index >= 0) { "Column '$name' not found" }
        colIndices.add(index)
    }
    
    val newCursor = CoreTensorCursor(cursor.rows, colIndices.size) { row, col ->
        cursor(row, colIndices[col])
    }
    val newMeta = TensorSeries(colIndices.size) { i -> meta(intArrayOf(colIndices[i])) }
    
    return newCursor j newMeta
}

// ============================================================================
// COLUMN EXCLUSION (TrikeShed Pattern)
// ============================================================================

/**
 * Column exclusion marker (preserves TrikeShed interface)
 */
@JvmInline
value class ColumnExclusion(val name: String) {
    override fun toString(): String = "ColumnExclusion($name)"
}

/**
 * Create column exclusion marker
 */
operator fun String.unaryMinus(): ColumnExclusion = ColumnExclusion(this)

/**
 * Exclude columns by names
 */
fun <T> CoreTensorCursorWithMeta<T>.exclude(exclusions: Array<ColumnExclusion>): CoreTensorCursorWithMeta<T> {
    val cursor = a
    val meta = b
    
    val excludeNames = mutableSetOf<String>()
    for (exclusion in exclusions) {
        excludeNames.add(exclusion.name)
    }
    val retainedIndices = mutableListOf<Int>()
    var i = 0
    while (i < meta.totalSize) {
        val columnMeta = meta(intArrayOf(i))
        if (columnMeta.name !in excludeNames) {
            retainedIndices.add(i)
        }
        i++
    }
    
    val newCursor = CoreTensorCursor(cursor.rows, retainedIndices.size) { row, col ->
        cursor(row, retainedIndices[col])
    }
    val newMeta = TensorSeries(retainedIndices.size) { i -> meta(intArrayOf(retainedIndices[i])) }
    
    return newCursor j newMeta
}

// ============================================================================
// CURSOR DISPLAY OPERATIONS (TrikeShed Heritage)
// ============================================================================

/**
 * Show cursor head (default 5 rows)
 */
fun <T> CoreTensorCursor<T>.head(count: Int = 5) {
    val showRows = minOf(count, rows)
    println("Cursor: $rows x $cols")
    
    var r = 0
    while (r < showRows) {
        val rowData = mutableListOf<T>()
        var c = 0
        while (c < cols) {
            rowData.add(this(r, c))
            c++
        }
        println("Row $r: $rowData")
        r++
    }
    
    if (showRows < rows) {
        println("... ${rows - showRows} more rows")
    }
}

/**
 * Show cursor with metadata
 */
fun <T> CoreTensorCursorWithMeta<T>.head(count: Int = 5) {
    val cursor = a
    val meta = b
    val showRows = minOf(count, cursor.rows)
    
    // Print column headers
    val headers = mutableListOf<String>()
    var col = 0
    while (col < cursor.cols) {
        val columnMeta = meta(intArrayOf(col))
        headers.add("${columnMeta.name}:${columnMeta.type}")
        col++
    }
    println("Columns: $headers")
    println("Data: ${cursor.rows} x ${cursor.cols}")
    
    // Print data rows
    var r = 0
    while (r < showRows) {
        val rowData = mutableListOf<T>()
        var c = 0
        while (c < cursor.cols) {
            rowData.add(cursor(r, c))
            c++
        }
        println("Row $r: $rowData")
        r++
    }
    
    if (showRows < cursor.rows) {
        println("... ${cursor.rows - showRows} more rows")
    }
}

/**
 * Show random rows (TrikeShed pattern)
 */
fun <T> CoreTensorCursor<T>.showRandom(count: Int = 5) {
    require(rows > 0) { "Cannot show random rows from empty cursor" }
    
    println("Random $count rows from cursor:")
    repeat(count) {
        val randomRow = kotlin.random.Random.nextInt(rows)
        val rowData = mutableListOf<T>()
        var c = 0
        while (c < cols) {
            rowData.add(this(randomRow, c))
            c++
        }
        println("Row $randomRow: $rowData")
    }
}

// ============================================================================
// CURSOR ANALYTICS (Tensor-Enabled)
// ============================================================================

/**
 * Check if cursor is numerical (all columns are numeric types)
 */
val <T> CoreTensorCursorWithMeta<T>.isNumerical: Boolean
    get() {
        val meta = b
        var i = 0
        while (i < meta.totalSize) {
            val typeMemento = meta(intArrayOf(i)).type
            val typeName = when (typeMemento) {
                is IOMemento -> typeMemento.name
                else -> typeMemento.toString()
            }
            when (typeName) {
                "IoByte", "IoShort", "IoInt", "IoFloat", "IoDouble", "IoLong" -> { /* continue */ }
                else -> return false
            }
            i++
        }
        return true
    }

/**
 * Check if cursor is homomorphic (all columns same type)
 */
val <T> CoreTensorCursorWithMeta<T>.isHomomorphic: Boolean
    get() {
        val meta = b
        if (meta.totalSize == 0) return true
        
        val firstType = meta(intArrayOf(0)).type
        var i = 1
        while (i < meta.totalSize) {
            if (meta(intArrayOf(i)).type != firstType) {
                return false
            }
            i++
        }
        return true
    }

// ============================================================================
// CURSOR TRANSFORMATIONS (Tensor-First)
// ============================================================================

/**
 * Transpose cursor (swap rows/columns)
 */
fun <T> CoreTensorCursor<T>.transposeMatrix(): CoreTensorCursor<T> = 
    CoreTensorCursor(cols, rows) { row, col -> this(col, row) }

/**
 * Map over all elements
 */
inline fun <T, R> CoreTensorCursor<T>.map(crossinline transform: (T) -> R): CoreTensorCursor<R> =
    CoreTensorCursor(rows, cols) { row, col -> transform(this(row, col)) }

/**
 * Map over rows
 */
inline fun <T, R> CoreTensorCursor<T>.mapRows(crossinline transform: (CoreTensorRowVec<T>) -> R): CoreTensorColumnVec<R> =
    TensorSeries(rows) { row -> transform(this.row(row)) }

/**
 * Map over columns  
 */
inline fun <T, R> CoreTensorCursor<T>.mapCols(crossinline transform: (CoreTensorColumnVec<T>) -> R): CoreTensorRowVec<R> =
    TensorSeries(cols) { col -> transform(this.col(col)) }

/**
 * Filter rows by predicate
 */
inline fun <T> CoreTensorCursor<T>.filterRows(crossinline predicate: (CoreTensorRowVec<T>) -> Boolean): CoreTensorCursor<T> {
    val filteredRowIndices = mutableListOf<Int>()
    var row = 0
    while (row < rows) {
        if (predicate(this.row(row))) {
            filteredRowIndices.add(row)
        }
        row++
    }
    
    return CoreTensorCursor(filteredRowIndices.size, cols) { row, col ->
        this(filteredRowIndices[row], col)
    }
}

/**
 * Filter columns by predicate
 */
inline fun <T> CoreTensorCursor<T>.filterCols(crossinline predicate: (CoreTensorColumnVec<T>) -> Boolean): CoreTensorCursor<T> {
    val filteredColIndices = mutableListOf<Int>()
    var col = 0
    while (col < cols) {
        if (predicate(this.col(col))) {
            filteredColIndices.add(col)
        }
        col++
    }
    
    return CoreTensorCursor(rows, filteredColIndices.size) { row, col ->
        this(row, filteredColIndices[col])
    }
}

// ============================================================================
// CURSOR AGGREGATIONS (Tensor-Based)
// ============================================================================

/**
 * Reduce rows to single row
 */
inline fun <T> CoreTensorCursor<T>.reduceRows(crossinline operation: (T, T) -> T): CoreTensorRowVec<T> {
    require(rows > 0) { "Cannot reduce empty cursor" }
    
    return TensorSeries(cols) { col ->
        var result = this(0, col)
        var row = 1
        while (row < rows) {
            result = operation(result, this(row, col))
            row++
        }
        result
    }
}

/**
 * Reduce columns to single column
 */
inline fun <T> CoreTensorCursor<T>.reduceCols(crossinline operation: (T, T) -> T): CoreTensorColumnVec<T> {
    require(cols > 0) { "Cannot reduce empty cursor" }
    
    return TensorSeries(rows) { row ->
        var result = this(row, 0)
        var col = 1
        while (col < cols) {
            result = operation(result, this(row, col))
            col++
        }
        result
    }
}

/**
 * Sum all elements (requires numeric type)
 */
inline fun CoreTensorCursor<Number>.sum(): Number {
    var total = 0.0
    var row = 0
    while (row < rows) {
        var col = 0
        while (col < cols) {
            total += this(row, col).toDouble()
            col++
        }
        row++
    }
    return total
}

// ============================================================================
// CURSOR JOINS (Tensor-Based Relational Operations)
// ============================================================================

/**
 * Horizontal concatenation (join columns)
 */
fun <T> CoreTensorCursor<T>.concatCols(other: CoreTensorCursor<T>): CoreTensorCursor<T> {
    require(rows == other.rows) { "Row counts must match for column concatenation" }
    
    return CoreTensorCursor(rows, cols + other.cols) { row, col ->
        if (col < cols) this(row, col)
        else other(row, col - cols)
    }
}

/**
 * Vertical concatenation (join rows)
 */
fun <T> CoreTensorCursor<T>.concatRows(other: CoreTensorCursor<T>): CoreTensorCursor<T> {
    require(cols == other.cols) { "Column counts must match for row concatenation" }
    
    return CoreTensorCursor(rows + other.rows, cols) { row, col ->
        if (row < rows) this(row, col)
        else other(row - rows, col)
    }
}

// ============================================================================
// CONVERSION UTILITIES
// ============================================================================

/**
 * Convert cursor to list of lists (materialization)
 */
fun <T> CoreTensorCursor<T>.toListOfLists(): List<List<T>> {
    val result = mutableListOf<List<T>>()
    var row = 0
    while (row < rows) {
        val rowList = mutableListOf<T>()
        var col = 0
        while (col < cols) {
            rowList.add(this(row, col))
            col++
        }
        result.add(rowList)
        row++
    }
    return result
}

/**
 * Convert cursor to list of rows
 */
fun <T> CoreTensorCursor<T>.toRowList(): List<CoreTensorRowVec<T>> {
    val result = mutableListOf<CoreTensorRowVec<T>>()
    var row = 0
    while (row < rows) {
        result.add(this.row(row))
        row++
    }
    return result
}

/**
 * Convert cursor to list of columns
 */
fun <T> CoreTensorCursor<T>.toColList(): List<CoreTensorColumnVec<T>> {
    val result = mutableListOf<CoreTensorColumnVec<T>>()
    var col = 0
    while (col < cols) {
        result.add(this.col(col))
        col++
    }
    return result
}

// ============================================================================
// BACKWARDS COMPATIBILITY (TrikeShed Interface)
// ============================================================================

/**
 * Legacy cursor size property (rows)
 */
inline val <T> CoreTensorCursor<T>.coreTensorSize: Int get() = rows

/**
 * Legacy at/row operators
 */
inline infix fun <T> CoreTensorCursor<T>.at(y: Int): CoreTensorRowVec<T> = 
    row(if (y < 0) rows + y else y)

/**
 * Legacy meta access (requires CoreTensorCursorWithMeta)
 */
inline val <T> CoreTensorCursorWithMeta<T>.coreTensorMeta: CursorMeta get() = b

/**
 * Legacy column names
 */
inline val CursorMeta.names: List<String> get() {
    val result = mutableListOf<String>()
    var i = 0
    while (i < totalSize) {
        result.add(this(intArrayOf(i)).name)
        i++
    }
    return result
}
