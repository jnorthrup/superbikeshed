@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.impl

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*

/**
 * Base implementation for channel providers with common functionality.
 */
abstract class AbstractChannelProvider(
    override val name: String,
    override val supportedTypes: Set<ChannelType>,
    override val priority: Int = 0
) : ChannelProvider {
    
    internal val channels = mutableMapOf<ChannelId, Channel>()
    
    override fun canHandle(config: ChannelConfig): Boolean {
        return config.type in supportedTypes
    }
    
    internal fun registerChannel(channel: Channel) {
        channels[channel.id] = channel
    }
    
    internal fun unregisterChannel(channelId: ChannelId) {
        channels.remove(channelId)
    }
    
    internal fun getChannel(channelId: ChannelId): Channel? {
        return channels[channelId]
    }
    
    fun getAllChannels(): List<Channel> = channels.values.toList()
    
    suspend fun closeAllChannels() {
        channels.values.forEach { it.close() }
        channels.clear()
    }
}

/**
 * Base channel implementation with lifecycle management.
 */
abstract class AbstractChannel(
    override val id: ChannelId,
    override val config: ChannelConfig
) : ManagedChannel {
    
    internal var _lifecycle: ChannelLifecycle = ChannelLifecycle.Created
    internal var _metadata: ChannelMetadata = ChannelMetadata(
        createdAt = System.currentTimeMillis(),
        lastActivity = System.currentTimeMillis(),
        bytesRead = 0,
        bytesWritten = 0,
        errors = 0
    )
    
    override val lifecycle: ChannelLifecycle get() = _lifecycle
    override val metadata: ChannelMetadata get() = _metadata
    
    override val isOpen: Boolean 
        get() = _lifecycle in setOf(ChannelLifecycle.Open, ChannelLifecycle.Connected)
    
    override val isActive: Boolean 
        get() = _lifecycle == ChannelLifecycle.Connected
    
    internal fun updateLifecycle(newState: ChannelLifecycle) {
        _lifecycle = newState
        updateLastActivity()
    }
    
    internal fun updateLastActivity() {
        _metadata = _metadata.copy(lastActivity = System.currentTimeMillis())
    }
    
    internal fun recordBytesRead(bytes: Int) {
        _metadata = _metadata.copy(bytesRead = _metadata.bytesRead + bytes)
        updateLastActivity()
    }
    
    internal fun recordBytesWritten(bytes: Int) {
        _metadata = _metadata.copy(bytesWritten = _metadata.bytesWritten + bytes)
        updateLastActivity()
    }
    
    internal fun recordError() {
        _metadata = _metadata.copy(errors = _metadata.errors + 1)
        updateLastActivity()
    }
    
    override suspend fun healthCheck(): ChannelHealth {
        return ChannelHealth(
            status = when {
                !isOpen -> HealthStatus.UNHEALTHY
                _metadata.errors > 10 -> HealthStatus.DEGRADED
                else -> HealthStatus.HEALTHY
            },
            lastCheck = System.currentTimeMillis(),
            details = mapOf(
                "lifecycle" to _lifecycle,
                "bytesRead" to _metadata.bytesRead,
                "bytesWritten" to _metadata.bytesWritten,
                "errors" to _metadata.errors
            )
        )
    }
}

/**
 * Memory-based channel for testing and in-process communication.
 */
class MemoryChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val readBuffer: ByteArray = ByteArray(config.bufferSize),
    internal val writeBuffer: ByteArray = ByteArray(config.bufferSize)
) : AbstractChannel(id, config) {
    
    internal var readPosition = 0
    internal var writePosition = 0
    internal var closed = false
    
    init {
        updateLifecycle(ChannelLifecycle.Open)
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        if (closed) return -1
        
        val available = minOf(buffer.remaining(), readBuffer.size - readPosition)
        if (available <= 0) return 0
        
        buffer.put(readBuffer, readPosition, available)
        readPosition += available
        recordBytesRead(available)
        
        return available
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        if (closed) throw ChannelException.ChannelClosed("Channel is closed")
        
        val available = minOf(buffer.remaining(), writeBuffer.size - writePosition)
        if (available <= 0) return 0
        
        buffer.get(writeBuffer, writePosition, available)
        writePosition += available
        recordBytesWritten(available)
        
        return available
    }
    
    override suspend fun flush() {
        // Memory channel doesn't need flushing
    }
    
    override suspend fun close() {
        if (!closed) {
            closed = true
            updateLifecycle(ChannelLifecycle.Closed)
        }
    }
    
    override fun isReadable(): Boolean = !closed && readPosition < readBuffer.size
    override fun isWritable(): Boolean = !closed && writePosition < writeBuffer.size
}

/**
 * Memory channel provider for testing and in-process communication.
 */
class MemoryChannelProvider : AbstractChannelProvider(
    name = "memory",
    supportedTypes = setOf(ChannelType.MEMORY)
) {
    
    override suspend fun createChannel(config: ChannelConfig): Channel {
        val channel = MemoryChannel(ChannelId.generate(), config)
        registerChannel(channel)
        return channel
    }
    
    override suspend fun createConnectedChannel(
        config: ChannelConfig,
        address: ChannelAddress
    ): ConnectedChannel {
        TODO("Memory connected channels not implemented")
    }
    
    override suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel {
        TODO("Memory server channels not implemented")
    }
}