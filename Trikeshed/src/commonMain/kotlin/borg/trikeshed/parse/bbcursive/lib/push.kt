package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer

/**
 * Created by jim on 1/17/16.
 */
object push {
    /**
     * @param src
     * @param dest
     * @return
     */

    fun push(src: ByteBuffer, dest: ByteBuffer): ByteBuffer {
        val need = src.remaining()
        val have = dest.remaining()
        if (have > need) {
            return dest.put(src)
        }
        dest.put(src.slice().limit(have) as ByteBuffer)
        src.position(src.position() + have)
        return dest
    }
}