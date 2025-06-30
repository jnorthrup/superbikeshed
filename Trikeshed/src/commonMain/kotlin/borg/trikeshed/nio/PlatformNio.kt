package borg.trikeshed.nio

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Platform-agnostic NIO abstractions for Trikeshed
 */

/**
 * Platform-agnostic byte buffer abstraction
 * This abstracts java.nio.ByteBuffer.
 */
expect class PlatformByteBuffer {
    companion object {
        fun allocate(capacity: Int): PlatformByteBuffer
        fun wrap(array: ByteArray, offset: Int = 0, length: Int = array.size): PlatformByteBuffer
    }
    
    fun put(byte: Byte): PlatformByteBuffer
    fun put(src: ByteArray): PlatformByteBuffer
    fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    fun putLong(value: Long): PlatformByteBuffer
    fun get(): Byte
    fun get(dst: ByteArray): PlatformByteBuffer
    fun getLong(): Long
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
 * This abstracts java.net.InetSocketAddress.
 */
expect class PlatformInetSocketAddress {
    constructor(host: String, port: Int)
    constructor(address: PlatformInetAddress, port: Int)
    
    val hostName: String
    val hostString: String
    val port: Int
    val isUnresolved: Boolean
    val address: Any  // Platform-specific address type
}

/**
 * Platform-agnostic internet address
 * This abstracts java.net.InetAddress.
 */
expect class PlatformInetAddress {
    companion object {
        fun getLocalHost(): PlatformInetAddress
    }
    // Add other necessary methods/properties if needed, e.g., getHostAddress()
}

/**
 * Platform-agnostic socket address
 * This abstracts java.net.SocketAddress.
 */
expect abstract class PlatformSocketAddress

/**
 * Platform-agnostic datagram packet
 * This abstracts java.net.DatagramPacket.
 */
expect class PlatformDatagramPacket {
    constructor(data: ByteArray, length: Int)
    constructor(data: ByteArray, length: Int, address: PlatformSocketAddress)
    constructor(data: ByteArray, length: Int, address: PlatformInetSocketAddress)
    constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress)
    
    val data: ByteArray
    var length: Int
    val offset: Int
    val address: PlatformSocketAddress?
    val socketAddress: PlatformInetSocketAddress?
    val port: Int
    val packet: Any  // Platform-specific packet type
}

/**
 * Platform-agnostic datagram socket
 * This abstracts java.net.DatagramSocket.
 */
expect class PlatformDatagramSocket {
    companion object {
        fun create(): PlatformDatagramSocket
        fun create(port: Int): PlatformDatagramSocket
        fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket
    }
    
    fun connect(address: PlatformInetSocketAddress)
    fun send(packet: PlatformDatagramPacket)
    fun receive(packet: PlatformDatagramPacket)
    fun close()
    
    val isConnected: Boolean
    val isClosed: Boolean
    val localAddress: PlatformInetSocketAddress?
    val remoteAddress: PlatformInetSocketAddress?
    val remoteSocketAddress: PlatformSocketAddress?
    val inetAddress: PlatformInetAddress?
    val port: Int
}

/**
 * Platform-agnostic channel abstraction
 * This abstracts java.nio.channels.Channel.
 */
expect class PlatformChannel {
    fun read(buffer: PlatformByteBuffer): Int
    fun write(buffer: PlatformByteBuffer): Int
    fun close()
    fun isOpen(): Boolean
    val isOpen: Boolean
}

/**
 * Platform-agnostic socket exception
 */
expect class PlatformSocketException(message: String?) : Exception

/**
 * Platform-agnostic current time in milliseconds
 * This abstracts System.currentTimeMillis().
 */
expect fun platformCurrentTimeMillis(): Long