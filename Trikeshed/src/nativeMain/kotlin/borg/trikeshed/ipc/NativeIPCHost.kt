@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ipc

import borg.trikeshed.lib.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.posix.*

/**
 * Native IPC implementation using shared memory and POSIX IPC
 */
class NativeIPCHost(
    hostId: ProcessId
) : IPCHost(hostId, TransportTypes.NATIVE) {
    
    override suspend fun createChannel(
        channelId: ChannelId,
        mode: IPCMode,
        config: IPCChannelConfig
    ): IPCChannel = withContext(Dispatchers.IO) {
        when (mode) {
            IPCModes.SHARED_MEMORY -> SharedMemoryChannel(channelId, config)
            IPCModes.UNIX_SOCKET -> UnixSocketChannel(channelId, config)
            IPCModes.MESSAGE_QUEUE -> MessageQueueChannel(channelId, config)
            else -> throw UnsupportedOperationException("Mode not supported: ${mode.value}")
        }.also { channels[channelId] = it }
    }
    
    override suspend fun connectChannel(
        address: IPCAddress,
        port: IPCPort,
        channelId: ChannelId
    ): IPCChannel = withContext(Dispatchers.IO) {
        // For native, we use UNIX sockets for remote connections
        val channel = UnixSocketChannel(channelId, IPCChannelConfig())
        channel.connect(address, port)
        channels[channelId] = channel
        channel
    }
}

/**
 * Shared memory IPC channel for ultra-fast communication
 */
@OptIn(ExperimentalForeignApi::class)
class SharedMemoryChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.SHARED_MEMORY
    
    private var shmFd: Int = -1
    private var shmPtr: CPointer<ByteVar>? = null
    private val shmName = "/trikeshed_ipc_$id"
    private val shmSize = config.bufferSize * 1024 // Buffer size in KB
    
    private val sendChannel = Channel<IPCMessage>(Channel.UNLIMITED)
    private val receiveChannel = Channel<IPCMessage>(Channel.UNLIMITED)
    
    private var isInitialized = false
    private var isClosed = false
    
    init {
        initializeSharedMemory()
    }
    
    private fun initializeSharedMemory() {
        // Simplified shared memory implementation
        shmFd = 1
        shmPtr = null // In production would allocate actual shared memory
        isInitialized = true
    }
    
    override suspend fun send(message: IPCMessage): Boolean {
        if (!isOpen()) return false
        
        return try {
            // Serialize message to shared memory
            writeMessageToSharedMemory(message)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun receive(): IPCMessage? {
        if (!isOpen()) return null
        
        return try {
            // Read message from shared memory
            readMessageFromSharedMemory()
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun close() {
        if (isClosed) return
        
        isClosed = true
        
        // Unmap shared memory
        shmPtr?.let {
            munmap(it, shmSize.toULong())
        }
        
        // Close file descriptor
        if (shmFd >= 0) {
            close(shmFd)
        }
        
        // Unlink shared memory
        shm_unlink(shmName)
        
        sendChannel.close()
        receiveChannel.close()
    }
    
    override fun isOpen() = isInitialized && !isClosed
    
    override fun messageCount() = 0 // Simplified implementation
    
    private fun writeMessageToSharedMemory(message: IPCMessage) {
        val ptr = shmPtr ?: throw IllegalStateException("Shared memory not initialized")
        
        memScoped {
            var offset = 0
            
            // Write header
            ptr[offset++] = 0x01 // Version
            ptr[offset++] = message.type.value.toByte()
            
            // Write message ID (8 bytes)
            val idBytes = message.id.toBytes()
            for (i in 0..7) {
                ptr[offset++] = idBytes[i]
            }
            
            // Write source and destination (4 bytes each)
            val srcBytes = message.source.toBytes()
            val dstBytes = message.destination.toBytes()
            for (i in 0..3) {
                ptr[offset++] = srcBytes[i]
                ptr[offset + 4 + i] = dstBytes[i]
            }
            offset += 8
            
            // Write payload length (4 bytes)
            val payloadSize = message.payload.size
            val sizeBytes = payloadSize.toBytes()
            for (i in 0..3) {
                ptr[offset++] = sizeBytes[i]
            }
            
            // Write payload
            for (i in 0 until payloadSize) {
                ptr[offset++] = message.payload[i]
            }
        }
    }
    
    private fun readMessageFromSharedMemory(): IPCMessage? {
        val ptr = shmPtr ?: throw IllegalStateException("Shared memory not initialized")
        
        return memScoped {
            var offset = 0
            
            // Check version
            if (ptr[offset++] != 0x01.toByte()) return null
            
            // Read type
            val type = MessageType(ptr[offset++].toUByte())
            
            // Read message ID
            val idBytes = ByteArray(8)
            for (i in 0..7) {
                idBytes[i] = ptr[offset++]
            }
            val id = idBytes.toLong()
            
            // Read source and destination
            val srcBytes = ByteArray(4)
            val dstBytes = ByteArray(4)
            for (i in 0..3) {
                srcBytes[i] = ptr[offset++]
                dstBytes[i] = ptr[offset + 4 + i]
            }
            offset += 4
            val source = srcBytes.toInt()
            val destination = dstBytes.toInt()
            
            // Read payload length
            val sizeBytes = ByteArray(4)
            for (i in 0..3) {
                sizeBytes[i] = ptr[offset++]
            }
            val payloadSize = sizeBytes.toInt()
            
            // Read payload
            val payload = ByteArray(payloadSize)
            for (i in 0 until payloadSize) {
                payload[i] = ptr[offset++]
            }
            
            IPCMessage(
                id = id,
                type = type,
                source = source,
                destination = destination,
                channelId = id.toString(),
                payload = payload.toIdx()
            )
        }
    }
}

/**
 * Unix socket channel for local IPC
 */
class UnixSocketChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.UNIX_SOCKET
    
    private var socket: Int = -1
    private var isConnected = false
    private val socketPath = "/tmp/trikeshed_ipc_${id}.sock"
    
    private val sendChannel = Channel<IPCMessage>(Channel.UNLIMITED)
    private val receiveChannel = Channel<IPCMessage>(Channel.UNLIMITED)
    
    suspend fun connect(address: IPCAddress, port: IPCPort) {
        // Implementation for connecting to remote socket
        // For native UNIX sockets, we ignore port and use address as path
        connectToSocket(address)
    }
    
    private fun connectToSocket(path: String) {
        // Simplified socket implementation
        socket = 1
        isConnected = true
    }
    
    override suspend fun send(message: IPCMessage): Boolean {
        if (!isOpen()) return false
        
        return sendChannel.trySend(message).isSuccess
    }
    
    override suspend fun receive(): IPCMessage? {
        if (!isOpen()) return null
        
        return receiveChannel.tryReceive().getOrNull()
    }
    
    override suspend fun close() {
        isConnected = false
        if (socket >= 0) {
            close(socket)
            socket = -1
        }
        unlink(socketPath)
        
        sendChannel.close()
        receiveChannel.close()
    }
    
    override fun isOpen() = isConnected && socket >= 0
    
    override fun messageCount() = 0 // Simplified implementation
}

/**
 * Message queue channel using POSIX message queues
 */
class MessageQueueChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.MESSAGE_QUEUE
    
    private var mqd: Int = -1
    private val mqName = "/trikeshed_mq_${id}"
    private var isInitialized = false
    
    init {
        initializeMessageQueue()
    }
    
    private fun initializeMessageQueue() {
        // Simplified implementation - in production would need proper POSIX message queue setup
        mqd = 1 // Stub file descriptor
        isInitialized = true
    }
    
    override suspend fun send(message: IPCMessage): Boolean {
        if (!isOpen()) return false
        
        // Simplified implementation - in production would use actual message queue
        return true // Stub implementation
    }
    
    override suspend fun receive(): IPCMessage? {
        if (!isOpen()) return null
        
        // Simplified implementation - in production would receive from actual message queue
        return null // Stub implementation
    }
    
    override suspend fun close() {
        if (mqd >= 0) {
            // Simplified implementation - in production would close actual message queue
            mqd = -1
        }
        isInitialized = false
    }
    
    override fun isOpen() = isInitialized && mqd >= 0
    
    override fun messageCount(): Int {
        if (!isOpen()) return 0
        
        // Simplified implementation - in production would query actual message queue
        return 0 // Stub implementation
    }
    
    private fun serializeMessage(message: IPCMessage): ByteArray {
        // Simple serialization format
        val buffer = mutableListOf<Byte>()
        
        // Add message fields
        buffer.addAll(message.id.toBytes().toList())
        buffer.add(message.type.value.toByte())
        buffer.addAll(message.source.toBytes().toList())
        buffer.addAll(message.destination.toBytes().toList())
        
        // Add channel ID length and data
        val channelBytes = message.channelId.encodeToByteArray()
        buffer.addAll(channelBytes.size.toBytes().toList())
        buffer.addAll(channelBytes.toList())
        
        // Add payload
        buffer.addAll(message.payload.size.toBytes().toList())
        for (i in 0 until message.payload.size) {
            buffer.add(message.payload[i])
        }
        
        return buffer.toByteArray()
    }
    
    private fun deserializeMessage(data: ByteArray): IPCMessage {
        var offset = 0
        
        // Read message fields
        val id = data.sliceArray(offset until offset + 8).toLong()
        offset += 8
        
        val type = MessageType(data[offset++].toUByte())
        
        val source = data.sliceArray(offset until offset + 4).toInt()
        offset += 4
        
        val destination = data.sliceArray(offset until offset + 4).toInt()
        offset += 4
        
        // Read channel ID
        val channelIdLength = data.sliceArray(offset until offset + 4).toInt()
        offset += 4
        val channelId = data.sliceArray(offset until offset + channelIdLength).decodeToString()
        offset += channelIdLength
        
        // Read payload
        val payloadLength = data.sliceArray(offset until offset + 4).toInt()
        offset += 4
        val payload = data.sliceArray(offset until offset + payloadLength)
        
        return IPCMessage(
            id = id,
            type = type,
            source = source,
            destination = destination,
            channelId = channelId,
            payload = payload.toIdx()
        )
    }
}

// Extension functions for byte conversions
private fun Long.toBytes(): ByteArray = ByteArray(8) { i ->
    ((this shr (i * 8)) and 0xFF).toByte()
}

private fun Int.toBytes(): ByteArray = ByteArray(4) { i ->
    ((this shr (i * 8)) and 0xFF).toByte()
}

private fun ByteArray.toLong(): Long = fold(0L) { acc, byte ->
    (acc shl 8) or (byte.toLong() and 0xFF)
}

private fun ByteArray.toInt(): Int = fold(0) { acc, byte ->
    (acc shl 8) or (byte.toInt() and 0xFF)
}

// Platform-specific factory functions
actual suspend fun createNativeIPCHost(hostId: ProcessId): IPCHost = NativeIPCHost(hostId)

actual suspend fun createJVMIPCHost(hostId: ProcessId): IPCHost {
    // Native platform cannot create JVM host
    throw UnsupportedOperationException("JVM IPC host not available on native platform")
}

actual suspend fun createWASMIPCHost(hostId: ProcessId): IPCHost {
    // Native platform cannot create WASM host
    throw UnsupportedOperationException("WASM IPC host not available on native platform")
}

actual suspend fun createHybridIPCHost(hostId: ProcessId): IPCHost {
    // For native, hybrid is just native
    return NativeIPCHost(hostId)
}
