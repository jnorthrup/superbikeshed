
package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import kotlin.reflect.KClassifier
import kotlin.reflect.KClass



typealias Cell = Join<Any?, ColumnMeta>

// Canonical RowVec and Cursor definitions
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>

// Cursor with ArrayLike trait - WHENEVER THEY NEED get[i] OPERATOR
@kotlin.jvm.JvmInline
value class Cursor(internal val data: MetaSeries<CursorRowIndex, RowVec>) : ArrayLike<Int, RowVec> {
    override operator fun get(index: Int): RowVec = data.b(CursorRowIndex(index))
    override val size: Int get() = data.a.value
    // Delegate to the underlying MetaSeries for operations that need it
    fun asSeries(): MetaSeries<CursorRowIndex, RowVec> = data
}





















/** Get row at index y, supporting negative indices */
infix fun Cursor.at(y: Int): RowVec = this[if (y < 0) size + y else y]

/** Get slice of rows */
infix fun Cursor.at(r: IntRange): Cursor {
    val actualStart = if (r.first < 0) size + r.first else r.first
    val actualEnd = if (r.last < 0) size + r.last else r.last
    require(actualStart >= 0 && actualEnd < size && actualStart <= actualEnd) { 
        "Invalid range $r for cursor size $size" 
    }
    val sliceSize = actualEnd - actualStart + 1
    return Cursor(MetaSeries(CursorRowIndex(sliceSize)) { iy: CursorRowIndex ->
        this[iy.value + actualStart]
    })
}

/** Get cursor with specified row indices */

/**
 * Transforms the Cursor into a MetaSeries, allowing for specialized DSL transformations.
 * This provides a flexible way to project the cursor data into a new structure.
 */
inline fun <T> Cursor.asMetaSeries(crossinline transform: (RowVec) -> T): MetaSeries<CursorRowIndex, T> {
    return MetaSeries(CursorRowIndex(size)) { iy: CursorRowIndex ->
        transform(this[iy.value])
    }
}

operator fun Cursor.get(vararg indices: Int): Cursor = 
    Cursor(MetaSeries(CursorRowIndex(indices.size)) { iy: CursorRowIndex -> data.b(CursorRowIndex(indices[iy.value])) })

/** Get cursor with specified row indices from iterable */
operator fun Cursor.get(indices: Iterable<Int>): Cursor {
    val array = indices.toList().toIntArray()
    return Cursor(array.size j { iy: Int -> data.b(CursorRowIndex(array[iy])) })
}

// Core cursor operations

/** Get row at index y, supporting negative indices */
infix fun Cursor.at(y: Int): RowVec = this[if (y < 0) size + y else y]

/** Get column by index */
fun Cursor.column(index: Int): Indexed<Any?> = 
    size j { rowIndex: Int -> this[rowIndex].b(index) }

/** Get column by name */
fun Cursor.column(name: String): Indexed<Any?> {
    val columnIndex = findColumnIndex(name)
    require(columnIndex >= 0) { "Column '$name' not found" }
    return column(columnIndex)
}

/** Find column index by name */
internal fun Cursor.findColumnIndex(name: String): Int {
    val columnMetas = scalars
    for (i in 0 until columnMetas.size) {
        if (columnMetas[i].a == name) {
            return i
        }
    }
    return -1
}

/** Get multiple columns */
fun Cursor.columns(vararg indices: Int): Cursor = 
    Cursor(MetaSeries(CursorRowIndex(size)) { rowIndex: CursorRowIndex ->
        MetaSeries(CursorRowIndex(indices.size)) { colIndex: CursorRowIndex ->
            this[rowIndex.value].b(indices[colIndex.value])
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
    val cell = this.b(index)
    return if (expectedClass.isInstance(cell.a)) {
        cell.a as? T
    } else null
}

// Transformations

/** Transform cursor values */
fun <T> Cursor.map(transform: (RowVec) -> T): Indexed<T> = 
    size j { i: Int -> this[i].let(transform) }

/** Filter cursor rows */
fun Cursor.filter(predicate: (RowVec) -> Boolean): Cursor {
    val matchingIndices = mutableListOf<Int>()
    for (i in 0 until size) {
        if (predicate(this[i])) {
            matchingIndices.add(i)
        }
    }
    return this[matchingIndices]
}

/** Sort cursor by column values */
fun Cursor.sortBy(columnIndex: Int): Cursor {
    val indices = (0 until size).sortedWith { i1, i2 ->
        val v1 = this[i1].b(columnIndex).a
        val v2 = this[i2].b(columnIndex).a
        compareValues(v1 as? Comparable<Any>, v2 as? Comparable<Any>)
    }
    return this[indices]
}

/** Group cursor by column values */
fun Cursor.groupBy(columnIndex: Int): Indexed<Cursor> {
    val groups = mutableMapOf<Any?, MutableList<Int>>()
    for (i in 0 until size) {
        val key = this[i].b(columnIndex).a
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
        val value = this[i].b(columnIndex).a
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
        if (this[i].b(columnIndex).a != null) count++
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
    for (i in 0 until size) {
        action(this[i])
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
fun Cursor.toList(): List<RowVec> = (0 until size).map { this[it] }

/** Create simple cursor from data */
fun cursorOf(
    data: List<List<Any?>>,
    columnNames: List<String> = data.indices.map { "col_$it" },
    columnTypes: List<KClassifier> = data.firstOrNull()?.map { inferType(it) } ?: emptyList()
): Cursor {
    require(data.isNotEmpty()) { "Data cannot be empty" }
    require(columnNames.size == data.first().size) { "Column names size mismatch" }
    require(columnTypes.size == data.first().size) { "Column types size mismatch" }
    
    val scalars: Indexed<ColumnMeta> = columnNames.size j { i ->
        columnNames[i] j columnTypes[i]
    }

    return data.size j { rowIndex: Int ->
        val rowData = data[rowIndex]
        rowData.a j { colIndex: Int ->
            rowData[colIndex] j scalars.b(colIndex)
        }
    }
}





