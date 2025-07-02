package borg.trikeshed.parse.bbcursive.vtables

import java.nio.ByteBuffer

/**
 * pointer class -- approximation of c++ '*'
 * <p>
 * this class is not exactly a Pair, it is a ByteBuffer reference with a settable position() sensor designed only for DirectByteBuffer work.
 *
 * @author jim
 */
class _ptr : _edge<ByteBuffer, Int>() {
    override fun at(): Int {
        return r$()
    }

    /**
     * bb pos
     *
     * @param integer
     * @return
     */
    override fun goTo(integer: Int): Int {
        core()!!.position(integer)
        return integer
    }

    override fun r$(): Int {
        return core()!!.position()
    }
}
