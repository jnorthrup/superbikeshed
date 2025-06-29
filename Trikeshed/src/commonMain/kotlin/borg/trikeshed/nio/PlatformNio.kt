package borg.trikeshed.nio

<<<<<<< HEAD

import borg.trikeshed.lib.*

/**
 * Platform-agnostic NIO abstractions for Trikeshed
 */

/**
 * Platform-agnostic byte buffer abstraction
=======
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Expected interface for a platform-agnostic ByteBuffer.
 * This abstracts java.nio.ByteBuffer.
>>>>>>> origin/feat/core-serialization-impl
 */
expect class PlatformByteBuffer {
    companion object {
        fun allocate(capacity: Int): PlatformByteBuffer
<<<<<<< HEAD
        fun wrap(array: ByteArray, offset: Int = 0, length: Int = array.size): PlatformByteBuffer
    }
    
    fun put(byte: Byte): PlatformByteBuffer
    fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    fun putLong(value: Long): PlatformByteBuffer
    fun getLong(): Long
    fun get(dst: ByteArray): PlatformByteBuffer
    fun flip(): PlatformByteBuffer
    fun clear(): PlatformByteBuffer
    fun rewind(): PlatformByteBuffer
    
    fun array(): ByteArray
    fun position(): Int
    fun position(newPosition: Int): PlatformByteBuffer
    fun limit(): Int
    fun limit(newLimit: Int): PlatformByteBuffer
    fun capacity(): Int
    fun remaining(): Int
    fun hasRemaining(): Boolean
}

/**
 * Platform-agnostic internet socket address
 */
expect class PlatformInetSocketAddress {
    constructor(host: String, port: Int)
    
    val hostName: String
    val hostString: String
    val port: Int
    val isUnresolved: Boolean
    val address: Any  // Platform-specific address type
}

/**
 * Platform-agnostic datagram packet
 */
expect class PlatformDatagramPacket {
    constructor(data: ByteArray, length: Int, address: PlatformInetSocketAddress)
    constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress)
    
    val data: ByteArray
    val length: Int
    val offset: Int
    val address: PlatformInetSocketAddress
    val packet: Any  // Platform-specific packet type
}

/**
 * Platform-agnostic datagram socket
 */
expect class PlatformDatagramSocket {
    companion object {
        fun create(): PlatformDatagramSocket
        fun create(port: Int): PlatformDatagramSocket
        fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket
    }
    
=======
        fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    }

    fun put(byte: Byte): PlatformByteBuffer
    fun put(src: ByteArray): PlatformByteBuffer
    fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    fun putLong(value: Long): PlatformByteBuffer
    fun get(): Byte
    fun get(dst: ByteArray): PlatformByteBuffer
    fun getLong(): Long
    fun flip(): PlatformByteBuffer
    fun remaining(): Int
    fun array(): ByteArray
    fun limit(): Int
    fun position(): Int
    fun position(newPosition: Int): PlatformByteBuffer
}

/**
 * Expected interface for a platform-agnostic Channel.
 * This abstracts java.nio.channels.Channel.
 */
expect class PlatformChannel {
    fun read(buffer: PlatformByteBuffer): Int
    fun write(buffer: PlatformByteBuffer): Int
    fun close()
    val isOpen: Boolean
}

/**
 * Expected interface for a platform-agnostic DatagramSocket.
 * This abstracts java.net.DatagramSocket.
 */
expect class PlatformDatagramSocket {
>>>>>>> origin/feat/core-serialization-impl
    fun connect(address: PlatformInetSocketAddress)
    fun send(packet: PlatformDatagramPacket)
    fun receive(packet: PlatformDatagramPacket)
    fun close()
<<<<<<< HEAD
    
    val isConnected: Boolean
    val isClosed: Boolean
    val localAddress: PlatformInetSocketAddress?
    val remoteAddress: PlatformInetSocketAddress?
}

/**
 * Platform-agnostic channel abstraction
 */
expect class PlatformChannel {
    fun isOpen(): Boolean
    fun close()
}
=======
    val isConnected: Boolean
    val isClosed: Boolean
    val remoteSocketAddress: PlatformSocketAddress?
    val inetAddress: PlatformInetAddress?
    val port: Int
}

/**
 * Expected interface for a platform-agnostic InetSocketAddress.
 * This abstracts java.net.InetSocketAddress.
 */
expect class PlatformInetSocketAddress {
    constructor(hostname: String, port: Int)
    constructor(address: PlatformInetAddress, port: Int)

    val hostName: String
    val port: Int
}

/**
 * Expected interface for a platform-agnostic InetAddress.
 * This abstracts java.net.InetAddress.
 */
expect class PlatformInetAddress {
    companion object {
        fun getLocalHost(): PlatformInetAddress
    }
    // Add other necessary methods/properties if needed, e.g., getHostAddress()
}

/**
 * Expected interface for a platform-agnostic DatagramPacket.
 * This abstracts java.net.DatagramPacket.
 */
expect class PlatformDatagramPacket {
    constructor(buf: ByteArray, length: Int)
    constructor(buf: ByteArray, length: Int, address: PlatformSocketAddress)

    val data: ByteArray
    var length: Int
    val address: PlatformSocketAddress?
    val port: Int
}

/**
 * Expected interface for a platform-agnostic SocketAddress.
 * This abstracts java.net.SocketAddress.
 */
expect abstract class PlatformSocketAddress

/**
 * Expected function to get current time in milliseconds.
 * This abstracts System.currentTimeMillis().
 */
expect fun platformCurrentTimeMillis(): Long

/**
 * Expected interface for platform-specific SocketException.
 */
expect class PlatformSocketException(message: String?) : Exception
>>>>>>> origin/feat/core-serialization-impl
