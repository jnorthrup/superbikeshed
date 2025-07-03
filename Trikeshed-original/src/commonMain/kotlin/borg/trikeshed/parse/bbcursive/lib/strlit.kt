package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.decodeUtf8
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator
import java.text.MessageFormat

/**
 * Created by jim on 1/17/16.
 */
object strlit {

    fun strlit(s: CharSequence): UnaryOperator<ByteIndexedBuffer> {
        return ByteBufferUnaryOperator(s)
    }

    private class ByteBufferUnaryOperator(private val s: CharSequence) : UnaryOperator<ByteIndexedBuffer> {

        override fun toString(): String {
            return MessageFormat.format("u8\"{0}\"", s)
        }

        override fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
            val encode = s.toString().encodeToByteArray().toByteIndexedBuffer()
            while (encode.hasRemaining && buffer.hasRemaining && encode.get == buffer.get) {
            }
            return if (encode.hasRemaining) null else buffer
        }
    }
}