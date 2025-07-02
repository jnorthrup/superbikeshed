package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.lib.lim
import borg.trikeshed.lib.pos
import borg.trikeshed.lib.slice
import borg.trikeshed.lib.duplicate
import borg.trikeshed.lib.put
import borg.trikeshed.lib.rew

/**
 * User: jim
 * Date: Oct 6, 2007
 * Time: 3:10:32 AM
 */
class Allocator {

    private var DIRECT_HEAP: ByteIndexedBuffer? = null
    private val MEG = (1 shl 10) shl 10
    private val BLOCKSIZE = MEG * 2

    private var initialCapacity = Runtime.getRuntime().availableProcessors() * 20 * 2


    val EMPTY_SET: ByteIndexedBuffer = ByteIndexedBuffer(ByteArray(0).toIndexed()).ro()

    private var size = initialCapacity

    constructor(vararg bytes: Int) {
        if (bytes.isNotEmpty())
            initialCapacity = bytes[0]

        var buffer: ByteIndexedBuffer? = null
        while (buffer == null)
            try {

                // if (isDirect())
                //     buffer = (ByteBuffer) ByteBuffer.allocateDirect(size) .limit(0);
                // else
                //     buffer = (ByteBuffer) ByteBuffer.allocate(size) .limit(0);
                // For now, we'll use a simple ByteArray-backed ByteIndexedBuffer
                buffer = ByteIndexedBuffer(ByteArray(size).toIndexed()).lim(0)

                DIRECT_HEAP = buffer
                System.err.println("Heap allocated at " + size / MEG + " megs")
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

        var buffer: ByteIndexedBuffer? = null
        while (buffer == null)
            try {

                // if (isDirect())
                //     buffer = (ByteBuffer) ByteBuffer.allocateDirect(size) .limit(0);
                // else
                //     buffer = (ByteBuffer) ByteBuffer.allocate(size) .limit(0);
                // For now, we'll use a simple ByteArray-backed ByteIndexedBuffer
                buffer = ByteIndexedBuffer(ByteArray(size).toIndexed()).lim(0)

                DIRECT_HEAP = buffer
                System.err.println("Heap allocated at " + size / MEG + " megs")
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
        if (size == 0) return EMPTY_SET
        try {
            DIRECT_HEAP?.lim(DIRECT_HEAP!!.limit + size)
        } catch (e: IllegalArgumentException) {
            init()
            return allocate(size)
        }
        val ret = DIRECT_HEAP!!.slice().lim(size).mk
        DIRECT_HEAP!!.pos(DIRECT_HEAP!!.limit)
        return ret
    }

    fun isDirect(): Boolean {
        return false
    }
}