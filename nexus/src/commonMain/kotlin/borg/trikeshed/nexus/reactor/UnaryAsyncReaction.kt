package borg.trikeshed.nexus.reactor

import borg.trikeshed.lib.Join

/**
 * UnaryAsyncReaction: WAM-Style Continuation for Event-Driven Attention
 * 
 * Reclaimed from superbikeshed TrikeShed with full architectural artistry.
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

/**
 * AsyncReaction - Simplified reaction interface for common use cases
 */
interface AsyncReaction {
    suspend fun react(): AsyncReaction?
}

/**
 * Simple continuation reaction that chains operations
 */
class ContinuationReaction(
    private val operation: suspend () -> Unit,
    private val next: (() -> AsyncReaction?)? = null
) : AsyncReaction {
    override suspend fun react(): AsyncReaction? {
        operation()
        return next?.invoke()
    }
}

/**
 * Terminal reaction that ends the chain
 */
object TerminalReaction : AsyncReaction {
    override suspend fun react(): AsyncReaction? = null
}

/**
 * Utility functions for creating reactions
 */
fun reaction(operation: suspend () -> Unit): AsyncReaction = 
    ContinuationReaction(operation)

fun reaction(operation: suspend () -> Unit, next: () -> AsyncReaction?): AsyncReaction = 
    ContinuationReaction(operation, next)

fun terminalReaction(operation: suspend () -> Unit): AsyncReaction = 
    ContinuationReaction(operation) { TerminalReaction }