package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.std

import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object abort {
    fun abort(rollbackPosition: Int): UnaryOperator<ByteBuffer> {
        return UnaryOperator<ByteBuffer> { b -> if (null == b) b else std.bb(b, pos.pos(rollbackPosition)) }
    }
}