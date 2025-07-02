package borg.trikeshed.parse.bbcursive.vtables

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import borg.trikeshed.parse.bbcursive.Cursive.pre.*
import borg.trikeshed.parse.bbcursive.std.bb
import borg.trikeshed.parse.bbcursive.std.str

/**
 * this class reads a null terminated string. the null is not included
 */

class CString {
    private val mutator = _mutator$()
    private val reifier = _reifier$()
    fun getMutator(): _mutator<String> {
        return mutator
    }
    fun getReifier(): _reifier<String> { return reifier }

    private inner class _mutator$ : _mutator<String>() {
        override fun apply(s: String): _ptr {

            val context = getContext()
            val at = context.at()
            bb(bb(at.core(), mark), StandardCharsets.UTF_8.encode(s), duplicate, reset, slice, debug)//wordy, but not doing much
            return getContext().at()
        }
    }
    private inner class _reifier$ : _reifier<String> {
        override fun apply(ptr: _ptr): String {
            val b = bb(ptr.core(ptr), mark, slice)
            while (b.hasRemaining() && b.get() > 0) {}
            return str(b.flip())
        }
    }
}
