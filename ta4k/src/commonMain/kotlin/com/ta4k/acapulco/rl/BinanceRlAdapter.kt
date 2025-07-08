// =====================================================================
// === TrikeShed/src/commonMain/kotlin/borg/trikeshed/acapulco/rl/BinanceRlAdapter.kt ===
// =====================================================================
package borg.trikeshed.acapulco.rl

import borg.trikeshed.cursor.Cursor // Assuming Cursor is Indexed<RowVec>
import borg.trikeshed.rl.Agent
import borg.trikeshed.rl.Environment
import borg.trikeshed.cursor.RowVec

/**
 * Adapts a Binance cursor-driven simulator into an RL environment.
 * S: State (e.g., cursor row, which is RowVec)
 * A: Action type determined by the agent
 */
class BinanceRlAdapter<A>(
    internal val cursor: Cursor, // Cursor is Indexed<RowVec>
    internal val agent: Agent<RowVec, A>
) : Environment<RowVec, A> {
    internal lateinit var currentState: RowVec
    internal var done = false
    internal val cursorList: List<RowVec> = cursor.toList()  // Convert to list
    internal var cursorIterator: Iterator<RowVec> = cursorList.iterator()

    override fun reset(): RowVec {
        cursorIterator = cursorList.iterator() // Reset iterator using list
        if (!cursorIterator.hasNext()) throw IllegalStateException("Cursor is empty, cannot reset environment.")
        currentState = cursorIterator.next()
        done = false
        return currentState
    }

    override fun step(action: A): Triple<RowVec, Double, Boolean> {
        // TODO: execute action on simulator (e.g., place trade based on 'action' and 'currentState')
        // This part requires domain-specific logic for how actions affect the Binance simulation

        // advance cursor to next event
        done = !cursorIterator.hasNext()
        val nextState = if (!done) cursorIterator.next() else currentState
        val reward = 0.0 // TODO: compute reward based on action, currentState, and nextState
        currentState = nextState
        return Triple(nextState, reward, done)
    }
}