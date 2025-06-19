package borg.trikeshed.lib

import kotlin.jvm.JvmInline

typealias Series<T> = Join<Int, (Int) -> T>
typealias Series2<A, B> = Series<Join<A, B>>

// Essential operators
val <T> Series<T>.size: Int get() = a
operator fun <T> Series<T>.get(i: Int): T = b(i)
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = size j { i -> xform(this[i]) }

// Play materialization - ENSHRINED PATTERN
val <T> Series<T>.play: IterableSeries<T> get() = this as? IterableSeries ?: IterableSeries(this)

@JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A> {
    override fun iterator(): Iterator<A> = s.iterator()
    val size: Int get() = s.size
    operator fun get(i: Int): A = s[i]
}

// Helper for iterator implementation
operator fun <A> Series<A>.iterator(): Iterator<A> = object : Iterator<A> {
    private var currentIndex = 0
    override fun hasNext(): Boolean = currentIndex < size
    override fun next(): A {
        if (!hasNext()) throw NoSuchElementException()
        return b(currentIndex++)
    }
} 