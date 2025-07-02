package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

interface allOf_ {
    companion object {
        fun allOf(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
            return object : UnaryOperator<ByteBuffer> {
                override fun toString(): String {
                    return "allOf" + Arrays.deepToString(allOf)
                }

                override fun apply(buffer: ByteBuffer): ByteBuffer? {
                    return bb(buffer, *allOf)
                }
            }
        }
    }
}
