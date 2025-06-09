package borg.trikeshed.net.quic.util

import kotlin.experimental.and
import kotlin.experimental.or

/**
 * A utility class for reading data from a byte array sequentially.
 * @param bytes The byte array to read from.
 */
class BufferReader(val bytes: ByteArray) {
    var offset: Int = 0
        private set // Allow reading offset, but only advance via read methods

    /**
     * Checks if there are more bytes to read.
     * @return True if `offset < bytes.size`, false otherwise.
     */
    fun hasRemaining(): Boolean = offset < bytes.size

    /**
     * Reads a single byte from the buffer and advances the offset.
     * @return The byte read.
     * @throws IndexOutOfBoundsException if no more bytes can be read.
     */
    fun readByte(): Byte {
        if (!hasRemaining()) throw IndexOutOfBoundsException("Buffer underflow")
        return bytes[offset++]
    }

    /**
     * Writes a portion of a byte array to the buffer.
     * @param bytes The source ByteArray.
     * @param offset The starting offset in the source array.
     * @param length The number of bytes to write from the source array.
     */
    fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset + length > bytes.size) {
            throw IndexOutOfBoundsException("Invalid offset/length for writing bytes")
        }
        for (i in 0 until length) {
            buffer.add(bytes[offset + i])
        }
    }

    /**
     * Peeks at the next byte without advancing the offset.
     * @return The next byte.
     * @throws IndexOutOfBoundsException if no more bytes can be read.
     */
    fun peekByte(): Byte {
        if (!hasRemaining()) throw IndexOutOfBoundsException("Buffer underflow")
        return bytes[offset]
    }

    /**
     * Reads a specified number of bytes from the buffer and advances the offset.
     * @param count The number of bytes to read.
     * @return A ByteArray containing the bytes read.
     * @throws IndexOutOfBoundsException if not enough bytes are available.
     */
    fun readBytes(count: Int): ByteArray {
        if (offset + count > bytes.size) throw IndexOutOfBoundsException("Buffer underflow while reading $count bytes")
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    /**
     * Reads a QUIC variable-length integer (varint) from the buffer.
     * Advances the offset by the number of bytes read for the varint.
     * @return The decoded Long value.
     * @throws IllegalArgumentException if varint is malformed or not enough data.
     */
    fun readVarint(): Long {
        if (!hasRemaining()) throw IllegalArgumentException("Cannot decode varint, buffer empty")
        val firstByte = peekByte().toUByte()
        val lenIndicator = firstByte.toInt() shr 6 // Get the first two bits
        val length = 1 shl lenIndicator          // Determine length: 1, 2, 4, or 8 bytes

        if (offset + length > bytes.size) throw IllegalArgumentException("Not enough bytes to decode varint of length $length")

        var value = (readByte().toUByte() and 0x3Fu).toLong() // Read first byte, mask out length indicator
        for (i in 1 until length) {
            value = (value shl 8) or readByte().toUByte().toLong()
        }
        return value
    }
}

/**
 * A utility class for writing data sequentially into a byte buffer.
 */
class BufferWriter {
    private val buffer: MutableList<Byte> = mutableListOf()

    /**
     * Writes a single byte to the buffer.
     * @param byte The byte to write.
     */
    fun writeByte(byte: Byte) {
        buffer.add(byte)
    }

    /**
     * Writes multiple bytes to the buffer.
     * @param bytes The ByteArray to write.
     */
    fun writeBytes(bytes: ByteArray) {
        buffer.addAll(bytes.toList())
    }

    /**
     * Writes a Long value as a QUIC variable-length integer (varint) to the buffer.
     * @param value The Long value to encode and write.
     * @throws IllegalArgumentException if value is negative or too large for varint encoding.
     */
    fun writeVarint(value: Long) {
        if (value < 0) throw IllegalArgumentException("Varint value cannot be negative: $value")
        when {
            value < (1L shl 6) -> { // Fits in 1 byte (6 bits data)
                writeByte(value.toByte())
            }
            value < (1L shl 14) -> { // Fits in 2 bytes (14 bits data)
                writeByte((0x40L or (value shr 8)).toByte())
                writeByte(value.toByte())
            }
            value < (1L shl 30) -> { // Fits in 4 bytes (30 bits data)
                writeByte((0x80L or (value shr 24)).toByte())
                writeByte((value shr 16).toByte())
                writeByte((value shr 8).toByte())
                writeByte(value.toByte())
            }
            value < (1L shl 62) -> { // Fits in 8 bytes (62 bits data)
                writeByte((0xC0L or (value shr 56)).toByte())
                writeByte((value shr 48).toByte())
                writeByte((value shr 40).toByte())
                writeByte((value shr 32).toByte())
                writeByte((value shr 24).toByte())
                writeByte((value shr 16).toByte())
                writeByte((value shr 8).toByte())
                writeByte(value.toByte())
            }
            else -> throw IllegalArgumentException("Value too large for varint encoding: $value")
        }
    }

    /**
     * Gets the current size of the written data.
     * @return The number of bytes written to the buffer.
     */
    val size: Int
        get() = buffer.size

    /**
     * Converts the written data into a ByteArray.
     * @return A ByteArray containing all bytes written to the buffer.
     */
    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }

    /**
     * Clears all data from the writer's internal buffer.
     */
    fun clear() {
        buffer.clear()
    }

    /**
     * Clears the current buffer and replaces its content with the new data.
     * @param newData The ByteArray to replace the current buffer content with.
     */
    fun clearAndReplace(newData: ByteArray) {
        clear()
        writeBytes(newData)
    }
}
