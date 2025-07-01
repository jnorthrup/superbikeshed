package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.toByteIndexedBuffer
import java.nio.ByteBuffer // Keep for now if direct ByteBuffer allocation is still needed

/**
 * User: jim
 * Date: Oct 6, 2007
 * Time: 3:10:32 AM
 */
class Allocator(vararg bytes: Int) {

    private lateinit var DIRECT_HEAP: ByteBuffer
    val MEG = (1 shl 10) shl 10
    val BLOCKSIZE = MEG * 2

    private var initialCapacity = Runtime.getRuntime().availableProcessors() * 20 * 2

    val EMPTY_SET: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

    private var size: Int = initialCapacity

    init {
        if (bytes.isNotEmpty())
            initialCapacity = bytes[0]

        var buffer: ByteBuffer? = null
        while (buffer == null)
            try {
                buffer = if (isDirect())
                    ByteBuffer.allocateDirect(size).limit(0)
                else
                    ByteBuffer.allocate(size).limit(0)

                DIRECT_HEAP = buffer
                println("Heap allocated at ${size / MEG} megs") // Changed log to println
                size *= 2

            } catch (e: IllegalArgumentException) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            } catch (e: OutOfMemoryError) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            }
    }

    private fun init() {
        var buffer: ByteBuffer? = null
        while (buffer == null)
            try {
                buffer = if (isDirect())
                    ByteBuffer.allocateDirect(size).limit(0)
                else
                    ByteBuffer.allocate(size).limit(0)

                DIRECT_HEAP = buffer
                println("Heap allocated at ${size / MEG} megs") // Changed log to println
                size *= 2

            } catch (e: IllegalArgumentException) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            } catch (e: OutOfMemoryError) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            }
    }

    fun allocate(size: Int): ByteIndexedBuffer {
        if (size == 0) return EMPTY_SET.toByteIndexedBuffer()
        try {
            DIRECT_HEAP.limit(DIRECT_HEAP.limit() + size)
        } catch (e: IllegalArgumentException) {
            init()
            return allocate(size)
        }
        val ret = DIRECT_HEAP.slice().limit(size).mark()
        DIRECT_HEAP.position(DIRECT_HEAP.limit())
        return ret.toByteIndexedBuffer()
    }

    fun isDirect(): Boolean {
        return false
    }
}
