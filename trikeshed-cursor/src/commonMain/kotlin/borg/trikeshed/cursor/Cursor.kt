package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import kotlin.reflect.KClassifier
import kotlin.reflect.KClass

/**
 * TrikeShed Cursor - Type-safe columnar data structure
 * 
 * Based on the proven columnar cursor implementation with full TrikeShed integration.
 * This is the canonical cursor implementation that supersedes all previous versions.
 */

// Core type definitions using TrikeShed foundation
// RowVec and Cursor are now defined in trikeshed-lib
// This module provides cursor operations and extensions

// ColumnMeta is now defined in trikeshed-lib as Join<String, KClassifier>
// Scalar removed - use ColumnMeta from lib

// Core cursor operations

/** Get row at index y, supporting negative indices */
infix fun Cursor.at(y: Int): RowVec = b(if (y < 0) a + y else y)

/** Get slice of rows */
infix fun Cursor.at(r: IntRange): Cursor {
    val actualStart = if (r.first < 0) a + r.first else r.first
    val actualEnd = if (r.last < 0) a + r.last else r.last
    require(actualStart >= 0 && actualEnd < a && actualStart <= actualEnd) { 
        "Invalid range $r for cursor size $a" 
    }
    val sliceSize = actualEnd - actualStart + 1
    return (sliceSize j { y: Int -> b(y + actualStart) })
}

/** Get cursor with specified row indices */
operator fun Cursor.get(vararg indices: Int): Cursor = 
    indices.size j { iy: Int -> b(indices[iy]) }

/** Get cursor with specified row indices from iterable */
operator fun Cursor.get(indices: Iterable<Int>): Cursor {
    val array = indices.toList().toIntArray()
    return array.size j { iy: Int -> b(array[iy]) }
}

// Column operations

/** Get column by index */
fun Cursor.column(index: Int): Indexed<Any?> = 
    a j { rowIndex: Int -> at(rowIndex).b(index).a }

/** Get column by name */
fun Cursor.column(name: String): Indexed<Any?> {
    val columnIndex = findColumnIndex(name)
    require(columnIndex >= 0) { "Column '$name' not found" }
    return column(columnIndex)
}

/** Find column index by name */
private fun Cursor.findColumnIndex(name: String): Int {
    val columnMetas = scalars
    for (i in 0 until columnMetas.a) {
        if (columnMetas.b(i).a == name) {
            return i
        }
    }
    return -1
}

/** Get multiple columns */
fun Cursor.columns(vararg indices: Int): Cursor = 
    a j { rowIndex: Int ->
        indices.size j { colIndex: Int ->
            at(rowIndex).b(indices[colIndex])
        }
    }

// Metadata access

/** Get column scalars/metadata */
val Cursor.scalars: Indexed<ColumnMeta>
    get() = if (a > 0) {
        val firstRow = at(0)
        firstRow.a j { colIndex: Int -> 
            // ColumnMeta is Join<String, KClassifier>, so we need to create the proper structure
            "col_$colIndex" j Int::class
        }
    } else {
        0 j { _: Int -> "col_0" j Int::class }
    }

/** Get column names */
val Cursor.columnNames: Indexed<String>
    get() = scalars.a j { i -> scalars.b(i).a }

/** Get column index by name */
val Cursor.colIdx: Map<String, Int>
    get() = columnNames.let { names ->
        (0 until names.a).associate { i -> names.b(i) to i }
    }

// Type-safe accessors

/** Get Int value with type safety */
fun RowVec.getInt(index: Int): Int? {
    val cell = b(index)
    return cell.a as? Int
}

/** Get String value with type safety */
fun RowVec.getString(index: Int): String? {
    val cell = b(index)
    return cell.a as? String
}

/** Get Float value with type safety */
fun RowVec.getFloat(index: Int): Float? {
    val cell = b(index)
    return cell.a as? Float
}

/** Get Double value with type safety */
fun RowVec.getDouble(index: Int): Double? {
    val cell = b(index)
    return cell.a as? Double
}

/** Generic typed getter */
fun <T : Any> RowVec.getTyped(index: Int, expectedClass: KClass<T>): T? {
    val cell = b(index)
    return if (expectedClass.isInstance(cell.a)) {
        cell.a as? T
    } else null
}

// Transformations

/** Transform cursor values */
fun <T> Cursor.map(transform: (RowVec) -> T): Indexed<T> = 
    a j { i: Int -> transform(at(i)) }

/** Filter cursor rows */
fun Cursor.filter(predicate: (RowVec) -> Boolean): Cursor {
    val matchingIndices = mutableListOf<Int>()
    for (i in 0 until a) {
        if (predicate(at(i))) {
            matchingIndices.add(i)
        }
    }
    return this[matchingIndices]
}

/** Sort cursor by column values */
fun Cursor.sortBy(columnIndex: Int): Cursor {
    val indices = (0 until a).sortedWith { i1, i2 ->
        val v1 = at(i1).b(columnIndex).a
        val v2 = at(i2).b(columnIndex).a
        compareValues(v1 as? Comparable<Any>, v2 as? Comparable<Any>)
    }
    return this[indices]
}

/** Group cursor by column values */
fun Cursor.groupBy(columnIndex: Int): Indexed<Cursor> {
    val groups = mutableMapOf<Any?, MutableList<Int>>()
    for (i in 0 until a) {
        val key = at(i).b(columnIndex).a
        groups.getOrPut(key) { mutableListOf() }.add(i)
    }
    val groupList = groups.values.toList()
    return groupList.size j { i: Int -> this[groupList[i]] }
}

// Aggregations

/** Sum numeric values in column */
fun Cursor.sumColumn(columnIndex: Int): Double {
    var sum = 0.0
    for (i in 0 until a) {
        val value = at(i).b(columnIndex).a
        sum += when (value) {
            is Number -> value.toDouble()
            else -> 0.0
        }
    }
    return sum
}

/** Count non-null values in column */
fun Cursor.countColumn(columnIndex: Int): Int {
    var count = 0
    for (i in 0 until a) {
        if (at(i).b(columnIndex).a != null) count++
    }
    return count
}

// Iteration support

/** Iterator for cursor rows */
fun Cursor.iterator(): Iterator<RowVec> = object : Iterator<RowVec> {
    private var index = 0
    override fun hasNext(): Boolean = index < a
    override fun next(): RowVec = at(index++)
}

/** forEach for cursor rows */
inline fun Cursor.forEach(action: (RowVec) -> Unit) {
    for (i in 0 until a) {
        action(at(i))
    }
}

// Utility functions

/** Convert cursor to list of rows */
fun Cursor.toList(): List<RowVec> = (0 until a).map { at(it) }

/** Create simple cursor from data */
fun cursorOf(
    data: List<List<Any?>>,
    columnNames: List<String> = data.indices.map { "col_$it" },
    columnTypes: List<KClassifier> = data.firstOrNull()?.map { inferType(it) } ?: emptyList()
): Cursor {
    require(data.isNotEmpty()) { "Data cannot be empty" }
    require(columnNames.size == data.first().size) { "Column names size mismatch" }
    require(columnTypes.size == data.first().size) { "Column types size mismatch" }
    
    val scalars = columnNames.zip(columnTypes) { name, type -> name j type }
    
    return data.size j { rowIndex: Int ->
        val rowData = data[rowIndex]
        rowData.size j { colIndex: Int ->
            rowData[colIndex] j { scalars[colIndex] }
        }
    }
}

/** Infer type from value */
private fun inferType(value: Any?): KClassifier = when (value) {
    is Int -> Int::class
    is String -> String::class
    is Float -> Float::class
    is Double -> Double::class
    else -> String::class
}