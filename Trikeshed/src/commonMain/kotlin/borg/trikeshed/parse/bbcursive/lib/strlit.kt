package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator
import java.text.MessageFormat // This might need a Kotlin equivalent or be removed if not critical

/**
 * Created by jim on 1/17/16.
 */
object strlit {

    fun strlit(s: CharSequence): UnaryOperator<ByteIndexedBuffer> {
        return object : UnaryOperator<ByteIndexedBuffer> {
            override fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                val encode = s.toString().encodeToByteArray().toByteIndexedBuffer()
                while (encode.hasRemaining && buffer.hasRemaining && encode.get() == buffer.get()) {
                    // continue
                }
                return if (encode.hasRemaining) null else buffer
            }

            override fun toString(): String {
                // MessageFormat might not be available in Kotlin Common, using string interpolation
                return "u8\"${s}""
            }
        }
    }
}
