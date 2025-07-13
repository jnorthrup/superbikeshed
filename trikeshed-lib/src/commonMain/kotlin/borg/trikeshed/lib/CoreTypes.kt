//ATTENTION AI, this file is immutable and not subject to debate without supervision and permission 

package borg.trikeshed.lib

import kotlinx.datetime.Clock
import kotlin.properties.Delegates
import kotlin.reflect.KClassifier
import kotlinx.serialization.Serializable

/**
 * Core Types - Architectural Decision Records Integration
 * 
 * ADR-001: SIMD Strategy Pattern - Performance-critical operations use C interop
 * ADR-002: String Performance War - No String allocations in speculative loops
 * 
 * This file implements the foundational types that support both ADRs.
 * 
 * **Pristine Columnar Patterns Applied:**
 * - Simple Join<A,B> interface without complex recursive aliases
 * - Clean Indexed<T> = Join<Int, (Int) -> T> pattern
 * - No circular type references that break Kotlin compiler
 * - Forward-compatible with MetaSeries architecture
 */

sealed interface Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>
    data class Right<R>(val value: R) : Either<Nothing, R>
    companion object {
        fun <L> left(value: L): Either<L, Nothing> = Left(value)
        fun <R> right(value: R): Either<Nothing, R> = Right(value)
    }
}

/**
 * Core composition operator - Universal binary composition
 * Pristine Columnar pattern: simple interface without complex aliases
 */
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = a to b //for emergency materialization
 }

/**
 * Universal indexed access - Foundation for all indexed types
 * Pristine Columnar pattern: MetaSeries<A, T> = Join<A, (A) -> T>
 */
typealias MetaSeries<A, T> = Join<A, (A) -> T>

// === CORE FACTORY RULE: a j b ONLY ===
// All Join, Indexed, MetaSeries use ONLY the j operator with full lambda type annotations
// Under packing context, these convert to registers for top scoring

/**
 * Indexed<T> - Int-indexed sequences (was Series)
 * Pristine Columnar pattern: Indexed<T> = Join<Int, (Int) -> T>
 */
typealias Indexed<T> = Join<Int, (Int) -> T>

// Extension properties for Indexed<T> to provide array-like access
val <T> Indexed<T>.size: Int get() = this.a
operator fun <T> Indexed<T>.get(index: Int): T = this.b(index)
fun <T> Indexed<T>.isEmpty(): Boolean = this.a == 0
fun <T> Indexed<T>.getOrNull(index: Int): T? = if (index in 0 until this.a) this.b(index) else null

/**
 * Core type aliases following pristine Columnar patterns
 */
typealias LongIndexed<T> = Join<Long, (Long) -> T>
typealias Twin<T> = Join<T, T>
typealias Indexed2<A, B> = Indexed<Join<A, B>>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>


/**
 * Trait for array-like access - WHENEVER THEY NEED get[i] OPERATOR
 */
interface ArrayLike<I, T> {
    operator fun get(index: I): T
    val size: Int
}

/**
 * Canonical RowVec and Cursor definitions
 * Pristine Columnar pattern: Simple composition without complex recursion
 */
typealias RowVec = Join<Int, Indexed<Any?>>

/**
 * TableMeta - Cured: Replaced String with ByteArray for ADR-002 compliance
 */
data class TableMeta(val name: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TableMeta) return false
        return name.contentEquals(other.name)
    }
    override fun hashCode(): Int = name.contentHashCode()
}

/**
 * Cursor types following pristine Columnar patterns
 */
typealias CursorIndex = Join<TableMeta, Int>

/**
 * Series types for byte and character data
 */
typealias ByteSeries = Indexed<Byte>
typealias CharSeries = Indexed<Char>

/**
 * Conversion functions for arrays to series
 */
fun ByteArray.toByteSeries(): ByteSeries = size j ::get
fun CharArray.toCharSeries(): CharSeries = size j ::get

// ByteIndexed and CharIndexed are now classes defined in IoTypes.kt

/**
 * Zero/non-zero extensions for numeric types
 */
val Byte.nz: Boolean get() = !z
val Short.nz: Boolean get() = !z
val Char.nz: Boolean get() = !z
val Int.nz: Boolean get() = !z
val Long.nz: Boolean get() = !z
val UByte.nz: Boolean get() = !z
val UShort.nz: Boolean get() = !z
val UInt.nz: Boolean get() = !z
val ULong.nz: Boolean get() = !z
val Byte.z: Boolean get() = 0 == this.toInt()
val Short.z: Boolean get() = 0 == this.toInt()
val Char.z: Boolean get() = 0 == this.code
val Int.z: Boolean get() = 0 == this
val Long.z: Boolean get() = 0L == this
val UByte.z: Boolean get() = 0 == this.toInt()
val UShort.z: Boolean get() = 0 == this.toInt()
val UInt.z: Boolean get() = 0U == this
val ULong.z: Boolean get() = 0UL == this

/**
 * Debug operator
 */
infix fun <T> T.d(other: T): T { println(other); return this }

/**
 * Core composition operator - j operator
 */
infix fun <A, B> A.j(b: B): Join<A, B> = object : Join<A, B> {
    override val a: A = this@j
    override val b: B = b
}

// === CATEGORICAL NOTATION OPERATORS FROM COLUMNAR ===

/**
 * Sum operator (∑) - Aggregate reduction across cursor
 * cursor ∑ reducer means reduce all values using the reducer function
 */
infix fun Cursor.`∑`(reducer: (Any?, Any?) -> Any?): Cursor {
    if (isEmpty()) return emptyIndexed()

    val firstRow = this[0]
    val numColumns = firstRow.size

    val aggregatedRowContent: Indexed<Any?> = makeIndexed(numColumns) { colIndex ->
        val columnValues: Indexed<Any?> = makeIndexed(size) { rowIndex ->
            this[rowIndex].b[colIndex]
        }
        if (!columnValues.isEmpty()) {
            columnValues.play.reduce(reducer)
        } else {
            null
        }
    }
    val aggregatedRow = makeJoin(firstRow.a, aggregatedRowContent)
    return makeIndexed(1) { aggregatedRow }
}

/**
 * Transform operator (α) - Apply unary function to cursor
 * cursor α transform means apply transform to all values
 */
infix fun Cursor.α(unaryFunctor: (Any?) -> Any?): Cursor = a j { iy: Int ->
    val row: RowVec = b(iy)
    row.a j (row.b.a j { i: Int -> unaryFunctor(row.b[i]) })
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
infix fun <A, B, C> ((B) -> C).`⚬`(f: (A) -> B): (A) -> C = { a -> this(f(a)) }

/**
 * Reverse operator (◂) - Reverse direction
 * a ◂ b means b j a (reverse composition)
 */
infix fun <A, B> A.`◂`(b: B): Join<B, A> = b j this

/**
 * Infinite operator (⟲) - Infinite sequence
 * n ⟲ generator means infinite sequence starting at n
 */
infix fun Int.`⟲`(generator: (Int) -> Any?): Indexed<Any?> = Int.MAX_VALUE j generator

/**
 * Forward operator (➤) - Forward sequence
 * n ➤ generator means sequence from n forward
 */
infix fun Int.`➤`(generator: (Int) -> Any?): Indexed<Any?> = Int.MAX_VALUE j { i -> generator(this + i) }

/**
 * First operator (f1rst) - Get first element
 * indexed f1rst means first element
 */
val <T> Indexed<T>.f1rst: T get() = this[0]

/**
 * Last operator (last) - Get last element
 * indexed last means last element
 */
val <T> Indexed<T>.last: T get() = this[a - 1]

/**
 * Reverse operator (reverse) - Reverse sequence
 * indexed reverse means reversed sequence
 */
val <T> Indexed<T>.reverse: Indexed<T> get() = a j { i -> this[a - 1 - i] }

/**
 * Infinite operator (infinite) - Infinite sequence
 * indexed infinite means infinite repetition
 */
val <T> Indexed<T>.infinite: Indexed<T> get() = Int.MAX_VALUE j { i -> this[i % a] }

/**
 * Division operator (/) - Slice sequence
 * indexed / n means every nth element
 */
infix fun <T> Indexed<T>.`/`(n: Int): Indexed<T> = (a / n) j { i -> this[i * n] }

// === ALPHA CONVERSION AND FUNCTIONAL COMPOSITION ===

/**
 * Alpha conversion operator - Transform elements
 * indexed α transform means transform all elements
 */
inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = a j { index: Int -> xform(b(index)) }

/**
 * IterableIndexed - Bridge between Indexed and Iterable
 */
@kotlin.jvm.JvmInline
value class IterableIndexed<A>(val s: Indexed<A>) : Iterable<A>, Indexed<A> by s {
    override fun iterator(): Iterator<A> = object : Iterator<A> {
        internal var currentIndex = 0
        override fun hasNext(): Boolean = currentIndex < s.a
        override fun next(): A = s.b(currentIndex++)
    }
}

/**
 * Play property - Convert Indexed to Iterable
 */
val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

/**
 * Factory functions for MetaSeries and Indexed - ONLY j operator
 */
fun <A, T> MetaSeries_create(a: A, getter: (A) -> T): MetaSeries<A, T> = a j getter
fun <T> Indexed_create(size: Int, getter: (Int) -> T): Indexed<T> = size j getter

// === COLUMNAR METADATA TYPES ===

/**
 * TypeMemento - Metadata for data types
 * From Columnar codebase with categorical features
 */
interface TypeMemento { 
    val networkSize: Int? 
}

/**
 * IoMemento - I/O metadata for ISAM and cursor operations
 * From Columnar codebase with categorical features
 */
enum class IOMemento : TypeMemento {
    IoByte, IoShort, IoInt, IoFloat, IoDouble, IoLong,
    IoBoolean, IoChar, IoString, IoCharSeries, IoBigDecimal,
    IoBigInt, IoDateTime, IoDuration, IoUUID, IoBinary, IoUnknown;
    
    override val networkSize: Int? get() = when(this) {
        IoByte, IoBoolean -> 1
        IoShort, IoChar -> 2
        IoInt, IoFloat -> 4
        IoLong, IoDouble -> 8
        else -> null // Variable size or not applicable
    }
}

/**
 * ColumnMeta - Enhanced with categorical features
 * From Columnar codebase with wireproto integration
 */
typealias ColumnMeta = Join<ByteArray, TypeMemento>

// Extension properties for ColumnMeta
inline val ColumnMeta.name: ByteArray get() = a
inline val ColumnMeta.type: TypeMemento get() = b

/**
 * CursorMeta - Metadata accessor for cursors
 * From Columnar codebase
 */
typealias CursorMeta = Indexed<ColumnMeta>

// === CURSOR DEFINITIONS WITH CATEGORICAL FEATURES ===


/**
 * Cursor - Database table metaclass with categorical features
 * From Columnar codebase with old wireproto integration
 */
typealias Cursor = Indexed<RowVec>

/**
 * Cursor with metadata - Enhanced cursor with categorical features
 * From Columnar codebase
 */
typealias CursorWithMeta = Join<Cursor, CursorMeta>

// === WIREPROTO FOR CURSORS WITH CATEGORICAL FEATURES ===

/**
 * Wire format for IoMemento with categorical features
 * Old wireproto integration for cursor operations
 */
@Serializable
data class WireIoMemento(
    val name: String?,
    val type: String?,
    val width: Int?,
    val nullable: Boolean?,
    val encoding: String?,
    val format: String?
)

/**
 * Wire format for cursor operations with categorical features
 * Old wireproto integration
 */
@Serializable
data class CursorOpenRequest(
    val dataFile: String,
    val columns: Indexed<WireIoMemento>,
    val readOnly: Boolean
)

@Serializable
data class CursorReadRequest(
    val cursorId: Long,
    val offset: Long,
    val limit: Int
)

@Serializable
data class CursorDataResponse(
    val cursorId: Long,
    val rows: Indexed<Indexed<Any?>>, // RowVec data
    val hasMore: Boolean
)

// === CATEGORICAL CURSOR OPERATIONS ===

/**
 * Cursor-specific categorical operators
 * From Columnar codebase with enhanced features
 */

/**
 * Cursor filter operator (⟲) - Filter rows by predicate
 * cursor ⟲ predicate means filter rows where predicate is true
 */
infix fun Cursor.`⟲`(predicate: (RowVec) -> Boolean): Cursor = a j { i: Int ->
    val row = b(i)
    if (predicate(row)) row else null
}.filterNotNull()

/**
 * Cursor map operator (➤) - Transform rows
 * cursor ➤ transform means transform each row
 */
infix fun Cursor.`➤`(transform: (RowVec) -> RowVec): Cursor = a j { i: Int ->
    transform(b(i))
}

/**
 * Cursor first operator (f1rst) - Get first row
 * cursor.f1rst means get first row or null
 */
val Cursor.f1rst: RowVec? get() = if (a > 0) b(0) else null

/**
 * Cursor last operator (last) - Get last row
 * cursor.last means get last row or null
 */
val Cursor.last: RowVec? get() = if (a > 0) b(a - 1) else null

/**
 * Cursor reverse operator (reverse) - Reverse cursor order
 * cursor.reverse means reverse row order
 */
val Cursor.reverse: Cursor get() = a j { i: Int -> b(a - 1 - i) }

/**
 * Cursor infinite operator (infinite) - Create infinite cursor
 * cursor.infinite means repeat cursor infinitely
 */
val Cursor.infinite: Cursor get() = (-1) j { i: Int -> b(i % a) }

/**
 * Cursor take operator (/) - Take first n rows
 * cursor / n means take first n rows
 */
infix fun Cursor.`/`(n: Int): Cursor = minOf(n, a) j { i: Int -> b(i) }

// === CURSOR CORE OPERATIONS ===

/** Get the RowVec at y or if y is negative then -y from last */
infix fun Cursor.at(y: Int): RowVec = this[if (y < 0) a + y else y]

/** Get a slice of rows */
infix fun Cursor.slice(range: IntRange): Cursor = (range.last - range.first + 1) j { i -> this[range.first + i] }

/** Get column names - Cured: Returns Indexed<ByteArray> instead of List */
val Cursor.columnNames: Indexed<ByteArray>
    get() = f1rst?.let { firstRow ->
        firstRow.b.a j { i -> firstRow.b[i] }
    } ?: (0 j { ByteArray(0) })

/** Get column types - Cured: Returns Indexed<TypeMemento> instead of List */
val Cursor.columnTypes: Indexed<TypeMemento>
    get() = f1rst?.let { firstRow ->
        firstRow.b.a j { i -> inferType(firstRow.b[i]) }
    } ?: (0 j { IOMemento.IoUnknown })

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
    columnNames: Indexed<ByteArray> = data.f1rst?.let { firstRow ->
        firstRow.a j { i -> "col_$i".toByteArray() }
    } ?: (0 j { ByteArray(0) }),
    columnTypes: Indexed<TypeMemento> = data.f1rst?.let { firstRow ->
        firstRow.a j { i -> inferType(firstRow.b(i)) }
    } ?: (0 j { IOMemento.IoUnknown })
): Cursor {
    require(data.a > 0) { "Data cannot be empty" }
    val firstRow = data.b(0)
    require(columnNames.a == firstRow.a) { "Column names size mismatch" }
    require(columnTypes.a == firstRow.a) { "Column types size mismatch" }

    return data.a j { rowIndex: Int ->
        val rowData = data.b(rowIndex)
        rowData.a j { colIndex: Int -> rowData.b(colIndex) }
    }
}

/** Infer type from value with categorical features */
internal fun inferType(value: Any?): TypeMemento = when (value) {
    is Int -> IOMemento.IoInt
    is ByteArray -> IOMemento.IoBinary
    is Float -> IOMemento.IoFloat
    is Double -> IOMemento.IoDouble
    is String -> IOMemento.IoString
    is Boolean -> IOMemento.IoBoolean
    is Long -> IOMemento.IoLong
    is Byte -> IOMemento.IoByte
    is Short -> IOMemento.IoShort
    is Char -> IOMemento.IoChar
    else -> IOMemento.IoUnknown
}

// === CURSOR UTILITY EXTENSIONS ===

/**
 * Get cursor metadata
 * From Columnar codebase
 */
val Cursor.meta: CursorMeta get() = a j { i: Int ->
    val row = b(i)
    row.a j { j: Int -> 
        val columnName = "col_$j".toByteArray()
        val columnType = inferType(row.b(j))
        columnName j columnType
    }
}

/**
 * Get cursor with metadata
 * From Columnar codebase
 */
val Cursor.withMeta: CursorWithMeta get() = this j meta

// === HELPER EXTENSIONS FOR INDEXED ===

/** Convert Iterable to Indexed */
fun <T> Iterable<T>.toIndexed(): Indexed<T> {
    val list = this.toList()
    return list.size j { list[it] }
}

/** Get first element or null */
fun <T> Indexed<T>.firstOrNull(): T? = if (a > 0) b(0) else null

/** Filter null values from Indexed */
fun <T> Indexed<T?>.filterNotNull(): Indexed<T> {
    val nonNull = mutableListOf<T>()
    for (i in 0 until a) {
        b(i)?.let { nonNull.add(it) }
    }
    return nonNull.size j { nonNull[it] }
}

/** Slice Indexed from start to end inclusive */
fun <T> Indexed<T>.slice(start: Int, endInclusive: Int): Indexed<T> {
    val sliceSize = endInclusive - start + 1
    return sliceSize j { b(start + it) }
}

// === QOL HELPERS MIGRATED FROM borg.trikeshed.common.collections ===

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

// === TYPE CONVERSION HELPERS ===

@Suppress("UNCHECKED_CAST")
inline fun <T> Any.toIndexed(): Indexed<T> = (this as? Indexed<T>) ?: (this as? List<T>)?.let { l -> 
    l.size j { l[it] }
} ?: error("Cannot convert to Indexed")

// expect fun assert(value: Boolean, lazyMessage: () -> Any) 

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