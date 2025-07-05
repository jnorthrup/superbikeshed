package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * SSH State Machine DSL
 * 
 * Provides a declarative way to define SSH protocol state transitions,
 * guards, and effects in a type-safe manner.
 */

// State machine builder
class SSHStateMachineBuilder {
    private val transitions = mutableListOf<StateTransitionRule>()
    private val entryActions = mutableMapOf<SSHState, SSHStateEffect>()
    private val exitActions = mutableMapOf<SSHState, SSHStateEffect>()
    
    // DSL entry point for state transitions
    infix fun from(state: SSHState): TransitionBuilder = TransitionBuilder(state)
    
    // DSL for entry/exit actions
    fun onEntry(state: SSHState, action: SSHStateEffect) {
        entryActions[state] = action
    }
    
    fun onExit(state: SSHState, action: SSHStateEffect) {
        exitActions[state] = action
    }
    
    // Transition builder
    inner class TransitionBuilder(private val fromState: SSHState) {
        infix fun on(event: SSHEvent): EffectBuilder = EffectBuilder(fromState, event)
    }
    
    // Effect builder with fluent interface
    inner class EffectBuilder(
        private val fromState: SSHState,
        private val event: SSHEvent
    ) {
        private var toState: SSHState? = null
        private var guard: SSHStateGuard? = null
        private val effects = mutableListOf<SSHStateEffect>()
        
        infix fun transitionTo(state: SSHState): EffectBuilder {
            toState = state
            return this
        }
        
        infix fun withEffect(effect: SSHStateEffect): EffectBuilder {
            effects.add(effect)
            return this
        }
        
        infix fun guardedBy(guard: SSHStateGuard): EffectBuilder {
            this.guard = guard
            return this
        }
        
        // Terminal operation to register the transition
        fun register() {
            val targetState = toState ?: fromState // Stay in same state if no transition
            transitions.add(
                StateTransitionRule(
                    fromState = fromState,
                    event = event,
                    toState = targetState,
                    guard = guard,
                    effects = effects.toList()
                )
            )
        }
    }
    
    // Build the state machine
    fun build(): SSHStateMachine {
        return SSHStateMachineImpl(
            transitions = transitions.toList(),
            entryActions = entryActions.toMap(),
            exitActions = exitActions.toMap()
        )
    }
}

// State transition rule
data class StateTransitionRule(
    val fromState: SSHState,
    val event: SSHEvent,
    val toState: SSHState,
    val guard: SSHStateGuard?,
    val effects: List<SSHStateEffect>
)

// State machine interface
interface SSHStateMachine {
    suspend fun processEvent(
        currentState: SSHTransportState,
        event: SSHEvent,
        context: SSHStateContext
    ): SSHTransportState
    
    fun getCurrentState(state: SSHTransportState): SSHState
}

// State machine implementation
class SSHStateMachineImpl(
    private val transitions: List<StateTransitionRule>,
    private val entryActions: Map<SSHState, SSHStateEffect>,
    private val exitActions: Map<SSHState, SSHStateEffect>
) : SSHStateMachine {
    
    override suspend fun processEvent(
        currentState: SSHTransportState,
        event: SSHEvent,
        context: SSHStateContext
    ): SSHTransportState = coroutineScope {
        val current = getCurrentState(currentState)
        
        // Find applicable transition
        val transition = transitions.find { rule ->
            rule.fromState == current && 
            rule.event == event &&
            (rule.guard?.invoke(currentState, context) ?: true)
        }
        
        if (transition == null) {
            // No valid transition, stay in current state
            return@coroutineScope currentState
        }
        
        // Execute exit action for current state
        exitActions[current]?.invoke(currentState, context)
        
        // Execute transition effects
        transition.effects.forEach { effect ->
            effect(currentState, context)
        }
        
        // Create new state
        val newState = createTransportState(transition.toState, currentState)
        
        // Execute entry action for new state
        entryActions[transition.toState]?.invoke(newState, context)
        
        return@coroutineScope newState
    }
    
    override fun getCurrentState(state: SSHTransportState): SSHState {
        // Extract current state from transport state
        return if (state.a > 0) {
            SSHState.values()[state[0].value]
        } else {
            SSHState.DISCONNECTED
        }
    }
    
    private fun createTransportState(state: SSHState, previous: SSHTransportState): SSHTransportState {
        // Create new transport state preserving context
        val stateToken = StateToken(state.ordinal)
        return 1 j { i: Int -> stateToken }
    }
}

// DSL function to create state machine
fun sshStateMachine(init: SSHStateMachineBuilder.() -> Unit): SSHStateMachine {
    val builder = SSHStateMachineBuilder()
    builder.init()
    return builder.build()
}

// Standard SSH state machine definition
fun createSSHProtocolStateMachine(): SSHStateMachine = sshStateMachine {
    // Initial connection
    from(SSHState.DISCONNECTED) on SSHEvents.CONNECT transitionTo SSHState.CONNECTING withEffect { state, context ->
        // Initialize transport
        val transport = SSHServiceLocator.getTransportService(context.b.b)
        // Connect to server
    } guardedBy { _, context ->
        // Only connect if we have valid server info
        true
    }
    
    // Version exchange
    from(SSHState.CONNECTING) on SSHEvents.VERSION_RECEIVED transitionTo SSHState.VERSION_EXCHANGE withEffect { state, context ->
        // Send our version string
    }
    
    // Key exchange initialization
    from(SSHState.VERSION_EXCHANGE) on SSHEvents.KEX_INIT_RECEIVED transitionTo SSHState.KEX_INIT withEffect { state, context ->
        // Negotiate algorithms
        val crypto = SSHServiceLocator.getCryptoService(context.b.b)
    }
    
    // DH key exchange
    from(SSHState.KEX_INIT) on SSHEvents.KEX_DH_REPLY_RECEIVED transitionTo SSHState.KEX_DH withEffect { state, context ->
        // Compute shared secret
    }
    
    // New keys
    from(SSHState.KEX_DH) on SSHEvents.NEW_KEYS_RECEIVED transitionTo SSHState.NEW_KEYS withEffect { state, context ->
        // Derive and install new keys
    }
    
    // Service request
    from(SSHState.NEW_KEYS) on SSHEvents.SERVICE_ACCEPT_RECEIVED transitionTo SSHState.SERVICE_REQUEST withEffect { state, context ->
        // Request user authentication service
    }
    
    // User authentication
    from(SSHState.SERVICE_REQUEST) on SSHEvents.USERAUTH_SUCCESS transitionTo SSHState.CONNECTED withEffect { state, context ->
        // Authentication successful
    }
    
    from(SSHState.SERVICE_REQUEST) on SSHEvents.USERAUTH_FAILURE transitionTo SSHState.SERVICE_REQUEST withEffect { state, context ->
        // Try next authentication method
    }
    
    // Disconnection from any state
    from(SSHState.CONNECTING) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.VERSION_EXCHANGE) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.KEX_INIT) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.KEX_DH) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.NEW_KEYS) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.SERVICE_REQUEST) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.USERAUTH) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    from(SSHState.CONNECTED) on SSHEvents.DISCONNECT transitionTo SSHState.DISCONNECTED
    
    // Error handling
    from(SSHState.CONNECTING) on SSHEvents.ERROR transitionTo SSHState.DISCONNECTED
    from(SSHState.VERSION_EXCHANGE) on SSHEvents.ERROR transitionTo SSHState.DISCONNECTED
    from(SSHState.KEX_INIT) on SSHEvents.ERROR transitionTo SSHState.DISCONNECTED
    from(SSHState.KEX_DH) on SSHEvents.ERROR transitionTo SSHState.DISCONNECTED
    
    // Entry/exit actions
    onEntry(SSHState.CONNECTING) { state, context ->
        // Log connection attempt
    }
    
    onExit(SSHState.CONNECTED) { state, context ->
        // Clean up resources
    }
    
    onEntry(SSHState.DISCONNECTED) { state, context ->
        // Reset state
    }
}

// Helper functions for state machine operations
suspend fun SSHStateMachine.connect(
    serverInfo: SSHServerInfo,
    context: CoroutineContext
): SSHTransportState {
    val initialState = 1 j { StateToken(SSHState.DISCONNECTED.ordinal) }
    val stateContext = Join(
        Join(initialState, context),
        0 j { SSHEvents.CONNECT } // Empty event queue with connect event
    )
    
    return processEvent(initialState, SSHEvents.CONNECT, stateContext)
}

suspend fun SSHStateMachine.disconnect(
    currentState: SSHTransportState,
    reason: SSHDisconnectReason,
    context: CoroutineContext
): SSHTransportState {
    val stateContext = Join(
        Join(currentState, context),
        1 j { SSHEvents.DISCONNECT }
    )
    
    return processEvent(currentState, SSHEvents.DISCONNECT, stateContext)
}

// Extension functions for state queries
fun SSHStateMachine.isConnected(state: SSHTransportState): Boolean {
    return getCurrentState(state) == SSHState.CONNECTED
}

fun SSHStateMachine.isDisconnected(state: SSHTransportState): Boolean {
    return getCurrentState(state) == SSHState.DISCONNECTED
}

fun SSHStateMachine.isAuthenticating(state: SSHTransportState): Boolean {
    return getCurrentState(state) in listOf(
        SSHState.SERVICE_REQUEST,
        SSHState.USERAUTH
    )
}

fun SSHStateMachine.isNegotiating(state: SSHTransportState): Boolean {
    return getCurrentState(state) in listOf(
        SSHState.VERSION_EXCHANGE,
        SSHState.KEX_INIT,
        SSHState.KEX_DH,
        SSHState.NEW_KEYS
    )
}