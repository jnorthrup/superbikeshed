package borg.trikeshed.lib

// Import core Series for reference if needed by implementers, but not extended directly
import borg.trikeshed.core.Series

/**
 * Mutable Series with operators.
 * Does NOT extend Series<T> directly to avoid compiler issues.
 * Implementers must provide size and get.
 */
interface MutableSeries<T> {
    val size: Int
    operator fun get(index: Int): T

    operator fun set(index: Int, item: T)
    fun add(item: T)
    fun add(index: Int, item: T)
    fun removeAt(index: Int): T
    fun remove(item: T): Boolean
    fun clear()

    //+,-,+=,-=, etc
    operator fun plus(item: T): MutableSeries<T>
    operator fun minus(item: T): MutableSeries<T>
    operator fun plusAssign(item: T)
    operator fun minusAssign(item: T)
}