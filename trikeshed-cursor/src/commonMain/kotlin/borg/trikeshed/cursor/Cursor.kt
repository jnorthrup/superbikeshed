package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

// Re-export cursor types and operations from trikeshed-lib
// This module now serves as a compatibility layer and extension point

// === CURSOR EXTENSIONS AND ADDITIONAL OPERATIONS ===

// Type-safe accessors for RowVec
/** Get Int value with type safety */
fun RowVec.getInt(index: Int): Int? {
    val cell = this.b(index)
    return cell.a as? Int
}

/** Get String value with type safety */
fun RowVec.getString(index: Int): String? {
    val cell = this.b(index)
    return cell.a as? String
}

/** Get Float value with type safety */
fun RowVec.getFloat(index: Int): Float? {
    val cell = this.b(index)
    return cell.a as? Float 
}

/** Get Double value with type safety */
fun RowVec.getDouble(index: Int): Double? {
    val cell = this.b(index)
    return cell.a as? Double
}

/** Generic typed getter */
fun <T : Any> RowVec.getTyped(index: Int, expectedClass: KClass<T>): T? {
    val cell = this.b(index)
    return if (expectedClass.isInstance(cell.a)) {
        cell.a as? T
    } else null
}

// === CURSOR TRANSFORMATIONS ===

/** Transform cursor values */
fun <T> Cursor.map(transform: (RowVec) -> T): Indexed<T> =
    a j { i: Int -> at(i).let(transform) }

/** Filter cursor rows */
fun Cursor.filter(predicate: (RowVec) -> Boolean): Cursor {
    val matchingIndices = (0 until a).filter { i -> predicate(at(i)) }.toList()
    return this[matchingIndices]
}

/** Sort cursor by column values */
fun Cursor.sortBy(columnIndex: Int): Cursor {
    val indices = (0 until a).sortedWith { i1, i2 ->
        val v1 = at(i1).b(columnIndex).a
        val v2 = at(i2).b(columnIndex).a
        compareValues(v1 as? Comparable<Any>, v2 as? Comparable<Any>?)
    }
    return this[indices]
}

/** Group cursor by column values */
fun Cursor.groupBy(columnIndex: Int): Indexed<Cursor> {
    val groupsMap = (0 until a).groupBy { i -> at(i).b(columnIndex).a }
    val groupList = groupsMap.values.toList()
    return groupList.size j { i: Int -> this[groupList[i]] }
}

// === CURSOR AGGREGATIONS ===

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

// === CURSOR UTILITIES ===

/** Get multiple columns */
fun Cursor.columns(vararg indices: Int): Cursor =
    a j { rowIndex: Int ->
        val oldRow = at(rowIndex)
        indices.size j { colIdx: Int ->
            oldRow.b(indices[colIdx])
        }
    }

/** Transforms the Cursor into a MetaSeries, allowing for specialized DSL transformations */
inline fun <T> Cursor.asMetaSeries(crossinline transform: (RowVec) -> T): MetaSeries<Int, T> {
    return MetaSeries_create(a) { iy: Int ->
        transform(at(iy))
    }
}