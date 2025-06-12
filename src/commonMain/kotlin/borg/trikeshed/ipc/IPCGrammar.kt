package borg.trikeshed.ipc

import borg.trikeshed.lib.ByteSeries
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * IPC Message Types
 */
enum class IPCMessageType {
    CONNECT,
    DISCONNECT,
    ACK,
    COMMAND,
    EVENT,
    ERROR
}

/**
 * IPC Message Origin
 */
enum class IPCOrigin {
    CLIENT,
    SERVER
}

/**
 * Base sealed class for all IPC messages
 */
@Serializable
sealed class IPCMessage {
    abstract val type: IPCMessageType
    abstract val origin: IPCOrigin
    abstract val messageId: String
    
    fun toJson(): String = Json.encodeToString(this)
}

/**
 * Connect message - sent when a client wants to establish a connection
 */
@Serializable
data class ConnectMessage(
    override val messageId: String,
    override val origin: IPCOrigin = IPCOrigin.CLIENT,
    val clientInfo: ClientInfo
) : IPCMessage() {
    override val type = IPCMessageType.CONNECT
}

/**
 * Disconnect message - sent when a client wants to close the connection
 */
@Serializable
data class DisconnectMessage(
    override val messageId: String,
    override val origin: IPCOrigin = IPCOrigin.CLIENT,
    val reason: String? = null
) : IPCMessage() {
    override val type = IPCMessageType.DISCONNECT
}

/**
 * Acknowledgment message - sent to confirm receipt of a message
 */
@Serializable
data class AckMessage(
    override val messageId: String,
    override val origin: IPCOrigin = IPCOrigin.SERVER,
    val originalMessageId: String,
    val success: Boolean,
    val error: String? = null
) : IPCMessage() {
    override val type = IPCMessageType.ACK
}

/**
 * Command message - sent to request an action
 */
@Serializable
data class CommandMessage(
    override val messageId: String,
    override val origin: IPCOrigin = IPCOrigin.CLIENT,
    val command: String,
    val parameters: Map<String, String>,
    val payload: ByteSeries? = null
) : IPCMessage() {
    override val type = IPCMessageType.COMMAND
}

/**
 * Event message - sent to notify about state changes
 */
@Serializable
data class EventMessage(
    override val messageId: String,
    override val origin: IPCOrigin = IPCOrigin.SERVER,
    val event: String,
    val data: Map<String, String>,
    val payload: ByteSeries? = null
) : IPCMessage() {
    override val type = IPCMessageType.EVENT
}

/**
 * Error message - sent when an error occurs
 */
@Serializable
data class ErrorMessage(
    override val messageId: String,
    override val origin: IPCOrigin,
    val code: Int,
    val message: String,
    val details: Map<String, String>? = null
) : IPCMessage() {
    override val type = IPCMessageType.ERROR
}

/**
 * Client information
 */
@Serializable
data class ClientInfo(
    val id: String,
    val name: String,
    val version: String,
    val capabilities: List<String>,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * IPC Protocol Constants
 */
object IPCProtocol {
    const val VERSION = "1.0"
    const val DELIMITER = "\u000C" // Form feed character
    const val MAX_MESSAGE_SIZE = 1024 * 1024 // 1MB
    const val DEFAULT_TIMEOUT = 30000L // 30 seconds
    
    // Error codes
    const val ERROR_INVALID_MESSAGE = 1001
    const val ERROR_TIMEOUT = 1002
    const val ERROR_CONNECTION_LOST = 1003
    const val ERROR_INTERNAL = 1004
    const val ERROR_UNAUTHORIZED = 1005
    const val ERROR_INVALID_COMMAND = 1006
    const val ERROR_INVALID_PARAMETERS = 1007
} 