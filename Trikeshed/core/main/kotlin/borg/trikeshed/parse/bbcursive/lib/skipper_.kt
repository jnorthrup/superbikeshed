package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.ann.Skipper
import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

@Skipper
interface skipper_ {

    @Skipper
    companion object {
        fun skipperAll(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
            return ByteBufferUnaryOperator(allOf)
        }
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
