package borg.trikeshed.lib

import kotlin.jvm.JvmInline

// Core double dispatch types using TrikeShed patterns
typealias DoubleDispatchEntry<A, B, R> = Join<Join<(A) -> Boolean, (B) -> Boolean>, (A, B) -> R>
typealias DoubleDispatchTable<A, B, R> = Series<DoubleDispatchEntry<A, B, R>>

// Double dispatch operator - finds first matching entry and executes handler
fun <A, B, R> DoubleDispatchTable<A, B, R>.doubleDispatch(a: A, b: B): R? = 
    this.`play`.firstOrNull { entry ->
        val (predicateJoin, _) = entry
        val (aPredicate, bPredicate) = predicateJoin
        aPredicate(a) && bPredicate(b)
    }?.b?.invoke(a, b)

//// Operator version using Join<A,B> input
//infix fun <A, B, R> DoubleDispatchTable<A, B, R>.`**`(pair: Join<A, B>): R? =
//    doubleDispatch(pair.a, pair.b)

// Helper for common wildcard patterns
fun <T> wildcard(): (T) -> Boolean = { true }

// Safe spill system for non-register operations
@JvmInline value class SpillLog(val joins: MutableList<Join<*, *>> = mutableListOf())

// Enhanced double dispatch with spill strategy
fun <A, B, R> DoubleDispatchTable<A, B, R>.dispatchWithStrategy(
    a: A, 
    b: B
): R? = safeDoubleDispatch(a, b)

// Safe double dispatch with Join preservation in spills
fun <A, B, R> DoubleDispatchTable<A, B, R>.safeDoubleDispatch(a: A, b: B): R? = 
    doubleDispatch(a, b)

// Exact dispatch for register operations - no spills allowed
fun <A, B, R> DoubleDispatchTable<A, B, R>.exactDispatch(a: A, b: B): R = 
    doubleDispatch(a, b) ?: throw IllegalStateException("No exact match for register operation: ${a j b}")

// Add seriesOf function that was missing
fun <T> seriesOf(vararg elements: T): Series<T> = elements.toList().toSeries()