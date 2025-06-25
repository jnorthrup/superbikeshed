package borg.trikeshed.nio

import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * JVM implementation of platform NIO abstractions
 */

actual class PlatformByteBuffer(private val buffer: ByteBuffer) {
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer = 
            PlatformByteBuffer(ByteBuffer.allocate(capacity))
            
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer = 
            PlatformByteBuffer(ByteBuffer.wrap(array, offset, length))
    }
    
    actual fun put(byte: Byte): PlatformByteBuffer {
        buffer.put(byte)
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

actual class PlatformInetSocketAddress(private val address: InetSocketAddress) {
    actual constructor(host: String, port: Int) : this(InetSocketAddress(host, port))
    
    actual val hostName: String get() = address.hostName
    actual val hostString: String get() = address.hostString
    actual val port: Int get() = address.port
    actual val isUnresolved: Boolean get() = address.isUnresolved
}

actual class PlatformDatagramPacket(private val packet: DatagramPacket) {
    actual constructor(data: ByteArray, length: Int, address: PlatformInetSocketAddress) : 
        this(DatagramPacket(data, length, address.address))
    
    actual constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress) : 
        this(DatagramPacket(data, offset, length, address.address))
    
    actual val data: ByteArray get() = packet.data
    actual val length: Int get() = packet.length
    actual val offset: Int get() = packet.offset
    actual val address: PlatformInetSocketAddress get() = PlatformInetSocketAddress(packet.socketAddress as InetSocketAddress)
}

actual class PlatformDatagramSocket(private val socket: DatagramSocket = DatagramSocket()) {
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket()
        
        actual fun create(port: Int): PlatformDatagramSocket = PlatformDatagramSocket(DatagramSocket(port))
        
        actual fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket = 
            PlatformDatagramSocket(DatagramSocket(address.address))
    }
    
    actual fun connect(address: PlatformInetSocketAddress) {
        socket.connect(address.address)
    }
    
    actual fun send(packet: PlatformDatagramPacket) {
        socket.send(packet.packet)
    }
    
    actual fun receive(packet: PlatformDatagramPacket) {
        socket.receive(packet.packet)
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