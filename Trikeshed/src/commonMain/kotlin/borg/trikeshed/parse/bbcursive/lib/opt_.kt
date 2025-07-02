package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import bbcursive.std.bb

/**
 * Created by jim on 1/17/16.
 */
object opt_ {

    fun opt(vararg unaryOperators: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return ByteBufferUnaryOperator(unaryOperators)
    }

    class ByteBufferUnaryOperator(private val allOrPrevious: Array<out UnaryOperator<ByteBuffer>>) : UnaryOperator<ByteBuffer> {

        override fun toString(): String {
            return "opt:" + Arrays.deepToString(allOrPrevious)
        }

        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            val position = buffer.position()
            val r = bb(buffer, *allOrPrevious)
            if (null == r) {
                buffer.position(position)
            }
            return buffer
        }
    }
}