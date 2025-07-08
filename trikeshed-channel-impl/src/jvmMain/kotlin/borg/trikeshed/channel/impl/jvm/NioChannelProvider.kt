@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.impl.jvm

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.channel.impl.*
import borg.trikeshed.reactor.ByteBuffer
import java.nio.channels.*
import java.net.InetSocketAddress
import kotlinx.coroutines.*

/**
 * JVM NIO-based channel provider for high-performance I/O.
 */
class NioChannelProvider : AbstractChannelProvider(
    name = "nio",
    supportedTypes = setOf(ChannelType.TCP, ChannelType.UDP, ChannelType.FILE),
    priority = 100
) {
    
    internal val selector = Selector.open()
    internal val selectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override suspend fun createChannel(config: ChannelConfig): Channel {
        return when (config.type) {
            ChannelType.TCP -> {
                val socketChannel = SocketChannel.open()
                socketChannel.configureBlocking(false)
                val channel = NioTcpChannel(ChannelId.generate(), config, socketChannel)
                registerChannel(channel)
                channel
            }
            ChannelType.UDP -> {
                val datagramChannel = DatagramChannel.open()
                datagramChannel.configureBlocking(false)
                val channel = NioUdpChannel(ChannelId.generate(), config, datagramChannel)
                registerChannel(channel)
                channel
            }
            ChannelType.FILE -> {
                TODO("File channels not implemented yet")
            }
            else -> throw ChannelException.ConfigurationError("Unsupported channel type: ${config.type}")
        }
    }
    
    override suspend fun createConnectedChannel(
        config: ChannelConfig,
        address: ChannelAddress
    ): ConnectedChannel {
        when (address) {
            is ChannelAddress.InetAddress -> {
                val socketChannel = SocketChannel.open()
                socketChannel.configureBlocking(false)
                
                val channel = NioTcpConnectedChannel(
                    ChannelId.generate(), 
                    config, 
                    socketChannel,
                    address
                )
                
                registerChannel(channel)
                channel.connect(address)
                return channel
            }
            else -> throw ChannelException.ConfigurationError("Unsupported address type for TCP: $address")
        }
    }
    
    override suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel {
        when (bindAddress) {
            is ChannelAddress.InetAddress -> {
                val serverSocketChannel = ServerSocketChannel.open()
                serverSocketChannel.configureBlocking(false)
                
                val channel = NioTcpServerChannel(
                    ChannelId.generate(),
                    config,
                    serverSocketChannel,
                    bindAddress
                )
                
                registerChannel(channel)
                return channel
            }
            else -> throw ChannelException.ConfigurationError("Unsupported address type for server: $bindAddress")
        }
    }
}

/**
 * NIO TCP channel implementation.
 */
class NioTcpChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val socketChannel: SocketChannel
) : AbstractChannel(id, config) {
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        try {
            val nioBuffer = java.nio.ByteBuffer.allocate(buffer.remaining())
            val bytesRead = socketChannel.read(nioBuffer)
            
            if (bytesRead > 0) {
                nioBuffer.flip()
                buffer.put(nioBuffer.array(), 0, bytesRead)
                recordBytesRead(bytesRead)
            }
            
            bytesRead
        } catch (e: Exception) {
            recordError()
            throw ChannelException.ConnectionFailed("Read failed", e)
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        try {
            val nioBuffer = java.nio.ByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
            val bytesWritten = socketChannel.write(nioBuffer)
            
            if (bytesWritten > 0) {
                buffer.position(buffer.position() + bytesWritten)
                recordBytesWritten(bytesWritten)
            }
            
            bytesWritten
        } catch (e: Exception) {
            recordError()
            throw ChannelException.ConnectionFailed("Write failed", e)
        }
    }
    
    override suspend fun flush() {
        // TCP channels flush automatically
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socketChannel.close()
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = socketChannel.isConnected && socketChannel.isOpen
    override fun isWritable(): Boolean = socketChannel.isConnected && socketChannel.isOpen
}

/**
 * NIO TCP connected channel implementation.
 */
class NioTcpConnectedChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val socketChannel: SocketChannel,
    internal val targetAddress: ChannelAddress.InetAddress
) : NioTcpChannel(id, config, socketChannel), ConnectedChannel {
    
    override val localAddress: ChannelAddress?
        get() = socketChannel.localAddress?.let { addr ->
            if (addr is InetSocketAddress) {
                ChannelAddress.InetAddress(addr.hostString, addr.port)
            } else null
        }
    
    override val remoteAddress: ChannelAddress?
        get() = socketChannel.remoteAddress?.let { addr ->
            if (addr is InetSocketAddress) {
                ChannelAddress.InetAddress(addr.hostString, addr.port)
            } else null
        }
    
    override suspend fun connect(address: ChannelAddress) = withContext(Dispatchers.IO) {
        when (address) {
            is ChannelAddress.InetAddress -> {
                updateLifecycle(ChannelLifecycle.Opening)
                try {
                    val connected = socketChannel.connect(InetSocketAddress(address.host, address.port))
                    if (!connected) {
                        // Non-blocking connect, need to wait for completion
                        while (!socketChannel.finishConnect()) {
                            delay(10)
                        }
                    }
                    updateLifecycle(ChannelLifecycle.Connected)
                } catch (e: Exception) {
                    recordError()
                    updateLifecycle(ChannelLifecycle.Error(e))
                    throw ChannelException.ConnectionFailed("Failed to connect to $address", e)
                }
            }
            else -> throw ChannelException.InvalidOperation("Cannot connect to non-inet address: $address")
        }
    }
    
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            socketChannel.shutdownOutput()
            updateLifecycle(ChannelLifecycle.Closing)
        } catch (e: Exception) {
            recordError()
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
}

/**
 * NIO UDP channel implementation.
 */
class NioUdpChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val datagramChannel: DatagramChannel
) : AbstractChannel(id, config) {
    
    init {
        updateLifecycle(ChannelLifecycle.Open)
    }
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        try {
            val nioBuffer = java.nio.ByteBuffer.allocate(buffer.remaining())
            val bytesRead = datagramChannel.read(nioBuffer)
            
            if (bytesRead > 0) {
                nioBuffer.flip()
                buffer.put(nioBuffer.array(), 0, bytesRead)
                recordBytesRead(bytesRead)
            }
            
            bytesRead
        } catch (e: Exception) {
            recordError()
            throw ChannelException.ConnectionFailed("UDP read failed", e)
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        try {
            val nioBuffer = java.nio.ByteBuffer.wrap(buffer.array(), buffer.position(), buffer.remaining())
            val bytesWritten = datagramChannel.write(nioBuffer)
            
            if (bytesWritten > 0) {
                buffer.position(buffer.position() + bytesWritten)
                recordBytesWritten(bytesWritten)
            }
            
            bytesWritten
        } catch (e: Exception) {
            recordError()
            throw ChannelException.ConnectionFailed("UDP write failed", e)
        }
    }
    
    override suspend fun flush() {
        // UDP doesn't need explicit flushing
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            datagramChannel.close()
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = datagramChannel.isOpen
    override fun isWritable(): Boolean = datagramChannel.isOpen
}

/**
 * NIO TCP server channel implementation.
 */
class NioTcpServerChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val serverSocketChannel: ServerSocketChannel,
    override val bindAddress: ChannelAddress
) : AbstractChannel(id, config), ServerChannel {
    
    override suspend fun accept(): ConnectedChannel? = withContext(Dispatchers.IO) {
        try {
            val clientSocket = serverSocketChannel.accept()
            if (clientSocket != null) {
                clientSocket.configureBlocking(false)
                val clientAddress = clientSocket.remoteAddress as InetSocketAddress
                NioTcpConnectedChannel(
                    ChannelId.generate(),
                    config,
                    clientSocket,
                    ChannelAddress.InetAddress(clientAddress.hostString, clientAddress.port)
                ).also {
                    it.updateLifecycle(ChannelLifecycle.Connected)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            recordError()
            throw ChannelException.ConnectionFailed("Accept failed", e)
        }
    }
    
    override suspend fun bind() = withContext(Dispatchers.IO) {
        when (bindAddress) {
            is ChannelAddress.InetAddress -> {
                try {
                    serverSocketChannel.bind(InetSocketAddress(bindAddress.host, bindAddress.port))
                    updateLifecycle(ChannelLifecycle.Open)
                } catch (e: Exception) {
                    recordError()
                    updateLifecycle(ChannelLifecycle.Error(e))
                    throw ChannelException.ConfigurationError("Failed to bind to $bindAddress", e)
                }
            }
            else -> throw ChannelException.InvalidOperation("Cannot bind to non-inet address: $bindAddress")
        }
    }
    
    override suspend fun unbind() = withContext(Dispatchers.IO) {
        try {
            updateLifecycle(ChannelLifecycle.Closing)
            close()
        } catch (e: Exception) {
            recordError()
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        throw ChannelException.InvalidOperation("Cannot read from server channel")
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        throw ChannelException.InvalidOperation("Cannot write to server channel")
    }
    
    override suspend fun flush() {
        // Server channels don't flush
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            serverSocketChannel.close()
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = false
    override fun isWritable(): Boolean = false
}