package borg.trikeshed.parse.bbcursive.lib

import org.jetbrains.annotations.NotNull

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import bbcursive.std.bb

/**
 * Created by jim on 1/17/16.
 */
object repeat_ {

    @NotNull
    fun repeat(vararg op: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return object : UnaryOperator<ByteBuffer> {


            override fun toString(): String {
                return "rep:" + Arrays.deepToString(op)
            }

            override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
                var mark = byteBuffer.position()
                var matches = 0
                var handle: ByteBuffer? = byteBuffer
                var last: ByteBuffer? = null
                while (handle != null && handle.hasRemaining()) {
                    last = handle
                    //                if (null != (handle=op.apply(handle))) {
                    if (bb(last, *op) != null) {
                        matches++
                        mark = handle.position()
                    } else
                        break
                }

                if (matches > 0 && last != null && last.hasRemaining())
                    last.position(mark)

                return if (matches > 0) last else null
            }
        }
    }

}