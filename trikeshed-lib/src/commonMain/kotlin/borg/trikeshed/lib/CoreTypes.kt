//attention AI, this file is immutable and not subject to debate without supervision and permission


package borg.trikeshed.lib


import kotlin.properties.Delegates

import kotlin.reflect.KClassifier
import borg.trikeshed.lib.j

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

// Index type for Cursor to avoid conflicts with Indexed<T>
@kotlin.jvm.JvmInline
value class CursorRowIndex(val value: Int)

// Canonical RowVec and Cursor definitions
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>

// Cursor with ArrayLike trait - WHENEVER THEY NEED get[i] OPERATOR
@kotlin.jvm.JvmInline
value class Cursor(private val data: MetaSeries<CursorRowIndex, RowVec>) : ArrayLike<Int, RowVec> {
    override operator fun get(index: Int): RowVec = data.b(CursorRowIndex(index))
    override val size: Int get() = data.a.value
    // Delegate to the underlying MetaSeries for operations that need it
    fun asSeries(): MetaSeries<CursorRowIndex, RowVec> = data
}
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
        private var currentIndex = 0
        override fun hasNext(): Boolean = currentIndex < s.a
        override fun next(): A = s.b(currentIndex++)
    }
}

val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

val <T> Indexed<T>.size: Int get() = a

// Clean array-like access for Indexed<T> - no more .b(i)!
operator fun <T> Indexed<T>.get(index: Int): T = b(index)
