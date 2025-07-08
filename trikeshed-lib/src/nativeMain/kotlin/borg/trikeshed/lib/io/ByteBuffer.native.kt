package borg.trikeshed.lib.io

actual class ByteBuffer internal constructor(
    internal val array: ByteArray,
    internal var _position: Int = 0,
    internal var _limit: Int = array.size,
    internal var _capacity: Int = array.size
) {
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = ByteBuffer(ByteArray(capacity))
        actual fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array.copyOf())
    }
    actual fun put(byte: Byte): ByteBuffer {
        if (_position >= _limit) throw IllegalStateException("Buffer overflow")
        array[_position++] = byte
        return this
    }
    actual fun get(): Byte {
        if (_position >= _limit) throw IllegalStateException("Buffer underflow")
        return array[_position++]
    }
    actual fun put(src: ByteArray): ByteBuffer {
        return put(src, 0, src.size)
    }
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (_position + length > _limit) throw IllegalStateException("Buffer overflow")
        src.copyInto(array, _position, offset, offset + length)
        _position += length
        return this
    }
    actual fun get(dst: ByteArray): ByteBuffer {
        return get(dst, 0, dst.size)
    }
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (_position + length > _limit) throw IllegalStateException("Buffer underflow")
        array.copyInto(dst, offset, _position, _position + length)
        _position += length
        return this
    }
    actual fun flip(): ByteBuffer {
        _limit = _position
        _position = 0
        return this
    }
    actual fun clear(): ByteBuffer {
        _position = 0
        _limit = _capacity
        return this
    }
    actual fun rewind(): ByteBuffer {
        _position = 0
        return this
    }
    actual fun mark(): ByteBuffer {
        // No-op for this simple implementation
        return this
    }
    actual fun reset(): ByteBuffer {
        // No-op for this simple implementation
        return this
    }
    actual fun position(): Int = _position
    actual fun position(newPosition: Int): ByteBuffer {
        if (newPosition < 0 || newPosition > _limit) throw IllegalArgumentException("Invalid position")
        _position = newPosition
        return this
    }
    actual fun limit(): Int = _limit
    actual fun limit(newLimit: Int): ByteBuffer {
        if (newLimit < 0 || newLimit > _capacity) throw IllegalArgumentException("Invalid limit")
        _limit = newLimit
        if (_position > _limit) _position = _limit
        return this
    }
    actual fun capacity(): Int = _capacity
    actual fun hasRemaining(): Boolean = _position < _limit
    actual fun remaining(): Int = _limit - _position
    actual fun array(): ByteArray = array.copyOf()
} 