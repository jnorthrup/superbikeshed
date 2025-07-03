package borg.trikeshed.nio

import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * JVM implementation of platform NIO abstractions
 */

actual class PlatformByteBuffer(val buffer: ByteBuffer) {
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer = PlatformByteBuffer(ByteBuffer.allocate(capacity))
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer = PlatformByteBuffer(ByteBuffer.wrap(array, offset, length))
    }

    actual fun put(byte: Byte): PlatformByteBuffer {
        buffer.put(byte)
        return this
    }

    actual fun put(src: ByteArray): PlatformByteBuffer {
        buffer.put(src)
        return this
    }

    actual fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
        buffer.put(src, offset, length)
        return this
    }

    actual fun putLong(value: Long): PlatformByteBuffer {
        buffer.putLong(value)
        return this
    }

    actual fun get(): Byte = buffer.get()

    actual fun getLong(): Long = buffer.getLong()

    actual fun get(dst: ByteArray): PlatformByteBuffer {
        buffer.get(dst)
        return this
    }

    actual fun flip(): PlatformByteBuffer {
        buffer.flip()
        return this
    }

    actual fun clear(): PlatformByteBuffer {
        buffer.clear()
        return this
    }

    actual fun rewind(): PlatformByteBuffer {
        buffer.rewind()
        return this
    }

    actual fun array(): ByteArray = buffer.array()

    actual fun position(): Int = buffer.position()

    actual fun position(newPosition: Int): PlatformByteBuffer {
        buffer.position(newPosition)
        return this
    }

    actual fun limit(): Int = buffer.limit()

    actual fun limit(newLimit: Int): PlatformByteBuffer {
        buffer.limit(newLimit)
        return this
    }

    actual fun capacity(): Int = buffer.capacity()

    actual fun remaining(): Int = buffer.remaining()

    actual fun hasRemaining(): Boolean = buffer.hasRemaining()
}

actual class PlatformInetSocketAddress(private val inetSocketAddress: InetSocketAddress) {
    actual constructor(host: String, port: Int) : this(InetSocketAddress(host, port))
    
    actual val hostName: String get() = inetSocketAddress.hostName
    actual val hostString: String get() = inetSocketAddress.hostString
    actual val port: Int get() = inetSocketAddress.port
    actual val isUnresolved: Boolean get() = inetSocketAddress.isUnresolved
    actual val address: Any get() = inetSocketAddress
}

actual class PlatformDatagramPacket(private val datagramPacket: DatagramPacket) {
    actual constructor(data: ByteArray, length: Int, address: PlatformInetSocketAddress) : 
        this(DatagramPacket(data, length, address.address as InetSocketAddress))
    
    actual constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress) : 
        this(DatagramPacket(data, offset, length, address.address as InetSocketAddress))
    
    actual val data: ByteArray get() = datagramPacket.data
    actual val length: Int get() = datagramPacket.length
    actual val offset: Int get() = datagramPacket.offset
    actual val address: PlatformInetSocketAddress get() = PlatformInetSocketAddress(datagramPacket.socketAddress as InetSocketAddress)
    actual val packet: Any get() = datagramPacket
}

actual class PlatformDatagramSocket(private val socket: DatagramSocket = DatagramSocket()) {
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket()
        
        actual fun create(port: Int): PlatformDatagramSocket = PlatformDatagramSocket(DatagramSocket(port))
        
        actual fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket = 
            PlatformDatagramSocket(DatagramSocket(address.address as InetSocketAddress))
    }
    
    actual fun connect(address: PlatformInetSocketAddress) {
        socket.connect(address.address as InetSocketAddress)
    }
    
    actual fun send(packet: PlatformDatagramPacket) {
        socket.send(packet.packet as DatagramPacket)
    }
    
    actual fun receive(packet: PlatformDatagramPacket) {
        socket.receive(packet.packet as DatagramPacket)
    }
    
    actual fun close() {
        socket.close()
    }
    
    actual val isConnected: Boolean get() = socket.isConnected
    actual val isClosed: Boolean get() = socket.isClosed
    actual val localAddress: PlatformInetSocketAddress? get() = 
        socket.localSocketAddress?.let { 
            val addr = it as InetSocketAddress
            PlatformInetSocketAddress(addr.hostString, addr.port)
        }
    actual val remoteAddress: PlatformInetSocketAddress? get() = 
        socket.remoteSocketAddress?.let { 
            val addr = it as InetSocketAddress
            PlatformInetSocketAddress(addr.hostString, addr.port)
        }
}

actual class PlatformChannel(private val channel: DatagramChannel = DatagramChannel.open()) {
    actual fun isOpen(): Boolean = channel.isOpen
    
    actual fun close() {
        channel.close()
    }
}

actual fun platformCurrentTimeMillis(): Long = System.currentTimeMillis()

actual class PlatformSocketException actual constructor(message: String?) : Exception(message) {
    constructor(cause: SocketException) : this(cause.message)
}