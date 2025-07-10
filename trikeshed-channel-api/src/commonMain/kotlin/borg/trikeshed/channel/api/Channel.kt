@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import borg.trikeshed.lib.*

/**
 * Core channel interface for async I/O operations.
 * Provides unified abstraction over different transport mechanisms.
 */
interface Channel {
    val id: ChannelId
    val config: ChannelConfig
    val isOpen: Boolean
    val isActive: Boolean
    
    /**
     * Send data through the channel.
     */
    suspend fun send(data: ByteIndexed)
    
    /**
     * Receive data from the channel.
     * Returns empty ByteIndexed if no data available.
     */
    suspend fun receive(): ByteIndexed
    
    /**
     * Close the channel and release resources.
     */
    suspend fun close()
    
    /**
     * Check if the channel is readable without blocking.
     */
    fun isReadable(): Boolean
    
    /**
     * Check if the channel is writable without blocking.
     */
    fun isWritable(): Boolean
}

/**
 * Channel with additional metadata and lifecycle management.
 */
interface ManagedChannel : Channel {
    val metadata: ChannelMetadata
    val lifecycle: ChannelLifecycle
    
    /**
     * Perform channel-specific health check.
     */
    suspend fun healthCheck(): ChannelHealth
}

/**
 * Channel that supports seeking operations.
 */
interface SeekableChannel : Channel {
    val position: Long
    val size: Long
    
    suspend fun seek(position: Long)
    suspend fun truncate(size: Long)
}

/**
 * Channel that supports connection-oriented operations.
 */
interface ConnectedChannel : Channel {
    val localAddress: ChannelAddress?
    val remoteAddress: ChannelAddress?
    
    suspend fun connect(address: ChannelAddress)
    suspend fun disconnect()
}