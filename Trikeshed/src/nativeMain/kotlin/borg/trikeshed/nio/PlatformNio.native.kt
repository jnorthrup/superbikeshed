package borg.trikeshed.nio

import kotlinx.cinterop.*
import platform.posix.*

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

    actual fun put(src: ByteArray): PlatformByteBuffer {
        if (pos + src.size > lim) throw RuntimeException("Buffer overflow")
        src.copyInto(data, pos)
        pos += src.size
        return this
    }

    actual fun get(): Byte {
        if (pos >= lim) throw RuntimeException("Buffer underflow")
        return data[pos++]
    }

    actual fun get(dst: ByteArray): PlatformByteBuffer {
        if (pos + dst.size > lim) throw RuntimeException("Buffer underflow")
        data.copyInto(dst, 0, pos, pos + dst.size)
        pos += dst.size
        return this
    }

    actual fun getLong(): Long {
        if (pos + 8 > lim) throw RuntimeException("Buffer underflow")
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (data[pos++].toLong() and 0xFF)
        }
        return result
    }

    actual fun flip(): PlatformByteBuffer {
        lim = pos
        pos = 0
        return this
    }

    actual fun remaining(): Int = lim - pos
    actual fun array(): ByteArray = data
    actual fun limit(): Int = lim
    actual fun position(): Int = pos
    actual fun position(newPosition: Int): PlatformByteBuffer {
        pos = newPosition
        return this
    }
}

// Stub implementations for Native platform
actual class PlatformDatagramSocket {
    actual fun connect(address: PlatformInetSocketAddress) {
        TODO("Native DatagramSocket not implemented")
    }

    actual fun send(packet: PlatformDatagramPacket) {
        TODO("Native DatagramSocket not implemented")
    }

    actual fun receive(packet: PlatformDatagramPacket) {
        TODO("Native DatagramSocket not implemented")
    }

    actual fun close() {
        TODO("Native DatagramSocket not implemented")
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
        // TODO("Native socket implementation requires POSIX socket integration")
    }

    actual constructor(address: PlatformInetAddress, port: Int) {
        this.hostName = address.hostName
        this.port = port
        // TODO("Native socket implementation requires POSIX socket integration")
    }
}

actual class PlatformInetAddress(val hostName: String = "localhost") {
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
        // TODO("Native DatagramPacket implementation requires POSIX socket integration")
    }

    actual constructor(buf: ByteArray, length: Int, address: PlatformSocketAddress) {
        this.data = buf
        this.length = length
        this.address = address
        this.port = (address as? PlatformInetSocketAddress)?.port ?: 0
        // TODO("Native DatagramPacket implementation requires POSIX socket integration")
    }
}

actual abstract class PlatformSocketAddress

actual fun platformCurrentTimeMillis(): Long = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds

actual class PlatformSocketException actual constructor(message: String?) : Exception(message)