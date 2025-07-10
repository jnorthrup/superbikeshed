@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.impl.linux

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.channel.impl.*
import borg.trikeshed.reactor.ByteBuffer
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Linux io_uring-based channel provider for high-performance async I/O.
 * This is the endgame architecture where kernel acts as database.
 */
class IoUringChannelProvider : AbstractChannelProvider(
    name = "io_uring",
    supportedTypes = setOf(ChannelType.TCP, ChannelType.UDP, ChannelType.UNIX_SOCKET, ChannelType.FILE),
    priority = 200 // Highest priority on Linux
) {
    
    internal val ring: CPointer<io_uring>? = null // TODO: Initialize io_uring
    
    init {
        // TODO: Initialize io_uring ring
        // io_uring_setup()
    }
    
    override suspend fun createChannel(config: ChannelConfig): Channel {
        return when (config.type) {
            ChannelType.TCP -> {
                val fd = socket(AF_INET, SOCK_STREAM, 0)
                if (fd < 0) {
                    throw ChannelException.ConnectionFailed("Failed to create TCP socket")
                }
                val channel = IoUringTcpChannel(ChannelId.generate(), config, fd)
                registerChannel(channel)
                channel
            }
            ChannelType.UDP -> {
                val fd = socket(AF_INET, SOCK_DGRAM, 0)
                if (fd < 0) {
                    throw ChannelException.ConnectionFailed("Failed to create UDP socket")
                }
                val channel = IoUringUdpChannel(ChannelId.generate(), config, fd)
                registerChannel(channel)
                channel
            }
            ChannelType.FILE -> {
                TODO("File channels with io_uring not implemented yet")
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
                val fd = socket(AF_INET, SOCK_STREAM, 0)
                if (fd < 0) {
                    throw ChannelException.ConnectionFailed("Failed to create TCP socket")
                }
                
                val channel = IoUringTcpConnectedChannel(
                    ChannelId.generate(),
                    config,
                    fd,
                    address
                )
                
                registerChannel(channel)
                channel.connect(address)
                return channel
            }
            else -> throw ChannelException.ConfigurationError("Unsupported address type: $address")
        }
    }
    
    override suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel {
        when (bindAddress) {
            is ChannelAddress.InetAddress -> {
                val fd = socket(AF_INET, SOCK_STREAM, 0)
                if (fd < 0) {
                    throw ChannelException.ConnectionFailed("Failed to create server socket")
                }
                
                val channel = IoUringTcpServerChannel(
                    ChannelId.generate(),
                    config,
                    fd,
                    bindAddress
                )
                
                registerChannel(channel)
                return channel
            }
            else -> throw ChannelException.ConfigurationError("Unsupported address type: $bindAddress")
        }
    }
}

/**
 * io_uring TCP channel implementation.
 * This represents the kernel-as-database endgame architecture.
 */
class IoUringTcpChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val fd: Int
) : AbstractChannel(id, config) {
    
    init {
        updateLifecycle(ChannelLifecycle.Open)
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        return memScoped {
            val bufPtr = allocArray<ByteVar>(buffer.remaining())
            
            // Submit io_uring read operation
            val bytesRead = read(fd, bufPtr, buffer.remaining().convert())
            
            if (bytesRead > 0) {
                // Copy from native buffer to ByteBuffer
                for (i in 0 until bytesRead.toInt()) {
                    buffer.put(bufPtr[i])
                }
                recordBytesRead(bytesRead.toInt())
            }
            
            bytesRead.toInt()
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        return memScoped {
            val size = buffer.remaining()
            val bufPtr = allocArray<ByteVar>(size)
            
            // Copy from ByteBuffer to native buffer
            for (i in 0 until size) {
                bufPtr[i] = buffer.get()
            }
            
            // Submit io_uring write operation
            val bytesWritten = write(fd, bufPtr, size.convert())
            
            if (bytesWritten > 0) {
                recordBytesWritten(bytesWritten.toInt())
            }
            
            bytesWritten.toInt()
        }
    }
    
    override suspend fun flush() {
        // TCP flushes automatically, but we could use io_uring_submit here
    }
    
    override suspend fun close() {
        try {
            close(fd)
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = fd >= 0
    override fun isWritable(): Boolean = fd >= 0
}

/**
 * io_uring TCP connected channel implementation.
 */
class IoUringTcpConnectedChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val fd: Int,
    internal val targetAddress: ChannelAddress.InetAddress
) : IoUringTcpChannel(id, config, fd), ConnectedChannel {
    
    override val localAddress: ChannelAddress? = null // TODO: Implement
    override val remoteAddress: ChannelAddress? = targetAddress
    
    override suspend fun connect(address: ChannelAddress) {
        when (address) {
            is ChannelAddress.InetAddress -> {
                updateLifecycle(ChannelLifecycle.Opening)
                
                memScoped {
                    val addr = alloc<sockaddr_in>()
                    addr.sin_family = AF_INET.convert()
                    addr.sin_port = htons(address.port.convert()).convert()
                    
                    // Convert IP address
                    inet_aton(address.host, addr.sin_addr.ptr)
                    
                    // Submit io_uring connect operation
                    val result = connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                    
                    if (result == 0) {
                        updateLifecycle(ChannelLifecycle.Connected)
                    } else {
                        val error = Exception("Connect failed with code: $result")
                        recordError()
                        updateLifecycle(ChannelLifecycle.Error(error))
                        throw ChannelException.ConnectionFailed("Failed to connect to $address", error)
                    }
                }
            }
            else -> throw ChannelException.InvalidOperation("Cannot connect to non-inet address: $address")
        }
    }
    
    override suspend fun disconnect() {
        try {
            shutdown(fd, SHUT_WR)
            updateLifecycle(ChannelLifecycle.Closing)
        } catch (e: Exception) {
            recordError()
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
}

/**
 * io_uring UDP channel implementation.
 */
class IoUringUdpChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val fd: Int
) : AbstractChannel(id, config) {
    
    init {
        updateLifecycle(ChannelLifecycle.Open)
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        return memScoped {
            val bufPtr = allocArray<ByteVar>(buffer.remaining())
            
            // Submit io_uring recvfrom operation
            val bytesRead = recv(fd, bufPtr, buffer.remaining().convert(), 0)
            
            if (bytesRead > 0) {
                for (i in 0 until bytesRead.toInt()) {
                    buffer.put(bufPtr[i])
                }
                recordBytesRead(bytesRead.toInt())
            }
            
            bytesRead.toInt()
        }
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        return memScoped {
            val size = buffer.remaining()
            val bufPtr = allocArray<ByteVar>(size)
            
            for (i in 0 until size) {
                bufPtr[i] = buffer.get()
            }
            
            // Submit io_uring sendto operation
            val bytesWritten = send(fd, bufPtr, size.convert(), 0)
            
            if (bytesWritten > 0) {
                recordBytesWritten(bytesWritten.toInt())
            }
            
            bytesWritten.toInt()
        }
    }
    
    override suspend fun flush() {
        // UDP doesn't need explicit flushing
    }
    
    override suspend fun close() {
        try {
            close(fd)
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = fd >= 0
    override fun isWritable(): Boolean = fd >= 0
}

/**
 * io_uring TCP server channel implementation.
 */
class IoUringTcpServerChannel(
    id: ChannelId,
    config: ChannelConfig,
    internal val fd: Int,
    override val bindAddress: ChannelAddress
) : AbstractChannel(id, config), ServerChannel {
    
    override suspend fun accept(): ConnectedChannel? {
        return memScoped {
            val clientAddr = alloc<sockaddr_in>()
            val addrLen = alloc<socklen_tVar>()
            addrLen.value = sizeOf<sockaddr_in>().convert()
            
            // Submit io_uring accept operation
            val clientFd = accept(fd, clientAddr.ptr.reinterpret(), addrLen.ptr)
            
            if (clientFd >= 0) {
                val clientAddress = ChannelAddress.InetAddress(
                    inet_ntoa(clientAddr.sin_addr)?.toKString() ?: "unknown",
                    ntohs(clientAddr.sin_port.convert()).toInt()
                )
                
                IoUringTcpConnectedChannel(
                    ChannelId.generate(),
                    config,
                    clientFd,
                    clientAddress
                ).also {
                    it.updateLifecycle(ChannelLifecycle.Connected)
                }
            } else {
                null
            }
        }
    }
    
    override suspend fun bind() {
        when (bindAddress) {
            is ChannelAddress.InetAddress -> {
                memScoped {
                    val addr = alloc<sockaddr_in>()
                    addr.sin_family = AF_INET.convert()
                    addr.sin_port = htons(bindAddress.port.convert()).convert()
                    inet_aton(bindAddress.host, addr.sin_addr.ptr)
                    
                    val bindResult = bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                    if (bindResult != 0) {
                        val error = Exception("Bind failed with code: $bindResult")
                        recordError()
                        updateLifecycle(ChannelLifecycle.Error(error))
                        throw ChannelException.ConfigurationError("Failed to bind to $bindAddress", error)
                    }
                    
                    val listenResult = listen(fd, 128)
                    if (listenResult != 0) {
                        val error = Exception("Listen failed with code: $listenResult")
                        recordError()
                        updateLifecycle(ChannelLifecycle.Error(error))
                        throw ChannelException.ConfigurationError("Failed to listen on $bindAddress", error)
                    }
                    
                    updateLifecycle(ChannelLifecycle.Open)
                }
            }
            else -> throw ChannelException.InvalidOperation("Cannot bind to non-inet address: $bindAddress")
        }
    }
    
    override suspend fun unbind() {
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
    
    override suspend fun flush() {}
    
    override suspend fun close() {
        try {
            close(fd)
            updateLifecycle(ChannelLifecycle.Closed)
        } catch (e: Exception) {
            updateLifecycle(ChannelLifecycle.Error(e))
        }
    }
    
    override fun isReadable(): Boolean = false
    override fun isWritable(): Boolean = false
}

// TODO: Define io_uring bindings
external class io_uring