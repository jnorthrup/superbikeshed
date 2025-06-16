package borg.trikeshed.rl

/**
 * Represents a reinforcement learning agent.
 * S: State type
 * A: Action type
 */
interface Agent<S, A> {
    /**
     * Selects an action given the current state.
     */
    fun selectAction(state: S): A

    /**
     * Learns from the transition (state, action, reward, nextState).
     */
    fun learn(state: S, action: A, reward: Double, nextState: S, done: Boolean)
}