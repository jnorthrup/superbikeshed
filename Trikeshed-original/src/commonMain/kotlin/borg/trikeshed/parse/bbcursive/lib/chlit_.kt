package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.std
import java.nio.ByteBuffer
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

/**
 * Created by jim on 1/17/16.
 */
interface chlit_ {
    companion object {
        fun chlit(vararg chars: Char): UnaryOperator<ByteBuffer> {
            return object : UnaryOperator<ByteBuffer> {
                override fun apply(buffer: ByteBuffer): ByteBuffer? {
                    var b: ByteBuffer? = null
                    for (aChar in chars) {
                        b = bb(buffer, advance.genericAdvance(aChar.code.toByte()))
                        if (null == b) return null
                    }
                    return b
                }
            }
        }
    }
}
