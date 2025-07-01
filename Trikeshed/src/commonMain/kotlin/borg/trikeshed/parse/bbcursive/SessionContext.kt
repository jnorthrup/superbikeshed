package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Represents a parsing session's flags (traits) and outbox for publishing results.
 * This element will be stored in the CoroutineContext.
 */
class SessionContext(
    val flags: MutableSet<Traits>,
    val outbox: (ParseResult) -> Unit
) : AbstractCoroutineContextElement(SessionContext) {
    companion object Key : CoroutineContext.Key<SessionContext>
}

/**
 * Represents the result of a parse operation, including the parsed buffer,
 * the operator that produced it, and the start/end positions.
 */
data class ParseResult(
    val buffer: ByteIndexedBuffer,
    val operator: UnaryOperator<ByteIndexedBuffer>,
    val startPosition: Int,
    val endPosition: Int,
    val traits: Set<Traits>
)

/**
 * Defines the traits (flags) that can influence parsing behavior.
 */
enum class Traits {
    DEBUG, BACKTRACKING, SKIPPER
}

// Define a typealias for UnaryOperator for clarity in this context
typealias UnaryOperator<T> = (T) -> T
