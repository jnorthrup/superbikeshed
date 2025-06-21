package borg.trikeshed.nio

actual class ByteBuffer(
    private val data: ByteArray,
    private var pos: Int = 0,
    private var lim: Int = data.size
) {
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer {
            return ByteBuffer(ByteArray(capacity))
        }

        actual fun wrap(array: ByteArray): ByteBuffer {
            return ByteBuffer(array)
        }
    }

    actual val capacity: Int
        get() = data.size

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
        return data[pos++]
    }

    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) throw RuntimeException("Buffer underflow")
        data.copyInto(dst, offset, pos, pos + length)
        pos += length
        return this
    }

    actual fun put(byte: Byte): ByteBuffer {
        if (pos >= lim) throw RuntimeException("Buffer overflow")
        data[pos++] = byte
        return this
    }

    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) throw RuntimeException("Buffer overflow")
        src.copyInto(data, pos, offset, offset + length)
        pos += length
        return this
    }

    actual fun getInt(): Int {
        if (remaining() < 4) throw RuntimeException("Buffer underflow")
        val result = ((data[pos].toInt() and 0xFF) shl 24) or
                ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or
                (data[pos + 3].toInt() and 0xFF)
        pos += 4
        return result
    }

    actual fun putInt(value: Int): ByteBuffer {
        if (remaining() < 4) throw RuntimeException("Buffer overflow")
        data[pos] = (value ushr 24).toByte()
        data[pos + 1] = (value ushr 16).toByte()
        data[pos + 2] = (value ushr 8).toByte()
        data[pos + 3] = value.toByte()
        pos += 4
        return this
    }

    actual fun getLong(): Long {
        if (remaining() < 8) throw RuntimeException("Buffer underflow")
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (data[pos + i].toLong() and 0xFF)
        }
        pos += 8
        return result
    }

    actual fun putLong(value: Long): ByteBuffer {
        if (remaining() < 8) throw RuntimeException("Buffer overflow")
        for (i in 7 downTo 0) {
            data[pos + (7 - i)] = (value ushr (i * 8)).toByte()
        }
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
        return data
    }
}