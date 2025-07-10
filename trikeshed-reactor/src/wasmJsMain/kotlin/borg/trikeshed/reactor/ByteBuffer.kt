@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * WASM/JS platform implementation using ByteArray with manual position tracking.
 * This provides consistent buffer behavior across all platforms.
 */
actual class ByteBuffer internal constructor(
    internal val backing: ByteArray,
    internal var _position: Int = 0,
    internal var _limit: Int = backing.size,
    internal var _capacity: Int = backing.size,
    internal var _mark: Int = -1
) {
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = 
            ByteBuffer(ByteArray(capacity), 0, capacity, capacity)
            
        actual fun wrap(array: ByteArray): ByteBuffer = 
            ByteBuffer(array, 0, array.size, array.size)
    }
    
    // Single byte operations
    actual fun put(byte: Byte): ByteBuffer {
        if (_position >= _limit) {
            throw IndexOutOfBoundsException("Buffer overflow")
        }
        backing[_position++] = byte
        return this
    }
    
    actual fun get(): Byte {
        if (_position >= _limit) {
            throw IndexOutOfBoundsException("Buffer underflow")
        }
        return backing[_position++]
    }
    
    // Bulk operations
    actual fun put(src: ByteArray): ByteBuffer {
        if (remaining() < src.size) {
            throw IndexOutOfBoundsException("Buffer overflow")
        }
        src.copyInto(backing, _position)
        _position += src.size
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) {
            throw IndexOutOfBoundsException("Buffer overflow")
        }
        src.copyInto(backing, _position, offset, offset + length)
        _position += length
        return this
    }
    
    actual fun get(dst: ByteArray): ByteBuffer {
        if (remaining() < dst.size) {
            throw IndexOutOfBoundsException("Buffer underflow")
        }
        backing.copyInto(dst, 0, _position, _position + dst.size)
        _position += dst.size
        return this
    }
    
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining() < length) {
            throw IndexOutOfBoundsException("Buffer underflow")
        }
        backing.copyInto(dst, offset, _position, _position + length)
        _position += length
        return this
    }
    
    // Buffer state operations
    actual fun flip(): ByteBuffer {
        _limit = _position
        _position = 0
        _mark = -1
        return this
    }
    
    actual fun clear(): ByteBuffer {
        _position = 0
        _limit = _capacity
        _mark = -1
        return this
    }
    
    actual fun rewind(): ByteBuffer {
        _position = 0
        _mark = -1
        return this
    }
    
    actual fun mark(): ByteBuffer {
        _mark = _position
        return this
    }
    
    actual fun reset(): ByteBuffer {
        if (_mark < 0) {
            throw IndexOutOfBoundsException("Invalid mark")
        }
        _position = _mark
        return this
    }
    
    // Position and limit operations
    actual fun position(): Int = _position
    
    actual fun position(newPosition: Int): ByteBuffer {
        if (newPosition < 0 || newPosition > _limit) {
            throw IndexOutOfBoundsException("Invalid position: $newPosition")
        }
        _position = newPosition
        if (_mark > newPosition) {
            _mark = -1
        }
        return this
    }
    
    actual fun limit(): Int = _limit
    
    actual fun limit(newLimit: Int): ByteBuffer {
        if (newLimit < 0 || newLimit > _capacity) {
            throw IndexOutOfBoundsException("Invalid limit: $newLimit")
        }
        _limit = newLimit
        if (_position > newLimit) {
            _position = newLimit
        }
        if (_mark > newLimit) {
            _mark = -1
        }
        return this
    }
    
    actual fun capacity(): Int = _capacity
    
    // Remaining operations
    actual fun hasRemaining(): Boolean = _position < _limit
    
    actual fun remaining(): Int = _limit - _position
    
    // Array access
    actual fun array(): ByteArray = backing
    
    /**
     * WASM/JS-specific: Convert to JavaScript Uint8Array.
     */
    fun toUint8Array(): dynamic {
        // This would be implemented using JS interop in a real project
        return backing
    }
    
    /**
     * WASM/JS-specific: Get current slice as new ByteArray.
     */
    fun currentSlice(): ByteArray = backing.sliceArray(_position until _limit)
}