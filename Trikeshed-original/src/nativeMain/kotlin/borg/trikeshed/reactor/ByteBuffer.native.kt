@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.reactor

/**
 * Native implementation of ByteBuffer
 */
actual class ByteBuffer private constructor(private val data: ByteArray) {
    private var _position = 0
    private var _limit = data.size
    
    actual fun put(byte: Byte): ByteBuffer {
        if (_position < _limit) {
            data[_position++] = byte
        }
        return this
    }
    
    actual fun get(): Byte {
        return if (_position < _limit) {
            data[_position++]
        } else {
            throw IndexOutOfBoundsException()
        }
    }
    
    actual fun flip(): ByteBuffer {
        _limit = _position
        _position = 0
        return this
    }
    
    actual fun clear(): ByteBuffer {
        _position = 0
        _limit = data.size
        return this
    }
    
    actual fun position(): Int = _position
    
    actual fun position(newPosition: Int): ByteBuffer {
        _position = newPosition
        return this
    }
    
    actual fun limit(): Int = _limit
    
    actual fun limit(newLimit: Int): ByteBuffer {
        _limit = newLimit
        return this
    }
    
    actual fun capacity(): Int = data.size
    
    actual fun hasRemaining(): Boolean = _position < _limit
    
    actual fun remaining(): Int = _limit - _position
    
    actual fun array(): ByteArray = data
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = ByteBuffer(ByteArray(capacity))
        actual fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array.copyOf())
    }
}