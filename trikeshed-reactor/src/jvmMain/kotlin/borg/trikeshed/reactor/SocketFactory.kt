package borg.trikeshed.reactor

import java.nio.channels.SocketChannel as JvmSocketChannel
import java.nio.channels.ServerSocketChannel as JvmServerSocketChannel
import java.nio.channels.DatagramChannel as JvmDatagramChannel
import java.net.InetSocketAddress
import java.net.SocketAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext

/**
 * JVM-specific socket factory for real network implementations
 */
object SocketFactory {
    
    /**
     * Create a client socket channel
     */
    suspend fun createClientSocket(host: String, port: Int): ClientChannel {
        return withContext(Dispatchers.IO) {
            val jvmChannel = JvmSocketChannel.open()
            jvmChannel.configureBlocking(false)
            
            // Connect asynchronously
            val address = InetSocketAddress(host, port)
            val connected = jvmChannel.connect(address)
            
            if (!connected) {
                // Wait for connection to complete
                while (!jvmChannel.finishConnect()) {
                    delay(10)
                }
            }
            
            JvmClientChannel(jvmChannel)
        }
    }
    
    /**
     * Create a server socket channel
     */
    suspend fun createServerSocket(port: Int): ServerChannel {
        return withContext(Dispatchers.IO) {
            val jvmChannel = JvmServerSocketChannel.open()
            jvmChannel.configureBlocking(false)
            jvmChannel.socket().reuseAddress = true
            jvmChannel.bind(InetSocketAddress(port))
            
            JvmServerChannel(jvmChannel)
        }
    }
    
    /**
     * Create a UDP socket channel
     */
    suspend fun createUdpSocket(): DatagramChannel {
        return withContext(Dispatchers.IO) {
            val jvmChannel = JvmDatagramChannel.open()
            jvmChannel.configureBlocking(false)
            
            JvmDatagramChannel(jvmChannel)
        }
    }
}

/**
 * JVM implementation of ClientChannel using NIO
 */
class JvmClientChannel(
    internal val jvmChannel: JvmSocketChannel
) : ClientChannel {
    
    override val isOpen: Boolean get() = jvmChannel.isOpen
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        jvmChannel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        jvmChannel.configureBlocking(block)
        return this
    }
    
    override val isBlocking: Boolean get() = jvmChannel.isBlocking
    
    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        val address = InetSocketAddress(host, port)
        val connected = jvmChannel.connect(address)
        
        if (!connected) {
            while (!jvmChannel.finishConnect()) {
                delay(10)
            }
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val jvmBuffer = java.nio.ByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
        val bytesWritten = jvmChannel.write(jvmBuffer)
        
        if (bytesWritten > 0) {
            buffer.position(buffer.position() + bytesWritten)
        }
        
        bytesWritten
    }
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val jvmBuffer = java.nio.ByteBuffer.allocate(buffer.remaining())
        val bytesRead = jvmChannel.read(jvmBuffer)
        
        if (bytesRead > 0) {
            jvmBuffer.flip()
            buffer.put(jvmBuffer.array(), 0, bytesRead)
        }
        
        bytesRead
    }
    
    val remoteAddress: SocketAddress? get() = jvmChannel.remoteAddress
    val localAddress: SocketAddress? get() = jvmChannel.localAddress
}

/**
 * JVM implementation of ServerChannel using NIO
 */
class JvmServerChannel(
    internal val jvmChannel: JvmServerSocketChannel
) : ServerChannel {
    
    override val isOpen: Boolean get() = jvmChannel.isOpen
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        jvmChannel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        jvmChannel.configureBlocking(block)
        return this
    }
    
    override val isBlocking: Boolean get() = jvmChannel.isBlocking
    
    override suspend fun bind(port: Int) = withContext(Dispatchers.IO) {
        jvmChannel.bind(InetSocketAddress(port))
    }
    
    override suspend fun accept(): ClientChannel? = withContext(Dispatchers.IO) {
        val clientChannel = jvmChannel.accept()
        if (clientChannel != null) {
            clientChannel.configureBlocking(false)
            JvmClientChannel(clientChannel)
        } else {
            null
        }
    }
    
    val localAddress: SocketAddress? get() = jvmChannel.localAddress
}

/**
 * JVM implementation of DatagramChannel using NIO
 */
class JvmDatagramChannel(
    internal val jvmChannel: JvmDatagramChannel
) : DatagramChannel {
    
    override val isOpen: Boolean get() = jvmChannel.isOpen
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        jvmChannel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        jvmChannel.configureBlocking(block)
        return this
    }
    
    override val isBlocking: Boolean get() = jvmChannel.isBlocking
    
    override suspend fun bind(port: Int) = withContext(Dispatchers.IO) {
        jvmChannel.bind(InetSocketAddress(port))
    }
    
    override suspend fun send(buffer: ByteBuffer, address: SocketAddress): Int = withContext(Dispatchers.IO) {
        val jvmBuffer = java.nio.ByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
        jvmChannel.send(jvmBuffer, address)
    }
    
    override suspend fun receive(buffer: ByteBuffer): Pair<Int, SocketAddress?> = withContext(Dispatchers.IO) {
        val jvmBuffer = java.nio.ByteBuffer.allocate(buffer.remaining())
        val address = jvmChannel.receive(jvmBuffer)
        
        if (address != null) {
            jvmBuffer.flip()
            val bytesRead = jvmBuffer.remaining()
            buffer.put(jvmBuffer.array(), 0, bytesRead)
            bytesRead to address
        } else {
            0 to null
        }
    }
    
    val localAddress: SocketAddress? get() = jvmChannel.localAddress
}

/**
 * DatagramChannel interface for UDP operations
 */
interface DatagramChannel : SelectableChannel {
    suspend fun bind(port: Int)
    suspend fun send(buffer: ByteBuffer, address: SocketAddress): Int
    suspend fun receive(buffer: ByteBuffer): Pair<Int, SocketAddress?>
} 