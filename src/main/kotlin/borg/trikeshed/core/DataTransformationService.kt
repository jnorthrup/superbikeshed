package borg.trikeshed.core

import kotlin.coroutines.CoroutineContext

/**
 * Service for transforming and manipulating Series data.
 * This service provides utilities for common data transformations
 * and operations on Series.
 */
expect class DataTransformationService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<DataTransformationService>
    override val key: CoroutineContext.Key<*>

    /**
     * Transforms a Series using a mapping function.
     * @param series The input Series
     * @param transform The transformation function
     * @return A new Series with transformed elements
     */
    fun <T, R> transform(series: Series<T>, transform: (T) -> R): Series<R>

    /**
     * Filters a Series using a predicate.
     * @param series The input Series
     * @param predicate The filtering function
     * @return A new Series containing only elements that match the predicate
     */
    fun <T> filter(series: Series<T>, predicate: (T) -> Boolean): Series<T>

    /**
     * Reduces a Series to a single value.
     * @param series The input Series
     * @param initial The initial value
     * @param operation The reduction operation
     * @return The reduced value
     */
    fun <T, R> reduce(series: Series<T>, initial: R, operation: (R, T) -> R): R

    /**
     * Groups elements of a Series by a key function.
     * @param series The input Series
     * @param keySelector The function to extract keys
     * @return A map of keys to Series of elements
     */
    fun <T, K> groupBy(series: Series<T>, keySelector: (T) -> K): Map<K, Series<T>>

    /**
     * Sorts a Series using a comparator.
     * @param series The input Series
     * @param comparator The comparison function
     * @return A new Series with sorted elements
     */
    fun <T> sort(series: Series<T>, comparator: (T, T) -> Int): Series<T>
} 