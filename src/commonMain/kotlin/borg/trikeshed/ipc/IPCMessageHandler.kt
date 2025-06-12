package borg.trikeshed.ipc

import kotlinx.coroutines.flow.Flow

/**
 * Interface for handling IPC messages
 */
interface IPCMessageHandler {
    /**
     * Handle a connect message
     */
    suspend fun handleConnect(message: ConnectMessage): AckMessage
    
    /**
     * Handle a disconnect message
     */
    suspend fun handleDisconnect(message: DisconnectMessage): AckMessage
    
    /**
     * Handle a command message
     */
    suspend fun handleCommand(message: CommandMessage): AckMessage
    
    /**
     * Handle an event message
     */
    suspend fun handleEvent(message: EventMessage): AckMessage
    
    /**
     * Handle an error message
     */
    suspend fun handleError(message: ErrorMessage): AckMessage
    
    /**
     * Get a flow of events from the handler
     */
    fun getEventFlow(): Flow<EventMessage>
    
    /**
     * Get a flow of errors from the handler
     */
    fun getErrorFlow(): Flow<ErrorMessage>
} 