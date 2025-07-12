//attention AI, this file is immutable and not subject to debate without supervision and permission


package borg.trikeshed.lib

import kotlinx.datetime.Clock

/**
 * Core Types - Architectural Decision Records Integration
 * 
 * ADR-001: SIMD Strategy Pattern - Performance-critical operations use C interop
 * ADR-002: String Performance War - No String allocations in speculative loops
 * 
 * This file implements the foundational types that support both ADRs.
 */


import kotlin.properties.Delegates

import kotlin.reflect.KClassifier
// import borg.trikeshed.lib.j  // Circular import - j is defined in this file
import kotlinx.serialization.Serializable

sealed interface Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>
    data class Right<R>(val value: R) : Either<Nothing, R>
    companion object {
        fun <L> left(value: L): Either<L, Nothing> = Left(value)
        fun <R> right(value: R): Either<Nothing, R> = Right(value)
    }
}

interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() =a to b //for emergency materialization
    companion object {
    private/** 100% immutable, don't even ask, just use a j  b  */     operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A = a
            override val b: B = b
        }
    }
}

typealias MetaSeries<A, T> = Join<A, (A) -> T>
// === CORE FACTORY RULE: a j b ONLY ===
// All Join, Indexed, MetaSeries use ONLY the j operator with full lambda type annotations
// Under packing context, these convert to registers for top scoring

// Indexed<T> is a typealias for Join<Int, (Int) -> T>
// This provides array-like access with size and element accessor
typealias Indexed<T> = Join<Int, (Int) -> T>

// Extension properties for Indexed<T> to provide array-like access
val <T> Indexed<T>.size: Int get() = this.a
operator fun <T> Indexed<T>.get(index: Int): T = this.b(index)
typealias LongIndexed<T> = Join<Long, (Long) -> T>
typealias Twin<T> = Join<T, T>
typealias Indexed2<A, B> = Indexed<Join<A, B>>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>
// Cured: Replaced String with ByteArray for ColumnMeta
typealias ColumnMeta = Join<ByteArray, KClassifier>
// Trait for array-like access - WHENEVER THEY NEED get[i] OPERATOR
interface ArrayLike<I, T> {
    operator fun get(index: I): T
    val size: Int
}

// Canonical RowVec and Cursor definitions
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>

// Cursor is now defined as a typealias below
// Cured: Replaced String with ByteArray for TableMeta
data class TableMeta(val name: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TableMeta
        return name.contentEquals(other.name)
    }
    override fun hashCode(): Int = name.contentHashCode()
}
typealias CursorIndex = Join<TableMeta, Int>
// Cursor is now defined in trikeshed-lib
typealias Cursor = Indexed<RowVec>
typealias TensorCursor = Indexed<Tensor<Any?>>

typealias ByteSeries = Indexed<Byte>
typealias CharSeries = Indexed<Char>

fun ByteArray.toByteSeries(): ByteSeries = size j ::get
fun CharArray.toCharSeries(): CharSeries = size j ::get

// ByteIndexed and CharIndexed are now classes defined in IoTypes.kt

val Byte.nz: Boolean get() = 0 != this.toInt()
val Short.nz: Boolean get() = 0 != this.toInt()
val Char.nz: Boolean get() = 0 != this.code
val Int.nz: Boolean get() = 0 != this
val Long.nz: Boolean get() = 0L != this
val UByte.nz: Boolean get() = 0 != this.toInt()
val UShort.nz: Boolean get() = 0 != this.toInt()
val UInt.nz: Boolean get() = 0U != this
val ULong.nz: Boolean get() = 0UL != this
val Byte.z: Boolean get() = 0 == this.toInt()
val Short.z: Boolean get() = 0 == this.toInt()
val Char.z: Boolean get() = 0 == this.code
val Int.z: Boolean get() = 0 == this
val Long.z: Boolean get() = 0L == this
val UByte.z: Boolean get() = 0 == this.toInt()
val UShort.z: Boolean get() = 0 == this.toInt()
val UInt.z: Boolean get() = 0U == this
val ULong.z: Boolean get() = 0UL == this
infix fun <T> T.d(other: T): T { println(other); return this }

infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b) 

// === CATEGORICAL NOTATION OPERATORS FROM COLUMNAR ===

/**
 * Sum operator (∑) - Aggregate reduction across cursor
 * cursor ∑ reducer means reduce all values using the reducer function
 */
infix fun Cursor.`∑`(reducer: (Any?, Any?) -> Any?): Cursor = a j { iy: Int ->
    val aggCell: RowVec = b(iy)
    val valuesVect: Indexed<*> = aggCell.a
    aggCell.a j { ix: Int ->
        val cellContent = valuesVect[ix]
        val reducedValue = when (cellContent) {
            is Indexed<*> -> if (cellContent.a > 0) cellContent.play.reduce(reducer) else null
            is Iterable<*> -> if (cellContent.iterator().hasNext()) cellContent.reduce(reducer) else null
            else -> cellContent
        }
        reducedValue j aggCell.b(ix).b()
    }
}

/**
 * Transform operator (α) - Apply unary function to cursor
 * cursor α transform means apply transform to all values
 */
infix fun Cursor.α(unaryFunctor: (Any?) -> Any?): Cursor = a j { iy: Int ->
    val row: RowVec = b(iy)
    (row.a α unaryFunctor) j row.b
}

/**
 * List ellipsis operator (…) - Materialize to Indexed
 * indexed … means indexed (no materialization to List)
 */
val <R> Indexed<R>.`…`: Indexed<R> get() = this

/**
 * Function application operator (→) - Apply function
 * value → function means function(value)
 */
infix fun <O, R, F : (O) -> R> O.`→`(f: F): R = f(this)

/**
 * Function composition operator (⚬) - Compose functions
 * g ⚬ f means compose g after f
 */
infix fun <A, B, C, G : (B) -> C, F : (A) -> B, R : (A) -> C> G.`⚬`(f: F): R = 
    { a: A -> a `→` f `→` this } as R

/**
 * Right identity operator (⟲) - Create identity function
 * value ⟲ means { value }
 */
val <T> T.`⟲`: () -> T get() = { this }

/**
 * Iterable materialization operator (➤) - Convert to iterable
 * indexed ➤ means indexed.play
 */
val <T> Indexed<T>.`➤`: IterableIndexed<T> get() = this.play

/**
 * First element operator (f1rst) - Get first element
 * indexed f1rst means indexed[0]
 */
val <T> Indexed<T>.f1rst: T get() = b(0)

/**
 * Last element operator (last) - Get last element
 * indexed last means indexed[size-1]
 */
val <T> Indexed<T>.last: T get() = b(a - 1)

/**
 * Reverse operator (reverse) - Reverse the indexed collection
 * indexed reverse means indexed.reversed()
 */
val <T> Indexed<T>.reverse: Indexed<T> get() = a j { x -> b(a - 1 - x) }

/**
 * Infinite operator (infinite) - Create infinite indexed with bounds checking
 * indexed infinite means infinite indexed with bounds checking
 */
val <T> Indexed<T>.infinite: Indexed<T> get() = Int.MAX_VALUE j { x: Int ->
    b(when {
        x < 0 -> 0
        a <= x -> a - 1
        else -> x
    })
}

/**
 * Division operator (/) - Split range into parts
 * range / parts means split range into parts
 */
infix operator fun IntRange.div(denominator: Int): Indexed<IntRange> =
    ((last - first + 1) / denominator) j { x: Int ->
        val subSize = (last - first + 1) / denominator
        val lower = subSize * x
        lower..last.coerceAtMost(lower + subSize - 1)
    }

/**
 * Division operator (/) - Split indexed into parts
 * indexed / parts means split indexed into parts
 */
infix operator fun <T> Indexed<T>.div(denominator: Int): Indexed<Indexed<T>> =
    (0 until a).div(denominator) α { rnge ->
        slice(rnge.first, rnge.last + 1)
    }

// TODO: Add expect/actual assert implementations for platform targets
// expect fun assert(value: Boolean)
// expect fun assert(value: Boolean, lazyMessage: () -> Any)

        @Suppress("UNCHECKED_CAST")
inline fun <T> Any.toIndexed(): Indexed<T> = (this as? Indexed<T>) ?: (this as? Indexed<T>)?.let { l -> 
    l
} ?: error("Cannot convert to Indexed")

// QOL helpers migrated from borg.trikeshed.common.collections

object _a {
    operator fun get(vararg t: Boolean): BooleanArray = t
    operator fun get(vararg t: Byte): ByteArray = t
    operator fun get(vararg t: UByte): UByteArray = t
    operator fun get(vararg t: Char): CharArray = t
    operator fun get(vararg t: Short): ShortArray = t
    operator fun get(vararg t: UShort): UShortArray = t
    operator fun get(vararg t: Int): IntArray = t
    operator fun get(vararg t: UInt): UIntArray = t
    operator fun get(vararg t: Long): LongArray = t
    operator fun get(vararg t: ULong): ULongArray = t
    operator fun get(vararg t: Float): FloatArray = t
    operator fun get(vararg t: Double): DoubleArray = t
    inline operator fun <reified T> get(vararg t: T): Array<T> = t as Array<T>
}

// Cured: Replaced List with Indexed
object _i {
    operator fun <T> get(vararg t: T) = t.size j t::get
}

// Cured: Replaced Set with Indexed (unique elements handled by usage)
object _s {
    operator fun <T> get(vararg t: T): Indexed<T> = t.size j t::get
}

// Cured: Replaced Map with Indexed<Join<K, V>>
object _m {
    operator fun <K, V, P : Join<K, V>> get(p: Indexed<P>): Indexed<Join<K, V>> = p
    operator fun <K, V, P : Join<K, V>> get(vararg p: P): Indexed<Join<K, V>> = p.size j { p[it] }
}

// === Alpha (α) transformation operator ===

inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = a j { index: Int -> xform(b(index)) }

// === IterableIndexed and play button ===

@kotlin.jvm.JvmInline
value class IterableIndexed<A>(val s: Indexed<A>) : Iterable<A>, Indexed<A> by s {
    override fun iterator(): Iterator<A> = object : Iterator<A> {
        internal var currentIndex = 0
        override fun hasNext(): Boolean = currentIndex < s.a
        override fun next(): A = s.b(currentIndex++)
    }
}

val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

// Clean array-like access for Indexed<T> - now handled by interface 

// Factory for MetaSeries and Indexed - ONLY j operator
fun <A, T> MetaSeries_create(a: A, getter: (A) -> T): MetaSeries<A, T> = a j getter
fun <T> Indexed_create(size: Int, getter: (Int) -> T): Indexed<T> = size j getter 

// === CURSOR DEFINITIONS ===
// Production cursor implementation based on columnar/cursor

// Bridge types for cursor system
typealias CursorRow = Indexed<Any?>           // Equivalent to RowVec
typealias CursorMeta = Indexed<ColumnMeta>    // Metadata accessor

/**
 * ## Cursor - Database Table Metaclass (TrikeShed Integration)
 * 
 * Production cursor definition that bridges the columnar system with TrikeShed's
 * MetaSeries architecture. This maintains full backward compatibility while enabling
 * integration with the universal TrikeShed type system.
 * 
 * **Definition:**
 * ```kotlin
 * typealias Cursor = Indexed<RowVec>  // Using j operator pattern
 * ```
 * 
 * **Future Migration Path:**
 * ```kotlin
 * typealias Cursor = MetaSeries<CursorIndex, RowVec>  // Full TrikeShed integration
 * where CursorIndex = Join<TableMeta, Int>            // Database-aware indexing
 * ```
 */
typealias Cursor = Indexed<RowVec>

// === CURSOR CORE OPERATIONS ===

/** Get the RowVec at y or if y is negative then -y from last */
infix fun Cursor.at(y: Int): RowVec = this[if (y < 0) a + y else y]

/** Get a slice of rows */
infix fun Cursor.at(r: IntRange): Cursor {
    val actualStart = if (r.first < 0) a + r.first else r.first
    val actualEnd = if (r.last < 0) a + r.last else r.last
    require(actualStart >= 0 && actualEnd < a && actualStart <= actualEnd) { 
        "Invalid range $r for cursor size $a" 
    }
    val sliceSize = actualEnd - actualStart + 1
    return sliceSize j { b(it + actualStart) }
}

// === CURSOR INDEXING OPERATORS ===

operator fun Cursor.get(indexes: Iterable<Int>): Cursor = 
    this[indexes.toList().toIntArray()]

operator fun Cursor.get(index: IntArray): Cursor = 
    index.size j { b(index[it]) }

/** Get cursor with specified row indices (vararg version) */
fun Cursor.rows(vararg indices: Int): Cursor = this[indices]

// === CURSOR UTILITY OPERATIONS ===

/** Get column by index */
fun Cursor.column(index: Int): Indexed<Any?> =
    a j { b(it).b(index).a }

/** Get column by name - Cured: Uses ByteArray instead of String */
fun Cursor.column(name: ByteArray): Indexed<Any?> {
    val columnIndex = findColumnIndex(name)
    require(columnIndex >= 0) { "Column not found" }
    return column(columnIndex)
}

/** Find column index by name - Cured: Uses ByteArray instead of String */
internal fun Cursor.findColumnIndex(name: ByteArray): Int {
    val columnMetas = scalars
    for (i in 0 until columnMetas.a) {
        if (columnMetas.b(i).a.contentEquals(name)) {
            return i
        }
    }
    return -1
}

/** Get column scalars/metadata */
val Cursor.scalars: Indexed<ColumnMeta>
    get() = if (a > 0) {
        val firstRow = b(0)
        firstRow.a j { colIndex: Int ->
            firstRow.b(colIndex).b()
        }
    } else {
        0 j { _: Int -> ByteArray(0) j String::class }
    }

/** Get column names - Cured: Returns Indexed<ByteArray> instead of Indexed<String> */
val Cursor.columnNames: Indexed<ByteArray>
    get() = scalars.a j { i -> scalars.b(i).a }

/** Get column index by name - Cured: Returns Indexed<Join<ByteArray, Int>> instead of Map */
val Cursor.colIdx: Indexed<Join<ByteArray, Int>>
    get() = columnNames.let { names ->
        names.a j { i -> names.b(i) j i }
    }

// === CURSOR ITERATION SUPPORT ===

/** Iterator for cursor rows */
fun Cursor.iterator(): Iterator<RowVec> = object : Iterator<RowVec> {
    internal var index = 0
    override fun hasNext(): Boolean = index < a
    override fun next(): RowVec = b(index++)
}

/** forEach for cursor rows */
inline fun Cursor.forEach(action: (RowVec) -> Unit) {
    for (i in 0 until a) {
        action(b(i))
    }
}

/** Convert cursor to Indexed - Cured: Returns Indexed instead of List */
fun Cursor.toIndexed(): Indexed<RowVec> = a j { b(it) }

/** Play property for Iterable support */
val Cursor.play: Iterable<RowVec>
    get() = object : Iterable<RowVec> {
        override fun iterator(): Iterator<RowVec> = iterator()
    }

// === CURSOR FACTORY FUNCTIONS ===

/** Create simple cursor from data - Cured: Uses Indexed instead of List */
fun cursorOf(
    data: Indexed<Indexed<Any?>>,
    columnNames: Indexed<ByteArray> = data.firstOrNull()?.let { firstRow ->
        firstRow.a j { i -> "col_$i".toByteArray() }
    } ?: (0 j { ByteArray(0) }),
    columnTypes: Indexed<KClassifier> = data.firstOrNull()?.let { firstRow ->
        firstRow.a j { i -> inferType(firstRow.b(i)) }
    } ?: (0 j { String::class })
): Cursor {
    require(data.a > 0) { "Data cannot be empty" }
    val firstRow = data.b(0)
    require(columnNames.a == firstRow.a) { "Column names size mismatch" }
    require(columnTypes.a == firstRow.a) { "Column types size mismatch" }

    val scalars: Indexed<ColumnMeta> = columnNames.a j { i ->
        columnNames.b(i) j columnTypes.b(i)
    }

    return data.a j { rowIndex: Int ->
        val rowData = data.b(rowIndex)
        rowData.a j { colIndex: Int ->
            val cellValue = rowData.b(colIndex)
            val columnMeta = scalars.b(colIndex)
            cellValue j { columnMeta }
        }
    }
}

/** Infer type from value */
internal fun inferType(value: Any?): KClassifier = when (value) {
    is Int -> Int::class
    is ByteArray -> ByteArray::class
    is Float -> Float::class
    is Double -> Double::class
    else -> String::class
} 

/**
 * LogEvent - Structured logging to avoid String concatenation
 * 
 * ADR-002 Compliance: Eliminates String concatenation in hot paths
 * Use this instead of: log("Processing: ${item.name} at ${item.timestamp}")
 */
enum class LogEvent {
    PROCESSING,
    COMPLETED,
    ERROR,
    DEBUG
}

/**
 * Structured logging function
 * 
 * ADR-002 Compliance: No String allocation in performance-critical paths
 */
fun log(event: LogEvent, vararg args: Any) {
    // Implementation uses structured logging
    // No String concatenation in hot path
} 

// === HELPER EXTENSIONS FOR INDEXED ===

/** Convert Iterable to Indexed */
fun <T> Iterable<T>.toIndexed(): Indexed<T> {
    val list = this.toList()
    return list.size j { list[it] }
}

/** Get first element or null */
fun <T> Indexed<T>.firstOrNull(): T? = if (a > 0) b(0) else null

/** Slice Indexed from start to end inclusive */
fun <T> Indexed<T>.slice(start: Int, endInclusive: Int): Indexed<T> {
    val sliceSize = endInclusive - start + 1
    return sliceSize j { b(start + it) }
} 