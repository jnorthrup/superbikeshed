package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.Traits
import borg.trikeshed.parse.bbcursive.UnaryOperator
import borg.trikeshed.parse.bbcursive.SessionContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

// Assuming these annotations will be defined in Kotlin
import borg.trikeshed.parse.bbcursive.ann.Backtracking as BacktrackingAnn

/**
 * Created by jim on 1/17/16.
 */
interface backtrack_ {
    companion object {
        @BacktrackingAnn
        suspend fun backtracker(vararg allOf: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {
            return object : UnaryOperator<ByteIndexedBuffer> {
                override suspend fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
                    val sessionContext = coroutineContext[SessionContext.Key]
                        ?: throw IllegalStateException("SessionContext not found in CoroutineContext")

                    sessionContext.flags.add(Traits.SKIPPER) // Add skipper trait

                    return std.bb(buffer, *allOf)
                }

                override fun toString(): String {
                    return "backtracker${allOf.contentDeepToString()}"
                }
            }
        }
    }
}
