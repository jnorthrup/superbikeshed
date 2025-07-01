package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer

/**
 * Created by jim on 1/17/16.
 */
object push {
    /**
     * @param src
     * @param dest
     * @return
     */
    fun push(src: ByteIndexedBuffer, dest: ByteIndexedBuffer): ByteIndexedBuffer {
        val need = src.rem
        val have = dest.rem
        if (have > need) {
            return dest.put(src)
        }
        dest.put(src.slice().lim(have))
        src.pos(src.pos + have)
        return dest
    }
}
