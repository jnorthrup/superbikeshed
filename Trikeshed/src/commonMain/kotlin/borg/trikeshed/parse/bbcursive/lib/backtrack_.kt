package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.ann.Backtracking
import borg.trikeshed.std

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import bbcursive.std.bb

@Backtracking
object backtrack_ {

    @Backtracking
    fun backtracker(vararg allOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {
        return backTracker(allOf)

    }
    @Backtracking
    private class backTracker(private val allOf: Array<out UnaryOperator<ByteBuffer>>) : UnaryOperator<ByteBuffer> {

        override fun toString(): String {
            return "backtracker" + Arrays.deepToString(allOf)
        }


        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            std.flags.get().add(std.traits.skipper)

            return bb(buffer, *allOf)
        }
    }
}