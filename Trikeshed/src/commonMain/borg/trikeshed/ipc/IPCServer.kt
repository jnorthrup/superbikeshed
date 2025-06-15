package borg.trikeshed.ipc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * IPC Server implementation
 */
class IPCServer(
    private val handler: IPCMessageHandler,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val clients = ConcurrentHashMap<String, IPCTransport>()
    private var isRunning = false
    
    /**
     * Start the server
     */
    fun start() {
        isRunning = true
    }
    
    /**
     * Stop the server
     */
    suspend fun stop() {
        isRunning = false
        clients.values.forEach { it.close() }
        clients.clear()
    }
    
    /**
     * Add a client to the server
     */
    fun addClient(clientId: String, transport: IPCTransport) {
        clients[clientId] = transport
        startClientProcessing(clientId, transport)
    }
    
    /**
     * Remove a client from the server
     */
    suspend fun removeClient(clientId: String) {
        clients[clientId]?.close()
        clients.remove(clientId)
    }
    
    /**
     * Start processing messages for a client
     */
    private fun startClientProcessing(clientId: String, transport: IPCTransport) {
        scope.launch {
            transport.receive().collect { message ->
                when (message) {
                    is ConnectMessage -> {
                        val response = handler.handleConnect(message)
                        transport.send(response)
                    }
                    is DisconnectMessage -> {
                        val response = handler.handleDisconnect(message)
                        transport.send(response)
                        removeClient(clientId)
                    }
                    is CommandMessage -> {
                        val response = handler.handleCommand(message)
                        transport.send(response)
                    }
                    is EventMessage -> {
                        val response = handler.handleEvent(message)
                        transport.send(response)
                    }
                    is ErrorMessage -> {
                        val response = handler.handleError(message)
                        transport.send(response)
                    }
                    else -> {
                        // Handle unexpected message type
                        val error = ErrorMessage(
                            messageId = UUID.randomUUID().toString(),
                            origin = IPCOrigin.SERVER,
                            code = IPCProtocol.ERROR_INVALID_MESSAGE,
                            message = "Unexpected message type: ${message.type}"
                        )
                        transport.send(error)
                    }
                }
            }
        }
    }
    
    /**
     * Broadcast an event to all clients
     */
    suspend fun broadcastEvent(event: String, data: Map<String, String>, payload: ByteSeries? = null) {
        val message = EventMessage(
            messageId = UUID.randomUUID().toString(),
            event = event,
            data = data,
            payload = payload
        )
        
        clients.values.forEach { transport ->
            try {
                transport.send(message)
            } catch (e: Exception) {
                // Handle send error
            }
        }
    }
    
    /**
     * Send an event to a specific client
     */
    suspend fun sendEvent(clientId: String, event: String, data: Map<String, String>, payload: ByteSeries? = null) {
        val message = EventMessage(
            messageId = UUID.randomUUID().toString(),
            event = event,
            data = data,
            payload = payload
        )
        
        clients[clientId]?.send(message)
    }
} 