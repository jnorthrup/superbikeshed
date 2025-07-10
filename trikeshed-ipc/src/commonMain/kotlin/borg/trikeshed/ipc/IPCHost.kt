@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipc

import kotlinx.datetime.Clock
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

// === IPC TAXONOMICAL TYPEALIASES ===

typealias ProcessId = Int
typealias ChannelId = String
typealias MessageId = Long
typealias MessagePayload = Indexed<Byte>
typealias IPCAddress = String
typealias IPCPort = Int

// Message types
data class MessageType(val value: UByte)
data class MessageFlags(val value: UByte)
data class MessagePriority(val value: UByte)

// IPC modes
data class IPCMode(val value: String)
data class TransportType(val value: String)

// === IPC MESSAGE PROTOCOL ===

/**
 * IPC Message structure for cross-VM communication
 */
data class IPCMessage(
    val id: MessageId,
    val type: MessageType,
    val source: ProcessId,
    val destination: ProcessId,
    val channelId: ChannelId,
    val payload: MessagePayload,
    val flags: MessageFlags = MessageFlags(0u),
    val priority: MessagePriority = MessagePriority(5u),
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * IPC Channel for bidirectional communication
 */
interface IPCChannel {
    val id: ChannelId
    val mode: IPCMode
    
    suspend fun send(message: IPCMessage): Boolean
    suspend fun receive(): IPCMessage?
    suspend fun close()
    
    fun isOpen(): Boolean
    fun messageCount(): Int
}

/**
 * IPC Host manages cross-VM communication
 */
abstract class IPCHost(
    val hostId: ProcessId,
    val transportType: TransportType
) {
    internal val channels = mutableMapOf<ChannelId, IPCChannel>()
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Create a new IPC channel
     */
    abstract suspend fun createChannel(
        channelId: ChannelId,
        mode: IPCMode,
        config: IPCChannelConfig = IPCChannelConfig()
    ): IPCChannel
    
    /**
     * Connect to an existing IPC channel
     */
    abstract suspend fun connectChannel(
        address: IPCAddress,
        port: IPCPort,
        channelId: ChannelId
    ): IPCChannel
    
    /**
     * Send message to specific process
     */
    suspend fun sendTo(
        destination: ProcessId,
        channelId: ChannelId,
        payload: MessagePayload,
        type: MessageType = MessageTypes.DATA
    ): Boolean {
        val channel = channels[channelId] ?: return false
        
        val message = IPCMessage(
            id = generateMessageId(),
            type = type,
            source = hostId,
            destination = destination,
            channelId = channelId,
            payload = payload
        )
        
        return channel.send(message)
    }
    
    /**
     * Broadcast message to all connected processes
     */
    suspend fun broadcast(
        channelId: ChannelId,
        payload: MessagePayload,
        type: MessageType = MessageTypes.BROADCAST
    ) {
        channels.values
            .filter { it.id == channelId && it.isOpen() }
            .forEach { channel ->
                val message = IPCMessage(
                    id = generateMessageId(),
                    type = type,
                    source = hostId,
                    destination = -1, // Broadcast
                    channelId = channelId,
                    payload = payload
                )
                channel.send(message)
            }
    }
    
    /**
     * Subscribe to channel messages
     */
    fun subscribe(channelId: ChannelId): Flow<IPCMessage> = flow {
        val channel = channels[channelId] ?: return@flow
        
        while (channel.isOpen()) {
            channel.receive()?.let { message ->
                if (message.destination == hostId || message.destination == -1) {
                    emit(message)
                }
            }
            delay(10) // Small delay to prevent busy waiting
        }
    }
    
    /**
     * Close specific channel
     */
    suspend fun closeChannel(channelId: ChannelId) {
        channels[channelId]?.close()
        channels.remove(channelId)
    }
    
    /**
     * Shutdown the IPC host
     */
    open suspend fun shutdown() {
        channels.values.forEach { it.close() }
        channels.clear()
        scope.cancel()
    }
    
    internal var messageCounter = 0L
    internal fun generateMessageId(): MessageId = ++messageCounter
}

/**
 * IPC Channel configuration
 */
data class IPCChannelConfig(
    val bufferSize: Int = 1024,
    val maxMessageSize: Int = 1024 * 1024, // 1MB
    val timeout: Long = 5000, // 5 seconds
    val retryCount: Int = 3,
    val enableEncryption: Boolean = false,
    val enableCompression: Boolean = false
)

/**
 * Common message types
 */
object MessageTypes {
    val HANDSHAKE = MessageType(1u)
    val DATA = MessageType(2u)
    val ACK = MessageType(3u)
    val NACK = MessageType(4u)
    val PING = MessageType(5u)
    val PONG = MessageType(6u)
    val CLOSE = MessageType(7u)
    val ERROR = MessageType(8u)
    val BROADCAST = MessageType(9u)
    val SUBSCRIBE = MessageType(10u)
    val UNSUBSCRIBE = MessageType(11u)
}

/**
 * IPC modes
 */
object IPCModes {
    val SHARED_MEMORY = IPCMode("shared_memory")
    val NAMED_PIPE = IPCMode("named_pipe")
    val UNIX_SOCKET = IPCMode("unix_socket")
    val TCP_SOCKET = IPCMode("tcp_socket")
    val MESSAGE_QUEUE = IPCMode("message_queue")
}

/**
 * Transport types
 */
object TransportTypes {
    val NATIVE = TransportType("native")
    val JVM = TransportType("jvm")
    val WASM = TransportType("wasm")
    val HYBRID = TransportType("hybrid")
}

/**
 * DSL for IPC configuration
 */
class IPCBuilder {
    internal var hostId: ProcessId = 0
    internal var transportType: TransportType = TransportTypes.NATIVE
    internal val channelConfigs = mutableMapOf<ChannelId, IPCChannelConfig>()
    
    fun hostId(id: ProcessId) {
        this.hostId = id
    }
    
    fun transport(type: TransportType) {
        this.transportType = type
    }
    
    fun channel(id: ChannelId, config: IPCChannelConfig.() -> Unit = {}) {
        channelConfigs[id] = IPCChannelConfig().apply(config)
    }
    
    suspend fun build(): IPCHost {
        return when (transportType) {
            TransportTypes.NATIVE -> createNativeIPCHost(hostId)
            TransportTypes.JVM -> createJVMIPCHost(hostId)
            TransportTypes.WASM -> createWASMIPCHost(hostId)
            else -> createHybridIPCHost(hostId)
        }
    }
}

/**
 * DSL entry point
 */
suspend fun ipcHost(init: IPCBuilder.() -> Unit): IPCHost {
    return IPCBuilder().apply(init).build()
}

// Platform-specific factory functions (to be implemented)
expect suspend fun createNativeIPCHost(hostId: ProcessId): IPCHost
expect suspend fun createJVMIPCHost(hostId: ProcessId): IPCHost
expect suspend fun createWASMIPCHost(hostId: ProcessId): IPCHost
expect suspend fun createHybridIPCHost(hostId: ProcessId): IPCHost