package borg.trikeshed.nio

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Int8Array

// JavaScript implementation using ArrayBuffer/DataView
actual class PlatformByteBuffer(private val dataView: DataView, private var _position: Int = 0, private var _limit: Int = dataView.byteLength) {
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer {
            val buffer = ArrayBuffer(capacity)
            return PlatformByteBuffer(DataView(buffer))
        }
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer {
            val buffer = ArrayBuffer(length)
            val dataView = DataView(buffer)
            val int8Array = Int8Array(buffer)
            for (i in 0 until length) {
                int8Array[i] = array[offset + i]
            }
            return PlatformByteBuffer(dataView)
        }
    }

    actual fun put(byte: Byte): PlatformByteBuffer {
        dataView.setInt8(_position, byte)
        _position++
        return this
    }

    actual fun put(src: ByteArray): PlatformByteBuffer {
        val int8Array = Int8Array(dataView.buffer, _position, src.size)
        for (i in src.indices) {
            int8Array[i] = src[i]
        }
        _position += src.size
        return this
    }

    actual fun get(): Byte {
        val value = dataView.getInt8(_position)
        _position++
        return value
    }

    actual fun get(dst: ByteArray): PlatformByteBuffer {
        val int8Array = Int8Array(dataView.buffer, _position, dst.size)
        for (i in dst.indices) {
            dst[i] = int8Array[i]!!
        }
        _position += dst.size
        return this
    }

    actual fun getLong(): Long {
        // JavaScript doesn't have native 64-bit int support, construct from two 32-bit values
        val high = dataView.getInt32(_position, false)
        val low = dataView.getInt32(_position + 4, false)
        _position += 8
        return (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFF)
    }

    actual fun flip(): PlatformByteBuffer {
        _limit = _position
        _position = 0
        return this
    }

    actual fun remaining(): Int = _limit - _position

    actual fun array(): ByteArray {
        val result = ByteArray(dataView.byteLength)
        val int8Array = Int8Array(dataView.buffer)
        for (i in result.indices) {
            result[i] = int8Array[i]!!
        }
        return result
    }

    actual fun limit(): Int = _limit
    actual fun position(): Int = _position
    actual fun position(newPosition: Int): PlatformByteBuffer {
        _position = newPosition
        return this
    }
}

// JavaScript implementations - limited by browser security model
actual class PlatformDatagramSocket {
    actual fun connect(address: PlatformInetSocketAddress) {
        TODO("DatagramSocket not available in browser - requires WebRTC or WebSocket alternatives")
    }

    actual fun send(packet: PlatformDatagramPacket) {
        TODO("DatagramSocket not available in browser - requires WebRTC or WebSocket alternatives")
    }

    actual fun receive(packet: PlatformDatagramPacket) {
        TODO("DatagramSocket not available in browser - requires WebRTC or WebSocket alternatives")
    }

    actual fun close() {
        // No-op in browser context
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

actual fun platformCurrentTimeMillis(): Long = js("Date.now()").unsafeCast<Long>()

actual class PlatformSocketException actual constructor(message: String?) : Exception(message)