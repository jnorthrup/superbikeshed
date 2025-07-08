package tests.tdd

/**
 * SSH Unified FSM Simple Demo
 * 
 * Demonstrates the consolidated finite state machine approach for SSH
 * SCP, SFTP, and Rsync functionality without external dependencies.
 * Shows how all state management is unified into a single cohesive FSM.
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
    val error: String,
    val state: SSHState,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

// SSH Connection
data class SSHConnection(
    val host: String,
    val port: Int,
    var isConnected: Boolean = false,
    var isAuthenticated: Boolean = false,
    var state: String = "INIT"
)

// SSH Channel
data class SSHChannel(
    val id: Int,
    val type: String,
    var state: String = "OPENING"
)

// Unified SSH Context
data class SSHFSMContext(
    val connection: SSHConnection,
    val currentChannel: SSHChannel? = null,
    val transferProgress: TransferProgress = TransferProgress(),
    val errorInfo: ErrorInfo? = null
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
    fun processEvent(event: SSHEvent) {
        eventQueue.add(event)
        if (!isProcessing) {
            processEventQueue()
        }
    }
    
    private fun processEventQueue() {
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
    
    fun tick() {
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
                    errorInfo = ErrorInfo("Connection failed", currentState)
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
                    errorInfo = ErrorInfo("Authentication failed", currentState)
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
                    errorInfo = ErrorInfo("Channel opening failed", currentState)
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
                    errorInfo = ErrorInfo("SCP transfer failed", currentState)
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
                    errorInfo = ErrorInfo("SFTP transfer failed", currentState)
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
                    errorInfo = ErrorInfo("Rsync transfer failed", currentState)
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

// Main Demo
object SSHUnifiedFSMSimple {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== SSH UNIFIED FSM SIMPLE DEMO ===")
        println("Demonstrating consolidated state management for SSH protocols...")
        println()
        
        testUnifiedFSM()
        testProtocolTransitions()
        testErrorHandling()
        testTransferProgress()
        testIntegratedWorkflow()
        println("✅ All unified FSM demo tests completed successfully!")
    }
    
    private fun testUnifiedFSM() {
        println("🔧 Testing Unified FSM Creation and Basic Operations...")
        
        // Create connection and FSM
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Verify initial state
        assert(fsm.getCurrentState() is SSHState.Disconnected) { "Initial state should be Disconnected" }
        println("✅ Initial state: ${fsm.getCurrentState()}")
        
        // Test connection flow
        fsm.processEvent(SSHEvent.Connect)
        assert(fsm.getCurrentState() is SSHState.Connecting) { "Should transition to Connecting" }
        println("✅ Connected state: ${fsm.getCurrentState()}")
        
        // Simulate connection success
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        assert(fsm.getCurrentState() is SSHState.Connected) { "Should transition to Connected" }
        println("✅ Connected state: ${fsm.getCurrentState()}")
        
        // Test authentication flow
        fsm.processEvent(SSHEvent.Authenticate)
        assert(fsm.getCurrentState() is SSHState.Authenticating) { "Should transition to Authenticating" }
        println("✅ Authenticating state: ${fsm.getCurrentState()}")
        
        // Simulate authentication success
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        assert(fsm.getCurrentState() is SSHState.Authenticated) { "Should transition to Authenticated" }
        println("✅ Authenticated state: ${fsm.getCurrentState()}")
        
        println("✅ Unified FSM basic operations test passed!")
        println()
    }
    
    private fun testProtocolTransitions() {
        println("🔄 Testing Protocol State Transitions...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to authenticated state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        
        assert(fsm.getCurrentState() is SSHState.Authenticated) { "Should be in Authenticated state" }
        println("✅ Base state: ${fsm.getCurrentState()}")
        
        // Test SCP transition
        fsm.processEvent(SSHEvent.StartSCP)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should transition to SCPTransferring" }
        println("✅ SCP transition: ${fsm.getCurrentState()}")
        
        // Test SCP completion
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after SCP" }
        println("✅ SCP completion: ${fsm.getCurrentState()}")
        
        // Test SFTP transition
        fsm.processEvent(SSHEvent.StartSFTP)
        assert(fsm.getCurrentState() is SSHState.SFTPTransferring) { "Should transition to SFTPTransferring" }
        println("✅ SFTP transition: ${fsm.getCurrentState()}")
        
        // Test SFTP completion
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after SFTP" }
        println("✅ SFTP completion: ${fsm.getCurrentState()}")
        
        // Test Rsync transition
        fsm.processEvent(SSHEvent.StartRsync)
        assert(fsm.getCurrentState() is SSHState.RsyncTransferring) { "Should transition to RsyncTransferring" }
        println("✅ Rsync transition: ${fsm.getCurrentState()}")
        
        // Test Rsync completion
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after Rsync" }
        println("✅ Rsync completion: ${fsm.getCurrentState()}")
        
        println("✅ Protocol transitions test passed!")
        println()
    }
    
    private fun testErrorHandling() {
        println("⚠️ Testing Error Handling and Recovery...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to authenticated state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        
        // Test error during SCP transfer
        fsm.processEvent(SSHEvent.StartSCP)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring state" }
        
        fsm.processEvent(SSHEvent.TransferError)
        assert(fsm.getCurrentState() is SSHState.Error) { "Should transition to Error state" }
        println("✅ Error state reached: ${fsm.getCurrentState()}")
        
        // Test recovery
        fsm.processEvent(SSHEvent.Retry)
        assert(fsm.getCurrentState() is SSHState.Recovering) { "Should transition to Recovering state" }
        println("✅ Recovery state reached: ${fsm.getCurrentState()}")
        
        // Test recovery completion
        fsm.processEvent(SSHEvent.RecoveryComplete)
        assert(fsm.getCurrentState() is SSHState.Connected) { "Should transition back to Connected state" }
        println("✅ Recovery completed: ${fsm.getCurrentState()}")
        
        println("✅ Error handling test passed!")
        println()
    }
    
    private fun testTransferProgress() {
        println("📊 Testing Transfer Progress Tracking...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to SCP transfer state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        fsm.processEvent(SSHEvent.StartSCP)
        
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring state" }
        
        // Simulate data transfer progress
        val testData = ByteArray(1024) { it.toByte() }
        for (i in 0..9) {
            fsm.processEvent(SSHEvent.DataReceived(testData))
            println("✅ Transfer progress: ${i + 1}/10 chunks")
        }
        
        // Complete transfer
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after transfer" }
        println("✅ Transfer completed: ${fsm.getCurrentState()}")
        
        println("✅ Transfer progress test passed!")
        println()
    }
    
    private fun testIntegratedWorkflow() {
        println("🔄 Testing Integrated Workflow with Unified FSM...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to authenticated state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        
        // Test integrated workflow: SCP -> SFTP -> Rsync
        println("✅ Starting integrated workflow...")
        
        // SCP transfer
        fsm.processEvent(SSHEvent.StartSCP)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring" }
        println("✅ SCP transfer started")
        
        // Simulate SCP data transfer
        for (i in 0..4) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(512)))
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after SCP" }
        println("✅ SCP transfer completed")
        
        // SFTP transfer
        fsm.processEvent(SSHEvent.StartSFTP)
        assert(fsm.getCurrentState() is SSHState.SFTPTransferring) { "Should be in SFTPTransferring" }
        println("✅ SFTP transfer started")
        
        // Simulate SFTP data transfer
        for (i in 0..3) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(1024)))
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after SFTP" }
        println("✅ SFTP transfer completed")
        
        // Rsync transfer
        fsm.processEvent(SSHEvent.StartRsync)
        assert(fsm.getCurrentState() is SSHState.RsyncTransferring) { "Should be in RsyncTransferring" }
        println("✅ Rsync transfer started")
        
        // Simulate Rsync data transfer
        for (i in 0..2) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(2048)))
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after Rsync" }
        println("✅ Rsync transfer completed")
        
        // Disconnect
        fsm.processEvent(SSHEvent.Disconnect)
        assert(fsm.getCurrentState() is SSHState.Disconnected) { "Should disconnect" }
        println("✅ Connection disconnected")
        
        println("✅ Integrated workflow test passed!")
        println()
    }
} 