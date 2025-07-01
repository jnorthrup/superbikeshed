package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object chlit_ {
    fun chlit(c: Char): UnaryOperator<ByteIndexedBuffer> {
        return object : UnaryOperator<ByteIndexedBuffer> {
            override fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                return if (buffer.hasRemaining && buffer.get().toInt().toChar() == c) buffer else null
            }

            override fun toString(): String {
                return "chlit:$c"
            }
        }
    }
}
