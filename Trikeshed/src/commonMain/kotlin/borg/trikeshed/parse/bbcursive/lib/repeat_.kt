package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator
import org.jetbrains.annotations.NotNull // Keep for now if needed for interop, otherwise remove

/**
 * Created by jim on 1/17/16.
 */
interface repeat_ {
    companion object {
        @NotNull
        suspend fun repeat(vararg op: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {
            return object : UnaryOperator<ByteIndexedBuffer> {
                override fun invoke(byteIndexedBuffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                    var mark = byteIndexedBuffer.pos
                    var matches = 0
                    var handle: ByteIndexedBuffer? = byteIndexedBuffer
                    var last: ByteIndexedBuffer? = null
                    while (handle?.hasRemaining == true) {
                        last = handle
                        handle = std.bb(last, *op)
                        if (handle != null) {
                            matches++
                            mark = handle.pos
                        } else {
                            break
                        }
                    }

                    if (matches > 0 && last?.hasRemaining == true)
                        last.pos(mark)

                    return if (matches > 0) last else null
                }

                override fun toString(): String {
                    return "rep:${op.contentDeepToString()}"
                }
            }
        }
    }
}
