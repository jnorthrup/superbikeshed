package borg.trikeshed.nio

import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// Actual class implementing the expect interface ByteBuffer for JS
actual class JsArrayByteBuffer actual constructor(
    private val underlying: Int8Array, // Using Int8Array which is JS native for byte arrays
    actual override val capacity: Int
) : ByteBuffer {
    private var _position: Int = 0
    actual override fun position(): Int = _position
    actual override fun position(newPosition: Int) {
        if (newPosition < 0 || newPosition > _limit) throw IndexOutOfBoundsException("Position $newPosition out of bounds for limit $_limit")
        _position = newPosition
    }

    private var _limit: Int = capacity
    actual override fun limit(): Int = _limit
    actual override fun limit(newLimit: Int) {
        if (newLimit < 0 || newLimit > capacity) throw IllegalArgumentException("New limit $newLimit out of bounds for capacity $capacity")
        _limit = newLimit
        if (_position > _limit) _position = _limit
    }

    private var isReadOnly: Boolean = false // Common ByteBuffer property

    actual override fun clear(): ByteBuffer {
        _position = 0
        _limit = capacity
        return this
    }

    actual override fun flip(): ByteBuffer {
        _limit = _position
        _position = 0
        return this
    }

    actual override fun rewind(): ByteBuffer {
        _position = 0
        return this
    }

    actual override fun remaining(): Int = _limit - _position
    actual override fun hasRemaining(): Boolean = _position < _limit

    actual override fun get(): Byte {
        if (_position >= _limit) throw IndexOutOfBoundsException("BufferUnderflow")
        return underlying[_position++].toByte()
    }

    // Simplified get(bytes) for brevity, actual would need offset and length
    actual override fun get(bytes: ByteArray) {
        if (remaining() < bytes.size) throw IndexOutOfBoundsException("BufferUnderflow")
        for (i in bytes.indices) {
            bytes[i] = underlying[_position + i].toByte()
        }
        _position += bytes.size
    }

    actual override fun put(byte: Byte) {
        if (isReadOnly) throw RuntimeException("ReadOnlyBufferException")
        if (_position >= _limit) throw IndexOutOfBoundsException("BufferOverflow")
        underlying[_position++] = byte.toInt()
    }

    // Simplified put(bytes) for brevity
    actual override fun put(bytes: ByteArray) {
        if (isReadOnly) throw RuntimeException("ReadOnlyBufferException")
        if (remaining() < bytes.size) throw IndexOutOfBoundsException("BufferOverflow")
        bytes.forEachIndexed { index, byte -> underlying[_position + index] = byte.toInt() }
        _position += bytes.size
    }

    // getInt, putInt, getLong, putLong would require careful byte-order handling (DataView)
    actual override fun getInt(): Int = TODO("ByteBuffer.js.kt: Not yet implemented getInt")
    actual override fun putInt(value: Int): Unit = TODO("ByteBuffer.js.kt: Not yet implemented putInt")
    actual override fun getLong(): Long = TODO("ByteBuffer.js.kt: Not yet implemented getLong")
    actual override fun putLong(value: Long): Unit = TODO("ByteBuffer.js.kt: Not yet implemented putLong")

    actual override fun duplicate(): ByteBuffer {
        val newBuffer = JsArrayByteBuffer(underlying, capacity) // Shares underlying data
        newBuffer._position = this._position
        newBuffer._limit = this._limit
        return newBuffer
    }
}

// Exceptions would typically be defined in common or be standard JS errors.
// class BufferOverflowException : RuntimeException()
// class BufferUnderflowException : RuntimeException()
// class ReadOnlyBufferException : RuntimeException()
