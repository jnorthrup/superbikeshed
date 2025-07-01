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
 * JVM implementation of the Zstd object, using exec for compression/decompression and in-code for framing.
 * Bottles channels of byteranges into exec stdio.
 */
actual object Zstd {
    /**
     * Compresses the input data using the system zstd tool.
     * Bottles byterange channel through exec stdio.
     */
    actual fun compress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("zstd", "-c").start()
        
        // Bottle byterange channel to exec stdio
        process.outputStream.write(inputArray)
        process.outputStream.close()
        
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Decompresses the input data using the system zstd tool.
     * Bottles byterange channel through exec stdio.
     */
    actual fun decompress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("zstd", "-d", "-c").start()
        
        // Bottle byterange channel to exec stdio
        process.outputStream.write(inputArray)
        process.outputStream.close()
        
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Gets the decompressed size of a Zstd frame by parsing the frame header.
     * Uses exec to get frame info without full decompression.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed size.
     */
    actual fun getDecompressedSize(input: Indexed<Byte>): Long {
        // Parse Zstd frame header to get decompressed size
        // Zstd frame header format: Frame Header (2-14 bytes)
        if (input.a < 2) return 0L
        
        val frameHeader = input[0].toInt() and 0xFF
        val frameHeaderDescriptor = input[1].toInt() and 0xFF
        
        // Check if it's a Zstd frame
        if (frameHeader != 0x28 && frameHeader != 0x2E) return 0L
        
        // Check if content size is present (bit 3 of frame header descriptor)
        val hasContentSize = (frameHeaderDescriptor shr 3 and 0x1) == 1
        
        if (!hasContentSize) return 0L
        
        // Content size is stored as variable length quantity
        var offset = 2
        var contentSize = 0L
        
        // Read content size (1-8 bytes)
        var shift = 0
        while (offset < input.a && shift < 56) {
            val byte = input[offset].toInt() and 0xFF
            contentSize = contentSize or ((byte and 0x7F).toLong() shl shift)
            offset++
            shift += 7
            if ((byte and 0x80) == 0) break
        }
        
        return contentSize
    }

    /**
     * Finds the compressed size of a Zstd frame by parsing the frame header.
     * Uses exec to get frame info without full decompression.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The compressed size.
     */
    actual fun findFrameCompressedSize(input: Indexed<Byte>): Long {
        // For Zstd, we need to parse the frame to find the end
        // This is a simplified implementation that looks for frame boundaries
        if (input.a < 2) return 0L
        
        val frameHeader = input[0].toInt() and 0xFF
        val frameHeaderDescriptor = input[1].toInt() and 0xFF
        
        // Check if it's a Zstd frame
        if (frameHeader != 0x28 && frameHeader != 0x2E) return 0L
        
        var offset = 2
        
        // Skip content size if present
        val hasContentSize = (frameHeaderDescriptor shr 3 and 0x1) == 1
        if (hasContentSize) {
            while (offset < input.a) {
                val byte = input[offset].toInt() and 0xFF
                offset++
                if ((byte and 0x80) == 0) break
            }
        }
        
        // Skip dictionary ID if present
        val hasDictionary = (frameHeaderDescriptor shr 0 and 0x1) == 1
        if (hasDictionary) {
            offset += 4 // Dictionary ID is 4 bytes
        }
        
        // Skip FCS (Frame Content Size) if present
        val hasFCS = (frameHeaderDescriptor shr 2 and 0x1) == 1
        if (hasFCS) {
            offset += 4 // FCS is 4 bytes
        }
        
        // For now, return the remaining size as compressed size
        // A full implementation would need to parse block headers
        return (input.a - offset).toLong()
    }
}