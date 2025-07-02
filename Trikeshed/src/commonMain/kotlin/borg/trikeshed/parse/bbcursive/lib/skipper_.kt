package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.ann.Skipper
import borg.trikeshed.std

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import bbcursive.std.bb

@Skipper
interface skipper_ {

    @Skipper
    fun skipper(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return ByteBufferUnaryOperator(allOf)

    }

    @Skipper
    class ByteBufferUnaryOperator(private val allOf: Array<out UnaryOperator<ByteBuffer>>) : UnaryOperator<ByteBuffer> {

        override fun toString(): String {
            return "skipper" + Arrays.deepToString(allOf)
        }


        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            std.flags.get().add(std.traits.skipper)

            return bb(buffer, *allOf)
        }
    }
}