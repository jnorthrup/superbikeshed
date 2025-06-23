package borg.trikeshed.nio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.Channel
import java.nio.channels.SocketChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.DatagramChannel
import java.nio.channels.FileChannel
import java.net.SocketAddress

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
    actual fun get(dst: ByteArray): PlatformByteBuffer {
        buffer.get(dst)
        return this
    }
    actual fun getLong(): Long = buffer.getLong()
    actual fun flip(): PlatformByteBuffer {
        buffer.flip()
        return this
    }
    actual fun remaining(): Int = buffer.remaining()
    actual fun array(): ByteArray = buffer.array()
    actual fun limit(): Int = buffer.limit()
    actual fun position(): Int = buffer.position()
    actual fun position(newPosition: Int): PlatformByteBuffer {
        buffer.position(newPosition)
        return this
    }
}

actual class PlatformChannel(private val channel: Channel) {
    actual fun read(buffer: PlatformByteBuffer): Int {
        return when (channel) {
            is java.nio.channels.ReadableByteChannel -> channel.read(buffer.buffer)
            else -> throw UnsupportedOperationException("Channel does not support reading")
        }
    }
    
    actual fun write(buffer: PlatformByteBuffer): Int {
        return when (channel) {
            is java.nio.channels.WritableByteChannel -> channel.write(buffer.buffer)
            else -> throw UnsupportedOperationException("Channel does not support writing")
        }
    }
    
    actual fun close() {
        channel.close()
    }
    
    actual val isOpen: Boolean get() = channel.isOpen
}

actual class PlatformDatagramSocket(val socket: DatagramSocket = DatagramSocket()) {
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket()
    }
    actual fun connect(address: PlatformInetSocketAddress) {
        socket.connect(address.javaInetSocketAddress)
    }

    actual fun send(packet: PlatformDatagramPacket) {
        socket.send(packet.javaDatagramPacket)
    }

    actual fun receive(packet: PlatformDatagramPacket) {
        socket.receive(packet.javaDatagramPacket)
    }

    actual fun close() {
        socket.close()
    }

    actual val isConnected: Boolean
        get() = socket.isConnected
    actual val isClosed: Boolean
        get() = socket.isClosed
    actual val remoteSocketAddress: PlatformSocketAddress?
        get() = (socket.remoteSocketAddress as? InetSocketAddress)?.let { PlatformInetSocketAddress(it) }
    actual val inetAddress: PlatformInetAddress?
        get() = socket.inetAddress?.let { PlatformInetAddress(it) }
    actual val port: Int
        get() = socket.port
}

actual class PlatformInetSocketAddress(val javaInetSocketAddress: InetSocketAddress) : PlatformSocketAddress() {
    actual constructor(hostname: String, port: Int) : this(InetSocketAddress(hostname, port))

    actual constructor(address: PlatformInetAddress, port: Int) : this(InetSocketAddress(address.javaInetAddress, port))

    actual val hostName: String
        get() = javaInetSocketAddress.hostName
    actual val port: Int
        get() = javaInetSocketAddress.port
}

actual class PlatformInetAddress(val javaInetAddress: InetAddress) {
    actual companion object {
        actual fun getLocalHost(): PlatformInetAddress = PlatformInetAddress(InetAddress.getLocalHost())
    }
}

actual class PlatformDatagramPacket(val javaDatagramPacket: DatagramPacket) {
    actual constructor(buf: ByteArray, length: Int) : this(DatagramPacket(buf, length))

    actual constructor(buf: ByteArray, length: Int, address: PlatformSocketAddress) : this(DatagramPacket(buf, length, (address as PlatformInetSocketAddress).javaInetSocketAddress))

    actual val data: ByteArray
        get() = javaDatagramPacket.data
    actual var length: Int
        get() = javaDatagramPacket.length
        set(value) { javaDatagramPacket.length = value }
    actual val address: PlatformSocketAddress?
        get() = javaDatagramPacket.socketAddress?.let { PlatformInetSocketAddress(it as InetSocketAddress) }
    actual val port: Int
        get() = javaDatagramPacket.port
}

actual abstract class PlatformSocketAddress

actual fun platformCurrentTimeMillis(): Long = System.currentTimeMillis()

actual class PlatformSocketException actual constructor(message: String?) : Exception(message) {
    constructor(cause: SocketException) : this(cause.message)
}