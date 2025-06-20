package borg.trikeshed.nio.spi

import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformChannel
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.Selector

class JvmNioProvider : NioServiceProvider {
    override fun createBuffer(capacity: Int): PlatformByteBuffer {
        val startTime = System.nanoTime()
        val buffer = PlatformByteBuffer.allocate(capacity)
        val duration = System.nanoTime() - startTime
        getAttentionDelegate().onBufferAllocated(capacity, duration)
        return buffer
    }
    
    override fun wrapBuffer(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
        val startTime = System.nanoTime()
        val buffer = PlatformByteBuffer.wrap(array, offset, length)
        val duration = System.nanoTime() - startTime
        getAttentionDelegate().onBufferWrapped(array.size, offset, length, duration)
        return buffer
    }
    
    override fun createChannel(): PlatformChannel {
        // Default to socket channel for now
        val config = mapOf("type" to "socket", "blocking" to false)
        getAttentionDelegate().onChannelCreated("socket", config)
        return PlatformChannel(SocketChannel.open())
    }
    
    override fun getAttentionDelegate(): AttentionDelegate = JvmAttentionDelegate
}

object JvmAttentionDelegate : AttentionDelegate {
    override fun onBufferAllocated(capacity: Int, duration: Long) {
        // Log or monitor buffer allocation performance
        if (duration > 1_000_000) { // 1ms threshold
            println("Slow buffer allocation: ${capacity} bytes in ${duration}ns")
        }
    }
    
    override fun onBufferWrapped(arraySize: Int, offset: Int, length: Int, duration: Long) {
        // Log or monitor buffer wrapping performance
        if (duration > 500_000) { // 0.5ms threshold
            println("Slow buffer wrap: ${length} bytes in ${duration}ns")
        }
    }
    
    override fun onChannelCreated(type: String, config: Map<String, Any>) {
        println("Channel created: $type with config $config")
    }
    
    override fun onIoOperation(operation: String, bytes: Int, duration: Long) {
        // Log or monitor I/O operation performance
        if (duration > 1_000_000) { // 1ms threshold
            println("Slow I/O operation: $operation ${bytes} bytes in ${duration}ns")
        }
    }
} 