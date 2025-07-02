package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
class lim(private val position: Int) : UnaryOperator<ByteIndexedBuffer> {

    /**
     * reposition
     *
     * @param position
     * @return
     */
    fun lim(position: Int): UnaryOperator<ByteIndexedBuffer> {
        return lim(position)

    }

    override fun invoke(t: ByteIndexedBuffer): ByteIndexedBuffer? {
        return t.lim(position)
    }
}