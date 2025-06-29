package borg.trikeshed.rl

/**
 * A simple Q-learning stub implementation.
 * Not yet implemented.
 */
class QLearner<S, A> : Agent<S, A> {
    override fun selectAction(state: S): A {
        throw NotImplementedError("selectAction() not implemented")
    }

    override fun learn(state: S, action: A, reward: Double, nextState: S, done: Boolean) {
        // TODO: implement Q-learning update
    }
}