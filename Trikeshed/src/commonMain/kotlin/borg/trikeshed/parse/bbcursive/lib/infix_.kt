package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.ann.Infix
import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

interface infix_ {
    @Infix
    companion object {
        fun infixAll(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
            return ByteBufferUnaryOperator(allOf)

        }
    }

    @Infix
    class ByteBufferUnaryOperator(private val allOf: Array<out UnaryOperator<ByteBuffer>>) : UnaryOperator<ByteBuffer> {

        override fun toString(): String {
            return "infix" + Arrays.deepToString(allOf)
        }

        override fun apply(buffer: ByteBuffer): ByteBuffer? {

            return std.bb(buffer, *allOf)
        }
    }
}
