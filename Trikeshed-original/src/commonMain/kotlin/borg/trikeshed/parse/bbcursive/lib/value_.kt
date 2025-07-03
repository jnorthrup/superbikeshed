package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.ann.ForwardOnly
import borg.trikeshed.ann.Infix
import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.UnaryOperator
import borg.trikeshed.parse.bbcursive.lib.chlit_.chlit
import borg.trikeshed.parse.bbcursive.lib.infix_.infix
import borg.trikeshed.parse.bbcursive.lib.opt_.opt
import borg.trikeshed.parse.bbcursive.lib.repeat_.repeat

/**
 * Created by jim on 1/21/16.
 */
@Infix
@ForwardOnly
class value_ private constructor() : UnaryOperator<ByteIndexedBuffer> {

    companion object {
        val VALUE_ = value_()

        fun value(): value_ {
            return VALUE_
        }
    }

    override fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
        return infix(opt(chlit('0')), anyOf_.anyIn("1.0"), opt(repeat(anyOf_.anyIn("1029384756"))))?.invoke(buffer)
    }
}