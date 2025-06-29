package borg.trikeshed.nio

import borg.trikeshed.lib.*

/**
 * WasmJs implementations of platform NIO classes
 * Simplified stub implementations for compilation
 */

actual class PlatformByteBuffer {
    private val data = mutableListOf<Byte>()
    private var pos = 0
    private var lim = 0
    
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer = PlatformByteBuffer()
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
            val buffer = PlatformByteBuffer()
            for (i in offset until (offset + length)) {
                buffer.data.add(array[i])
            }
            return buffer
        }
    }
    
    actual fun put(byte: Byte): PlatformByteBuffer {
        data.add(byte)
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
        for (i in offset until (offset + length)) {
            data.add(src[i])
        }
        return this
    }
    
    actual fun putLong(value: Long): PlatformByteBuffer {
        // Simple implementation - add 8 bytes
        repeat(8) { data.add(0) }
        return this
    }
    
    actual fun getLong(): Long = 0L
    
    actual fun get(dst: ByteArray): PlatformByteBuffer {
        for (i in dst.indices) {
            dst[i] = if (pos < data.size) data[pos++] else 0
        }
        return this
    }
    
    actual fun flip(): PlatformByteBuffer {
        lim = data.size
        pos = 0
        return this
    }
    
    actual fun clear(): PlatformByteBuffer {
        data.clear()
        pos = 0
        lim = 0
        return this
    }
    
    actual fun rewind(): PlatformByteBuffer {
        pos = 0
        return this
    }
    
    actual fun array(): ByteArray = data.toByteArray()
    actual fun position(): Int = pos
    actual fun position(newPosition: Int): PlatformByteBuffer {
        pos = newPosition
        return this
    }
    actual fun limit(): Int = lim
    actual fun limit(newLimit: Int): PlatformByteBuffer {
        lim = newLimit
        return this
    }
    actual fun capacity(): Int = data.size
    actual fun remaining(): Int = lim - pos
    actual fun hasRemaining(): Boolean = pos < lim
}

actual class PlatformInetSocketAddress {
    actual constructor(host: String, port: Int) {
        this.hostName = host
        this.hostString = host
        this.port = port
        this.isUnresolved = false
        this.address = "stub-address"
    }
    
    actual val hostName: String
    actual val hostString: String
    actual val port: Int
    actual val isUnresolved: Boolean
    actual val address: Any
}

actual class PlatformDatagramPacket {
    actual constructor(data: ByteArray, length: Int, address: PlatformInetSocketAddress) {
        this.data = data
        this.length = length
        this.offset = 0
        this.address = address
    }
    
    actual constructor(data: ByteArray, offset: Int, length: Int, address: PlatformInetSocketAddress) {
        this.data = data
        this.length = length
        this.offset = offset
        this.address = address
    }
    
    actual val data: ByteArray
    actual val length: Int
    actual val offset: Int
    actual val address: PlatformInetSocketAddress
    actual val packet: Any = "stub-packet"
}

actual class PlatformDatagramSocket {
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket()
        actual fun create(port: Int): PlatformDatagramSocket = PlatformDatagramSocket()
        actual fun create(address: PlatformInetSocketAddress): PlatformDatagramSocket = PlatformDatagramSocket()
    }
    
    actual fun connect(address: PlatformInetSocketAddress) {}
    actual fun send(packet: PlatformDatagramPacket) {}
    actual fun receive(packet: PlatformDatagramPacket) {}
    actual fun close() {}
    
    actual val isConnected: Boolean = false
    actual val isClosed: Boolean = false
    actual val localAddress: PlatformInetSocketAddress? = null
    actual val remoteAddress: PlatformInetSocketAddress? = null
}

actual class PlatformChannel {
    actual fun isOpen(): Boolean = false
    actual fun close() {}
}