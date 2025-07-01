package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object abort {
    fun abort(rollbackPosition: Int): UnaryOperator<ByteIndexedBuffer> {
        return object : UnaryOperator<ByteIndexedBuffer> {
            override suspend fun invoke(b: ByteIndexedBuffer): ByteIndexedBuffer? { // Added suspend
                return if (b == null) null else std.bb(b.pos(rollbackPosition)) // Use std.bb with pos
            }
        }
    }
}
