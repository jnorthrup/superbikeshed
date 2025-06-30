package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray
import borg.trikeshed.lib.toSeries
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Actual JVM implementation of the Lz4 object, using LZ4 frame format.
 */
actual object Lz4 {

    private val factory = LZ4Factory.fastestInstance()

    /**
     * Compresses a single block of data into an LZ4 frame.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed LZ4 frame as an Indexed<Byte>.
     */
    actual fun compressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val baos = ByteArrayOutputStream()
        LZ4FrameOutputStream(baos).use { lz4Fos ->
            lz4Fos.write(inputArray)
        }
        return baos.toByteArray().toSeries()
    }

    /**
     * Decompresses a single LZ4 frame.
     * @param input The LZ4 frame data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    actual fun decompressFrame(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val bais = ByteArrayInputStream(inputArray)
        val baos = ByteArrayOutputStream()
        LZ4FrameInputStream(bais).use { lz4Fis ->
            lz4Fis.copyTo(baos)
        }
        return baos.toByteArray().toSeries()
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
}
