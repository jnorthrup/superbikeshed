package borg.trikeshed.nio.slabs

import borg.trikeshed.io.ByteBuffer // This is borg.trikeshed.io.ByteBuffer

/**
 * Kotlin/Native-specific actual implementation of the [MemorySlab] interface.
 * This implementation wraps a [borg.trikeshed.nio.ByteBuffer] instance, which, for the native target,
 * is currently [borg.trikeshed.nio.NativeArrayByteBuffer]. This means slabs are backed by `ByteArray`
 * instances on the Kotlin/Native heap and are managed by its garbage collector.
 */
actual class ActualMemorySlab actual constructor(
    internal val nativeBuffer: ByteBuffer, // Instance of NativeArrayByteBuffer
    actual override val capacity: Int
) : MemorySlab {

    actual override val position: Int
        get() = nativeBuffer.position()

    actual override val remaining: Int
        get() = nativeBuffer.remaining()

    private fun checkCapacity(needed: Int) {
        if (remaining < needed) {
            throw IllegalArgumentException("Slab overflow. Remaining: $remaining, Needed: $needed, Capacity: $capacity")
        }
    }

    actual override fun putByte(value: Byte) {
        checkCapacity(1)
        nativeBuffer.put(value)
    }

    actual override fun putShort(value: Short) {
        checkCapacity(2)
        nativeBuffer.putShort(value)
    }

    actual override fun putInt(value: Int) {
        checkCapacity(4)
        nativeBuffer.putInt(value)
    }

    actual override fun putLong(value: Long) {
        checkCapacity(8)
        nativeBuffer.putLong(value)
    }

    actual override fun putFloat(value: Float) {
        checkCapacity(4)
        nativeBuffer.putFloat(value)
    }

    actual override fun putDouble(value: Double) {
        checkCapacity(8)
        nativeBuffer.putDouble(value)
    }

    actual override fun putBytes(source: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= source.size) { "Invalid offset/length for source ByteArray" }
        checkCapacity(length)
        nativeBuffer.put(source, offset, length)
    }

    actual override fun putBytes(source: ByteBuffer, length: Int) {
        require(length >= 0) { "Length must be non-negative" }
        require(source.remaining() >= length) { "Source buffer has less data (${source.remaining()}) than requested length ($length)" }
        checkCapacity(length)

        val originalLimit = source.limit()
        source.limit(source.position() + length)
        nativeBuffer.put(source)
        source.limit(originalLimit)
    }

    actual override fun getRawByteBuffer(): ByteBuffer {
        val currentPosition = nativeBuffer.position()
        val readOnlyBufferView = nativeBuffer.duplicate()

        readOnlyBufferView.position(0)
        readOnlyBufferView.limit(currentPosition)

        return readOnlyBufferView
    }

    actual override fun clear() {
        nativeBuffer.clear()
    }

    actual override fun isFull(): Boolean {
        return remaining == 0
    }
}
