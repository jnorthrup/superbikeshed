package borg.trikeshed.nio.slabs

import borg.trikeshed.io.ByteBuffer // This is borg.trikeshed.io.ByteBuffer

/**
 * JVM-specific actual implementation of the [MemorySlab] interface.
 * This implementation wraps a [borg.trikeshed.nio.ByteBuffer] instance, which, on the JVM,
 * is typically a wrapper around `java.nio.ByteBuffer`.
 * The underlying buffer is expected to be a direct ByteBuffer, as managed by [ActualMemorySlabManagerService].
 */
actual class ActualMemorySlab actual constructor(
    internal val directBuffer: ByteBuffer, // Instance of JvmByteBuffer
    actual override val capacity: Int
) : MemorySlab {

    actual override val position: Int
        get() = directBuffer.position()

    actual override val remaining: Int
        get() = directBuffer.remaining()

    private fun checkCapacity(needed: Int) {
        if (remaining < needed) {
            // Consider using a more specific exception type if defined, e.g., SlabOverflowException
            throw IllegalArgumentException("Slab overflow. Remaining: $remaining, Needed: $needed, Capacity: $capacity")
        }
    }

    actual override fun putByte(value: Byte) {
        checkCapacity(1)
        directBuffer.put(value)
    }

    actual override fun putShort(value: Short) {
        checkCapacity(2)
        directBuffer.putShort(value)
    }

    actual override fun putInt(value: Int) {
        checkCapacity(4)
        directBuffer.putInt(value)
    }

    actual override fun putLong(value: Long) {
        checkCapacity(8)
        directBuffer.putLong(value)
    }

    actual override fun putFloat(value: Float) {
        checkCapacity(4)
        directBuffer.putFloat(value)
    }

    actual override fun putDouble(value: Double) {
        checkCapacity(8)
        directBuffer.putDouble(value)
    }

    actual override fun putBytes(source: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= source.size) { "Invalid offset/length for source ByteArray" }
        checkCapacity(length)
        directBuffer.put(source, offset, length)
    }

    actual override fun putBytes(source: ByteBuffer, length: Int) {
        require(length >= 0) { "Length must be non-negative" }
        require(source.remaining() >= length) { "Source buffer has less data (${source.remaining()}) than requested length ($length)" }
        checkCapacity(length)

        val originalLimit = source.limit()
        source.limit(source.position() + length) // Set limit on source to read exactly 'length' bytes
        directBuffer.put(source)
        source.limit(originalLimit) // Restore original limit on source buffer
    }

    actual override fun getRawByteBuffer(): ByteBuffer {
        val currentPosition = directBuffer.position()
        val readOnlyBufferView = directBuffer.duplicate()

        readOnlyBufferView.position(0)
        readOnlyBufferView.limit(currentPosition)

        return readOnlyBufferView
    }

    actual override fun clear() {
        directBuffer.clear()
    }

    actual override fun isFull(): Boolean {
        return remaining == 0
    }
}
