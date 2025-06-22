@file:Suppress("NOTHING_TO_INLINE")

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
 * TRIKESHED CURSOR IMPLEMENTATION - Series-Based Design
 *
 * Restores TrikeShed's original philosophy of functional composition:
 * - Cursor = Indexed<RowVec> (delegates to Indexed, doesn't inherit)
 * - RowVec = Indexed<Join<Value, Meta>> (preserves Join pattern)
 * - All operations through Indexed composition
 * - Maintains TrikeShed's Join<Value, Meta> pattern for columnar data
 * - Enables 50k+ column scale through lazy evaluation
 *
 * CANONICAL USAGE PATTERN:
 * ```
 * curs[-"unused"][2..7]["Close","High"] at(1)
 * ```
 * 
 * This elegant chained syntax demonstrates the power of operator overloading:
 * 1. curs[-"unused"] - exclude the "unused" column
 * 2. [2..7] - select rows 2-7  
 * 3. ["Close","High"] - select columns by name
 * 4. at(1) - get row 1 from the result
 * 
 * The operator overloads work together to create this fluent, composable API.
 * Collisions between get(row: Int) and get(vararg colIndices: Int) are resolved
 * by the compiler based on parameter types and context.
 */

// ============================================================================
// INDEXED-BASED CURSOR TYPEALIASES
// ============================================================================

/**
 * Cursor - Indexed of RowVec (functional composition)
 */
typealias Cursor = Indexed<RowVec>

/**
 * CursorWithMeta - Cursor with attached metadata (Join pattern)
 */
typealias CursorWithMeta = Join<Cursor, Indexed<ColumnMeta>>

// ============================================================================
// CURSOR ACCESSORS (Indexed-Based)
// ============================================================================

// Cursor dimensions
val Cursor.rows: Int get() = size
val Cursor.cols: Int get() = if (size > 0) this[0].size else 0

// Row access (delegates to Indexed)
fun Cursor.row(index: Int): RowVec {
    require(index >= 0 && index < size) { "Row index $index out of bounds [0, $size)" }
    return this[index]
}

// Column access through Indexed composition
fun Cursor.columns(): Indexed<Indexed<Any?>> = this.α { row -> row.α { it.a } }

// ============================================================================
// TRIKESHED CURSOR OPERATORS (Indexed-Based)
// ============================================================================

/**
 * Cursor slicing by row range
 * 
 * Usage: cursor[0..5] - selects rows 0-5
 */
operator fun Cursor.get(rowRange: IntRange): Cursor {
    require(rowRange.first >= 0) { "Row range start ${rowRange.first} out of bounds" }
    require(rowRange.last < size) { "Row range end ${rowRange.last} out of bounds" }

    val newSize = rowRange.last - rowRange.first + 1
    return newSize j { i -> this[rowRange.first + i] }
}

/**
 * Cursor slicing by column indices (preserves TrikeShed pattern)
 * 
 * Usage: cursor[0, 2, 4] - selects columns 0, 2, and 4
 * 
 * Note: This can collide with get(row: Int) when called with a single integer.
 * The compiler resolves this based on parameter types and context.
 */
operator fun Cursor.get(vararg colIndices: Int): Cursor {
    require(colIndices.all { it >= 0 }) { "Column indices must be non-negative" }
    
    return this.α { row ->
        colIndices.size j { i ->
            val colIndex = colIndices[i]
            require(colIndex < row.size) { "Column index $colIndex out of bounds" }
            row[colIndex]
        }
    }
}

/**
 * Cursor slicing by column names (requires metadata)
 * 
 * Usage: cursorWithMeta["Close", "High"] - selects columns by name
 */
fun CursorWithMeta.get(vararg columnNames: String): CursorWithMeta {
    val cursor = a // data
    val meta = b // metadata

    val colIndices = mutableListOf<Int>()
    for (name in columnNames) {
        val index = meta.play.indexOfFirst { it.name == name }
        require(index >= 0) { "Column '$name' not found" }
        colIndices.add(index)
    }

    val newCursor = cursor[colIndices.toIntArray()]
    val newMeta = colIndices.size j { i -> meta[colIndices[i]] }

    return newCursor j newMeta
}

// ============================================================================
// COLUMN EXCLUSION (TrikeShed Pattern)
// ============================================================================

/** ColumnExclusion value class
 *
 * used to exclude columns from a cursor by name
 *
 * @param name the name of the column to exclude */
@JvmInline
value class ColumnExclusion(val name: String) {
    override fun toString(): String = "ColumnExclusion($name)"
}

/** create operator unary minus for ColumnExclusion on string */
operator fun String.unaryMinus(): ColumnExclusion = ColumnExclusion(this)

/**
 * Exclude columns by names
 * 
 * Usage: cursor[-"unused", -"temp"] - excludes columns named "unused" and "temp"
 */
fun CursorWithMeta.exclude(vararg exclusions: ColumnExclusion): CursorWithMeta {
    val cursor = a
    val meta = b

    val excludeNames = exclusions.map { it.name }.toSet()
    val retainedIndices = meta.play.mapIndexedNotNull { index, columnMeta ->
        if (columnMeta.name !in excludeNames) index else null
    }

    val newCursor = cursor[retainedIndices.toIntArray()]
    val newMeta = retainedIndices.size j { i -> meta[retainedIndices[i]] }

    return newCursor j newMeta
}

// ============================================================================
// CURSOR DISPLAY OPERATIONS (TrikeShed Heritage)
// ============================================================================

/**
 * Show cursor head (default 5 rows)
 */
fun Cursor.head(count: Int = 5) {
    val showRows = min(count, size)
    println("Cursor: $size rows x $cols columns")

    for (r in 0 until showRows) {
        val row = this[r]
        val rowData = row.play.map { it.a }
        println("Row $r: $rowData")
    }

    if (showRows < size) {
        println("... ${size - showRows} more rows")
    }
}

/** head default 5 rows just like unix head */
@JvmOverloads
fun Cursor.head(last: Int = 5): Unit = show(0 until (max(0, min(last, size))))

/** run head starting at random index */
fun Cursor.showRandom(n: Int = 5) {
    head(0); repeat(n) {
        if (size > 0) showValues(Random.nextInt(0, size).let { it..it })
    }
}

/** simple printout macro*/
fun CursorWithMeta.show(range: IntRange = 0 until a.size) {
    val meta: Indexed<ColumnMeta> = b
    println("rows:${a.size}" to meta.play.map { it.name })
    showValues(range)
}

fun CursorWithMeta.showValues(range: IntRange) {
    val cursor = a
    try {
        for (x in range) {
            val row: RowVec = cursor.row(x)
            val rowData = row.play.map { it.a }
            println(rowData)
        }
    } catch (e: NoSuchElementException) {
        println("cannot fully access range $range")
    }
}

infix fun Cursor.at(y: Int): RowVec = row(if (y < 0) size + y else y)

/** IsNumerical
 * iterate the meta enum types and check if all are numerical
 *
 * IoByte,IoShort,IoInt,IoDouble,IoLong qualify as numerical
 */
val CursorWithMeta.isNumerical: Boolean
    get() {
        val meta = b
        return meta.play.all { columnMeta ->
            when (columnMeta.type) {
                "byte", "short", "int", "float", "double", "long" -> true
                else -> false
            }
        }
    }

val CursorWithMeta.isHomomorphic: Boolean
    get() {
        val meta = b
        if (meta.size == 0) return true
        val firstType = meta[0].type
        return meta.play.all { it.type == firstType }
    }

// ============================================================================
// TYPE-BASED FLATMAPPING (TrikeShed Pattern)
// ============================================================================

/**
 * Operator Cursor '/' Class<A>
 *
 * returns Indexed<Indexed<A?>>> where the meta is stripped out and the values are cast using
 * "as?" A return only A values and null for non-A values
 */
inline operator fun <A : Any, IR : Any?, SrInnr : Indexed<Join<A, *>>, SrOutr : Indexed<SrInnr>, RC : KClass<A?>> SrOutr.div(
    c: KClass<out A>,
): Indexed<Indexed<A?>> = this α { it α Join<A, *>::a } α { it α { it } } α { it α { it } }

// ============================================================================
// CURSOR INTERFACE (Platform-Specific Implementations)
// ============================================================================

/**
 * Cursor interface for platform-specific implementations
 */
interface CursorInterface {
    val a: Int
    val b: (Int) -> RowVec
} 