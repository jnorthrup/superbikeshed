@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.nio.ByteBuffer as NioByteBuffer
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * Bridge from Java NIO to TrikeShed channelization.
 * This is the SINGLE converter that adapts all NIO channels to our channel API.
 */
class NioToChannelBridge {
    
    /**
     * Convert any NIO channel to TrikeShed channel.
     */
    fun convertChannel(nioChannel: java.nio.channels.Channel): Channel = when (nioChannel) {
        is SocketChannel -> NioSocketChannelAdapter(nioChannel)
        is ServerSocketChannel -> NioServerSocketChannelAdapter(nioChannel)
        is DatagramChannel -> NioDatagramChannelAdapter(nioChannel)
        is Pipe.SourceChannel -> NioPipeSourceChannelAdapter(nioChannel)
        is Pipe.SinkChannel -> NioPipeSinkChannelAdapter(nioChannel)
        else -> throw IllegalArgumentException("Unsupported NIO channel type: ${nioChannel::class}")
    }
    
    /**
     * Convert NIO ByteBuffer to TrikeShed ByteBuffer.
     */
    fun convertBuffer(nioBuffer: NioByteBuffer): ByteBuffer {
        return ByteBuffer.wrap(nioBuffer.array())
    }
    
    /**
     * Convert TrikeShed ByteBuffer to NIO ByteBuffer.
     */
    fun convertToNioBuffer(buffer: ByteBuffer): NioByteBuffer {
        return NioByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
    }
}

/**
 * Base adapter for all NIO channels.
 */
abstract class NioChannelAdapter(
    internal val nioChannel: java.nio.channels.Channel
) : Channel {
    
    override val id: ChannelId = ChannelId.generate()
    override val config: ChannelConfig = ChannelConfig(
        type = ChannelType.TCP,
        bufferSize = 8192,
        timeout = 30000,
        options = emptyMap()
    )
    
    override val lifecycle: Flow<ChannelLifecycle> = MutableStateFlow(
        if (nioChannel.isOpen) ChannelLifecycle.Open else ChannelLifecycle.Closed
    )
    
    override val stats: Flow<ChannelStats> = MutableStateFlow(ChannelStats())
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        nioChannel.close()
    }
    
    override suspend fun flush() {
        // Most NIO channels auto-flush
    }
    
    override fun isReadable(): Boolean = nioChannel.isOpen
    override fun isWritable(): Boolean = nioChannel.isOpen
}

/**
 * Adapter for NIO SocketChannel.
 */
class NioSocketChannelAdapter(
    internal val socketChannel: SocketChannel
) : NioChannelAdapter(socketChannel), ConnectedChannel {
    
    override val localAddress: ChannelAddress?
        get() = socketChannel.localAddress?.toChannelAddress()
        
    override val remoteAddress: ChannelAddress?
        get() = socketChannel.remoteAddress?.toChannelAddress()
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.allocate(buffer.remaining())
        val bytesRead = socketChannel.read(nioBuffer)
        
        if (bytesRead > 0) {
            nioBuffer.flip()
            buffer.put(nioBuffer.array(), 0, bytesRead)
        }
        
        bytesRead
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
        val bytesWritten = socketChannel.write(nioBuffer)
        
        if (bytesWritten > 0) {
            buffer.position(buffer.position() + bytesWritten)
        }
        
        bytesWritten
    }
    
    override suspend fun connect(address: ChannelAddress) = withContext(Dispatchers.IO) {
        when (address) {
            is ChannelAddress.InetAddress -> {
                socketChannel.connect(InetSocketAddress(address.host, address.port))
            }
            else -> throw IllegalArgumentException("Unsupported address type: $address")
        }
    }
    
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socketChannel.shutdownOutput()
    }
}

/**
 * Adapter for NIO ServerSocketChannel.
 */
class NioServerSocketChannelAdapter(
    internal val serverChannel: ServerSocketChannel
) : NioChannelAdapter(serverChannel), ServerChannel {
    
    override val bindAddress: ChannelAddress
        get() = serverChannel.localAddress.toChannelAddress()
    
    override suspend fun accept(): ConnectedChannel? = withContext(Dispatchers.IO) {
        val clientSocket = serverChannel.accept()
        clientSocket?.let {
            it.configureBlocking(false)
            NioSocketChannelAdapter(it)
        }
    }
    
    override suspend fun bind() = withContext(Dispatchers.IO) {
        // Already bound through NIO
    }
    
    override suspend fun unbind() = withContext(Dispatchers.IO) {
        close()
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        throw UnsupportedOperationException("Cannot read from server channel")
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        throw UnsupportedOperationException("Cannot write to server channel")
    }
}

/**
 * Adapter for NIO DatagramChannel.
 */
class NioDatagramChannelAdapter(
    internal val datagramChannel: DatagramChannel
) : NioChannelAdapter(datagramChannel) {
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.allocate(buffer.remaining())
        val address = datagramChannel.receive(nioBuffer)
        
        if (address != null) {
            nioBuffer.flip()
            buffer.put(nioBuffer.array(), 0, nioBuffer.remaining())
            nioBuffer.remaining()
        } else {
            0
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
        val bytesWritten = datagramChannel.write(nioBuffer)
        
        if (bytesWritten > 0) {
            buffer.position(buffer.position() + bytesWritten)
        }
        
        bytesWritten
    }
}

/**
 * Adapter for NIO Pipe.SourceChannel.
 */
class NioPipeSourceChannelAdapter(
    internal val sourceChannel: Pipe.SourceChannel
) : NioChannelAdapter(sourceChannel) {
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.allocate(buffer.remaining())
        val bytesRead = sourceChannel.read(nioBuffer)
        
        if (bytesRead > 0) {
            nioBuffer.flip()
            buffer.put(nioBuffer.array(), 0, bytesRead)
        }
        
        bytesRead
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        throw UnsupportedOperationException("Cannot write to source channel")
    }
}

/**
 * Adapter for NIO Pipe.SinkChannel.
 */
class NioPipeSinkChannelAdapter(
    internal val sinkChannel: Pipe.SinkChannel
) : NioChannelAdapter(sinkChannel) {
    
    override suspend fun read(buffer: ByteBuffer): Int {
        throw UnsupportedOperationException("Cannot read from sink channel")
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        val nioBuffer = NioByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
        val bytesWritten = sinkChannel.write(nioBuffer)
        
        if (bytesWritten > 0) {
            buffer.position(buffer.position() + bytesWritten)
        }
        
        bytesWritten
    }
}

/**
 * Extension to convert SocketAddress to ChannelAddress.
 */
internal fun SocketAddress.toChannelAddress(): ChannelAddress = when (this) {
    is InetSocketAddress -> ChannelAddress.InetAddress(hostString, port)
    else -> ChannelAddress.UnixSocket(toString())
}

/**
 * Global NIO bridge instance.
 */
val nioChannelBridge = NioToChannelBridge()

/**
 * Extension function for easy conversion.
 */
fun java.nio.channels.Channel.toTrikeShedChannel(): Channel = 
    nioChannelBridge.convertChannel(this)

/**
 * Extension function for ByteBuffer conversion.
 */
fun NioByteBuffer.toTrikeShedBuffer(): ByteBuffer = 
    nioChannelBridge.convertBuffer(this)

fun ByteBuffer.toNioBuffer(): NioByteBuffer = 
    nioChannelBridge.convertToNioBuffer(this)