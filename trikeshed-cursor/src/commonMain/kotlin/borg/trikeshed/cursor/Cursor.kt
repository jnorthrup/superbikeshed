@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import kotlin.reflect.KClassifier
import kotlin.reflect.KClass



typealias Cursor = Indexed<RowVec>
typealias RowVec = Indexed<Cell>
data class ColumnMeta(val name: String, val type: KClassifier)
typealias Cell = Join<Any?, ColumnMeta>

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
    return sliceSize j { y: Int -> b(y + actualStart) }
}

/** Get cursor with specified row indices */
operator fun Cursor.get(vararg indices: Int): Cursor = 
    Cursor(MetaSeries(CursorRowIndex(indices.size)) { iy: CursorRowIndex -> this[indices[iy.value]] })

/** Get cursor with specified row indices from iterable */
operator fun Cursor.get(indices: Iterable<Int>): Cursor {
    val array = indices.toList().toIntArray()
    return array.size j { iy: Int -> b(array[iy]) }
}

// Column operations

/** Get column by index */
fun Cursor.column(index: Int): Indexed<Any?> = 
    size j { rowIndex: Int -> at(rowIndex).b(index) }

/** Get column by name */
fun Cursor.column(name: String): Indexed<Any?> {
    val columnIndex = findColumnIndex(name)
    require(columnIndex >= 0) { "Column '$name' not found" }
    return column(columnIndex)
}

/** Find column index by name */
internal fun Cursor.findColumnIndex(name: String): Int {
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
    Cursor(MetaSeries(CursorRowIndex(size)) { rowIndex: CursorRowIndex ->
        MetaSeries(CursorRowIndex(indices.size)) { colIndex: CursorRowIndex ->
            at(rowIndex.value).b(indices[colIndex.value])
        }
    })

// Metadata access

/** Get column scalars/metadata */
val Cursor.scalars: Indexed<ColumnMeta>
    get() = if (size > 0) {
        val firstRow = this[0]
        firstRow.size j { colIndex: Int -> 
            firstRow.b(colIndex).b
        }
    } else {
        0 j { _: Int -> "" j String::class }
    }

/** Get column names */
val Cursor.columnNames: Indexed<String>
    get() = scalars.size j { i -> scalars[i].a }

/** Get column index by name */
val Cursor.colIdx: Map<String, Int>
    get() = columnNames.let { names ->
        (0 until names.size).associate { i -> names[i] to i }
    }

// Type-safe accessors

/** Get Int value with type safety */
fun RowVec.getInt(index: Int): Int? {
    val cell = this[index]
    return cell.a as? Int
}

/** Get String value with type safety */
fun RowVec.getString(index: Int): String? {
    val cell = this[index]
    return cell.a as? String
}

/** Get Float value with type safety */
fun RowVec.getFloat(index: Int): Float? {
    val cell = this[index]
    return cell.a as? Float
}

/** Get Double value with type safety */
fun RowVec.getDouble(index: Int): Double? {
    val cell = this[index]
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
    size j { i: Int -> transform(at(i)) }

/** Filter cursor rows */
fun Cursor.filter(predicate: (RowVec) -> Boolean): Cursor {
    val matchingIndices = mutableListOf<Int>()
    for (i in 0 until a) {
        if (predicate(b(i))) {
            matchingIndices.add(i)
        }
    }
    return this[matchingIndices]
}

/** Sort cursor by column values */
fun Cursor.sortBy(columnIndex: Int): Cursor {
    val indices = (0 until size).sortedWith { i1, i2 ->
        val v1 = at(i1).b(columnIndex).a
        val v2 = at(i2).b(columnIndex).a
        compareValues(v1 as? Comparable<Any>, v2 as? Comparable<Any>)
    }
    return this[indices]
}

/** Group cursor by column values */
fun Cursor.groupBy(columnIndex: Int): Indexed<Cursor> {
    val groups = mutableMapOf<Any?, MutableList<Int>>()
    for (i in 0 until size) {
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
    for (i in 0 until size) {
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
    for (i in 0 until size) {
        if (at(i).b(columnIndex).a != null) count++
    }
    return count
}

// Iteration support

/** Iterator for cursor rows */
fun Cursor.iterator(): Iterator<RowVec> = object : Iterator<RowVec> {
    internal var index = 0
    override fun hasNext(): Boolean = index < size
    override fun next(): RowVec = at(index++)
}

/** forEach for cursor rows */
inline fun Cursor.forEach(action: (RowVec) -> Unit) {
    for (i in 0 until a) {
        action(b(i))
    }
}

/** Infer type from value */
internal fun inferType(value: Any?): KClassifier = when (value) {
    is Int -> Int::class
    is String -> String::class
    is Float -> Float::class
    is Double -> Double::class
    else -> String::class
}

// Utility functions

/** Convert cursor to list of rows */
fun Cursor.toList(): List<RowVec> = (0 until a).map { b(it) }

/** Create simple cursor from data */
fun cursorOf(
    data: List<List<Any?>>,
    columnNames: List<String> = data.indices.map { "col_$it" },
    columnTypes: List<KClassifier> = data.firstOrNull()?.map { inferType(it) } ?: emptyList()
): Cursor {
    require(data.isNotEmpty()) { "Data cannot be empty" }
    require(columnNames.size == data.first().size) { "Column names size mismatch" }
    require(columnTypes.size == data.first().size) { "Column types size mismatch" }
    
    val scalars: Indexed<ColumnMeta> = columnNames.a j { i ->
        columnNames[i] j columnTypes[i]
    }

    return data.a j { rowIndex: Int ->
        val rowData = data[rowIndex]
        rowData.a j { colIndex: Int ->
            rowData[colIndex] j scalars.b(colIndex)
        }
    }
}





