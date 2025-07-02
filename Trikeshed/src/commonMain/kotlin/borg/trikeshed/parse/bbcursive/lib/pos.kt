package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
class pos(private val position: Int) : UnaryOperator<ByteIndexedBuffer> {

    /**
     * reposition
     *
     * @param position
     * @return
     */
    companion object {
        fun pos(position: Int): UnaryOperator<ByteIndexedBuffer> {
            return object : pos(position)() {
                override fun toString(): String {
                    return "pos(" + position + ")"
                }
            }
        }
    }

    override fun invoke(t: ByteIndexedBuffer): ByteIndexedBuffer? {
        return if (null == t) t else t.pos(position)
    }
}