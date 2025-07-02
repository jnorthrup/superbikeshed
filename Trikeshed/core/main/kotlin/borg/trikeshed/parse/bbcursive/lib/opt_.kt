package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

interface opt_ {
    companion object {
        fun opt(vararg allOrPrevious: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
            return object : UnaryOperator<ByteBuffer> {
                override fun toString(): String {
                    return "opt" + Arrays.deepToString(allOrPrevious)
                }

                override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
                    val bb = bb(byteBuffer, *allOrPrevious)
                    return bb ?: byteBuffer
                }
            }
        }
    }
}
