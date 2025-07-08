package com.ta4k.trikeshedutils

import kotlin.jvm.JvmInline

/**
 * Simple data series implementation without external dependencies.
 * Following TrikeShed patterns with @kotlin.jvm.JvmInline value classes and typealiases.
 */

interface Indexed<T> {
    val size: Int
    operator fun get(index: Int): T
}

@kotlin.jvm.JvmInline
value class ArraySeries<T>(internal val data: Array<T>) : Indexed<T> {
    override val size: Int get() = data.size
    override fun get(index: Int): T = data[index]
}

@kotlin.jvm.JvmInline
value class ListSeries<T>(internal val data: List<T>) : Indexed<T> {
    override val size: Int get() = data.size
    override fun get(index: Int): T = data[index]
}

/**
 * Helper function to easily create a [Indexed] from a [List].
 */
fun <T> List<T>.toSeries(): Indexed<T> = ListSeries(this)

/**
 * Helper function to create a [Indexed] from an [Array].
 */
fun <T> Array<T>.toSeries(): Indexed<T> = ArraySeries(this)

/**
 * Helper function to collect all elements of a [Indexed] into a [List].
 */
fun <T> Indexed<T>.toList(): List<T> = List(size) { index -> this[index] }

/**
 * Maps each element of the series to a new type.
 */
fun <T, R> Indexed<T>.map(transform: (T) -> R): Indexed<R> {
    val result = Array<Any?>(size) { transform(this[it]) }
    @Suppress("UNCHECKED_CAST")
    return ArraySeries(result as Array<R>)
}

/**
 * Filters elements of the series based on a predicate.
 */
inline fun <T> Indexed<T>.filter(predicate: (T) -> Boolean): Indexed<T> {
    val filtered = mutableListOf<T>()
    for (i in 0 until size) {
        val element = this[i]
        if (predicate(element)) {
            filtered.add(element)
        }
    }
    return ListSeries(filtered)
}