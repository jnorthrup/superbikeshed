package com.ta4k.trikeshedutils

import kotlin.jvm.JvmInline

/**
 * Simple data series implementation without external dependencies.
 * Following TrikeShed patterns with @JvmInline value classes and typealiases.
 */

interface Series<T> {
    val size: Int
    operator fun get(index: Int): T
}

@JvmInline
value class ArraySeries<T>(private val data: Array<T>) : Series<T> {
    override val size: Int get() = data.size
    override fun get(index: Int): T = data[index]
}

@JvmInline
value class ListSeries<T>(private val data: List<T>) : Series<T> {
    override val size: Int get() = data.size
    override fun get(index: Int): T = data[index]
}

/**
 * Helper function to easily create a [Series] from a [List].
 */
fun <T> List<T>.toSeries(): Series<T> = ListSeries(this)

/**
 * Helper function to create a [Series] from an [Array].
 */
fun <T> Array<T>.toSeries(): Series<T> = ArraySeries(this)

/**
 * Helper function to collect all elements of a [Series] into a [List].
 */
fun <T> Series<T>.toList(): List<T> = List(size) { index -> this[index] }

/**
 * Maps each element of the series to a new type.
 */
fun <T, R> Series<T>.map(transform: (T) -> R): Series<R> {
    val result = Array<Any?>(size) { transform(this[it]) }
    @Suppress("UNCHECKED_CAST")
    return ArraySeries(result as Array<R>)
}

/**
 * Filters elements of the series based on a predicate.
 */
inline fun <T> Series<T>.filter(predicate: (T) -> Boolean): Series<T> {
    val filtered = mutableListOf<T>()
    for (i in 0 until size) {
        val element = this[i]
        if (predicate(element)) {
            filtered.add(element)
        }
    }
    return ListSeries(filtered)
}