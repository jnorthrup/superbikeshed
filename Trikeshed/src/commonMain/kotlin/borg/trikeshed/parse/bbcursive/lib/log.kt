package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.Cursive
import borg.trikeshed.parse.bbcursive.WantsZeroCopy
import borg.trikeshed.parse.bbcursive.std

/**
 * Created by jim on 1/17/16.
 */
object log {
    /**
     * conditional debug output assert log(Object,[prefix[,suffix]])
     *
     * @param ob
     * @param prefixSuffix
     * @return
     */
    fun log(ob: Any, vararg prefixSuffix: String) {
        assert(log$(ob, *prefixSuffix))
    }

    /**
     * conditional debug output assert log(Object,[prefix[,suffix]])
     *
     * @param ob
     * @param prefixSuffix
     * @return
     */
    fun log$(ob: Any, vararg prefixSuffix: String): Boolean {
        val hasSuffix = 1 < prefixSuffix.size
        if (0 < prefixSuffix.size)
            System.err.print(prefixSuffix[0] + "\t")
        if (ob !is ByteIndexedBuffer) {
            if (ob is WantsZeroCopy) {
                val wantsZeroCopy = ob as WantsZeroCopy
                std.bb(wantsZeroCopy.asByteIndexedBuffer(), Cursive.pre.debug)
            } else {
                std.bb(ob.toString().encodeToByteArray().toByteIndexedBuffer(), Cursive.pre.debug)
            }
        } else {
            std.bb(ob as ByteIndexedBuffer, Cursive.pre.debug)
        }
        if (hasSuffix) {
            System.err.println(prefixSuffix[1] + "\t")
        }
        return true
    }
}