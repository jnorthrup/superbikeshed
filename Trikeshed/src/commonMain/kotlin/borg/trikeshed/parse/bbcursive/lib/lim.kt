package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
class lim(private val position: Int) : UnaryOperator<ByteIndexedBuffer> { // Changed ByteBuffer to ByteIndexedBuffer
    /**
     * reposition
     *
     * @param position
     * @return
     */
    companion object {
        fun lim(position: Int): UnaryOperator<ByteIndexedBuffer> { // Changed ByteBuffer to ByteIndexedBuffer
            return lim(position)
        }
    }

    override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer { // Changed apply to invoke, ByteBuffer to ByteIndexedBuffer
        return target.lim(position)
    }
}

infix fun ByteIndexedBuffer.lim(position: Int): ByteIndexedBuffer = this.lim(position)
