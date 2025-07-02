package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.lib.allOf_.allOf
import borg.trikeshed.parse.bbcursive.std.bb

/**
 * Created by jim on 1/17/16.
 */
object confix_ {
    fun confixChars(operator: UnaryOperator<ByteBuffer>, vararg chars: Char): UnaryOperator<ByteBuffer> {
        return object : UnaryOperator<ByteBuffer> {
            override fun toString(): String {
                return "confix_:" + Arrays.toString(chars) + " : " + operator
            }

            override fun apply(buffer: ByteBuffer): ByteBuffer? {
                val chlit = chlit_.chlit(chars[0])
                val aChar = chars[if (2 > chars.size) 0 else 1]
                val chlit1 = chlit_.chlit(aChar)
                return bb(buffer, confixOperators(chlit, chlit1, operator))
            }
        }
    }

    fun confixOperators(before: UnaryOperator<ByteBuffer>, after: UnaryOperator<ByteBuffer>, operator: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {

        return object : UnaryOperator<ByteBuffer> {

            override fun toString(): String {
                return "confix" + Arrays.deepToString(arrayOf(before, operator, after))
            }

            override fun apply(buffer: ByteBuffer): ByteBuffer? {
                return bb(buffer, allOf(before, operator, after))
            }
        }
    }
}
