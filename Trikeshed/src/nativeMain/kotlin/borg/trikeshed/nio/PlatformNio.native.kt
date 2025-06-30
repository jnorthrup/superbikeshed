@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.nio

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native implementation of platform NIO abstractions
 */

actual class PlatformByteBuffer private constructor(private val data: ByteArray, private var pos: Int = 0, private var lim: Int = data.size) {
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer = 
            PlatformByteBuffer(ByteArray(capacity), 0, capacity)
            
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer = 
            PlatformByteBuffer(array.copyOfRange(offset, offset + length), 0, length)
    }
    
    actual fun put(byte: Byte): PlatformByteBuffer {
        if (pos >= lim) throw RuntimeException("Buffer overflow")
        data[pos++] = byte
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
        if (pos + length > lim) throw RuntimeException("Buffer overflow")
        src.copyInto(data, pos, offset, offset + length)
        pos += length
        return this
    }
    
    actual fun put(src: ByteArray): PlatformByteBuffer {
        if (pos + src.size > lim) throw RuntimeException("Buffer overflow")
        src.copyInto(data, pos)
        pos += src.size
        return this
    }
    
    actual fun putLong(value: Long): PlatformByteBuffer {
        if (pos + 8 > lim) throw RuntimeException("Buffer overflow")
        for (i in 0 until 8) {
            data[pos + i] = ((value shr (56 - i * 8)) and 0xFF).toByte()
        }
        pos += 8
        return this
    }
    
    actual fun get(): Byte {
        if (pos >= lim) throw RuntimeException("Buffer underflow")
        return data[pos++]
    }
    
    actual fun getLong(): Long {
        if (pos + 8 > lim) throw RuntimeException("Buffer underflow")
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (data[pos++].toLong() and 0xFF)
        }
        return result
    }
    
    actual fun get(dst: ByteArray): PlatformByteBuffer {
        if (pos + dst.size > lim) throw RuntimeException("Buffer underflow")
        data.copyInto(dst, 0, pos, pos + dst.size)
        pos += dst.size
        return this
    }
    
    actual fun flip(): PlatformByteBuffer {
        lim = pos
        pos = 0
        return this
    }
    
    actual fun clear(): PlatformByteBuffer {
        pos = 0
        lim = data.size
        return this
    }
    
    actual fun rewind(): PlatformByteBuffer {
        pos = 0
        return this
    }
    
    actual fun array(): ByteArray = data
    
    actual fun position(): Int = pos
    
    actual fun position(newPosition: Int): PlatformByteBuffer {
        if (newPosition < 0 || newPosition > lim) throw RuntimeException("Invalid position")
        pos = newPosition
        return this
    }
    
    actual fun limit(): Int = lim
    
    actual fun limit(newLimit: Int): PlatformByteBuffer {
        if (newLimit < 0 || newLimit > data.size) throw RuntimeException("Invalid limit")
        lim = newLimit
        if (pos > lim) pos = lim
        return this
    }
    
    actual fun capacity(): Int = data.size
    
    actual fun remaining(): Int = lim - pos
    
    actual fun hasRemaining(): Boolean = pos < lim
}

actual class PlatformInetSocketAddress actual constructor(host: String, actual val port: Int) {
    actual val hostName: String = host
    actual val hostString: String get() = hostName
    actual val isUnresolved: Boolean get() = false // Simplified for native
    actual val address: Any get() = "$hostName:$port" // Simplified representation
}

actual class PlatformDatagramPacket actual constructor(
    actual val data: ByteArray,
    actual val length: Int,
    actual val address: PlatformInetSocketAddress
) {
    actual constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress) : 
        this(data.copyOfRange(offset, offset + length), length, address)
    
    actual val offset: Int get() = 0
    actual val packet: Any get() = this // Return self as packet representation
}

actual class PlatformDatagramSocket {
    private var sockfd: Int = -1
    private var connected: Boolean = false
    private var closed: Boolean = false
    private var localAddr: PlatformInetSocketAddress? = null
    private var remoteAddr: PlatformInetSocketAddress? = null
    
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket().apply { 
            sockfd = socket(AF_INET, SOCK_DGRAM, 0)
            if (sockfd == -1) throw RuntimeException("Failed to create socket")
        }
        
        actual fun create(port: Int): PlatformDatagramSocket = create().apply {
            // Bind to port - simplified implementation
            localAddr = PlatformInetSocketAddress("0.0.0.0", port)
        }
        
        actual fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket = create().apply {
            localAddr = address
        }
    }
    
    actual fun connect(address: PlatformInetSocketAddress) {
        remoteAddr = address
        connected = true
    }
    
    actual fun send(packet: PlatformDatagramPacket) {
        // Simplified send implementation for native
        if (closed) throw RuntimeException("Socket is closed")
    }
    
    actual fun receive(packet: PlatformDatagramPacket) {
        // Simplified receive implementation for native
        if (closed) throw RuntimeException("Socket is closed")
    }
    
    actual fun close() {
        if (sockfd != -1) {
            platform.posix.close(sockfd)
            sockfd = -1
        }
        closed = true
        connected = false
    }
    
    actual val isConnected: Boolean get() = connected && !closed
    actual val isClosed: Boolean get() = closed
    actual val localAddress: PlatformInetSocketAddress? get() = localAddr
    actual val remoteAddress: PlatformInetSocketAddress? get() = remoteAddr
}

actual class PlatformChannel {
    private var open: Boolean = true
    
    actual fun isOpen(): Boolean = open
    
    actual fun close() {
        open = false
    }
}
