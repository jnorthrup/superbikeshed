package borg.trikeshed.nio.spi

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
     * Wrap a byte array in a platform-specific buffer
     */
    fun wrapBuffer(array: ByteArray, offset: Int = 0, length: Int = array.size): PlatformByteBuffer
    
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
    fun getAttentionDelegate(): AttentionDelegate
}

/**
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
    fun afterOperation(operation: String, result: String = "", error: Throwable? = null)
    
    /**
     * Called when attention is transferred between components
     */
    fun attentionTransferred(from: String, to: String, reason: String = "")
}

/**
 * Default no-op implementation of AttentionDelegate
 */
object NoOpAttentionDelegate : AttentionDelegate {
    override fun beforeOperation(operation: String, details: String) {}
    override fun afterOperation(operation: String, result: String, error: Throwable?) {}
    override fun attentionTransferred(from: String, to: String, reason: String) {}
}