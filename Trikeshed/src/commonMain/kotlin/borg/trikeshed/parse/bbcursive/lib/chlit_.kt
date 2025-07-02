package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
char literal
 */
object chlit_ {
    fun chlit(c: Char): UnaryOperator<ByteIndexedBuffer> {
        return ByteBufferUnaryOperator(c)
    }

    fun chlit(s: CharSequence): UnaryOperator<ByteIndexedBuffer> {
        return chlit(s[0])
    }


    private class ByteBufferUnaryOperator(private val c: Char) : UnaryOperator<ByteIndexedBuffer> {

        override fun toString(): String {
            return "c8'" +
                    c + "'"
        }

        override fun invoke(buf: ByteIndexedBuffer): ByteIndexedBuffer? {
            if (!buf.hasRemaining) {
                return null
            }
            val b = buf.get()
            return if ((c.toInt() and 0xff) == (b.toInt() and 0xff)) buf else null
        }
    }
}