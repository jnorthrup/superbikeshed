package borg.trikeshed.ipc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * IPC Client implementation
 */
class IPCClient(
    private val transport: IPCTransport,
    private val handler: IPCMessageHandler,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var isConnected = false
    private val pendingResponses = mutableMapOf<String, Channel<IPCMessage>>()
    
    init {
        startMessageProcessing()
    }
    
    private fun startMessageProcessing() {
        scope.launch {
            transport.receive().collect { message ->
                when (message) {
                    is AckMessage -> {
                        val channel = pendingResponses.remove(message.originalMessageId)
                        channel?.trySend(message)
                    }
                    is EventMessage -> {
                        handler.handleEvent(message)
                    }
                    is ErrorMessage -> {
                        handler.handleError(message)
                    }
                    else -> {
                        // Handle unexpected message type
                        handler.handleError(ErrorMessage(
                            messageId = UUID.randomUUID().toString(),
                            origin = IPCOrigin.SERVER,
                            code = IPCProtocol.ERROR_INVALID_MESSAGE,
                            message = "Unexpected message type: ${message.type}"
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Connect to the server
     */
    suspend fun connect(clientInfo: ClientInfo): Boolean {
        val message = ConnectMessage(
            messageId = UUID.randomUUID().toString(),
            clientInfo = clientInfo
        )
        
        val response = sendAndWaitForResponse(message)
        isConnected = response is AckMessage && response.success
        return isConnected
    }
    
    /**
     * Disconnect from the server
     */
    suspend fun disconnect(reason: String? = null): Boolean {
        val message = DisconnectMessage(
            messageId = UUID.randomUUID().toString(),
            reason = reason
        )
        
        val response = sendAndWaitForResponse(message)
        isConnected = false
        return response is AckMessage && response.success
    }
    
    /**
     * Send a command to the server
     */
    suspend fun sendCommand(command: String, parameters: Map<String, String>, payload: ByteSeries? = null): Boolean {
        val message = CommandMessage(
            messageId = UUID.randomUUID().toString(),
            command = command,
            parameters = parameters,
            payload = payload
        )
        
        val response = sendAndWaitForResponse(message)
        return response is AckMessage && response.success
    }
    
    /**
     * Send a message and wait for a response
     */
    private suspend fun sendAndWaitForResponse(message: IPCMessage): IPCMessage {
        val responseChannel = Channel<IPCMessage>(Channel.CONFLATED)
        pendingResponses[message.messageId] = responseChannel
        
        try {
            transport.send(message)
            return withTimeout(IPCProtocol.DEFAULT_TIMEOUT) {
                responseChannel.receive()
            }
        } finally {
            pendingResponses.remove(message.messageId)
            responseChannel.close()
        }
    }
    
    /**
     * Close the client
     */
    suspend fun close() {
        if (isConnected) {
            disconnect()
        }
        transport.close()
    }
} 