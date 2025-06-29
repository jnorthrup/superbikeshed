package borg.trikeshed.nio.spi

<<<<<<< HEAD

import borg.trikeshed.lib.*
import borg.trikeshed.nio.*

/**
 * Service Provider Interface for NIO operations
 * Eliminates boilerplate and provides platform-specific implementations
 */
interface NioServiceProvider {
    /**
     * Platform identifier
     */
    val platformId: String
    
    /**
     * Create a platform-specific byte buffer
     */
    fun createBuffer(capacity: Int): PlatformByteBuffer
    
    /**
     * Create a platform-specific channel
     */
    fun createChannel(): PlatformChannel
    
    /**
     * Create a platform-specific datagram socket
     */
    fun createDatagramSocket(): PlatformDatagramSocket
    
    /**
     * Create a platform-specific datagram socket bound to address
     */
    fun createDatagramSocket(address: PlatformInetSocketAddress): PlatformDatagramSocket
    
    /**
     * Create a platform-specific internet socket address
     */
    fun createAddress(host: String, port: Int): PlatformInetSocketAddress
    
    /**
     * Create a platform-specific datagram packet
     */
    fun createPacket(data: ByteArray, length: Int, address: PlatformInetSocketAddress): PlatformDatagramPacket
    
    /**
     * Get the attention delegate for this provider
     */
=======
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformChannel

/**
 * Service Provider Interface for NIO implementations
 */
interface NioServiceProvider {
    fun createBuffer(capacity: Int): PlatformByteBuffer
    fun wrapBuffer(array: ByteArray, offset: Int = 0, length: Int = array.size): PlatformByteBuffer
    fun createChannel(): PlatformChannel
>>>>>>> origin/feat/core-serialization-impl
    fun getAttentionDelegate(): AttentionDelegate
}

/**
<<<<<<< HEAD
 * Interface for monitoring and logging NIO operations
 */
interface AttentionDelegate {
    /**
     * Called before a NIO operation begins
     */
    fun beforeOperation(operation: String, details: String = "")
    
    /**
     * Called after a NIO operation completes
     */
    fun afterOperation(operation: String, durationMs: Long, success: Boolean, details: String = "")
    
    /**
     * Called when a slow operation is detected
     */
    fun onSlowOperation(operation: String, durationMs: Long, threshold: Long, details: String = "")
    
    /**
     * Called when an operation fails
     */
    fun onOperationError(operation: String, error: Throwable, details: String = "")
}
=======
 * Attention delegate for monitoring NIO operations
 */
interface AttentionDelegate {
    fun onBufferAllocated(capacity: Int, duration: Long)
    fun onBufferWrapped(arraySize: Int, offset: Int, length: Int, duration: Long)
    fun onChannelCreated(type: String, config: Map<String, Any>)
    fun onIoOperation(operation: String, bytes: Int, duration: Long)
} 
>>>>>>> origin/feat/core-serialization-impl
