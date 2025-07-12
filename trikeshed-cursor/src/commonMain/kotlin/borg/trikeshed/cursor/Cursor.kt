package borg.trikeshed.cursor

import borg.trikeshed.lib.ArrayLike
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.MetaSeries
import borg.trikeshed.lib.RowVec
import borg.trikeshed.lib.j
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

typealias ColumnMeta = Join<String, KClassifier>
typealias Cell = Join<Any?, () -> ColumnMeta>

@kotlin.jvm.JvmInline
value class CursorRowIndex(val value: Int)

@kotlin.jvm.JvmInline
value class Cursor(val data: MetaSeries<CursorRowIndex, RowVec>) : ArrayLike<Int, RowVec> {
    override operator fun get(index: Int): RowVec = data.b(CursorRowIndex(index))
    override val size: Int get() = data.a.value
    fun asSeries(): MetaSeries<CursorRowIndex, RowVec> = data
}

/** Get row at index y, supporting negative indices */
infix fun Cursor.at(y: Int): RowVec = this[if (y < 0) this.data.a.value + y else y]

/** Get slice of rows */
infix fun Cursor.at(r: IntRange): Cursor {
    val actualStart = if (r.first < 0) this.data.a.value + r.first else r.first
    val actualEnd = if (r.last < 0) this.data.a.value + r.last else r.last
    require(actualStart >= 0 && actualEnd < this.data.a.value && actualStart <= actualEnd) {
        "Invalid range $r for cursor size ${this.data.a.value}"
    }
    val sliceSize = actualEnd - actualStart + 1
    return Cursor(MetaSeries(CursorRowIndex(sliceSize)) { iy: CursorRowIndex ->
        this[iy.value + actualStart]
    })
}

/**
 * Transforms the Cursor into a MetaSeries, allowing for specialized DSL transformations.
 * This provides a flexible way to project the cursor data into a new structure.
 */
inline fun <T> Cursor.asMetaSeries(crossinline transform: (RowVec) -> T): MetaSeries<CursorRowIndex, T> {
    return MetaSeries(CursorRowIndex(this.data.a.value)) { iy: CursorRowIndex ->
        transform(this[iy.value])
    }
}

operator fun Cursor.get(vararg indices: Int): Cursor {
    val indexedIndices = indices.size j { i -> indices[i] }
    return Cursor(MetaSeries(CursorRowIndex(indexedIndices.a)) { iy: CursorRowIndex -> this.data.b(CursorRowIndex(indexedIndices.b(iy.value))) })
}

/** Get cursor with specified row indices from iterable */
operator fun Cursor.get(indices: Iterable<Int>): Cursor {
    val array = indices.toList().toIntArray()
    return Cursor(MetaSeries(CursorRowIndex(array.size)) { iy: CursorRowIndex -> this.data.b(CursorRowIndex(array[iy.value])) })
}

// Core cursor operations

/** Get column by index */
fun Cursor.column(index: Int): Indexed<Any?> =
    this.data.a.value j { rowIndex: Int -> this[rowIndex].b(index).a }

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
    Cursor(MetaSeries(CursorRowIndex(this.data.a.value)) { rowIndex: CursorRowIndex ->
        val oldRow = this[rowIndex.value]
        indices.size j { colIdx: Int ->
            oldRow.b(indices[colIdx])
        }
    })

// Metadata access

/** Get column scalars/metadata */
val Cursor.scalars: Indexed<ColumnMeta>
    get() = if (this.data.a.value > 0) {
        val firstRow = this[0]
        firstRow.a j { colIndex: Int ->
            firstRow.b(colIndex).b()
        }
    } else {
        0 j { _: Int -> "" j String::class }
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
    this.data.a.value j { i: Int -> this[i].let(transform) }

/** Filter cursor rows */
fun Cursor.filter(predicate: (RowVec) -> Boolean): Cursor {
    val matchingIndices = (0 until this.data.a.value).filter { i -> predicate(this[i]) }.toList()
    return this[matchingIndices]
}

/** Sort cursor by column values */
fun Cursor.sortBy(columnIndex: Int): Cursor {
    val indices = (0 until this.data.a.value).sortedWith { i1, i2 ->
        val v1 = this[i1].b(columnIndex).a
        val v2 = this[i2].b(columnIndex).a
        compareValues(v1 as? Comparable<Any>, v2 as? Comparable<Any>?)
    }
    return this[indices]
}

/** Group cursor by column values */
fun Cursor.groupBy(columnIndex: Int): Indexed<Cursor> {
    val groupsMap = (0 until this.data.a.value).groupBy { i -> this[i].b(columnIndex).a }
    val groupList = groupsMap.values.toList()
    return groupList.size j { i: Int -> this[groupList[i]] }
}

// Aggregations

/** Sum numeric values in column */
fun Cursor.sumColumn(columnIndex: Int): Double {
    var sum = 0.0
    for (i in 0 until this.data.a.value) {
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
    for (i in 0 until this.data.a.value) {
        if (this[i].b(columnIndex).a != null) count++
    }
    return count
}

// Iteration support

/** Iterator for cursor rows */
fun Cursor.iterator(): Iterator<RowVec> = object : Iterator<RowVec> {
    internal var index = 0
    override fun hasNext(): Boolean = index < this@iterator.data.a.value
    override fun next(): RowVec = at(index++)
}

/** forEach for cursor rows */
inline fun Cursor.forEach(action: (RowVec) -> Unit) {
    for (i in 0 until this.data.a.value) {
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
fun Cursor.toList(): List<RowVec> = (0 until this.data.a.value).map { this[it] }

/** Create simple cursor from data */
fun cursorOf(
    data: List<List<Any?>>,
    columnNames: List<String> = data.firstOrNull()?.indices?.map { "col_$it" } ?: emptyList(),
    columnTypes: List<KClassifier> = data.firstOrNull()?.map { inferType(it) } ?: emptyList()
): Cursor {
    require(data.isNotEmpty()) { "Data cannot be empty" }
    val firstRow = data.first()
    require(columnNames.size == firstRow.size) { "Column names size mismatch" }
    require(columnTypes.size == firstRow.size) { "Column types size mismatch" }

    val scalars: Indexed<ColumnMeta> = columnNames.size j { i ->
        Join(columnNames[i], columnTypes[i])
    }

    val metaSeries = MetaSeries(CursorRowIndex(data.size)) { rowIndex: CursorRowIndex ->
        val rowData = data[rowIndex.value]
        val rowVec: RowVec = rowData.size j { colIndex: Int ->
            val cellValue = rowData[colIndex]
            val columnMeta = scalars.b(colIndex)
            cellValue j { columnMeta }
        }
        rowVec
    }
    return Cursor(metaSeries)
}