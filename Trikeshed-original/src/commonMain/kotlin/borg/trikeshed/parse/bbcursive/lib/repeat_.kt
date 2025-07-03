package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

interface repeat_ {
    companion object {
        fun repeat(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
            return object : UnaryOperator<ByteBuffer> {
                override fun toString(): String {
                    return "repeat" + Arrays.deepToString(allOf)
                }

                override fun apply(buffer: ByteBuffer): ByteBuffer? {
                    var buf: ByteBuffer? = buffer
                    while (null != bb(buf, *allOf)) {
                        buf = bb(buf, *allOf)
                    }
                    return buf
                }
            }
        }
    }
}
