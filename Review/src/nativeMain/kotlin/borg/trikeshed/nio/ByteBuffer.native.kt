package borg.trikeshed.nio

// Custom exceptions for Native target if not available or to ensure consistent behavior.
// These were already present and are fine.
class BufferOverflowException(message: String) : RuntimeException(message)
class BufferUnderflowException(message: String) : RuntimeException(message)
// class ReadOnlyBufferException(message: String) : RuntimeException(message) // Not currently used by MemorySlab

// Actual implementation of the common ByteBuffer interface for Native platforms.
// It uses a ByteArray internally.
actual class NativeArrayByteBuffer(
    private val array: ByteArray,
    private val cap: Int, // Capacity
    private var pos: Int,  // Position
    private var lim: Int   // Limit
) : ByteBuffer {

    // Secondary constructor for simpler initialization, matching common usage.
    constructor(array: ByteArray, capacity: Int) : this(array, capacity, 0, capacity)

    actual override fun clear() {
        pos = 0
        lim = cap
    }

    actual override fun flip() {
        lim = pos
        pos = 0
    }

    actual override fun hasRemaining(): Boolean = pos < lim

    actual override fun remaining(): Int = lim - pos

    actual override fun position(): Int = pos

    actual override fun position(newPosition: Int) {
        if (newPosition < 0 || newPosition > lim) throw IndexOutOfBoundsException("Position $newPosition out of bounds for limit $lim (capacity $cap)")
        pos = newPosition
    }

    private inline fun checkPut(length: Int) {
        if (remaining() < length) throw BufferOverflowException("Buffer overflow: remaining=${remaining()}, needed=$length")
    }

    private inline fun checkGet(length: Int) {
        if (remaining() < length) throw BufferUnderflowException("Buffer underflow: remaining=${remaining()}, needed=$length")
    }

    actual override fun put(byte: Byte) {
        checkPut(1)
        array[pos++] = byte
    }

    actual override fun put(bytes: ByteArray) {
        checkPut(bytes.size)
        bytes.copyInto(array, pos)
        pos += bytes.size
    }

    actual override fun put(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "Invalid offset/length for source ByteArray" }
        checkPut(length)
        bytes.copyInto(destination = array, destinationOffset = pos, startIndex = offset, endIndex = offset + length)
        pos += length
    }

    actual override fun put(source: ByteBuffer) {
        val length = source.remaining()
        checkPut(length)
        if (source is NativeArrayByteBuffer) { // Fast path if also NativeArrayByteBuffer
            source.array.copyInto(destination = this.array, destinationOffset = this.pos, startIndex = source.pos, endIndex = source.lim)
            source.position(source.lim) // Mark source as read
        } else { // Slower path for generic ByteBuffer
            for (i in 0 until length) {
                this.array[pos + i] = source.get()
            }
        }
        pos += length
    }

    actual override fun putShort(value: Short) {
        checkPut(2)
        array[pos++] = (value.toInt() shr 8).toByte()
        array[pos++] = value.toByte()
    }

    actual override fun putInt(value: Int) {
        checkPut(4)
        array[pos++] = (value shr 24).toByte()
        array[pos++] = (value shr 16).toByte()
        array[pos++] = (value shr 8).toByte()
        array[pos++] = value.toByte()
    }

    actual override fun putLong(value: Long) {
        checkPut(8)
        array[pos++] = (value shr 56).toByte()
        array[pos++] = (value shr 48).toByte()
        array[pos++] = (value shr 40).toByte()
        array[pos++] = (value shr 32).toByte()
        array[pos++] = (value shr 24).toByte()
        array[pos++] = (value shr 16).toByte()
        array[pos++] = (value shr 8).toByte()
        array[pos++] = value.toByte()
    }

    // For Float and Double, we need to convert to their bit representations.
    // Kotlin/Native provides ways to do this.
    actual override fun putFloat(value: Float) {
        putInt(value.toBits())
    }

    actual override fun putDouble(value: Double) {
        putLong(value.toBits())
    }

    actual override fun get(): Byte {
        checkGet(1)
        return array[pos++]
    }

    actual override fun get(bytes: ByteArray) {
        checkGet(bytes.size)
        array.copyInto(destination = bytes, destinationOffset = 0, startIndex = pos, endIndex = pos + bytes.size)
        pos += bytes.size
    }

    actual override fun getShort(): Short {
        checkGet(2)
        return ((array[pos++].toInt() and 0xFF shl 8) or
                (array[pos++].toInt() and 0xFF)).toShort()
    }

    actual override fun getInt(): Int {
        checkGet(4)
        return ((array[pos++].toInt() and 0xFF shl 24) or
                (array[pos++].toInt() and 0xFF shl 16) or
                (array[pos++].toInt() and 0xFF shl 8) or
                (array[pos++].toInt() and 0xFF))
    }

    actual override fun getLong(): Long {
        checkGet(8)
        return ((array[pos++].toLong() and 0xFF shl 56) or
                (array[pos++].toLong() and 0xFF shl 48) or
                (array[pos++].toLong() and 0xFF shl 40) or
                (array[pos++].toLong() and 0xFF shl 32) or
                (array[pos++].toLong() and 0xFF shl 24) or
                (array[pos++].toLong() and 0xFF shl 16) or
                (array[pos++].toLong() and 0xFF shl 8) or
                (array[pos++].toLong() and 0xFF))
    }

    actual override fun getFloat(): Float {
        return Float.fromBits(getInt())
    }

    actual override fun getDouble(): Double {
        return Double.fromBits(getLong())
    }

    actual override fun limit(): Int = lim

    actual override fun limit(newLimit: Int) {
        if (newLimit < 0 || newLimit > cap) throw IllegalArgumentException("New limit $newLimit out of bounds for capacity $cap")
        lim = newLimit
        if (pos > lim) pos = lim // Adjust position if new limit is smaller
    }

    actual override fun capacity(): Int = cap

    actual override fun duplicate(): ByteBuffer {
        // Creates a new NativeArrayByteBuffer instance that shares the same underlying ByteArray.
        // Modifications to the buffer's content will be visible in the duplicate, but position, limit, mark are independent.
        return NativeArrayByteBuffer(array, cap, pos, lim)
    }

    actual override fun slice(): ByteBuffer {
        // Creates a new buffer whose content is a shared subsequence of this buffer's content.
        // The new buffer's capacity will be this buffer's remaining(), and its position will be zero.
        val sliceCapacity = remaining()
        val slicedArrayView = this.array // Still shares the same underlying array
        // The slice starts at the current position of this buffer.
        // We need a constructor that allows specifying the initial backing array offset for the slice,
        // or adjust how copyInto/access is done.
        // For simplicity, if NativeArrayByteBuffer always "owns" its array segment from 0,
        // a true slice sharing memory without copying would be more complex.
        // A common approach for array-backed slices is to create a new buffer that refers to a sub-segment.
        // However, our current NativeArrayByteBuffer doesn't have an internal offset for the array.
        // So, a simple slice here would copy, or we need to enhance NativeArrayByteBuffer.
        // For now, let's do a copying slice for simplicity, though not ideal for performance.
        // A true slice would be: return NativeArrayByteBuffer(array, remaining(), 0, remaining(), current_pos_as_offset_in_array)
        // This current implementation makes a copy for slice:
        val newArray = ByteArray(remaining())
        array.copyInto(newArray, 0, pos, lim)
        return NativeArrayByteBuffer(newArray, newArray.size, 0, newArray.size)
    }

    actual override fun rewind() {
        pos = 0
        // The mark is not explicitly handled in this simplified version, but rewind usually discards it.
    }
}
