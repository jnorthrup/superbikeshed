package borg.trikeshed.nio

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*

/**
 * Platform-agnostic NIO abstractions for Trikeshed
 */

/**
 * Platform-agnostic byte buffer abstraction
 */
expect class PlatformByteBuffer {
    companion object {
        fun allocate(capacity: Int): PlatformByteBuffer
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
    
    fun connect(address: PlatformInetSocketAddress)
    fun send(packet: PlatformDatagramPacket)
    fun receive(packet: PlatformDatagramPacket)
    fun close()
    
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