package borg.trikeshed.parse.bbcursive.vtables

import borg.trikeshed.lib.ByteIndexedBuffer

/**
 * pointer class -- approximation of c++ '*'
 *
 * this class is not exactly a Pair, it is a ByteIndexedBuffer reference with a settable position() sensor designed only for DirectByteBuffer work.
 */
class _ptr : _edge<ByteIndexedBuffer, Int>() { // Changed ByteBuffer to ByteIndexedBuffer, Integer to Int
    override fun at(): Int {
        return r$()
    }

    /**
     * bb pos
     */
    override fun goTo(integer: Int): Int {
        core()?.pos(integer) // Changed core().position(integer) to core()?.pos(integer)
        return integer
    }

    override fun r$(): Int {
        return core()?.pos ?: 0 // Changed core().position() to core()?.pos ?: 0
    }
}
