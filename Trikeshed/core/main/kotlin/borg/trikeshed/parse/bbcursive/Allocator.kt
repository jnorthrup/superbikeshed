package borg.trikeshed.parse.bbcursive

import borg.trikeshed.parse.bbcursive.lib.log.log
import java.nio.ByteBuffer

/**
 * User: jim
 * Date: Oct 6, 2007
 * Time: 3:10:32 AM
 */
class Allocator {

    var DIRECT_HEAP: ByteBuffer? = null
    val MEG = (1 shl 10) shl 10
    val BLOCKSIZE = MEG * 2

    private var initialCapacity = Runtime.getRuntime().availableProcessors() * 20 * 2


    val EMPTY_SET: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

    var size = initialCapacity

    constructor(vararg bytes: Int) {
        if (bytes.isNotEmpty())
            initialCapacity = bytes[0]

        var buffer: ByteBuffer? = null
        while (buffer == null)
            try {

                if (isDirect())
                    buffer = ByteBuffer.allocateDirect(size).limit(0)
                else
                    buffer = ByteBuffer.allocate(size).limit(0)

                DIRECT_HEAP = buffer
                log("Heap allocated at " + size / MEG + " megs")
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

                if (isDirect())
                    buffer = ByteBuffer.allocateDirect(size).limit(0)
                else
                    buffer = ByteBuffer.allocate(size).limit(0)

                DIRECT_HEAP = buffer
                log("Heap allocated at " + size / MEG + " megs")
                size *= 2

            } catch (e: IllegalArgumentException) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            } catch (e: OutOfMemoryError) {
                size = Math.max(16 * MEG, size / 2)
                System.gc()
            }
    }

    fun allocate(size: Int): ByteBuffer {
        if (size == 0) return EMPTY_SET
        try {
            DIRECT_HEAP!!.limit(DIRECT_HEAP!!.limit() + size)
        } catch (e: IllegalArgumentException) {
            init()
            return allocate(size)
        }
        val ret = DIRECT_HEAP!!.slice().limit(size).mark() as ByteBuffer
        DIRECT_HEAP!!.position(DIRECT_HEAP!!.limit())
        return ret
    }

    fun isDirect(): Boolean {
        return false
    }

}
