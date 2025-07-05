
package borg.trikeshed.lib

import borg.trikeshed.lib.IOMemento.*
import borg.trikeshed.lib.IOMemento
import borg.trikeshed.lib.TypeMemento
import kotlin.reflect.KClassifier



/**
 * Columnar Extensions - Categorical expressions and cursor operations
 * 
 * Brings over the essential patterns from columnar without higher-arity tuples.
 * Focuses on Join<A,B> (Pair semantics) and Indexed<T> operations.
 * 
 */

// === Cursor Type Definitions ===


typealias CursorLike = Indexed<RowVec>
typealias ColumnTypeMemento = Join<IOMemento, String?>

// === Categorical Operators ===

/**
 * Sum operator (∑) - Reduces values in a cursor
 */
infix fun <T> Indexed<T>.`∑`(reducer: (T, T) -> T): T? {
    if (a == 0) return null
    return if (a == 1) b(0) else reduce(reducer)
}

/**
 * List ellipsis operator (…) - Converts to list
 */
val <T> Indexed<T>.`…`: List<T> get() = this.toList()

/**
 * Right arrow operator (→) - Creates a Join (pair)
 */
infix fun <A, B> A.`→`(b: B): Join<A, B> = this j b

/**
 * Left arrow operator (←) - Reverse Join
 */
infix fun <A, B> B.`←`(a: A): Join<A, B> = a j this

/**
 * Right shift operator (➤) - Sequential application  
 * Note: This is defined as an infix function, not a property
 */
infix fun <T, R> Indexed<T>.`➤`(transform: (T) -> R): Indexed<R> = this.map(transform)

/**
 * Double right arrow (⇒) - Flat map operation
 */
infix fun <T, R> Indexed<T>.`⇒`(transform: (T) -> Indexed<R>): Indexed<R> = this.flatMap(transform)

/**
 * Composition operator (⚬) - Function composition (g ∘ f)
 * G follows F: (g ⚬ f)(x) = g(f(x))
 */
infix fun <A, B, C> ((B) -> C).`⚬`(f: (A) -> B): (A) -> C = { a: A -> this(f(a)) }

/**
 * Composition operator alias (c) - Function composition
 * Same as ⚬ but using ASCII for easier typing
 */
infix fun <A, B, C> ((B) -> C).c(f: (A) -> B): (A) -> C = this `⚬` f

/**
 * MetaSeries accessor composition - Compose with the b accessor
 * Allows: transform ⚬ metaSeries to create a new composed accessor
 */
infix fun <A, T, R> ((T) -> R).`⚬`(series: MetaSeries<A, T>): (A) -> R = this `⚬` series.b

/**
 * MetaSeries accessor composition alias (c) - Compose with the b accessor
 * Same as ⚬ but using ASCII for easier typing
 */
infix fun <A, T, R> ((T) -> R).c(series: MetaSeries<A, T>): (A) -> R = this c series.b

/**
 * MetaSeries composition - Compose two MetaSeries through their accessors
 * Creates a new MetaSeries with composed accessor functions
 */
infix fun <A, B, C> MetaSeries<A, B>.`⚬`(lookup: MetaSeries<B, C>): MetaSeries<A, C> = 
    this.a j (lookup.b `⚬` this.b)

/**
 * MetaSeries composition alias (c) - Compose two MetaSeries through their accessors
 * Same as ⚬ but using ASCII for easier typing
 */
infix fun <A, B, C> MetaSeries<A, B>.c(lookup: MetaSeries<B, C>): MetaSeries<A, C> = 
    this.a j (lookup.b c this.b)

/**
 * Right identity operator (⟲) - Returns a function that returns the value
 * The right identity element in functional composition
 */
val <T> T.`⟲`: () -> T get() = { this }

/**
 * Lift value into MetaSeries - Creates a constant MetaSeries
 * Useful for composition: value.lift<A>() ⚬ series
 */
fun <A, T> T.lift(): MetaSeries<A, T> = null as A j { _: A -> this }

/**
 * Accessor reference - Extract b as a composable function
 * Allows: series.accessor ⚬ transform
 */
val <A, T> MetaSeries<A, T>.accessor: (A) -> T get() = this.b


// === Cursor-specific Operations ===

/**
 * Get cursor width (number of columns)
 */
val CursorLike.width: Int 
    get() = if (a > 0) b(0).a else 0

/**
 * Get column names from cursor
 */
val CursorLike.columnNames: Indexed<String?>
    get() = if (a > 0) {
        val firstRow = b(0)
        firstRow.a j { i: Int -> firstRow.b(i).b().a }
    } else {
        0 j { null }
    }

/**
 * Get column types from cursor
 */
val CursorLike.columnTypes: Indexed<KClassifier>
    get() = if (a > 0) {
        val firstRow = b(0)
        firstRow.a j { i: Int -> firstRow.b(i).b().b }
    } else {
        0 j { Int::class }
    }

/**
 * Access cursor row by index
 */
infix fun CursorLike.at(index: Int): RowVec = b(index)

/**
 * Select columns by indices
 */
fun CursorLike.select(vararg indices: Int): CursorLike = a j { rowIdx ->
    val row = b(rowIdx)
    indices.size j { colIdx -> row.b(indices[colIdx]) }
}

/**
 * Select columns by names
 */
fun CursorLike.select(vararg names: String): CursorLike {
    val nameToIndex = columnNames.toList().mapIndexed { i: Int, name: String? -> name to i }.toMap()
    val indices = names.mapNotNull { nameToIndex[it] }.toIntArray()
    return select(*indices)
}

/**
 * Filter cursor rows
 */
inline fun CursorLike.where(predicate: (RowVec) -> Boolean): CursorLike {
    val filtered = mutableListOf<RowVec>()
    for (i in 0 until a) {
        val row = b(i)
        if (predicate(row)) filtered.add(row)
    }
    return filtered.size j filtered::get
}

/**
 * Map over cursor rows
 */
inline fun CursorLike.mapRows(crossinline transform: (RowVec) -> RowVec): CursorLike = 
    a j { i -> transform(b(i)) }

/**
 * Group by column index - using Indexed2 pattern
 */
fun CursorLike.groupBy(columnIndex: Int): Indexed2<Any?, CursorLike> {
    val groups = mutableMapOf<Any?, MutableList<RowVec>>()
    
    for (i in 0 until a) {
        val row = b(i)
        val key = if (columnIndex < row.a) row.b(columnIndex).a else null
        groups.getOrPut(key) { mutableListOf() }.add(row)
    }
    
    return groups.size j { i ->
        val entry = groups.entries.elementAt(i)
        entry.key j (entry.value.size j entry.value::get)
    }
}

/**
 * Aggregate grouped data - using Indexed2 pattern
 */
inline fun <T> Indexed2<Any?, CursorLike>.aggregate(
    crossinline aggregator: (CursorLike) -> T
): Indexed2<Any?, T> = a j { i ->
    val group = b(i)
    group.a j aggregator(group.b)
}

// === Categorical Expressions ===

/**
 * Categorical type for type-safe column operations
 */
sealed class Categorical<T> {
    abstract val values: Indexed<T>
    abstract val categories: Indexed<String>
    
    data class Nominal<T>(
        override val values: Indexed<T>,
        override val categories: Indexed<String>
    ) : Categorical<T>()
    
    data class Ordinal<T : Comparable<T>>(
        override val values: Indexed<T>,
        override val categories: Indexed<String>,
        val ordering: Comparator<T>
    ) : Categorical<T>()
}

/**
 * Convert column to categorical
 */
fun <T> Indexed<T>.asCategorical(categories: Indexed<String>): Categorical.Nominal<T> =
    Categorical.Nominal(this, categories)

/**
 * Convert column to ordinal categorical
 */
fun <T : Comparable<T>> Indexed<T>.asOrdinal(
    categories: Indexed<String>
): Categorical.Ordinal<T> = Categorical.Ordinal(this, categories, naturalOrder())

// === Network Coordinate Helpers ===

/**
 * Calculate network coordinates for serialization - using Indexed2 pattern
 */
fun networkCoords(
    types: Indexed<ColumnTypeMemento>,
    defaultVarcharSize: Int = 255,
    varcharSizes: Map<Int, Int> = emptyMap()
): Indexed2<Int, Int> {
    // Use Indexed2 pattern to avoid type hardening
    val coordCalculator: (Indexed<ColumnTypeMemento>, Int, Map<Int, Int>) -> Indexed2<Int, Int> = { typeMementos, defaultSize, varSizes ->
        var offset = 0
        typeMementos.a j { i ->
            val memento = typeMementos.b(i).a
            val size = when (memento) {
                IOMemento.IoVarchar -> varSizes[i] ?: defaultSize
                else -> memento.networkSize ?: 0
            }
            val start = offset
            offset += size
            start j offset
        }
    }
    return coordCalculator(types, defaultVarcharSize, varcharSizes)
}

// === Async Cursor Operations ===



// === Type-safe Column Access ===

/**
 * Column reference for type-safe access
 */
data class Column<T>(
    val name: String,
    val index: Int,
    val type: IOMemento
)

/**
 * Get typed value from row - softened to avoid type hardening
 */
operator fun <T> RowVec.get(column: Column<T>): T? {
    // Lambda-based column access to avoid type hardening
    val columnAccessor: (RowVec, Column<T>) -> T? = { row, col ->
        if (col.index < row.a) (row.b(col.index) as Join<Any?, () -> ColumnMeta>).a as? T else null
    }
    return columnAccessor(this, column)
}

/**
 * Create column reference
 */
inline fun <reified T> column(name: String, index: Int, memento: IOMemento): Column<T> =
    Column(name, index, memento)

// === IOMemento Extensions ===

/**
 * Network size for different types
 */
val IOMemento.networkSize: Int?
    get() = when (this) {
        IOMemento.IoBoolean -> 1
        IOMemento.IoByte -> 1
        IOMemento.IoShort -> 2
        IOMemento.IoInt -> 4
        IOMemento.IoLong -> 8
        IOMemento.IoFloat -> 4
        IOMemento.IoDouble -> 8
        IOMemento.IoChar -> 2
        IOMemento.IoString -> null // Variable length
        IOMemento.IoVarchar -> null // Variable length
        IOMemento.IoLocalDate -> 8
        IOMemento.IoLocalDateTime -> 16
        IOMemento.IoInstant -> 8
        IOMemento.IoNothing -> null
    }

/**
 * Check if type is numeric
 */
val IOMemento.isNumeric: Boolean
    get() = when (this) {
        IOMemento.IoByte,
        IOMemento.IoShort,
        IOMemento.IoInt,
        IOMemento.IoLong,
        IOMemento.IoFloat,
        IOMemento.IoDouble,
        IOMemento.IoChar -> true
        else -> false
    }

// === Cursor Builders ===

/**
 * Build a cursor from data - using Indexed2 pattern
 */
fun buildCursor(
    columns: Indexed2<String, IOMemento>,
    data: Indexed<Indexed<Any?>>
): CursorLike {
    // Use Indexed2 pattern to avoid type hardening
    val cursorBuilder: (Indexed2<String, IOMemento>, Indexed<Indexed<Any?>>) -> CursorLike = { cols, dat ->
        dat.a j { rowIdx ->
            val rowData = dat.b(rowIdx)
            cols.a j { colIdx ->
                val value = if (colIdx < rowData.a) rowData.b(colIdx) else null
                val name = cols.b(colIdx).a
                val memento = cols.b(colIdx).b
                value j { Join(name, memento::class) }
            }
        }
    }
    return cursorBuilder(columns, data)
}

/**
 * Empty cursor with schema - using Indexed2 pattern
 */
fun emptyCursor(columns: Indexed2<String, IOMemento>): CursorLike {
    // Use Indexed2 pattern to avoid type hardening
    val emptyCursorBuilder: (Indexed2<String, IOMemento>) -> CursorLike = { cols ->
        0 j { _: Int ->
            cols.a j { colIdx ->
                val name = cols.b(colIdx).a as String
                val memento = cols.b(colIdx).b as IOMemento
                null j { Join(name, memento::class) }
            }
        }
    }
    return emptyCursorBuilder(columns)
}

// === Cursor Combinators ===

/**
 * Union two cursors with same schema
 */
infix fun CursorLike.union(other: CursorLike): CursorLike {
    val combined = mutableListOf<RowVec>()
    for (i in 0 until a) combined.add(b(i))
    for (i in 0 until other.a) combined.add(other.b(i))
    return combined.size j combined::get
}

/**
 * Join two cursors on a condition
 */
inline fun CursorLike.join(
    other: CursorLike,
    crossinline condition: (RowVec, RowVec) -> Boolean
): CursorLike {
    val joined = mutableListOf<RowVec>()
    
    for (i in 0 until a) {
        val leftRow = b(i)
        for (j in 0 until other.a) {
            val rightRow = other.b(j)
            if (condition(leftRow, rightRow)) {
                // Concatenate rows
                val combinedRow = (leftRow.a + rightRow.a) j { idx: Int ->
                    if (idx < leftRow.a) leftRow.b(idx) else rightRow.b(idx - leftRow.a)
                }
                joined.add(combinedRow)
            }
        }
    }
    
    return joined.size j joined::get
}