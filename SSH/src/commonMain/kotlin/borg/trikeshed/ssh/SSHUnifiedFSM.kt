package borg.trikeshed.ssh

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Unified Finite State Machine
 * 
 * Consolidates all SSH state management into a single unified FSM that handles:
 * - Connection lifecycle states
 * - Channel management states  
 * - Protocol-specific states (SCP, SFTP, Rsync)
 * - Authentication states
 * - Error and recovery states
 */

// Unified SSH State
sealed class SSHState {
    // Connection States
    object Disconnected : SSHState()
    object Connecting : SSHState()
    object Connected : SSHState()
    object Authenticating : SSHState()
    object Authenticated : SSHState()
    
    // Channel States
    object ChannelOpening : SSHState()
    object ChannelOpen : SSHState()
    object ChannelClosing : SSHState()
    object ChannelClosed : SSHState()
    
    // Protocol States
    object SCPTransferring : SSHState()
    object SFTPTransferring : SSHState()
    object RsyncTransferring : SSHState()
    
    // Error States
    object Error : SSHState()
    object Recovering : SSHState()
    
    // Idle State
    object Idle : SSHState()
}

// Unified SSH Event
sealed class SSHEvent {
    // Connection Events
    object Connect : SSHEvent()
    object Disconnect : SSHEvent()
    object Authenticate : SSHEvent()
    object AuthenticationSuccess : SSHEvent()
    object AuthenticationFailure : SSHEvent()
    
    // Channel Events
    object OpenChannel : SSHEvent()
    object CloseChannel : SSHEvent()
    object ChannelOpened : SSHEvent()
    object ChannelClosed : SSHEvent()
    
    // Protocol Events
    object StartSCP : SSHEvent()
    object StartSFTP : SSHEvent()
    object StartRsync : SSHEvent()
    object TransferComplete : SSHEvent()
    object TransferError : SSHEvent()
    
    // Error Events
    object ErrorOccurred : SSHEvent()
    object Retry : SSHEvent()
    object RecoveryComplete : SSHEvent()
    
    // Data Events
    data class DataReceived(val data: ByteArray) : SSHEvent()
    data class DataSent(val data: ByteArray) : SSHEvent()
}

// Unified SSH Context
data class SSHFSMContext(
    val connection: SSHConnection,
    val currentChannel: SSHChannel? = null,
    val transferProgress: TransferProgress = TransferProgress(),
    val errorInfo: ErrorInfo? = null,
    val coroutineContext: CoroutineContext = Dispatchers.IO
)

// Transfer Progress
data class TransferProgress(
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val filesTransferred: Int = 0,
    val totalFiles: Int = 0,
    val currentFile: String = "",
    val percentage: Double = 0.0
)

// Error Information
data class ErrorInfo(
    val error: Throwable,
    val state: SSHState,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

// Unified SSH FSM
class SSHUnifiedFSM(
    private val context: SSHFSMContext
) {
    private var currentState: SSHState = SSHState.Disconnected
    private val stateHandlers = mutableMapOf<SSHState, SSHStateHandler>()
    private val eventQueue = mutableListOf<SSHEvent>()
    private var isProcessing = false
    
    init {
        initializeStateHandlers()
    }
    
    // State Handler Interface
    interface SSHStateHandler {
        fun onEnter(context: SSHFSMContext): SSHFSMContext
        fun onExit(context: SSHFSMContext): SSHFSMContext
        fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext>
        fun onTick(context: SSHFSMContext): SSHFSMContext
    }
    
    // Initialize all state handlers
    private fun initializeStateHandlers() {
        stateHandlers[SSHState.Disconnected] = DisconnectedHandler()
        stateHandlers[SSHState.Connecting] = ConnectingHandler()
        stateHandlers[SSHState.Connected] = ConnectedHandler()
        stateHandlers[SSHState.Authenticating] = AuthenticatingHandler()
        stateHandlers[SSHState.Authenticated] = AuthenticatedHandler()
        stateHandlers[SSHState.ChannelOpening] = ChannelOpeningHandler()
        stateHandlers[SSHState.ChannelOpen] = ChannelOpenHandler()
        stateHandlers[SSHState.ChannelClosing] = ChannelClosingHandler()
        stateHandlers[SSHState.ChannelClosed] = ChannelClosedHandler()
        stateHandlers[SSHState.SCPTransferring] = SCPTransferringHandler()
        stateHandlers[SSHState.SFTPTransferring] = SFTPTransferringHandler()
        stateHandlers[SSHState.RsyncTransferring] = RsyncTransferringHandler()
        stateHandlers[SSHState.Error] = ErrorHandler()
        stateHandlers[SSHState.Recovering] = RecoveringHandler()
        stateHandlers[SSHState.Idle] = IdleHandler()
    }
    
    // Main FSM methods
    suspend fun processEvent(event: SSHEvent) {
        eventQueue.add(event)
        if (!isProcessing) {
            processEventQueue()
        }
    }
    
    private suspend fun processEventQueue() {
        isProcessing = true
        while (eventQueue.isNotEmpty()) {
            val event = eventQueue.removeAt(0)
            val handler = stateHandlers[currentState]
            if (handler != null) {
                val (newState, newContext) = handler.handleEvent(event, context)
                transitionTo(newState, newContext)
            }
        }
        isProcessing = false
    }
    
    private fun transitionTo(newState: SSHState, newContext: SSHFSMContext) {
        val currentHandler = stateHandlers[currentState]
        val newHandler = stateHandlers[newState]
        
        // Exit current state
        currentHandler?.onExit(context)
        
        // Update state and context
        currentState = newState
        
        // Enter new state
        newHandler?.onEnter(newContext)
    }
    
    suspend fun tick() {
        val handler = stateHandlers[currentState]
        handler?.onTick(context)
    }
    
    fun getCurrentState(): SSHState = currentState
    
    // State Handlers Implementation
    private inner class DisconnectedHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Disconnected state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Disconnected state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.Connect -> SSHState.Connecting to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ConnectingHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Connecting state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Connecting state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.DataReceived -> SSHState.Connected to context
                is SSHEvent.ErrorOccurred -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("Connection failed"), currentState)
                )
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ConnectedHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Connected state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Connected state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.Authenticate -> SSHState.Authenticating to context
                is SSHEvent.Disconnect -> SSHState.Disconnected to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class AuthenticatingHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Authenticating state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Authenticating state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.AuthenticationSuccess -> SSHState.Authenticated to context
                is SSHEvent.AuthenticationFailure -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("Authentication failed"), currentState)
                )
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class AuthenticatedHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Authenticated state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Authenticated state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.OpenChannel -> SSHState.ChannelOpening to context
                is SSHEvent.StartSCP -> SSHState.SCPTransferring to context
                is SSHEvent.StartSFTP -> SSHState.SFTPTransferring to context
                is SSHEvent.StartRsync -> SSHState.RsyncTransferring to context
                is SSHEvent.Disconnect -> SSHState.Disconnected to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ChannelOpeningHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering ChannelOpening state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting ChannelOpening state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.ChannelOpened -> SSHState.ChannelOpen to context
                is SSHEvent.ErrorOccurred -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("Channel opening failed"), currentState)
                )
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ChannelOpenHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering ChannelOpen state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting ChannelOpen state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.CloseChannel -> SSHState.ChannelClosing to context
                is SSHEvent.StartSCP -> SSHState.SCPTransferring to context
                is SSHEvent.StartSFTP -> SSHState.SFTPTransferring to context
                is SSHEvent.StartRsync -> SSHState.RsyncTransferring to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ChannelClosingHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering ChannelClosing state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting ChannelClosing state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.ChannelClosed -> SSHState.ChannelClosed to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ChannelClosedHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering ChannelClosed state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting ChannelClosed state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.OpenChannel -> SSHState.ChannelOpening to context
                else -> SSHState.Idle to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class SCPTransferringHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering SCPTransferring state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting SCPTransferring state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.TransferComplete -> SSHState.Idle to context
                is SSHEvent.TransferError -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("SCP transfer failed"), currentState)
                )
                is SSHEvent.DataReceived -> {
                    // Update transfer progress
                    val newProgress = context.transferProgress.copy(
                        bytesTransferred = context.transferProgress.bytesTransferred + event.data.size
                    )
                    currentState to context.copy(transferProgress = newProgress)
                }
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class SFTPTransferringHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering SFTPTransferring state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting SFTPTransferring state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.TransferComplete -> SSHState.Idle to context
                is SSHEvent.TransferError -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("SFTP transfer failed"), currentState)
                )
                is SSHEvent.DataReceived -> {
                    val newProgress = context.transferProgress.copy(
                        bytesTransferred = context.transferProgress.bytesTransferred + event.data.size
                    )
                    currentState to context.copy(transferProgress = newProgress)
                }
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class RsyncTransferringHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering RsyncTransferring state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting RsyncTransferring state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.TransferComplete -> SSHState.Idle to context
                is SSHEvent.TransferError -> SSHState.Error to context.copy(
                    errorInfo = ErrorInfo(SSHException("Rsync transfer failed"), currentState)
                )
                is SSHEvent.DataReceived -> {
                    val newProgress = context.transferProgress.copy(
                        bytesTransferred = context.transferProgress.bytesTransferred + event.data.size
                    )
                    currentState to context.copy(transferProgress = newProgress)
                }
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class ErrorHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Error state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Error state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.Retry -> SSHState.Recovering to context
                is SSHEvent.Disconnect -> SSHState.Disconnected to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class RecoveringHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Recovering state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Recovering state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.RecoveryComplete -> SSHState.Connected to context
                is SSHEvent.ErrorOccurred -> SSHState.Error to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
    
    private inner class IdleHandler : SSHStateHandler {
        override fun onEnter(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Entering Idle state")
            return context
        }
        
        override fun onExit(context: SSHFSMContext): SSHFSMContext {
            println("FSM: Exiting Idle state")
            return context
        }
        
        override fun handleEvent(event: SSHEvent, context: SSHFSMContext): Pair<SSHState, SSHFSMContext> {
            return when (event) {
                is SSHEvent.OpenChannel -> SSHState.ChannelOpening to context
                is SSHEvent.StartSCP -> SSHState.SCPTransferring to context
                is SSHEvent.StartSFTP -> SSHState.SFTPTransferring to context
                is SSHEvent.StartRsync -> SSHState.RsyncTransferring to context
                is SSHEvent.Disconnect -> SSHState.Disconnected to context
                else -> currentState to context
            }
        }
        
        override fun onTick(context: SSHFSMContext): SSHFSMContext = context
    }
}

// FSM Factory for easy creation
object SSHFSMFactory {
    fun createFSM(connection: SSHConnection): SSHUnifiedFSM {
        val context = SSHFSMContext(connection = connection)
        return SSHUnifiedFSM(context)
    }
} 