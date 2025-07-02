package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * Created by jim on 1/17/16.
 */
object log {
    fun log(message: String): UnaryOperator<ByteBuffer> {
        return UnaryOperator { b ->
            System.err.println(message)
            b
        }
    }
}
