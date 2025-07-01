package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator
import kotlin.coroutines.coroutineContext // Import coroutineContext

/**
 * Created by jim on 1/17/16.
 */
interface opt_ {
    companion object {
        suspend fun opt(vararg unaryOperators: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {
            return object : UnaryOperator<ByteIndexedBuffer> {
                override fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                    val position = buffer.pos
                    val r = std.bb(buffer, *unaryOperators)
                    if (null == r) {
                        buffer.pos(position)
                    }
                    return buffer
                }

                override fun toString(): String {
                    return "opt:${unaryOperators.contentDeepToString()}"
                }
            }
        }
    }
}
