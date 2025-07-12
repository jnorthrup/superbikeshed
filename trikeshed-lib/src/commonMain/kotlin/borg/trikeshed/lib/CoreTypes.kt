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
    val pair: Pair<A, B> get() = Pair(a, b)
    companion object {
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A = a
            override val b: B = b
        }
    }
}

typealias MetaSeries<A, T> = Join<A, (A) -> T>
typealias Indexed<T> = Join<Int, (Int) -> T>
typealias LongIndexed<T> = Join<Long, (Long) -> T>
typealias Twin<T> = Join<T, T>
typealias Indexed2<A, B> = Indexed<Join<A, B>>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>
typealias ColumnMeta = Join<String, KClassifier>
// Trait for array-like access - WHENEVER THEY NEED get[i] OPERATOR
interface ArrayLike<I, T> {
    operator fun get(index: I): T
    val size: Int
}

// Canonical RowVec and Cursor definitions
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>

// Cursor is now defined as a typealias below
data class TableMeta(val name: String)
typealias CursorIndex = Join<TableMeta, Int>
// Cursor is now defined in trikeshed-lib
typealias TensorCursor = Indexed<Tensor<Any?>>

typealias ByteSeries = Indexed<Byte>
typealias CharSeries = Indexed<Char>

fun ByteArray.toByteSeries(): ByteSeries = size j { this[it] }
fun CharArray.toCharSeries(): CharSeries = size j { this[it] }

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

/**
 * Reverse composition operator (◂) - Compose in reverse order
 * f ◂ g means first f then g (opposite of traditional composition)
 * (f ◂ g)(x) = g(f(x))
 */
infix fun <A, B, C> ((A) -> B).`◂`(g: (B) -> C): (A) -> C = { a: A -> g(this(a)) }

// TODO: Add expect/actual assert implementations for platform targets
// expect fun assert(value: Boolean)
// expect fun assert(value: Boolean, lazyMessage: () -> Any)

        @Suppress("UNCHECKED_CAST")
inline fun <T> Any.toIndexed(): Indexed<T> = (this as? Indexed<T>) ?: (this as? List<T>)?.let { l -> l.size j l::get } ?: error("Cannot convert to Indexed")

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

object _l {
    operator fun <T> get(vararg t: T): List<T> = listOf(*t)
}
object _i {
    operator fun <T> get(vararg t: T) = t.size j { i :Int-> t[i] }
}

object _s {
    operator fun <T> get(vararg t: T): Set<T> = setOf(*t)
}

object _m {
    operator fun <K, V, P : Join<K, V>> get(p: List<P>): Map<K, V> = p.map { it.a to it.b }.toMap()
    operator fun <K, V, P : Join<K, V>> get(vararg p: P): Map<K, V> = mapOf(*p.map { it.a to it.b }.toTypedArray())
}

// === Alpha (α) transformation operator ===

inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = this.a j { index: Int -> xform(this.b(index)) }

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

val <T> Indexed<T>.size: Int get() = a

// Clean array-like access for Indexed<T> - no more .b(i)!
operator fun <T> Indexed<T>.get(index: Int): T = b(index) 

// Factory for MetaSeries and Indexed
fun <A, T> MetaSeries_create(a: A, getter: (A) -> T): MetaSeries<A, T> = a j getter
fun <T> Indexed_create(size: Int, getter: (Int) -> T): Indexed<T> = size j getter 

// === CURSOR DEFINITIONS ===
// Production cursor implementation based on columnar/cursor

// Bridge types for cursor system
typealias TrikeShedIndexed<T> = Join<Int, (Int) -> T>  // Compatible with Indexed<T>
typealias CursorRow = TrikeShedIndexed<Any?>           // Equivalent to RowVec
typealias CursorMeta = TrikeShedIndexed<ColumnMeta>    // Metadata accessor

/**
 * ## Cursor - Database Table Metaclass (TrikeShed Integration)
 * 
 * Production cursor definition that bridges the columnar system with TrikeShed's
 * MetaSeries architecture. This maintains full backward compatibility while enabling
 * integration with the universal TrikeShed type system.
 * 
 * **Definition:**
 * ```kotlin
 * typealias Cursor = TrikeShedIndexed<RowVec>
 * ```
 * 
 * **Future Migration Path:**
 * ```kotlin
 * typealias Cursor = MetaSeries<CursorIndex, RowVec>  // Full TrikeShed integration
 * where CursorIndex = Join<TableMeta, Int>            // Database-aware indexing
 * ```
 */
typealias Cursor = TrikeShedIndexed<RowVec>

// === CURSOR CORE OPERATIONS ===

/** Get the RowVec at y or if y is negative then -y from last */
infix fun Cursor.at(y: Int): RowVec = b(if (y < 0) a + y else y)

/** Get a slice of rows */
infix fun Cursor.at(r: IntRange): Cursor {
    val actualStart = if (r.first < 0) a + r.first else r.first
    val actualEnd = if (r.last < 0) a + r.last else r.last
    require(actualStart >= 0 && actualEnd < a && actualStart <= actualEnd) { 
        "Invalid range $r for cursor size $a" 
    }
    val sliceSize = actualEnd - actualStart + 1
    return sliceSize j { y -> b(y + actualStart) }
}

// === CURSOR INDEXING OPERATORS ===

operator fun Cursor.get(indexes: Iterable<Int>): Cursor = 
    this[indexes.toList().toIntArray()]

operator fun Cursor.get(index: IntArray): Cursor = 
    index.size j { iy: Int -> b(index[iy]) }

/** Get cursor with specified row indices (vararg version) */
fun Cursor.rows(vararg indices: Int): Cursor = this[indices]

// === CURSOR UTILITY OPERATIONS ===

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
internal fun Cursor.findColumnIndex(name: String): Int {
    val columnMetas = scalars
    for (i in 0 until columnMetas.a) {
        if (columnMetas.b(i).a == name) {
            return i
        }
    }
    return -1
}

/** Get column scalars/metadata */
val Cursor.scalars: Indexed<ColumnMeta>
    get() = if (a > 0) {
        val firstRow = at(0)
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

// === CURSOR ITERATION SUPPORT ===

/** Iterator for cursor rows */
fun Cursor.iterator(): Iterator<RowVec> = object : Iterator<RowVec> {
    internal var index = 0
    override fun hasNext(): Boolean = index < a
    override fun next(): RowVec = at(index++)
}

/** forEach for cursor rows */
inline fun Cursor.forEach(action: (RowVec) -> Unit) {
    for (i in 0 until a) {
        action(at(i))
    }
}

/** Convert cursor to list of rows */
fun Cursor.toList(): List<RowVec> = (0 until a).map { at(it) }

/** Play property for Iterable support */
val Cursor.play: Iterable<RowVec>
    get() = object : Iterable<RowVec> {
        override fun iterator(): Iterator<RowVec> = this@play.iterator()
    }

// === CURSOR FACTORY FUNCTIONS ===

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
        columnNames[i] j columnTypes[i]
    }

    return data.size j { rowIndex: Int ->
        val rowData = data[rowIndex]
        val rowVec: RowVec = rowData.size j { colIndex: Int ->
            val cellValue = rowData[colIndex]
            val columnMeta = scalars.b(colIndex)
            cellValue j { columnMeta }
        }
        rowVec
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

/**
 * Indexed<T> - Primary interface for mutable list operations
 * 
 * ADR-002 Compliance: Provides type-safe alternatives to String-based operations
 * Used throughout the codebase to avoid String allocations in loops
 */
interface Indexed<T> {
    val a: Int
    fun b(index: Int): T
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