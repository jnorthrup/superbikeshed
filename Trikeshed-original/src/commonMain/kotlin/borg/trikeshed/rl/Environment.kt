package borg.trikeshed.rl

/**
 * Represents an RL environment.
 * S: State type
 * A: Action type
 */
interface Environment<S, A> {
    /**
     * Resets the environment and returns the initial state.
     */
    fun reset(): S

    /**
     * Takes an action, returns (nextState, reward, done).
     */
    fun step(action: A): Triple<S, Double, Boolean>
}