package borg.trikeshed.nio.slabs

import borg.trikeshed.nio.ByteBufferFactory
import kotlin.coroutines.CoroutineContext

/**
 * JVM-specific actual implementation of the [MemorySlabManagerService].
 * This service provides [MemorySlab] instances ([ActualMemorySlab]) that are backed by
 * direct `java.nio.ByteBuffer`s acquired via [borg.trikeshed.nio.ByteBufferFactory.allocateDirect].
 *
 * Currently, this implementation does not feature slab pooling. Each call to [acquireSlab]
 * results in a new allocation. The [releaseSlab] method is effectively a no-op, as the
 * underlying direct ByteBuffers are managed by the JVM's garbage collector.
 */
actual class ActualMemorySlabManagerService actual constructor(
    actual override val defaultSlabSize: Int
) : MemorySlabManagerService {

    actual override fun acquireSlab(requestedCapacity: Int): MemorySlab {
        val capacityToAllocate = if (requestedCapacity <= 0) defaultSlabSize else requestedCapacity
        val directBuffer = ByteBufferFactory.allocateDirect(capacityToAllocate)
        val slab = ActualMemorySlab(directBuffer, capacityToAllocate)
        return slab
    }

    /**
     * Releases a [MemorySlab] instance.
     * In this JVM implementation, since slabs are not pooled and direct ByteBuffers are GC-managed,
     * this method is currently a no-op. It exists to fulfill the interface and for potential
     * future enhancements like pooling.
     * @param slab The slab to be released (currently ignored).
     */
    actual override fun releaseSlab(slab: MemorySlab) {
        // No-op for JVM as direct ByteBuffers are GC managed and no pooling is implemented.
    }

    /**
     * Reports basic diagnostic statistics.
     * Currently, this returns a placeholder string as no advanced tracking or pooling is implemented.
     * @return A string indicating basic JVM slab manager status.
     */
    actual override fun reportStats(): String {
        return "JVM Slab Manager: Stats TBD (no pooling implemented, uses direct ByteBuffers)"
    }

    actual override val key: CoroutineContext.Key<*> get() = MemorySlabManagerService.Key
}
