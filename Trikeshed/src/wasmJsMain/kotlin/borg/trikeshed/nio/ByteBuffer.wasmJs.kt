package borg.trikeshed.nio

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

class WasmByteBuffer(capacity: Int) : ByteBuffer {
    private val data = ByteArray(capacity)
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
        data[pos++] = byte
    }

    override fun put(bytes: ByteArray) {
        if (pos + bytes.size > lim) throw RuntimeException("Buffer overflow")
        bytes.copyInto(data, pos)
        pos += bytes.size
    }

    override fun putInt(value: Int) {
        if (pos + 4 > lim) throw RuntimeException("Buffer overflow")
        data[pos++] = (value ushr 24).toByte()
        data[pos++] = (value ushr 16).toByte()
        data[pos++] = (value ushr 8).toByte()
        data[pos++] = value.toByte()
    }

    override fun putLong(value: Long) {
        if (pos + 8 > lim) throw RuntimeException("Buffer overflow")
        for (i in 7 downTo 0) {
            data[pos++] = (value ushr (i * 8)).toByte()
        }
    }

    override fun get(): Byte {
        if (pos >= lim) throw RuntimeException("Buffer underflow")
        return data[pos++]
    }

    override fun get(bytes: ByteArray) {
        if (pos + bytes.size > lim) throw RuntimeException("Buffer underflow")
        data.copyInto(bytes, 0, pos, pos + bytes.size)
        pos += bytes.size
    }

    override fun getInt(): Int {
        if (pos + 4 > lim) throw RuntimeException("Buffer underflow")
        return ((data[pos++].toInt() and 0xFF) shl 24) or
               ((data[pos++].toInt() and 0xFF) shl 16) or
               ((data[pos++].toInt() and 0xFF) shl 8) or
               (data[pos++].toInt() and 0xFF)
    }

    override fun getLong(): Long {
        if (pos + 8 > lim) throw RuntimeException("Buffer underflow")
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (data[pos++].toLong() and 0xFF)
        }
        return result
    }

    override fun limit(): Int = lim
    override fun limit(newLimit: Int) { lim = newLimit }
    override fun capacity(): Int = cap
}