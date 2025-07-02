package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object pos {
    fun pos(position: Int): UnaryOperator<ByteBuffer> {
        return UnaryOperator { b -> b.position(position) }
    }
}
