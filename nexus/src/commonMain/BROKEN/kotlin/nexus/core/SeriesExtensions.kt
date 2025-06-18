package nexus.core

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j // For infix join
import borg.trikeshed.lib.TensorSeries // For creating Series instances
import borg.trikeshed.lib.materialize // To get an Iterable from Series (this replaces the old custom ▶ operator)
import borg.trikeshed.lib.seriesOf // Convenience for creating series
import borg.trikeshed.lib.emptySeries // For creating empty series

// Types imported from NexusTypes.kt (ensure they are defined there)
import nexus.core.Score // typealias Score = Double
import nexus.core.Pattern // typealias Pattern = Series<String>
import nexus.core.Confidence // typealias Confidence = Double


// ═══════════════════════════════════════════════════════════════════════════════
// MATHEMATICAL EXTENSIONS FOR SERIES OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Returns the best (maximum) element in the series, or null if the series is empty.
 * Uses the natural ordering of the elements.
 */
fun <T : Comparable<T>> Series<T>.best(): T? =
    this.materialize().maxOrNull()

/**
 * Takes the first n elements from the series.
 * If n is greater than the series size, returns all elements.
 * If n is zero or negative, returns an empty series.
 */
fun <T> Series<T>.take(n: Int): Series<T> {
    if (n <= 0) return emptySeries()
    val list = this.materialize().take(n)
    return TensorSeries.fromList(list)
}

/**
 * Converts a List<T> to a Series<T>.
 */
fun <T> List<T>.toSeries(): Series<T> = TensorSeries.fromList(this)

/**
 * Associates a score with each element of the series.
 */
fun <T> Series<T>.scoreWith(scorer: (T) -> Score): Series<Join<Score, T>> =
    this.map { element -> scorer(element) j element } // map is a standard Series op

/**
 * Sorts a series of (Score j Element) joins by score in descending order.
 */
fun <T> Series<Join<Score, T>>.rankByScore(): Series<Join<Score, T>> {
    val sortedList = this.materialize().sortedByDescending { it.a } // it.a is the Score
    return TensorSeries.fromList(sortedList)
}

/**
 * Applies an evolution function to each element of the series.
 */
fun <T> Series<T>.evolveWith(evolver: (T) -> T): Series<T> =
    this.map { element -> evolver(element) } // map is a standard Series op

/**
 * Selects the top elements from the series based on a ratio.
 * For example, a ratio of 0.1 selects the top 10% of elements.
 * Note: This implies an ordering. If the series is not pre-sorted, this selects based on current order.
 * If sorting is implied before selection, it should be done explicitly (e.g. rankByScore().selectTop(0.1)).
 */
fun <T> Series<T>.selectTop(ratio: Double): Series<T> {
    if (ratio < 0.0 || ratio > 1.0) throw IllegalArgumentException("Ratio must be between 0.0 and 1.0: $ratio")
    if (ratio == 0.0) return emptySeries()
    if (ratio == 1.0) return this // Or a copy if Series is mutable / copy-on-write

    val count = (this.size * ratio).toInt() // Assumes Series has a 'size' property
    return this.take(count) // Uses the take function defined above
}

/**
 * Learns a pattern from the series using a provided learner function.
 */
fun <T> Series<T>.learnPattern(learner: (Series<T>) -> Pattern): Pattern =
    learner(this) // Pattern is Series<String>

/**
 * Adapts each element in the series using a provided adapter function.
 */
fun <T> Series<T>.adaptWith(adapter: (T) -> T): Series<T> =
    this.map { element -> adapter(element) } // map is a standard Series op

/**
 * Predicts the next element based on the series using a predictor function.
 */
fun <T> Series<T>.predictNext(predictor: (Series<T>) -> T): T =
    predictor(this)

/**
 * Associates a confidence score with each element of the series.
 */
fun <T> Series<T>.withConfidence(confidenceCalc: (T) -> Confidence): Series<Join<Confidence, T>> =
    this.map { element -> confidenceCalc(element) j element } // map is a standard Series op

// Note: The original `α` operator from NexusAgent_OLD.kt was a simple map.
// `borg.trikeshed.lib.Series` is assumed to have a `map` function.
// If `α` had different semantics (like context passing or error handling), those are not preserved here.
// The custom `▶` operator from NexusAgent_OLD.kt `this ▶ { it.maxOrNull()!! }`
// is replaced by `this.materialize().maxOrNull()` assuming `materialize()` provides an `Iterable`.
// `borg.trikeshed.lib.Series` interface should define `map` and `size`.
// `borg.trikeshed.lib.materialize` is assumed to be an extension function `Series<T>.materialize(): Iterable<T>`.
// If these are not available in `borg.trikeshed.lib`, these implementations will fail.
// For `TensorSeries.fromList(list)`, this is assumed to be a standard constructor for Series.
// `emptySeries<T>()` and `seriesOf<T>(vararg elements: T)` are also assumed from `borg.trikeshed.lib`.
