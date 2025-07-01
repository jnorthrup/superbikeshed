package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object advance {
    /**
     * consumes a token from the current ByteIndexedBuffer position. null signals fail and should reset.
     *
     * @param exemplar usually name().getBytes(), but might be other value also.
     * @return null if no match -- rollback not done here use Narsive.$ for whitespace and rollback
     */
    fun genericAdvance(vararg exemplar: Byte): UnaryOperator<ByteIndexedBuffer> {
        return object : UnaryOperator<ByteIndexedBuffer> {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer? {
                var c = 0
                val initialPos = target.pos // Store initial position for potential reset
                while (target.hasRemaining && c < exemplar.size && exemplar[c] == target.get()) {
                    c++
                }
                return if (c == exemplar.size) target else {
                    target.pos(initialPos) // Reset position on failure
                    null
                }
            }

            override fun toString(): String {
                return "advance->${exemplar.joinToString("") { it.toInt().toChar().toString() }}"
            }
        }
    }
}
