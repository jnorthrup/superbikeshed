package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
class advance {
    /**
     * consumes a token from the current ByteBuffer position.  null signals fail and should reset.
     *
     * @param exemplar ussually name().getBytes(), but might be other value also.
     * @return null if no match -- rollback not done here use Narsive.$ for whitespace and rollback
     */
    fun genericAdvance(vararg exemplar: Byte): UnaryOperator<ByteBuffer> {

        return object : UnaryOperator<ByteBuffer> {

            private val bytes = exemplar

            override fun toString(): String {
                return asString()
            }


            fun asString(): String {
                return "advance->" + String(bytes.toByteArray())
            }

            override fun apply(target: ByteBuffer): ByteBuffer? {
                var c = 0
                while (target.hasRemaining() && c < exemplar.size && exemplar[c] == target.get()) {
                    c++
                }
                return if (c == exemplar.size) target else null
            }
        }
    }
}