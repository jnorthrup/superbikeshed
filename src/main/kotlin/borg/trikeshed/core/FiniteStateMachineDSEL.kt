sealed class State
data class Idle(val message: String = "") : State()
data class Animating(val currentFrame: Int) : State()
data class Paused(val pausedAt: Int) : State()
data class Completed(val completionMessage: String) : State()

sealed class Event
data class StartAnimation(val model: String) : Event()
data class PauseAnimation(val pauseAt: Int) : Event()
data class ResumeAnimation(val resumeFrom: Int) : Event()
data class CompleteAnimation(val completionMessage: String) : Event()

class FiniteStateMachineDSEL {
    private var currentState: State = Idle()

    fun transition(event: Event) {
        when (event) {
            is StartAnimation -> {
                currentState = Animating(0)
                // Logic to start animation
            }
            is PauseAnimation -> {
                if (currentState is Animating) {
                    currentState = Paused((currentState as Animating).currentFrame)
                    // Logic to pause animation
                }
            }
            is ResumeAnimation -> {
                if (currentState is Paused) {
                    currentState = Animating((currentState as Paused).pausedAt)
                    // Logic to resume animation
                }
            }
            is CompleteAnimation -> {
                currentState = Completed(completionMessage = "Animation Completed")
                // Logic to complete animation
            }
        }
    }

    fun getCurrentState(): State {
        return currentState
    }
}