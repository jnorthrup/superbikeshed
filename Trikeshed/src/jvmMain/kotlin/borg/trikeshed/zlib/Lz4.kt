package borg.trikeshed.zlib

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.*

/**
 * JVM implementation of the Lz4 object, using exec for compression/decompression and in-code for framing.
 * Bottles channels of byteranges into exec stdio.
 */
actual object Lz4 {

    /**
     * Compresses a single block of data into an LZ4 frame using the system lz4 tool.
     * Bottles byterange channel through exec stdio.
     */
    actual fun compressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("lz4", "-c", "-f", "--frame").start()
        
        // Bottle byterange channel to exec stdio
        process.outputStream.write(inputArray)
        process.outputStream.close()
        
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Decompresses a single LZ4 frame using the system lz4 tool.
     * Bottles byterange channel through exec stdio.
     */
    actual fun decompressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("lz4", "-d", "-c", "-f").start()
        
        // Bottle byterange channel to exec stdio
        process.outputStream.write(inputArray)
        process.outputStream.close()
        
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Gets the uncompressed size of an LZ4 frame by parsing the frame header.
     * Uses exec to get frame info without full decompression.
     * @param input The LZ4 frame data as an Indexed<Byte>.
     * @return The uncompressed size, or 0 if not present in the frame header.
     */
    actual fun getFrameUncompressedSize(input: Indexed<Byte>): Long {
        val (_, uncompressedSize) = parseFrameHeader(input)
        return uncompressedSize
    }

    /**
     * Parses LZ4 frame header to extract compressed and uncompressed sizes.
     * Bottles byterange parsing through direct byte access.
     */
    actual fun parseFrameHeader(input: Indexed<Byte>): Pair<Long, Long> {
        // Simplified LZ4 frame header parsing
        // Magic Number (4 bytes) - already checked by caller
        // Frame Descriptor (1 byte)
        // Content Size (8 bytes, optional)
        // Block Size (optional)
        // Block Checksum (optional)
        // Content Checksum (optional)

        var currentOffset = 4 // After magic number

        val frameDescriptor = input[currentOffset++].toInt() and 0xFF

        // FLG byte: Version (2 bits), Block Independence (1 bit), Block Checksum (1 bit), Content Size (1 bit), Content Checksum (1 bit), Dictionary (1 bit)
        val hasContentSize = (frameDescriptor shr 3 and 0x1) == 1
        val hasBlockChecksum = (frameDescriptor shr 2 and 0x1) == 1
        val hasContentChecksum = (frameDescriptor shr 1 and 0x1) == 1
        val hasDictionary = (frameDescriptor shr 0 and 0x1) == 1

        var uncompressedSize: Long = 0
        if (hasContentSize) {
            val contentSizeBytes = ByteArray(8)
            for (i in 0 until 8) {
                contentSizeBytes[i] = input[currentOffset++]
            }
            uncompressedSize = java.nio.ByteBuffer.wrap(contentSizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
        }

        // Skip Block Max Size (2 bits in FLG byte, but actual value is derived)
        // Skip Reserved (1 bit in FLG byte)

        // HC byte (Header Checksum) - 1 byte
        currentOffset++

        var compressedSize: Long = 0
        // Iterate through blocks to sum up compressed sizes
        // This is still a simplification. A full parser would need to handle block independence, etc.
        // For now, we'll assume blocks are sequential and read their sizes.
        while (true) {
            if (currentOffset + 4 > input.a) { // Need at least 4 bytes for block size
                break
            }
            val blockSize = getInt(input, currentOffset)
            if (blockSize == 0) { // End mark
                currentOffset += 4
                break
            }
            compressedSize += 4 + blockSize // 4 bytes for block size + actual block data
            currentOffset += 4 + blockSize

            if (hasBlockChecksum) {
                currentOffset += 4 // Skip block checksum
            }
        }

        return Pair(compressedSize, uncompressedSize)
    }

    /**
     * Reads a Variable Length Quantity (VLQ) from the input at the given offset.
     * Bottles byterange access through direct byte reading.
     */
    actual fun readVLQ(input: Indexed<Byte>, offset: Int): Pair<Long, Int> {
        var value = 0L
        var bytesRead = 0
        var currentOffset = offset
        while (true) {
            val byte = input[currentOffset].toInt() and 0xFF
            value = value or ((byte and 0x7F).toLong() shl (bytesRead * 7))
            bytesRead++
            currentOffset++
            if ((byte and 0x80) == 0) {
                break
            }
        }
        return Pair(value, bytesRead)
    }

    /**
     * Helper function to get Int from Indexed<Byte> at specific offset.
     * Bottles byterange access for integer reading.
     */
    private fun getInt(input: Indexed<Byte>, offset: Int): Int {
        return (input[offset].toInt() and 0xFF) or
               ((input[offset + 1].toInt() and 0xFF) shl 8) or
               ((input[offset + 2].toInt() and 0xFF) shl 16) or
               ((input[offset + 3].toInt() and 0xFF) shl 24)
    }
}
