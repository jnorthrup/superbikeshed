package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.CharIndexedBuffer
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.lib.decodeUtf8
import borg.trikeshed.lib.encodeUtf8

/**
 * unique code completion for utf8
 */
object u8tf {
    /**
     * utf8 encoder macro
     */
    fun c2b(charseq: CharSequence): ByteIndexedBuffer {
        return charseq.toString().encodeUtf8().toByteIndexedBuffer()
    }

    /**
     * UTF8 decoder macro
     *
     * @param buffer
     * @return deferred string translation decision
     */
    fun b2c(buffer: ByteIndexedBuffer): CharSequence {
        return buffer.decodeUtf8()
    }
}
