package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import borg.trikeshed.isam.meta.IOMemento
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * TRIKESHED CURSOR OPERATIONS - Merged from Columnar
 * 
 * Comprehensive cursor operations that combine TrikeShed's Indexed-based design
 * with sophisticated columnar operations from the original columnar system.
 * 
 * Key Features:
 * - Advanced slicing and indexing operations
 * - Pivot and group operations
 * - Type-safe transformations
 * - Performance optimizations
 * - Database-like operations
 */

// ============================================================================
// ADVANCED CURSOR OPERATIONS (Columnar Heritage)
// ============================================================================

/**
 * Resample operation - creates a new cursor with specified size
 * Useful for downsampling or upsampling data
 */
fun Cursor.resample(newSize: Int): Cursor {
    require(newSize >= 0) { "Resample size must be non-negative" }
    return newSize j { i -> 
        val originalIndex = (i * this.a) / newSize
        this.b(originalIndex)
    }
}

/**
 * Order operation - sorts cursor by specified columns
 * Supports multiple column sorting with custom comparators
 */
fun Cursor.ordered(
    columnIndices: IntArray,
    comparator: Comparator<RowVec>? = null
): Cursor {
    val rows = this.play.toMutableList()
    val actualComparator = comparator ?: compareBy { row ->
        columnIndices.joinToString("|") { colIndex ->
            row[colIndex].a?.toString() ?: ""
        }
    }
    rows.sortWith(actualComparator)
    return rows.size j { i -> rows[i] }
}

/**
 * Pivot operation - transforms cursor into pivot table format
 * Groups by key columns, creates value columns, and aggregates measure columns
 */
fun Cursor.pivot(
    keyColumns: IntArray,
    valueColumns: IntArray,
    measureColumns: IntArray
): Cursor {
    // Group by key columns
    val groups = mutableMapOf<String, MutableList<RowVec>>()
    
    for (i in 0 until this.a) {
        val row = this.b(i)
        val key = keyColumns.joinToString("|") { colIndex ->
            row[colIndex].a?.toString() ?: ""
        }
        groups.getOrPut(key) { mutableListOf() }.add(row)
    }
    
    // Create pivot table
    val pivotRows = mutableListOf<RowVec>()
    for ((key, groupRows) in groups) {
        val keyValues = key.split("|")
        val pivotRow = (keyColumns.size + valueColumns.size * measureColumns.size) j { colIndex ->
            when {
                colIndex < keyColumns.size -> groupRows[0][keyColumns[colIndex]]
                else -> {
                    val valueColIndex = (colIndex - keyColumns.size) / measureColumns.size
                    val measureColIndex = (colIndex - keyColumns.size) % measureColumns.size
                    val valueCol = valueColumns[valueColIndex]
                    val measureCol = measureColumns[measureColIndex]
                    
                    // Aggregate measure values for this key-value combination
                    val values = groupRows.mapNotNull { row ->
                        if (row[valueCol].a == groupRows[0][valueCol].a) {
                            row[measureCol].a as? Number
                        } else null
                    }
                    
                    values.sumOf { it.toDouble() } j { 
                        IOMemento().apply { 
                            name = "pivot_${valueCol}_${measureCol}"
                            type = "double"
                        }
                    }
                }
            }
        }
        pivotRows.add(pivotRow)
    }
    
    return pivotRows.size j { i -> pivotRows[i] }
}

/**
 * Group operation - groups cursor by specified columns
 * Returns map of group keys to cursors
 */
fun Cursor.group(
    keyColumns: IntArray,
    aggregator: (Indexed<Any?>) -> Any? = { it.play.firstOrNull() }
): Map<String, Cursor> {
    val groups = mutableMapOf<String, MutableList<RowVec>>()
    
    for (i in 0 until this.a) {
        val row = this.b(i)
        val key = keyColumns.joinToString("|") { colIndex ->
            row[colIndex].a?.toString() ?: ""
        }
        groups.getOrPut(key) { mutableListOf() }.add(row)
    }
    
    return groups.mapValues { (_, rows) ->
        rows.size j { i -> rows[i] }
    }
}

/**
 * Join operation - joins two cursors on specified columns
 * Supports inner, left, right, and full outer joins
 */
fun Cursor.join(
    other: Cursor,
    leftColumns: IntArray,
    rightColumns: IntArray,
    joinType: JoinType = JoinType.INNER
): Cursor {
    // Build index for right cursor
    val rightIndex = mutableMapOf<String, MutableList<RowVec>>()
    for (i in 0 until other.a) {
        val row = other.b(i)
        val key = rightColumns.joinToString("|") { colIndex ->
            row[colIndex].a?.toString() ?: ""
        }
        rightIndex.getOrPut(key) { mutableListOf() }.add(row)
    }
    
    val joinedRows = mutableListOf<RowVec>()
    
    // Process left cursor
    for (i in 0 until this.a) {
        val leftRow = this.b(i)
        val key = leftColumns.joinToString("|") { colIndex ->
            leftRow[colIndex].a?.toString() ?: ""
        }
        
        val matchingRightRows = rightIndex[key] ?: emptyList()
        
        when (joinType) {
            JoinType.INNER -> {
                for (rightRow in matchingRightRows) {
                    joinedRows.add(combineRows(leftRow, rightRow))
                }
            }
            JoinType.LEFT -> {
                if (matchingRightRows.isEmpty()) {
                    joinedRows.add(combineRows(leftRow, createNullRow(other.cols)))
                } else {
                    for (rightRow in matchingRightRows) {
                        joinedRows.add(combineRows(leftRow, rightRow))
                    }
                }
            }
            JoinType.RIGHT -> {
                if (matchingRightRows.isEmpty()) {
                    joinedRows.add(combineRows(createNullRow(this.cols), leftRow))
                } else {
                    for (rightRow in matchingRightRows) {
                        joinedRows.add(combineRows(leftRow, rightRow))
                    }
                }
            }
            JoinType.FULL -> {
                if (matchingRightRows.isEmpty()) {
                    joinedRows.add(combineRows(leftRow, createNullRow(other.cols)))
                } else {
                    for (rightRow in matchingRightRows) {
                        joinedRows.add(combineRows(leftRow, rightRow))
                    }
                }
            }
        }
    }
    
    return joinedRows.size j { i -> joinedRows[i] }
}

// ============================================================================
// UTILITY OPERATIONS
// ============================================================================

/**
 * Join types for cursor join operations
 */
enum class JoinType {
    INNER, LEFT, RIGHT, FULL
}

/**
 * Combine two rows into a single row
 */
private fun combineRows(left: RowVec, right: RowVec): RowVec {
    return (left.size + right.size) j { i ->
        if (i < left.size) left[i] else right[i - left.size]
    }
}

/**
 * Create a row with null values
 */
private fun createNullRow(cols: Int): RowVec {
    return cols j { i -> 
        null j { 
            IOMemento().apply { 
                name = "null_$i"
                type = "null"
            }
        }
    }
}

/**
 * Fill NA values with specified default
 */
fun Cursor.fillNa(defaultValue: Any?): Cursor {
    return this.α { row ->
        row.α { cell ->
            if (cell.a == null) defaultValue j { cell.b() } else cell
        }
    }
}

/**
 * Float-specific fill NA operation
 */
fun Cursor.floatFillNa(defaultValue: Float): Cursor {
    return this.α { row ->
        row.α { cell ->
            when (cell.a) {
                null, is Float -> (cell.a as? Float ?: defaultValue) j { cell.b() }
                is Number -> (cell.a.toFloat()) j { cell.b() }
                else -> cell
            }
        }
    }
}

/**
 * Sum aggregation for float values
 */
fun floatSum(values: Indexed<Any?>): Float {
    return values.play.sumOf { 
        when (it) {
            is Float -> it.toDouble()
            is Number -> it.toDouble()
            else -> 0.0
        }
    }.toFloat()
}

/**
 * String representation of row vector
 */
fun stringOf(row: RowVec): String {
    return row.play.joinToString(", ") { it.a?.toString() ?: "null" }
}

// ============================================================================
// PERFORMANCE OPTIMIZATIONS
// ============================================================================

/**
 * Lazy cursor - creates cursor that computes rows on-demand
 * Useful for large datasets that don't fit in memory
 */
fun lazyCursor(
    size: Int,
    rowFactory: (Int) -> RowVec
): Cursor {
    return size j { i -> rowFactory(i) }
}

/**
 * Cached cursor - memoizes computed rows
 * Useful for expensive row computations
 */
fun cachedCursor(
    size: Int,
    rowFactory: (Int) -> RowVec
): Cursor {
    val cache = mutableMapOf<Int, RowVec>()
    return size j { i -> 
        cache.getOrPut(i) { rowFactory(i) }
    }
} 