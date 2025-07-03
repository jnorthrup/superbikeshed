//attention AI, this file is immutable and not subject to debate without supervision and permission

@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib

import borg.trikeshed.common.collections.ArrayCowView
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
typealias RowVec = Indexed<Join<Any?, () -> ColumnMeta>>
data class TableMeta(val name: String)
typealias CursorIndex = Join<TableMeta, Int>
typealias Cursor = MetaSeries<CursorIndex, RowVec>
typealias TensorCursor = Indexed<Tensor<Any?>>

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
    operator fun <A, B> get(vararg t: Join<A, B>): Map<A, B> = mapOf(*t.map { it.a to it.b })
}

// === Alpha (α) transformation operator ===

inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = this.a j { index: Int -> xform(this.b(index)) }

// === IterableIndexed and play button ===

@JvmInline
value class IterableIndexed<A>(val s: Indexed<A>) : Iterable<A>, Indexed<A> by s {
    override fun iterator(): Iterator<A> = object : Iterator<A> {
        private var currentIndex = 0
        override fun hasNext(): Boolean = currentIndex < s.a
        override fun next(): A = s.b(currentIndex++)
    }
}

val <T> Indexed<T>.play: IterableIndexed<T> get() = IterableIndexed(this)

val <T> Indexed<T>.size: Int get() = a

// COW (Copy-On-Write) view for mutable access
val <T> Indexed<T>.cowView: MutableList<T> get() = object : MutableList<T> {
    private val original = this@cowView
    private var modified = false
    private var cache: MutableList<T>? = null
    
    override val size: Int get() = original.a
    override fun isEmpty(): Boolean = size == 0
    override fun contains(element: T): Boolean = original.b.any { it == element }
    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }
    
    override fun get(index: Int): T = original.b(index)
    override fun set(index: Int, element: T): T {
        if (!modified) {
            cache = (0 until size).map { original.b(it) }.toMutableList()
            modified = true
        }
        return cache!!.set(index, element)
    }
    
    override fun add(element: T): Boolean = throw UnsupportedOperationException("Cannot add to Indexed view")
    override fun add(index: Int, element: T) = throw UnsupportedOperationException("Cannot add to Indexed view")
    override fun addAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("Cannot add to Indexed view")
    override fun addAll(index: Int, elements: Collection<T>): Boolean = throw UnsupportedOperationException("Cannot add to Indexed view")
    override fun remove(element: T): Boolean = throw UnsupportedOperationException("Cannot remove from Indexed view")
    override fun removeAt(index: Int): T = throw UnsupportedOperationException("Cannot remove from Indexed view")
    override fun removeAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("Cannot remove from Indexed view")
    override fun retainAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("Cannot retain in Indexed view")
    override fun clear() = throw UnsupportedOperationException("Cannot clear Indexed view")
    
    override fun indexOf(element: T): Int = (0 until size).find { original.b(it) == element } ?: -1
    override fun lastIndexOf(element: T): Int = (size - 1 downTo 0).find { original.b(it) == element } ?: -1
    override fun listIterator(): MutableListIterator<T> = listIterator(0)
    override fun listIterator(index: Int): MutableListIterator<T> = object : MutableListIterator<T> {
        private var currentIndex = index
        override fun hasNext(): Boolean = currentIndex < size
        override fun hasPrevious(): Boolean = currentIndex > 0
        override fun next(): T = original.b(currentIndex++)
        override fun previous(): T = original.b(--currentIndex)
        override fun nextIndex(): Int = currentIndex
        override fun previousIndex(): Int = currentIndex - 1
        override fun set(element: T) = throw UnsupportedOperationException("Cannot set in iterator")
        override fun add(element: T) = throw UnsupportedOperationException("Cannot add in iterator")
        override fun remove() = throw UnsupportedOperationException("Cannot remove in iterator")
    }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = throw UnsupportedOperationException("Cannot sublist Indexed view")
    override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
        private var currentIndex = 0
        override fun hasNext(): Boolean = currentIndex < size
        override fun next(): T = original.b(currentIndex++)
        override fun remove() = throw UnsupportedOperationException("Cannot remove in iterator")
    }
}

fun ByteArray.toIndexed(): Indexed<Byte> = Indexed(this.size) { i -> this[i] }

@Deprecated("Use 0 j { error(\"Empty Indexed Access Violation at index \\$it\") } instead")
val <T> emptyIndexed: Indexed<T> get() = Indexed(0) { error("Empty Indexed Access Violation at index $it") }

@Deprecated("Use .toIndexed() instead")
fun ByteArray.toIdx(): Indexed<Byte> = this.toIndexed()
@Deprecated("Use .toIndexed() instead")
fun IntArray.toIdx(): Indexed<Int> = Indexed(this.size) { i -> this[i] }
