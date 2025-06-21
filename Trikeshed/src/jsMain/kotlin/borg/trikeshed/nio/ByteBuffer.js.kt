package borg.trikeshed.nio

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Int8Array

actual class ByteBuffer private constructor(
    private val arrayBuffer: ArrayBuffer,
    private val dataView: DataView,
    private val int8Array: Int8Array,
    private var pos: Int = 0,
    private var lim: Int
) {
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer {
            val arrayBuffer = ArrayBuffer(capacity)
            return ByteBuffer(
                arrayBuffer = arrayBuffer,
                dataView = DataView(arrayBuffer),
                int8Array = Int8Array(arrayBuffer),
                lim = capacity
            )
        }

        actual fun wrap(array: ByteArray): ByteBuffer {
            val int8Array = array.unsafeCast<Int8Array>()
            return ByteBuffer(
                arrayBuffer = int8Array.buffer,
                dataView = DataView(int8Array.buffer, int8Array.byteOffset, int8Array.byteLength),
                int8Array = int8Array,
                lim = array.size
            )
        }
    }

    actual val capacity: Int
        get() = arrayBuffer.byteLength

    actual var position: Int
        get() = pos
        set(value) {
            if (value < 0 || value > lim) throw IndexOutOfBoundsException("Invalid position: $value")
            pos = value
        }
    actual var limit: Int
        get() = lim
        set(value) {
            if (value < 0 || value > capacity) throw IndexOutOfBoundsException("Invalid limit: $value")
            lim = value
            if (pos > lim) pos = lim
        }

    actual fun remaining(): Int = lim - pos
    actual fun hasRemaining(): Boolean = pos < lim

    actual fun get(): Byte {
        if (pos >= lim) throw RuntimeException("Buffer underflow")
        return int8Array[pos++]
    }

    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) throw RuntimeException("Buffer underflow")
        for (i in 0 until length) {
            dst[offset + i] = int8Array[pos + i]
        }
        pos += length
        return this
    }

    actual fun put(byte: Byte): ByteBuffer {
        if (pos >= lim) throw RuntimeException("Buffer overflow")
        int8Array[pos++] = byte
        return this
    }

    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) throw RuntimeException("Buffer overflow")
        for (i in 0 until length) {
            int8Array[pos + i] = src[offset + i]
        }
        pos += length
        return this
    }

    actual fun getInt(): Int {
        if (remaining() < 4) throw RuntimeException("Buffer underflow")
        val result = dataView.getInt32(pos, false) // Big-endian
        pos += 4
        return result
    }

    actual fun putInt(value: Int): ByteBuffer {
        if (remaining() < 4) throw RuntimeException("Buffer overflow")
        dataView.setInt32(pos, value, false) // Big-endian
        pos += 4
        return this
    }

    actual fun getLong(): Long {
        if (remaining() < 8) throw RuntimeException("Buffer underflow")
        val high = dataView.getInt32(pos, false).toLong()
        val low = dataView.getInt32(pos + 4, false).toLong() and 0xFFFFFFFFL
        pos += 8
        return (high shl 32) or low
    }

    actual fun putLong(value: Long): ByteBuffer {
        if (remaining() < 8) throw RuntimeException("Buffer overflow")
        val high = (value shr 32).toInt()
        val low = value.toInt()
        dataView.setInt32(pos, high, false)
        dataView.setInt32(pos + 4, low, false)
        pos += 8
        return this
    }

    actual fun flip(): ByteBuffer {
        lim = pos
        pos = 0
        return this
    }

    actual fun rewind(): ByteBuffer {
        pos = 0
        return this
    }

    actual fun clear(): ByteBuffer {
        pos = 0
        lim = capacity
        return this
    }

    actual fun array(): ByteArray {
        return Int8Array(arrayBuffer, 0, lim).unsafeCast<ByteArray>()
    }
}