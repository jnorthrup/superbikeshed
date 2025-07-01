package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator
import kotlin.coroutines.coroutineContext // Import coroutineContext

/**
 * Created by jim on 1/17/16.
 */
interface confix_ {
    companion object {
        suspend fun confix(operator: UnaryOperator<ByteIndexedBuffer>, vararg chars: Char): UnaryOperator<ByteIndexedBuffer> { // Added suspend
            return object : UnaryOperator<ByteIndexedBuffer> {
                override suspend fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? { // Added suspend
                    val chlit = chlit_.chlit(chars[0])
                    val aChar = if (chars.size > 1) chars[1] else chars[0] // Corrected index for aChar
                    val chlit1 = chlit_.chlit(aChar)
                    return std.bb(buffer, confix(chlit, chlit1, operator))
                }

                override fun toString(): String {
                    return "confix_:${chars.contentToString()} : $operator"
                }
            }
        }

        suspend fun confix(before: UnaryOperator<ByteIndexedBuffer>, after: UnaryOperator<ByteIndexedBuffer>, operator: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> { // Added suspend
            return object : UnaryOperator<ByteIndexedBuffer> {
                override suspend fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? { // Added suspend
                    return std.bb(buffer, allOf_.allOf(before, operator, after))
                }

                override fun toString(): String {
                    return "confix[${before}, ${operator}, ${after}]"
                }
            }
        }
    }
}
