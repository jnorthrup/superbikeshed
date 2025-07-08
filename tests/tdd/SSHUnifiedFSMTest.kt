package tests.tdd

import borg.trikeshed.ssh.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Unified FSM Test
 * 
 * Demonstrates the consolidated finite state machine approach for SSH
 * SCP, SFTP, and Rsync functionality. Shows how all state management
 * is unified into a single cohesive FSM.
 */

object SSHUnifiedFSMTest {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== SSH UNIFIED FSM TEST ===")
        println("Testing consolidated state management for SSH protocols...")
        println()
        
        runBlocking {
            testUnifiedFSM()
            testConnectionLifecycle()
            testProtocolTransitions()
            testErrorHandling()
            testTransferProgress()
            println("✅ All unified FSM tests completed successfully!")
        }
    }
    
    private suspend fun testUnifiedFSM() {
        println("🔧 Testing Unified FSM Creation and Basic Operations...")
        
        // Create connection and FSM
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Verify initial state
        assert(fsm.getCurrentState() is SSHState.Disconnected) { "Initial state should be Disconnected" }
        println("✅ Initial state: ${fsm.getCurrentState()}")
        
        // Test connection flow
        fsm.processEvent(SSHEvent.Connect)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Connecting) { "Should transition to Connecting" }
        println("✅ Connected state: ${fsm.getCurrentState()}")
        
        // Simulate connection success
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Connected) { "Should transition to Connected" }
        println("✅ Connected state: ${fsm.getCurrentState()}")
        
        // Test authentication flow
        fsm.processEvent(SSHEvent.Authenticate)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Authenticating) { "Should transition to Authenticating" }
        println("✅ Authenticating state: ${fsm.getCurrentState()}")
        
        // Simulate authentication success
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Authenticated) { "Should transition to Authenticated" }
        println("✅ Authenticated state: ${fsm.getCurrentState()}")
        
        println("✅ Unified FSM basic operations test passed!")
        println()
    }
    
    private suspend fun testConnectionLifecycle() {
        println("🔗 Testing Connection Lifecycle with Unified FSM...")
        
        val connectionManager = SSHConnectionManagerFactory.createManager()
        val connection = connectionManager.createConnection("test.example.com", 22)
        
        // Test connection establishment
        val connected = connectionManager.connect(connection)
        assert(connected) { "Connection should be established" }
        println("✅ Connection established: ${connectionManager.getConnectionStatus(connection)}")
        
        // Test authentication
        val authenticated = connectionManager.authenticate(connection, "testuser", "testpass")
        assert(authenticated) { "Authentication should succeed" }
        println("✅ Authentication successful: ${connectionManager.getConnectionStatus(connection)}")
        
        // Test channel opening
        val channel = connectionManager.openChannel(connection, "session")
        assert(channel != null) { "Channel should be opened" }
        println("✅ Channel opened: ${connectionManager.getConnectionStatus(connection)}")
        
        // Test channel closing
        val channelClosed = connectionManager.closeChannel(connection, channel!!)
        assert(channelClosed) { "Channel should be closed" }
        println("✅ Channel closed: ${connectionManager.getConnectionStatus(connection)}")
        
        // Test disconnection
        val disconnected = connectionManager.disconnect(connection)
        assert(disconnected) { "Connection should be disconnected" }
        println("✅ Connection disconnected: ${connectionManager.getConnectionStatus(connection)}")
        
        connectionManager.shutdown()
        println("✅ Connection lifecycle test passed!")
        println()
    }
    
    private suspend fun testProtocolTransitions() {
        println("🔄 Testing Protocol State Transitions...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to authenticated state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        delay(100)
        
        assert(fsm.getCurrentState() is SSHState.Authenticated) { "Should be in Authenticated state" }
        println("✅ Base state: ${fsm.getCurrentState()}")
        
        // Test SCP transition
        fsm.processEvent(SSHEvent.StartSCP)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should transition to SCPTransferring" }
        println("✅ SCP transition: ${fsm.getCurrentState()}")
        
        // Test SCP completion
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after SCP" }
        println("✅ SCP completion: ${fsm.getCurrentState()}")
        
        // Test SFTP transition
        fsm.processEvent(SSHEvent.StartSFTP)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.SFTPTransferring) { "Should transition to SFTPTransferring" }
        println("✅ SFTP transition: ${fsm.getCurrentState()}")
        
        // Test SFTP completion
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after SFTP" }
        println("✅ SFTP completion: ${fsm.getCurrentState()}")
        
        // Test Rsync transition
        fsm.processEvent(SSHEvent.StartRsync)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.RsyncTransferring) { "Should transition to RsyncTransferring" }
        println("✅ Rsync transition: ${fsm.getCurrentState()}")
        
        // Test Rsync completion
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should transition to Idle after Rsync" }
        println("✅ Rsync completion: ${fsm.getCurrentState()}")
        
        println("✅ Protocol transitions test passed!")
        println()
    }
    
    private suspend fun testErrorHandling() {
        println("⚠️ Testing Error Handling and Recovery...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to authenticated state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        delay(100)
        
        // Test error during SCP transfer
        fsm.processEvent(SSHEvent.StartSCP)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring state" }
        
        fsm.processEvent(SSHEvent.TransferError)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Error) { "Should transition to Error state" }
        println("✅ Error state reached: ${fsm.getCurrentState()}")
        
        // Test recovery
        fsm.processEvent(SSHEvent.Retry)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Recovering) { "Should transition to Recovering state" }
        println("✅ Recovery state reached: ${fsm.getCurrentState()}")
        
        // Test recovery completion
        fsm.processEvent(SSHEvent.RecoveryComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Connected) { "Should transition back to Connected state" }
        println("✅ Recovery completed: ${fsm.getCurrentState()}")
        
        // Test disconnection from error state
        fsm.processEvent(SSHEvent.StartSCP)
        fsm.processEvent(SSHEvent.TransferError)
        fsm.processEvent(SSHEvent.Disconnect)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Disconnected) { "Should disconnect from error state" }
        println("✅ Disconnection from error: ${fsm.getCurrentState()}")
        
        println("✅ Error handling test passed!")
        println()
    }
    
    private suspend fun testTransferProgress() {
        println("📊 Testing Transfer Progress Tracking...")
        
        val connection = SSHConnection(host = "test.example.com", port = 22)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // Get to SCP transfer state
        fsm.processEvent(SSHEvent.Connect)
        fsm.processEvent(SSHEvent.DataReceived(ByteArray(10)))
        fsm.processEvent(SSHEvent.Authenticate)
        fsm.processEvent(SSHEvent.AuthenticationSuccess)
        fsm.processEvent(SSHEvent.StartSCP)
        delay(100)
        
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring state" }
        
        // Simulate data transfer progress
        val testData = ByteArray(1024) { it.toByte() }
        for (i in 0..9) {
            fsm.processEvent(SSHEvent.DataReceived(testData))
            delay(50)
            println("✅ Transfer progress: ${i + 1}/10 chunks")
        }
        
        // Complete transfer
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after transfer" }
        println("✅ Transfer completed: ${fsm.getCurrentState()}")
        
        println("✅ Transfer progress test passed!")
        println()
    }
    
    private suspend fun testIntegratedWorkflow() {
        println("🔄 Testing Integrated Workflow with Unified FSM...")
        
        val connectionManager = SSHConnectionManagerFactory.createManager()
        val connection = connectionManager.createConnection("test.example.com", 22)
        
        // Establish connection
        val connected = connectionManager.connect(connection)
        assert(connected) { "Connection should be established" }
        
        val authenticated = connectionManager.authenticate(connection, "testuser", "testpass")
        assert(authenticated) { "Authentication should succeed" }
        
        // Test integrated workflow: SCP -> SFTP -> Rsync
        val fsm = SSHFSMFactory.createFSM(connection)
        
        // SCP transfer
        fsm.processEvent(SSHEvent.StartSCP)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.SCPTransferring) { "Should be in SCPTransferring" }
        println("✅ SCP transfer started")
        
        // Simulate SCP data transfer
        for (i in 0..4) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(512)))
            delay(50)
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after SCP" }
        println("✅ SCP transfer completed")
        
        // SFTP transfer
        fsm.processEvent(SSHEvent.StartSFTP)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.SFTPTransferring) { "Should be in SFTPTransferring" }
        println("✅ SFTP transfer started")
        
        // Simulate SFTP data transfer
        for (i in 0..3) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(1024)))
            delay(50)
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after SFTP" }
        println("✅ SFTP transfer completed")
        
        // Rsync transfer
        fsm.processEvent(SSHEvent.StartRsync)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.RsyncTransferring) { "Should be in RsyncTransferring" }
        println("✅ Rsync transfer started")
        
        // Simulate Rsync data transfer
        for (i in 0..2) {
            fsm.processEvent(SSHEvent.DataReceived(ByteArray(2048)))
            delay(50)
        }
        fsm.processEvent(SSHEvent.TransferComplete)
        delay(100)
        assert(fsm.getCurrentState() is SSHState.Idle) { "Should return to Idle after Rsync" }
        println("✅ Rsync transfer completed")
        
        // Cleanup
        connectionManager.disconnect(connection)
        connectionManager.shutdown()
        
        println("✅ Integrated workflow test passed!")
        println()
    }
} 