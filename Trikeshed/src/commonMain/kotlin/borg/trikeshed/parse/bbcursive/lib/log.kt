package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.Cursive.pre.debug
import borg.trikeshed.parse.bbcursive.WantsZeroCopy // Assuming WantsZeroCopy is in the same package or imported

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
    fun log(ob: Any?, vararg prefixSuffix: String) {
        assert(log$(ob, *prefixSuffix))
    }

    /**
     * conditional debug output assert log(Object,[prefix[,suffix]])
     *
     * @param ob
     * @param prefixSuffix
     * @return
     */
    fun log$(ob: Any?, vararg prefixSuffix: String): Boolean {
        val hasSuffix = 1 < prefixSuffix.size
        if (prefixSuffix.isNotEmpty())
            System.err.print(prefixSuffix[0] + "\t")
        if (ob !is ByteIndexedBuffer) {
            if (ob is WantsZeroCopy) {
                val wantsZeroCopy = ob
                // std.bb is a suspend function, so we need to call it from a coroutine scope.
                // For logging, we might need a different approach or a runBlocking/launch if this is a top-level call.
                // For now, I'll assume a context where suspend calls are allowed or this will be refactored.
                // This is a placeholder for the actual call.
                // std.bb(wantsZeroCopy.asByteIndexedBuffer(), debug)
                System.err.println("DEBUG: WantsZeroCopy object: $ob")
            } else {
                // std.bb(String.valueOf(ob), debug)
                System.err.println("DEBUG: Other object: $ob")
            }
        } else {
            // std.bb((ByteIndexedBuffer) ob, debug)
            System.err.println("DEBUG: ByteIndexedBuffer: $ob")
        }
        if (hasSuffix) {
            System.err.println(prefixSuffix[1] + "\t")
        }
        return true
    }
}
