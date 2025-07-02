package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.ann.Infix
import borg.trikeshed.std

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

@Infix
interface infix_ {

    @Infix
    fun infix(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return ByteBufferUnaryOperator(allOf)

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