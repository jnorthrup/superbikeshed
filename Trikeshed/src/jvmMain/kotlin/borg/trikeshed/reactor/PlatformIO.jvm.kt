package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import borg.trikeshed.nio.ByteBufferFactory
import borg.trikeshed.nio.JvmByteBufferWrapper
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress

// Platform-specific IO dispatcher for JVM
actual val IO: CoroutineDispatcher = Dispatchers.IO

// Platform-specific System utilities for JVM  
actual object System {
    actual fun currentTimeMillis(): Long = java.lang.System.currentTimeMillis()
    actual fun nanoTime(): Long = java.lang.System.nanoTime()
}

actual class PlatformIO {
    actual fun createSelector(): SelectorInterface = SelectorInterface()
    
    actual fun createServerChannel(): ServerChannel = JvmServerChannel()
    
    actual fun createClientChannel(): ClientChannel = JvmClientChannel()
    
    actual fun createBufferPool(bufferSize: Int): BufferPool = JvmBufferPool(bufferSize)
    
    actual companion object {
        actual fun create(): PlatformIO = PlatformIO()
    }
}

class JvmServerChannel : ServerChannel {
    private val jvmChannel = ServerSocketChannel.open()
    private val selectableChannel = JvmSelectableChannel(jvmChannel)
    
    override fun isOpen(): Boolean = selectableChannel.isOpen()
    override fun close() = selectableChannel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = selectableChannel.configureBlocking(block)
    override fun isBlocking(): Boolean = selectableChannel.isBlocking()
    
    override fun bind(port: Int) {
        jvmChannel.bind(InetSocketAddress(port))
    }
    
    override fun accept(): ClientChannel? {
        val accepted = jvmChannel.accept()
        return accepted?.let { JvmClientChannel(it) }
    }
}

class JvmClientChannel(private val jvmChannel: SocketChannel = SocketChannel.open()) : ClientChannel {
    private val selectableChannel = JvmSelectableChannel(jvmChannel)
    
    override fun isOpen(): Boolean = selectableChannel.isOpen()
    override fun close() = selectableChannel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = selectableChannel.configureBlocking(block)
    override fun isBlocking(): Boolean = selectableChannel.isBlocking()
    
    override fun connect(host: String, port: Int) {
        jvmChannel.connect(InetSocketAddress(host, port))
    }
    
    override fun read(buffer: ByteBuffer): Int {
        // Bridge between TrikeShed ByteBuffer and JVM ByteBuffer
        val jvmBuffer = when (buffer) {
            is JvmByteBufferWrapper -> buffer.underlying
            else -> throw IllegalArgumentException("Expected JvmByteBufferWrapper on JVM platform")
        }
        return jvmChannel.read(jvmBuffer)
    }
    
    override fun write(buffer: ByteBuffer): Int {
        // Bridge between TrikeShed ByteBuffer and JVM ByteBuffer
        val jvmBuffer = when (buffer) {
            is JvmByteBufferWrapper -> buffer.underlying
            else -> throw IllegalArgumentException("Expected JvmByteBufferWrapper on JVM platform")
        }
        return jvmChannel.write(jvmBuffer)
    }
}

class JvmBufferPool(private val bufferSize: Int) : BufferPool {
    override fun acquire(): ByteBuffer {
        return ByteBufferFactory.allocate(bufferSize)
    }
    
    override fun release(buffer: ByteBuffer) {
        // In a real implementation, this would return the buffer to a pool
        // For now, we just let it be garbage collected
    }
}

actual interface ServerChannel : SelectableChannel {
    actual fun bind(port: Int)
    actual fun accept(): ClientChannel?
}

actual interface ClientChannel : SelectableChannel {
    actual fun connect(host: String, port: Int)
    actual fun read(buffer: ByteBuffer): Int
    actual fun write(buffer: ByteBuffer): Int
}