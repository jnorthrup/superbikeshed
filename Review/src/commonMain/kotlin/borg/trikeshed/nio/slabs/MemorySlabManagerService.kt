package borg.trikeshed.nio.slabs

import kotlin.coroutines.CoroutineContext

/**
 * A CoroutineContext Element Key (CCEK) service responsible for acquiring and managing [MemorySlab] instances.
 * This service abstracts the underlying allocation and potential pooling of memory slabs.
 *
 * Implementations of this service determine the source of slabs (e.g., new allocations, pools)
 * and how they are managed, especially concerning direct memory and its lifecycle.
 */
interface MemorySlabManagerService : CoroutineContext.Element {
    /**
     * The key for [MemorySlabManagerService] in a [CoroutineContext].
     * Used to retrieve the service instance, for example: `coroutineContext[MemorySlabManagerService.Key]`.
     */
    companion object Key : CoroutineContext.Key<MemorySlabManagerService>

    /**
     * The key for this service, allowing it to be retrieved from a [CoroutineContext].
     */
    override val key: CoroutineContext.Key<*> get() = Key

    /**
     * The default capacity in bytes for slabs acquired via [acquireSlab] if no specific capacity is requested.
     * Implementations may define a sensible default (e.g., 1MB, 4MB).
     */
    val defaultSlabSize: Int

    /**
     * Acquires a [MemorySlab], either by allocating a new one or retrieving one from a pool.
     * The returned slab will have at least the [requestedCapacity]. If the requested capacity
     * is very small, the manager might provide a slab of a minimum practical size.
     *
     * @param requestedCapacity The desired capacity for the slab in bytes. If not specified or less than
     *                          a platform-defined minimum, [defaultSlabSize] or a minimum practical size may be used.
     * @return A [MemorySlab] instance ready for writing.
     */
    fun acquireSlab(requestedCapacity: Int = defaultSlabSize): MemorySlab

    /**
     * Releases a [MemorySlab] instance.
     * The behavior of this method depends heavily on the specific implementation of the service.
     * For example:
     * - If the manager uses a slab pool, this method might return the slab to the pool for reuse.
     * - If slabs are manually managed native memory (not typical for current `Actual...native.kt`),
     *   this might trigger deallocation.
     * - If slabs are primarily GC-managed (e.g., simple ByteArray-backed slabs), this might be a no-op
     *   or only perform minimal bookkeeping.
     *
     * Callers should use this method when they are finished with a slab, allowing the manager
     * to reclaim or recycle resources as appropriate.
     *
     * @param slab The [MemorySlab] to be released.
     */
    fun releaseSlab(slab: MemorySlab)

    /**
     * Reports diagnostic statistics about the state of the slab manager.
     * The format and content of the returned string are implementation-specific.
     * Examples might include the number of active slabs, total memory allocated/managed,
     * pool utilization, etc.
     *
     * @return A string containing diagnostic information.
     */
    fun reportStats(): String
}
