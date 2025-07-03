package borg.trikeshed.lib


import kotlin.coroutines.CoroutineContext

/**
 * A CoroutineContext.Element that defines the memory and computation
 * strategy for Indexed<T> operations within a coroutine.
 *
 * This allows for dynamic tuning of the trade-off between re-computation
 * (Register) and memory usage/indirection (Pointer).
 */
sealed interface PackingMode : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<PackingMode>

    /**
     * Signals that Indexed<T> should be passed by value ("register packing").
     * Operations are composed functionally, and values are re-computed on each access.
     * This is optimal for shallow computations and linear access.
     */
    object Register : PackingMode

    /**
     * Signals that Indexed<T> should be passed by reference ("pointer packing").
     * The underlying data is materialized (memoized) into a concrete collection
     * to avoid costly re-computation on each access. This is optimal for
     * deep, complex computations or random-access patterns.
     */
    object Pointer : PackingMode
} 