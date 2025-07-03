package borg.trikeshed.nio

import borg.trikeshed.lib.*

/**
 * WasmJs implementations of platform NIO classes
 * Simplified stub implementations for compilation
 */

// WASM implementation with byte array backing
actual class PlatformByteBuffer(private val backing: ByteArray, private var _position: Int = 0, private var _limit: Int = backing.size) {
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer {
            return PlatformByteBuffer(ByteArray(capacity))
        }
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
            val wrapped = array.copyOfRange(offset, offset + length)
            return PlatformByteBuffer(wrapped)
        }
    }

    actual fun put(byte: Byte): PlatformByteBuffer {
        backing[_position] = byte
        _position++
        return this
    }

    actual fun put(src: ByteArray): PlatformByteBuffer {
        System.arraycopy(src, 0, backing, _position, src.size)
        _position += src.size
        return this
    }

    actual fun get(): Byte {
        val value = backing[_position]
        _position++
        return value
    }

    actual fun get(dst: ByteArray): PlatformByteBuffer {
        System.arraycopy(backing, _position, dst, 0, dst.size)
        _position += dst.size
        return this
    }

    actual fun getLong(): Long {
        // Manually construct Long from 8 bytes (big-endian)
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (backing[_position + i].toLong() and 0xFF)
        }
        _position += 8
        return result
    }

    actual fun flip(): PlatformByteBuffer {
        _limit = _position
        _position = 0
        return this
    }

    actual fun remaining(): Int = _limit - _position

    actual fun array(): ByteArray = backing.copyOf()

    actual fun limit(): Int = _limit
    actual fun position(): Int = _position
    actual fun position(newPosition: Int): PlatformByteBuffer {
        _position = newPosition
        return this
    }
}

actual class PlatformInetSocketAddress {
    actual constructor(host: String, port: Int) {
        this.hostName = host
        this.hostString = host
        this.port = port
        this.isUnresolved = false
        this.address = "$host:$port"
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

// Placeholder - WASM runtime should provide this
external fun wasmCurrentTimeMillis(): Long

actual fun platformCurrentTimeMillis(): Long = try {
    wasmCurrentTimeMillis()
} catch (e: Throwable) {
    // Fallback if host doesn't provide the function
    0L
}

actual class PlatformSocketException actual constructor(message: String?) : Exception(message)
