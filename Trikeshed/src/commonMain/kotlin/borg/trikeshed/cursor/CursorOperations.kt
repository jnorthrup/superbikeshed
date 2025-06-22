package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import borg.trikeshed.lib.ColumnMeta
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
 * Simplified cursor operations that combine TrikeShed's Indexed-based design
 * with columnar operations from the original columnar system.
 * 
 * Key Features:
 * - Basic cursor operations
 * - Utility functions
 * - Performance optimizations
 */

// ============================================================================
// BASIC CURSOR OPERATIONS
// ============================================================================

/**
 * Resample operation - creates a new cursor with specified size
 * Useful for downsampling or upsampling data
 */
fun DatabaseCursor.resample(newSize: Int): DatabaseCursor {
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
fun DatabaseCursor.ordered(
    columnIndices: IntArray,
    comparator: Comparator<RowVec>? = null
): DatabaseCursor {
    val rows = mutableListOf<RowVec>()
    for (i in 0 until this.a) {
        rows.add(this.b(i))
    }
    
    val actualComparator = comparator ?: compareBy { row ->
        columnIndices.joinToString("|") { colIndex ->
            row[colIndex].a?.toString() ?: ""
        }
    }
    rows.sortWith(actualComparator)
    return rows.size j { i -> rows[i] }
}

/**
 * Group operation - groups cursor by specified columns
 * Returns map of group keys to cursors
 */
fun DatabaseCursor.group(
    keyColumns: IntArray
): Map<String, DatabaseCursor> {
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

// ============================================================================
// UTILITY OPERATIONS
// ============================================================================

/**
 * Fill NA values with specified default
 */
fun DatabaseCursor.fillNa(defaultValue: Any?): DatabaseCursor {
    return this.α { row ->
        row.α { cell ->
            if (cell.a == null) defaultValue j { cell.b() } else cell
        }
    }
}

/**
 * Float-specific fill NA operation
 */
fun DatabaseCursor.floatFillNa(defaultValue: Float): DatabaseCursor {
    return this.α { row ->
        row.α { cell ->
            val value = cell.a
            when {
                value == null -> defaultValue j { cell.b() }
                value is Float -> value j { cell.b() }
                value is Number -> value.toFloat() j { cell.b() }
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
): DatabaseCursor {
    return size j { i -> rowFactory(i) }
}

/**
 * Cached cursor - memoizes computed rows
 * Useful for expensive row computations
 */
fun cachedCursor(
    size: Int,
    rowFactory: (Int) -> RowVec
): DatabaseCursor {
    val cache = mutableMapOf<Int, RowVec>()
    return size j { i -> 
        cache.getOrPut(i) { rowFactory(i) }
    }
} 