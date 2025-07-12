@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

// Temporary local Join type until trikeshed-lib is fixed
data class Join<A, B>(val a: A, val b: B)

infix fun <A, B> A.j(b: B): Join<A, B> = this j b

/**
 * UnaryAsyncReaction: WAM-Style Continuation for Event-Driven Attention
 * 
 * Each reaction is a unary function that takes a single event/context (SelectionKey)
 * and returns either a new (interest, reaction) pair (continuation) or null (termination).
 * 
 * This enables WAM-style continuation chains where each handler can "spin" the next
 * reaction and set what the reactor should pay attention to next.
 */
interface UnaryAsyncReaction {
    /**
     * Invoke this reaction with the given selection key.
     * 
     * @param key The selection key representing the event/context
     * @return A Join of (interest, nextReaction) to continue the chain, or null to terminate
     */
    operator fun invoke(key: SelectionKey): Join<Int, UnaryAsyncReaction>?
}
