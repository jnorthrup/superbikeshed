package borg.trikeshed.nio

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Int8Array

actual interface ByteBuffer {
    actual fun clear()
    actual fun flip()
    actual fun hasRemaining(): Boolean
    actual fun remaining(): Int
    actual fun position(): Int
    actual fun position(newPosition: Int)
    actual fun put(byte: Byte)
    actual fun put(bytes: ByteArray)
    actual fun putInt(value: Int)
    actual fun putLong(value: Long)
    actual fun get(): Byte
    actual fun get(bytes: ByteArray)
    actual fun getInt(): Int
    actual fun getLong(): Long
    actual fun limit(): Int
    actual fun limit(newLimit: Int)
    actual fun capacity(): Int
}

class JsNioByteBuffer(capacity: Int) : ByteBuffer {
    private val arrayBuffer = ArrayBuffer(capacity)
    private val dataView = DataView(arrayBuffer)
    private val int8Array = Int8Array(arrayBuffer)
    private var pos: Int = 0
    private var lim: Int = capacity
    private val cap: Int = capacity

    override fun clear() {
        pos = 0
        lim = cap
    }

    override fun flip() {
        lim = pos
        pos = 0
    }

    override fun hasRemaining(): Boolean = pos < lim
    override fun remaining(): Int = lim - pos
    override fun position(): Int = pos
    override fun position(newPosition: Int) { pos = newPosition }

    override fun put(byte: Byte) {
        if (pos >= lim) throw RuntimeException("Buffer overflow")
        int8Array[pos++] = byte
    }

    override fun put(bytes: ByteArray) {
        if (pos + bytes.size > lim) throw RuntimeException("Buffer overflow")
        for (i in bytes.indices) {
            int8Array[pos + i] = bytes[i]
        }
        pos += bytes.size
    }

    override fun putInt(value: Int) {
        if (pos + 4 > lim) throw RuntimeException("Buffer overflow")
        dataView.setInt32(pos, value, false) // Big-endian
        pos += 4
    }

    override fun putLong(value: Long) {
        if (pos + 8 > lim) throw RuntimeException("Buffer overflow")
        // JavaScript doesn't have native 64-bit ints, so we split into two 32-bit parts
        val high = (value shr 32).toInt()
        val low = value.toInt()
        dataView.setInt32(pos, high, false)
        dataView.setInt32(pos + 4, low, false)
        pos += 8
    }

    override fun get(): Byte {
        if (pos >= lim) throw RuntimeException("Buffer underflow")
        return int8Array[pos++]
    }

    override fun get(bytes: ByteArray) {
        if (pos + bytes.size > lim) throw RuntimeException("Buffer underflow")
        for (i in bytes.indices) {
            bytes[i] = int8Array[pos + i]
        }
        pos += bytes.size
    }

    override fun getInt(): Int {
        if (pos + 4 > lim) throw RuntimeException("Buffer underflow")
        val result = dataView.getInt32(pos, false) // Big-endian
        pos += 4
        return result
    }

    override fun getLong(): Long {
        if (pos + 8 > lim) throw RuntimeException("Buffer underflow")
        val high = dataView.getInt32(pos, false).toLong()
        val low = dataView.getInt32(pos + 4, false).toLong() and 0xFFFFFFFFL
        pos += 8
        return (high shl 32) or low
    }

    override fun limit(): Int = lim
    override fun limit(newLimit: Int) { lim = newLimit }
    override fun capacity(): Int = cap
}