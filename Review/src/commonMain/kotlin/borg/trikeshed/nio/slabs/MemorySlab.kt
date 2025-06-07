package borg.trikeshed.nio.slabs

import borg.trikeshed.nio.ByteBuffer

/**
 * Represents an append-only, fixed-capacity block of memory, typically backed by a direct ByteBuffer.
 * MemorySlabs are used for efficiently writing sequential data. Once full, a new slab is typically acquired.
 * The content of a slab is generally considered raw bytes until interpreted by a reader.
 */
interface MemorySlab {
    /**
     * The total fixed capacity of this memory slab in bytes.
     */
    val capacity: Int

    /**
     * The current writing position within the slab. Data is appended at this position.
     * This value is between 0 and [capacity].
     */
    val position: Int

    /**
     * The number of bytes remaining that can be written to this slab before it is full.
     * Equal to `capacity - position`.
     */
    val remaining: Int

    /**
     * Appends a single byte to the slab.
     * Advances the [position] by 1.
     * @param value The Byte value to write.
     * @throws IllegalArgumentException if the slab is full or does not have enough remaining capacity.
     */
    fun putByte(value: Byte)

    /**
     * Appends a Short (2 bytes) to the slab.
     * Advances the [position] by 2.
     * @param value The Short value to write.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity.
     */
    fun putShort(value: Short)

    /**
     * Appends an Int (4 bytes) to the slab.
     * Advances the [position] by 4.
     * @param value The Int value to write.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity.
     */
    fun putInt(value: Int)

    /**
     * Appends a Long (8 bytes) to the slab.
     * Advances the [position] by 8.
     * @param value The Long value to write.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity.
     */
    fun putLong(value: Long)

    /**
     * Appends a Float (4 bytes) to the slab.
     * Advances the [position] by 4.
     * @param value The Float value to write.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity.
     */
    fun putFloat(value: Float)

    /**
     * Appends a Double (8 bytes) to the slab.
     * Advances the [position] by 8.
     * @param value The Double value to write.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity.
     */
    fun putDouble(value: Double)

    /**
     * Appends a sequence of bytes from the [source] ByteArray to the slab.
     * Advances the [position] by [length].
     * @param source The ByteArray to read from.
     * @param offset The starting offset in the [source] array. Defaults to 0.
     * @param length The number of bytes to write from the [source] array. Defaults to `source.size - offset`.
     * @throws IllegalArgumentException if the slab does not have enough remaining capacity for [length] bytes.
     * @throws IndexOutOfBoundsException if [offset] or [length] are invalid for the [source] array.
     */
    fun putBytes(source: ByteArray, offset: Int = 0, length: Int = source.size - offset)

    /**
     * Appends [length] bytes from the given [source] ByteBuffer to this slab.
     * Reads [length] bytes from the [source] ByteBuffer, starting at its current position, and writes them into this slab.
     * Advances this slab's [position] by [length]. The [source] ByteBuffer's position is also advanced by [length].
     * @param source The source ByteBuffer to read from.
     * @param length The number of bytes to read from the [source] ByteBuffer. Defaults to `source.remaining()`.
     * @throws IllegalArgumentException if this slab does not have enough remaining capacity for [length] bytes,
     *         or if the [source] buffer does not have [length] bytes remaining.
     */
    fun putBytes(source: ByteBuffer, length: Int = source.remaining())

    /**
     * Returns a new ByteBuffer that provides a read-only view of this slab's current content.
     * The returned buffer's position will be 0, and its limit will be set to this slab's current [position].
     * The content of the returned buffer reflects the data written to the slab up to its current position.
     * Operations on the returned ByteBuffer (like reading) do not affect the state (e.g., position) of this MemorySlab.
     * The underlying memory is shared; modifications to the slab's backing store (if possible outside this interface)
     * could be visible, though MemorySlab is designed as append-only.
     * @return A new ByteBuffer instance for reading the slab's content.
     */
    fun getRawByteBuffer(): ByteBuffer

    /**
     * Resets the slab's [position] to 0, making the entire slab available for writing again.
     * The actual content of the slab is not zeroed out but is considered overwriteable.
     * This is useful for reusing a slab, for example, when it's part of a pool.
     */
    fun clear()

    /**
     * Checks if the slab is full, meaning no more bytes can be written to it.
     * @return True if `remaining == 0`, false otherwise.
     */
    fun isFull(): Boolean
}
