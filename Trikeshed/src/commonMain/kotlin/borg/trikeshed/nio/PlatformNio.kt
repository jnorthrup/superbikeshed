package borg.trikeshed.nio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Expected interface for a platform-agnostic ByteBuffer.
 * This abstracts java.nio.ByteBuffer.
 */
expect class PlatformByteBuffer {
    companion object {
        fun allocate(capacity: Int): PlatformByteBuffer
        fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    }

    fun put(byte: Byte): PlatformByteBuffer
    fun put(src: ByteArray): PlatformByteBuffer
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
 * Expected interface for a platform-agnostic DatagramSocket.
 * This abstracts java.net.DatagramSocket.
 */
expect class PlatformDatagramSocket {
    fun connect(address: PlatformInetSocketAddress)
    fun send(packet: PlatformDatagramPacket)
    fun receive(packet: PlatformDatagramPacket)
    fun close()
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