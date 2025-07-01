package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator
import kotlin.coroutines.coroutineContext // Import coroutineContext

/**
 * Created by jim on 1/17/16.
 */
interface allOf_ {
    companion object {
        /**
         * allOf, in sequence, without failures
         *
         * @param allOf
         * @return null if not allOf match in sequence
         */
        suspend fun allOf(vararg allOf: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {
            return object : UnaryOperator<ByteIndexedBuffer> {
                override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer? {
                    return std.bb(target, *allOf)
                }

                override fun toString(): String {
                    return "all${allOf.contentDeepToString()}"
                }
            }
        }
    }
}
