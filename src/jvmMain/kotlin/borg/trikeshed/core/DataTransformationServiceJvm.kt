package borg.trikeshed.core

import kotlin.coroutines.CoroutineContext

actual class DataTransformationService : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = DataTransformationService.Key

    actual fun <T, R> transform(series: Series<T>, transform: (T) -> R): Series<R> {
        return series.α(transform)
    }

    actual fun <T> filter(series: Series<T>, predicate: (T) -> Boolean): Series<T> {
        val filtered = series.▶.filter(predicate).toList()
        return filtered.toSeries()
    }

    actual fun <T, R> reduce(series: Series<T>, initial: R, operation: (R, T) -> R): R {
        return series.▶.fold(initial, operation)
    }

    actual fun <T, K> groupBy(series: Series<T>, keySelector: (T) -> K): Map<K, Series<T>> {
        return series.▶.groupBy(keySelector).mapValues { (_, values) -> values.toSeries() }
    }

    actual fun <T> sort(series: Series<T>, comparator: (T, T) -> Int): Series<T> {
        val sorted = series.▶.sortedWith(comparator).toList()
        return sorted.toSeries()
    }
} 