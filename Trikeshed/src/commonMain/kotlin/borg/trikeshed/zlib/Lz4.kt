package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed

/**
 * Expected API for a Lz4 implementation in Kotlin Multiplatform, focusing on frame-based operations.
 */
expect object Lz4 {

    /**
     * Compresses a single block of data into an LZ4 frame.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed LZ4 frame as an Indexed<Byte>.
     */
    fun compressFrame(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Decompresses a single LZ4 frame.
     * @param input The LZ4 frame data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun decompressFrame(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Gets the uncompressed size of an LZ4 frame.
     * @param input The LZ4 frame data as an Indexed<Byte>.
     * @return The uncompressed size.
     */
    fun getFrameUncompressedSize(input: Indexed<Byte>): Long

    /**
     * Parses the LZ4 frame header to extract its compressed and uncompressed sizes.
     * This is a simplified parser and may not handle all LZ4 frame variations.
     * Returns a Pair of (compressedSize, uncompressedSize).
     */
    fun parseFrameHeader(input: Indexed<Byte>): Pair<Long, Long>
}
