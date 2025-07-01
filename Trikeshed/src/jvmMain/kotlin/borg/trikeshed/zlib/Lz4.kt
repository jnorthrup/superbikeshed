package borg.trikeshed.zlib

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * JVM implementation of the Lz4 object, using exec for compression/decompression and in-code for framing.
 */
actual object Lz4 {

    /**
     * Compresses a single block of data into an LZ4 frame using the system lz4 tool.
     */
    actual fun compressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("lz4", "-c", "-f", "--frame").start()
        process.outputStream.write(inputArray)
        process.outputStream.close()
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Decompresses a single LZ4 frame using the system lz4 tool.
     */
    actual fun decompressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("lz4", "-d", "-c", "-f").start()
        process.outputStream.write(inputArray)
        process.outputStream.close()
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Gets the uncompressed size of an LZ4 frame.
     * This requires reading the frame header.
     * Note: LZ4 frame format can optionally include content size. If not present, this will return 0.
     * @param input The LZ4 frame data as an Indexed<Byte>.
     * @return The uncompressed size, or 0 if not present in the frame header.
     */
    actual fun getFrameUncompressedSize(input: Indexed<Byte>): Long {
        val inputArray = input.toByteArray()
        val bais = ByteArrayInputStream(inputArray)
        val lz4Fis = LZ4FrameInputStream(bais)
        val uncompressedSize = lz4Fis.contentLength
        lz4Fis.close() // Close to release resources
        return uncompressedSize
    }

    actual fun parseFrameHeader(input: Indexed<Byte>): Pair<Long, Long> {
        val inputArray = input.toByteArray()
        val bais = ByteArrayInputStream(inputArray)

        // Read magic number (4 bytes)
        bais.readNBytes(4)

        // Read Frame Descriptor (1 byte)
        val frameDescriptor = bais.read()

        // Check for Content Size flag (bit 3 of FLG byte)
        val hasContentSize = (frameDescriptor.toByte().toInt() shr 3 and 0x1) == 1

        var uncompressedSize: Long = 0
        if (hasContentSize) {
            val contentSizeBytes = bais.readNBytes(8)
            uncompressedSize = java.nio.ByteBuffer.wrap(contentSizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
        }

        // For compressed size, we cannot determine it from the header alone without reading blocks.
        // We'll return 0 for now, and buildIndex will have to read the entire frame to get the actual compressed size.
        return Pair(0L, uncompressedSize)
    }

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
            val blockSize = input.getInt(currentOffset)
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
}
