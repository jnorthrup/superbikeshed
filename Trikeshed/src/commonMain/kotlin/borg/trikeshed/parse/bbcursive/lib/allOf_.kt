package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.std

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
interface allOf_ {

    /**
     * bbcursive.lib.allOf_ of, in sequence, without failures
     *
     * @param allOf
     * @return null if not bbcursive.lib.allOf_ match in sequence
     */
    fun allOf(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return object : UnaryOperator<ByteBuffer> {
            override fun toString(): String {
                return "all" + Arrays.deepToString(allOf)
            }

            override fun apply(target: ByteBuffer): ByteBuffer? {
                return std.bb(target, *allOf)
            }
        }
    }
}