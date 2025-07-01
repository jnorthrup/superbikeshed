@file:Suppress("NonAsciiCharacters", "UNCHECKED_CAST", "FunctionName")

package borg.trikeshed.lib

import borg.trikeshed.cursor.Cursor
import kotlinx.coroutines.*

/**
 * Columnar Extensions - Categorical expressions and cursor operations
 * 
 * Brings over the essential patterns from columnar without higher-arity tuples.
 * Focuses on Join<A,B> (Pair semantics) and Indexed<T> operations.
 */

// === Cursor Type Definitions ===

typealias RowVec = Indexed<Join<Any?, TypeMemento>>
typealias CursorLike = Indexed<RowVec>
typealias TypeMemento = Join<IOMemento, String?>

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
 * Right identity operator (⟲) - Returns a function that returns the value
 * The right identity element in functional composition
 */
val <T> T.`⟲`: () -> T get() = { this }


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
        firstRow.a j { i -> firstRow.b(i).b.b }
    } else {
        0 j { null }
    }

/**
 * Get column types from cursor
 */
val CursorLike.columnTypes: Indexed<IOMemento>
    get() = if (a > 0) {
        val firstRow = b(0)
        firstRow.a j { i -> firstRow.b(i).b.a }
    } else {
        0 j { IOMemento.IoInt }
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
    val nameToIndex = columnNames.mapIndexed { i, name -> name to i }.toMap()
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
inline fun CursorLike.mapRows(transform: (RowVec) -> RowVec): CursorLike = 
    a j { i -> transform(b(i)) }

/**
 * Group by column index
 */
fun CursorLike.groupBy(columnIndex: Int): Indexed<Join<Any?, CursorLike>> {
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
 * Aggregate grouped data
 */
inline fun <T> Indexed<Join<Any?, CursorLike>>.aggregate(
    crossinline aggregator: (CursorLike) -> T
): Indexed<Join<Any?, T>> = a j { i ->
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
 * Calculate network coordinates for serialization
 */
fun networkCoords(
    types: Indexed<TypeMemento>,
    defaultVarcharSize: Int = 255,
    varcharSizes: Map<Int, Int> = emptyMap()
): Indexed<Join<Int, Int>> {
    var offset = 0
    return types.a j { i ->
        val memento = types.b(i).a
        val size = when (memento) {
            is IOMemento.IoVarchar -> varcharSizes[i] ?: defaultVarcharSize
            else -> memento.networkSize ?: 0
        }
        val start = offset
        offset += size
        start j offset
    }
}

// === Async Cursor Operations ===

/**
 * Process cursor rows in parallel batches
 */
suspend fun <T> CursorLike.parallelMap(
    batchSize: Int = 100,
    transform: suspend (RowVec) -> T
): Indexed<T> = coroutineScope {
    val results = mutableListOf<Deferred<List<T>>>()
    
    for (start in 0 until a step batchSize) {
        val end = minOf(start + batchSize, a)
        val batch = async {
            (start until end).map { i ->
                transform(b(i))
            }
        }
        results.add(batch)
    }
    
    val allResults = results.flatMap { it.await() }
    allResults.size j allResults::get
}

/**
 * Stream cursor rows
 */
fun CursorLike.asFlow(): kotlinx.coroutines.flow.Flow<RowVec> = 
    kotlinx.coroutines.flow.flow {
        for (i in 0 until a) {
            emit(b(i))
        }
    }

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
 * Get typed value from row
 */
@Suppress("UNCHECKED_CAST")
operator fun <T> RowVec.get(column: Column<T>): T? =
    if (column.index < a) b(column.index).a as? T else null

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
        is IOMemento.IoBoolean -> 1
        is IOMemento.IoByte -> 1
        is IOMemento.IoShort -> 2
        is IOMemento.IoInt -> 4
        is IOMemento.IoLong -> 8
        is IOMemento.IoFloat -> 4
        is IOMemento.IoDouble -> 8
        is IOMemento.IoChar -> 2
        is IOMemento.IoString -> null // Variable length
        is IOMemento.IoVarchar -> null // Variable length
        is IOMemento.IoLocalDate -> 8
        is IOMemento.IoLocalDateTime -> 16
        is IOMemento.IoInstant -> 8
        else -> null
    }

/**
 * Check if type is numeric
 */
val IOMemento.isNumeric: Boolean
    get() = when (this) {
        is IOMemento.IoByte,
        is IOMemento.IoShort,
        is IOMemento.IoInt,
        is IOMemento.IoLong,
        is IOMemento.IoFloat,
        is IOMemento.IoDouble -> true
        else -> false
    }

// === Cursor Builders ===

/**
 * Build a cursor from data
 */
fun buildCursor(
    columns: Indexed<Join<String, IOMemento>>,
    data: Indexed<Indexed<Any?>>
): CursorLike = data.a j { rowIdx ->
    val rowData = data.b(rowIdx)
    columns.a j { colIdx ->
        val value = if (colIdx < rowData.a) rowData.b(colIdx) else null
        val column = columns.b(colIdx)
        value j (column.b j column.a)
    }
}

/**
 * Empty cursor with schema
 */
fun emptyCursor(columns: Indexed<Join<String, IOMemento>>): CursorLike =
    0 j { columns.a j { colIdx ->
        val column = columns.b(colIdx)
        null j (column.b j column.a)
    }}

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
                val combinedRow = (leftRow.a + rightRow.a) j { idx ->
                    if (idx < leftRow.a) leftRow.b(idx) else rightRow.b(idx - leftRow.a)
                }
                joined.add(combinedRow)
            }
        }
    }
    
    return joined.size j joined::get
}