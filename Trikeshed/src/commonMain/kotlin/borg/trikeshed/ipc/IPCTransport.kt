package borg.trikeshed.ipc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Interface for IPC transport layer
 */
interface IPCTransport {
    /**
     * Send a message
     */
    suspend fun send(message: IPCMessage)
    
    /**
     * Receive messages
     */
    fun receive(): Flow<IPCMessage>
    
    /**
     * Close the transport
     */
    suspend fun close()
}

/**
 * Base implementation of IPCTransport that handles message serialization
 */
abstract class BaseIPCTransport : IPCTransport {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    override suspend fun send(message: IPCMessage) {
        val jsonString = json.encodeToString(message)
        sendRaw(jsonString + IPCProtocol.DELIMITER)
    }
    
    override fun receive(): Flow<IPCMessage> = flow {
        for (rawMessage in receiveRaw()) {
            try {
                val message = json.decodeFromString<IPCMessage>(rawMessage)
                emit(message)
            } catch (e: Exception) {
                // Handle deserialization error
                emit(ErrorMessage(
                    messageId = UUID.randomUUID().toString(),
                    origin = IPCOrigin.SERVER,
                    code = IPCProtocol.ERROR_INVALID_MESSAGE,
                    message = "Failed to deserialize message",
                    details = mapOf(
                        "error" to e.message ?: "Unknown error",
                        "rawMessage" to rawMessage
                    )
                ))
            }
        }
    }
    
    /**
     * Send raw data
     */
    protected abstract suspend fun sendRaw(data: String)
    
    /**
     * Receive raw data
     */
    protected abstract fun receiveRaw(): Flow<String>
} 