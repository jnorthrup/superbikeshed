@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib

/**
 * Core TrikeShed Types from BrokeShed
 * Minimal type definitions that TrikeShed needs
 */

// === CORE JOIN INTERFACE ===

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

typealias Twin<T> = Join<T, T>
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b)

// === SERIES TYPE SYSTEM ===

typealias Series<T> = Join<Int, (Int) -> T>
typealias Series2<A, B> = Series<Join<A, B>>

// Essential operators
val <T> Series<T>.size: Int get() = a
operator fun <T> Series<T>.get(i: Int): T = b(i)
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = size j { i -> xform(this[i]) }

// Play materialization - ENSHRINED PATTERN
val <T> Series<T>.play: IterableSeries<T> get() = this as? IterableSeries<T> ?: IterableSeries(this)

@kotlin.jvm.JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A> {
    override fun iterator(): Iterator<A> = s.iterator()
    val size: Int get() = s.size
    operator fun get(i: Int): A = s[i]
}

fun <T> Series<T>.iterator(): Iterator<T> = object : Iterator<T> {
    private var index = 0
    override fun hasNext(): Boolean = index < size
    override fun next(): T = get(index++)
}

// === COLLECTION CONVERSIONS ===

fun <T> List<T>.toSeries(): Series<T> = this.size j { i -> this[i] }
fun <T> Array<T>.toSeries(): Series<T> = this.size j { i -> this[i] }  
fun <T> Series<T>.toList(): List<T> = this.play.toList()
inline fun <reified T> Series<T>.toArray(): Array<T> = this.play.toList().toTypedArray()

// Special case for ByteArray
fun Series<Byte>.toArray(): ByteArray = ByteArray(this.size) { this[it] }

// === EMPTY SERIES ===

fun <T> emptySeries(): Series<T> = 0 j { throw IndexOutOfBoundsException("Empty series") }