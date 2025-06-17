package moneyfan.trikeshed

/**
 * A typealias for a Series of Join pairs.
 * This represents a two-column series, where each row is a pair of (A, B).
 */
typealias Series2<A, B> = Series<Join<A, B>>

/**
 * Extension property to get a Series of the left elements from a Series2.
 *
 * Example:
 * val seriesOfPairs: Series2<String, Int> = ...
 * val leftColumn: Series<String> = seriesOfPairs.left
 */
val <T, I> Series2<T, I>.left: Series<T>
    get() = this α { join -> join.a }

/**
 * Extension property to get a Series of the right elements from a Series2.
 *
 * Example:
 * val seriesOfPairs: Series2<String, Int> = ...
 * val rightColumn: Series<Int> = seriesOfPairs.right
 */
val <T, I> Series2<I, T>.right: Series<T>
    get() = this α { join -> join.b }

// Note:
// Additional operations for Series2 (like zipping, joining with other series, etc.)
// would be defined here or in other relevant files if a more complete TrikeShed
// replication were needed. For this task, only `left` and `right` are specified.
