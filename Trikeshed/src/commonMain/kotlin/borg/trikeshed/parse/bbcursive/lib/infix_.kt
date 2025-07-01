package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.UnaryOperator
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

// Assuming this annotation will be defined in Kotlin
import borg.trikeshed.parse.bbcursive.ann.Infix as InfixAnn

/**
 * Created by jim on 1/17/16.
 */
interface infix_ {
    companion object {
        @InfixAnn
        suspend fun infix(vararg allOf: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {
            return object : UnaryOperator<ByteIndexedBuffer> {
                override suspend fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                    return std.bb(buffer, *allOf)
                }

                override fun toString(): String {
                    return "infix${allOf.contentDeepToString()}"
                }
            }
        }
    }
}
