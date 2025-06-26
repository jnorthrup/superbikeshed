package borg.trikeshed.nio.spi

import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformChannel

/**
 * Service Provider Interface for NIO implementations
 */
interface NioServiceProvider {
    fun createBuffer(capacity: Int): PlatformByteBuffer
    fun wrapBuffer(array: ByteArray, offset: Int = 0, length: Int = array.size): PlatformByteBuffer
    fun createChannel(): PlatformChannel
    fun getAttentionDelegate(): AttentionDelegate
}

/**
 * Attention delegate for monitoring NIO operations
 */
interface AttentionDelegate {
    fun onBufferAllocated(capacity: Int, duration: Long)
    fun onBufferWrapped(arraySize: Int, offset: Int, length: Int, duration: Long)
    fun onChannelCreated(type: String, config: Map<String, Any>)
    fun onIoOperation(operation: String, bytes: Int, duration: Long)
} 