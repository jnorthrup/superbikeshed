package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Indexed

import borg.trikeshed.lib.size

/**
 * A high-performance bit-level input stream designed to work with Trikeshed's Indexed<Byte> (Indexed<Byte>).
 * It provides efficient methods for reading bits and bytes, crucial for compression/decompression algorithms
 * like DEFLATE (zlib), where register packing and minimal overhead are paramount.
 *
 * @param data The Indexed<Byte> containing the raw byte data to read from.
 */
class BitStream(internal val data: Indexed<Byte>) {
    internal var bytePosition: Int = 0
    internal var bitOffset: Int = 0 // 0-7, current bit within the current byte

    /**
     * Reads a specified number of bits from the stream, advancing the stream's position.
     * Bits are read from the current byte, then subsequent bytes as needed.
     *
     * @param numBits The number of bits to read (1 to 32).
     * @return The integer value represented by the read bits.
     * @throws IllegalArgumentException if numBits is out of range.
     * @throws IllegalStateException if attempting to read beyond the end of the stream.
     */
    fun readBits(numBits: Int): Int {
        val result = peekBits(numBits)
        skipBits(numBits)
        return result
    }

    /**
     * Peeks at a specified number of bits from the stream without advancing the stream's position.
     *
     * @param numBits The number of bits to peek (1 to 32).
     * @return The integer value represented by the peeked bits.
     * @throws IllegalArgumentException if numBits is out of range.
     * @throws IllegalStateException if attempting to peek beyond the end of the stream.
     */
    fun peekBits(numBits: Int): Int {
        if (numBits < 1 || numBits > 32) {
            throw IllegalArgumentException("numBits must be between 1 and 32.")
        }
        if (bytePosition >= data.size && bitOffset > 0) {
            throw IllegalStateException("Attempt to peek beyond end of stream.")
        }

        var result = 0
        var bitsPeeked = 0
        var currentBytePos = bytePosition
        var currentBitOff = bitOffset

        while (bitsPeeked < numBits) {
            if (currentBytePos >= data.size) {
                throw IllegalStateException("Unexpected end of stream while peeking bits.")
            }

            val currentByte = data.component2()(currentBytePos).toInt() and 0xFF // Ensure unsigned byte
            val bitsRemainingInByte = 8 - currentBitOff
            val bitsToPeekThisPass = minOf(numBits - bitsPeeked, bitsRemainingInByte)

            // Extract the relevant bits from the current byte
            val extractedBits = (currentByte shr currentBitOff) and ((1 shl bitsToPeekThisPass) - 1)

            // Add to result, shifting existing bits to make space
            result = result or (extractedBits shl bitsPeeked)
            bitsPeeked += bitsToPeekThisPass
            currentBitOff += bitsToPeekThisPass

            // Move to the next byte if current byte is exhausted
            if (currentBitOff == 8) {
                currentBytePos++
                currentBitOff = 0
            }
        }
        return result
    }

    /**
     * Advances the stream's position by a specified number of bits.
     *
     * @param numBits The number of bits to skip.
     * @throws IllegalArgumentException if numBits is negative.
     * @throws IllegalStateException if attempting to skip beyond the end of the stream.
     */
    fun skipBits(numBits: Int) {
        if (numBits < 0) {
            throw IllegalArgumentException("numBits cannot be negative.")
        }

        var totalBits = (bytePosition * 8) + bitOffset + numBits
        bytePosition = totalBits / 8
        bitOffset = totalBits % 8

        if (bytePosition > data.size || (bytePosition == data.size && bitOffset > 0)) {
            throw IllegalStateException("Attempt to skip beyond end of stream.")
        }
    }

    /**
     * Reads a single byte from the stream, aligning to the next byte boundary if necessary.
     * Advances the stream by 8 bits.
     *
     * @return The byte value as an Int (0-255).
     * @throws IllegalStateException if attempting to read beyond the end of the stream.
     */
    fun readByte(): Int {
        // Align to next byte boundary if not already there
        if (bitOffset != 0) {
            bytePosition++
            bitOffset = 0
        }

        if (bytePosition >= data.size) {
            throw IllegalStateException("Attempt to read byte beyond end of stream.")
        }

        return data.component2()(bytePosition++).toInt() and 0xFF
    }

    /**
     * Returns the current byte position in the underlying data array.
     */
    fun getBytePosition(): Int = bytePosition

    /**
     * Returns the current bit offset within the current byte (0-7).
     */
    fun getBitOffset(): Int = bitOffset

    /**
     * Checks if there are more bits available to read in the stream.
     */
    fun hasRemaining(): Boolean {
        return bytePosition < data.size - 1 || (bytePosition == data.size - 1 && bitOffset < 8)
    }

    /**
     * Returns the total number of bytes in the underlying data.
     */
    fun size(): Int = data.size

    /**
     * Resets the stream's read position to the beginning.
     */
    fun reset() {
        bytePosition = 0
        bitOffset = 0
    }
}

// Helper function for minOf, as it might not be directly available in commonMain without specific imports
// This is a temporary workaround if minOf is not resolved by the KMP setup.
internal fun minOf(a: Int, b: Int): Int = if (a < b) a else b
