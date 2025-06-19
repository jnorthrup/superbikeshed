package borg.trikeshed.nio

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

// WASM implementations - limited by runtime environment
actual class PlatformDatagramSocket {
    actual fun connect(address: PlatformInetSocketAddress) {
        TODO("DatagramSocket not available in WASM - requires host binding")
    }

    actual fun send(packet: PlatformDatagramPacket) {
        TODO("DatagramSocket not available in WASM - requires host binding")
    }

    actual fun receive(packet: PlatformDatagramPacket) {
        TODO("DatagramSocket not available in WASM - requires host binding")
    }

    actual fun close() {
        // No-op in WASM context
    }

    actual val isConnected: Boolean = false
    actual val isClosed: Boolean = false
    actual val remoteSocketAddress: PlatformSocketAddress? = null
    actual val inetAddress: PlatformInetAddress? = null
    actual val port: Int = 0
}

actual class PlatformInetSocketAddress : PlatformSocketAddress {
    actual val hostName: String
    actual val port: Int

    actual constructor(hostname: String, port: Int) {
        this.hostName = hostname
        this.port = port
    }

    actual constructor(address: PlatformInetAddress, port: Int) {
        this.hostName = address.hostName
        this.port = port
    }
}

actual class PlatformInetAddress(val hostName: String) {
    actual companion object {
        actual fun getLocalHost(): PlatformInetAddress = PlatformInetAddress("localhost")
    }
}

actual class PlatformDatagramPacket {
    actual val data: ByteArray
    actual var length: Int
    actual val address: PlatformSocketAddress?
    actual val port: Int

    actual constructor(buf: ByteArray, length: Int) {
        this.data = buf
        this.length = length
        this.address = null
        this.port = 0
    }

    actual constructor(buf: ByteArray, length: Int, address: PlatformSocketAddress) {
        this.data = buf
        this.length = length
        this.address = address
        this.port = (address as? PlatformInetSocketAddress)?.port ?: 0
    }
}

actual abstract class PlatformSocketAddress

// Placeholder - WASM runtime should provide this
external fun wasmCurrentTimeMillis(): Long

actual fun platformCurrentTimeMillis(): Long = try {
    wasmCurrentTimeMillis()
} catch (e: Throwable) {
    // Fallback if host doesn't provide the function
    0L
}

actual class PlatformSocketException actual constructor(message: String?) : Exception(message)