package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
class abort {
    companion object {
        fun abortPosition(rollbackPosition: Int): UnaryOperator<ByteBuffer> {
            return UnaryOperator { b -> if (b == null) null else std.bb(b, pos.pos(rollbackPosition), null) }
        }
    }
}
