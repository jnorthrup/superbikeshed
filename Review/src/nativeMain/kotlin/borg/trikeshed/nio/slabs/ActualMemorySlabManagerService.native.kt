package borg.trikeshed.nio.slabs

import borg.trikeshed.nio.ByteBufferFactory
import kotlin.coroutines.CoroutineContext

/**
 * Kotlin/Native-specific actual implementation of the [MemorySlabManagerService].
 * This service provides [MemorySlab] instances ([ActualMemorySlab]) that are currently backed by
 * [borg.trikeshed.nio.NativeArrayByteBuffer] instances. These, in turn, use `ByteArray` objects
 * on the Kotlin/Native heap.
 *
 * As `ByteArray`s are garbage collected, the [releaseSlab] method is currently a no-op.
 * If future implementations use true off-heap native memory (e.g., via C interop `malloc`),
 * `releaseSlab` would become critical for explicit memory deallocation.
 * No slab pooling is currently implemented.
 */
actual class ActualMemorySlabManagerService actual constructor(
    actual override val defaultSlabSize: Int
) : MemorySlabManagerService {

    actual override fun acquireSlab(requestedCapacity: Int): MemorySlab {
        val capacityToAllocate = if (requestedCapacity <= 0) defaultSlabSize else requestedCapacity

        // ByteBufferFactory.allocateDirect on native currently returns a NativeArrayByteBuffer,
        // which is backed by a Kotlin ByteArray and is GC managed.
        val nativeBuffer = ByteBufferFactory.allocateDirect(capacityToAllocate)

        val slab = ActualMemorySlab(nativeBuffer, capacityToAllocate)
        return slab
    }

    /**
     * Releases a [MemorySlab] instance.
     * In this Kotlin/Native implementation, slabs are backed by `ByteArray`s via `NativeArrayByteBuffer`.
     * These are managed by the Kotlin/Native garbage collector. Therefore, this method is currently a no-op.
     * It is present to fulfill the interface and for future compatibility should direct off-heap memory
     * management be introduced (requiring explicit free/deallocation).
     * @param slab The slab to be released (currently ignored).
     */
    actual override fun releaseSlab(slab: MemorySlab) {
        // No-op for current native implementation as slabs are ByteArray-backed and GC managed.
    }

    /**
     * Reports basic diagnostic statistics.
     * Currently, this returns a placeholder string, noting the on-heap nature of the current slab backing.
     * @return A string indicating basic native slab manager status.
     */
    actual override fun reportStats(): String {
        return "Native Slab Manager: Stats TBD (uses on-heap ByteArrays for slabs)"
    }

    actual override val key: CoroutineContext.Key<*> get() = MemorySlabManagerService.Key
}
