package borg.trikeshed.ipc

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Base implementation of IPCMessageHandler
 */
abstract class BaseIPCMessageHandler : IPCMessageHandler {
    private val eventChannel = Channel<EventMessage>(Channel.BUFFERED)
    private val errorChannel = Channel<ErrorMessage>(Channel.BUFFERED)
    
    override suspend fun handleConnect(message: ConnectMessage): AckMessage {
        return try {
            onConnect(message)
            AckMessage(
                messageId = generateMessageId(),
                originalMessageId = message.messageId,
                success = true
            )
        } catch (e: Exception) {
            handleException(e, message.messageId)
        }
    }
    
    override suspend fun handleDisconnect(message: DisconnectMessage): AckMessage {
        return try {
            onDisconnect(message)
            AckMessage(
                messageId = generateMessageId(),
                originalMessageId = message.messageId,
                success = true
            )
        } catch (e: Exception) {
            handleException(e, message.messageId)
        }
    }
    
    override suspend fun handleCommand(message: CommandMessage): AckMessage {
        return try {
            onCommand(message)
            AckMessage(
                messageId = generateMessageId(),
                originalMessageId = message.messageId,
                success = true
            )
        } catch (e: Exception) {
            handleException(e, message.messageId)
        }
    }
    
    override suspend fun handleEvent(message: EventMessage): AckMessage {
        return try {
            onEvent(message)
            AckMessage(
                messageId = generateMessageId(),
                originalMessageId = message.messageId,
                success = true
            )
        } catch (e: Exception) {
            handleException(e, message.messageId)
        }
    }
    
    override suspend fun handleError(message: ErrorMessage): AckMessage {
        return try {
            onError(message)
            AckMessage(
                messageId = generateMessageId(),
                originalMessageId = message.messageId,
                success = true
            )
        } catch (e: Exception) {
            handleException(e, message.messageId)
        }
    }
    
    override fun getEventFlow(): Flow<EventMessage> = flow {
        for (event in eventChannel) {
            emit(event)
        }
    }
    
    override fun getErrorFlow(): Flow<ErrorMessage> = flow {
        for (error in errorChannel) {
            emit(error)
        }
    }
    
    /**
     * Called when a connect message is received
     */
    protected abstract suspend fun onConnect(message: ConnectMessage)
    
    /**
     * Called when a disconnect message is received
     */
    protected abstract suspend fun onDisconnect(message: DisconnectMessage)
    
    /**
     * Called when a command message is received
     */
    protected abstract suspend fun onCommand(message: CommandMessage)
    
    /**
     * Called when an event message is received
     */
    protected abstract suspend fun onEvent(message: EventMessage)
    
    /**
     * Called when an error message is received
     */
    protected abstract suspend fun onError(message: ErrorMessage)
    
    /**
     * Send an event message
     */
    protected suspend fun sendEvent(event: EventMessage) {
        eventChannel.send(event)
    }
    
    /**
     * Send an error message
     */
    protected suspend fun sendError(error: ErrorMessage) {
        errorChannel.send(error)
    }
    
    /**
     * Handle an exception
     */
    private suspend fun handleException(e: Exception, originalMessageId: String): AckMessage {
        val error = ErrorMessage(
            messageId = generateMessageId(),
            origin = IPCOrigin.SERVER,
            code = IPCProtocol.ERROR_INTERNAL,
            message = e.message ?: "Internal error",
            details = mapOf("exception" to e.toString())
        )
        errorChannel.send(error)
        return AckMessage(
            messageId = generateMessageId(),
            originalMessageId = originalMessageId,
            success = false,
            error = e.message
        )
    }
    
    /**
     * Generate a unique message ID
     */
    protected fun generateMessageId(): String = UUID.randomUUID().toString()
} 