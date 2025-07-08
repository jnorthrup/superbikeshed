package borg.trikeshed.ssh

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Connection Manager with Unified FSM
 * 
 * Manages SSH connections using the unified finite state machine approach.
 * Consolidates connection lifecycle, authentication, and channel management.
 */

class SSHConnectionManager(
    private val context: SSHConnectionContext = SSHConnectionContext()
) {
    private val connections = mutableMapOf<String, SSHConnection>()
    private val fsms = mutableMapOf<String, SSHUnifiedFSM>()
    private val scope = CoroutineScope(context.b + SupervisorJob())
    
    /**
     * Create and manage a new SSH connection
     */
    suspend fun createConnection(
        host: String,
        port: Int = SSHConstants.DEFAULT_PORT
    ): SSHConnection {
        val connectionId = "$host:$port"
        
        if (connections.containsKey(connectionId)) {
            return connections[connectionId]!!
        }
        
        val connection = SSHConnection(host = host, port = port)
        val fsm = SSHFSMFactory.createFSM(connection)
        
        connections[connectionId] = connection
        fsms[connectionId] = fsm
        
        // Start FSM processing
        scope.launch {
            processFSMEvents(connectionId, fsm)
        }
        
        return connection
    }
    
    /**
     * Connect to SSH server
     */
    suspend fun connect(connection: SSHConnection): Boolean {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return false
        
        fsm.processEvent(SSHEvent.Connect)
        
        // Wait for connection to complete
        var attempts = 0
        while (attempts < 30) { // 30 second timeout
            when (fsm.getCurrentState()) {
                is SSHState.Connected -> {
                    connection.isConnected = true
                    connection.state = SSHConnectionState.CONNECTED
                    return true
                }
                is SSHState.Error -> {
                    return false
                }
                else -> {
                    delay(1000)
                    attempts++
                }
            }
        }
        
        return false
    }
    
    /**
     * Authenticate SSH connection
     */
    suspend fun authenticate(
        connection: SSHConnection,
        username: String,
        password: String? = null,
        privateKey: String? = null
    ): Boolean {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return false
        
        fsm.processEvent(SSHEvent.Authenticate)
        
        // Simulate authentication process
        delay(500) // Simulate auth time
        
        val success = password != null || privateKey != null
        if (success) {
            fsm.processEvent(SSHEvent.AuthenticationSuccess)
            connection.isAuthenticated = true
            connection.state = SSHConnectionState.AUTHENTICATED
        } else {
            fsm.processEvent(SSHEvent.AuthenticationFailure)
        }
        
        return success
    }
    
    /**
     * Open SSH channel
     */
    suspend fun openChannel(
        connection: SSHConnection,
        channelType: String = "session"
    ): SSHChannel? {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return null
        
        fsm.processEvent(SSHEvent.OpenChannel)
        
        // Wait for channel to open
        var attempts = 0
        while (attempts < 10) {
            when (fsm.getCurrentState()) {
                is SSHState.ChannelOpen -> {
                    val channelId = connection.nextChannelId++
                    val channel = SSHChannel(
                        id = channelId,
                        type = channelType,
                        state = SSHChannelState.OPEN
                    )
                    connection.channels[channelId] = channel
                    return channel
                }
                is SSHState.Error -> {
                    return null
                }
                else -> {
                    delay(500)
                    attempts++
                }
            }
        }
        
        return null
    }
    
    /**
     * Close SSH channel
     */
    suspend fun closeChannel(connection: SSHConnection, channel: SSHChannel): Boolean {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return false
        
        fsm.processEvent(SSHEvent.CloseChannel)
        
        // Wait for channel to close
        var attempts = 0
        while (attempts < 10) {
            when (fsm.getCurrentState()) {
                is SSHState.ChannelClosed -> {
                    channel.state = SSHChannelState.CLOSED
                    connection.channels.remove(channel.id)
                    return true
                }
                is SSHState.Error -> {
                    return false
                }
                else -> {
                    delay(500)
                    attempts++
                }
            }
        }
        
        return false
    }
    
    /**
     * Disconnect SSH connection
     */
    suspend fun disconnect(connection: SSHConnection): Boolean {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return false
        
        fsm.processEvent(SSHEvent.Disconnect)
        
        // Wait for disconnection
        var attempts = 0
        while (attempts < 10) {
            when (fsm.getCurrentState()) {
                is SSHState.Disconnected -> {
                    connection.isConnected = false
                    connection.isAuthenticated = false
                    connection.state = SSHConnectionState.DISCONNECTED
                    connections.remove(connectionId)
                    fsms.remove(connectionId)
                    return true
                }
                is SSHState.Error -> {
                    return false
                }
                else -> {
                    delay(500)
                    attempts++
                }
            }
        }
        
        return false
    }
    
    /**
     * Get connection status
     */
    fun getConnectionStatus(connection: SSHConnection): SSHState {
        val connectionId = "${connection.host}:${connection.port}"
        val fsm = fsms[connectionId] ?: return SSHState.Disconnected
        return fsm.getCurrentState()
    }
    
    /**
     * Process FSM events in background
     */
    private suspend fun processFSMEvents(connectionId: String, fsm: SSHUnifiedFSM) {
        while (connections.containsKey(connectionId)) {
            fsm.tick()
            delay(100) // Tick every 100ms
        }
    }
    
    /**
     * Cleanup resources
     */
    fun shutdown() {
        scope.cancel()
        connections.clear()
        fsms.clear()
    }
}

// Factory for creating connection managers
object SSHConnectionManagerFactory {
    fun createManager(context: SSHConnectionContext = SSHConnectionContext()): SSHConnectionManager {
        return SSHConnectionManager(context)
    }
} 