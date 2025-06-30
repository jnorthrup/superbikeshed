package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed

/**
 * Expected API for a Zlib (DEFLATE, GZIP, ZRAN) implementation in Kotlin Multiplatform.
 * This interface defines the core functionalities for compression, decompression, and random access.
 */
expect object Zlib {

    /**
     * Compresses the input data using the DEFLATE algorithm.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed data as an Indexed<Byte>.
     */
    fun compress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Decompresses the input data using the DEFLATE algorithm.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun decompress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Compresses the input data into GZIP format.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed data in GZIP format as an Indexed<Byte>.
     */
    fun gzipCompress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Decompresses data from GZIP format.
     * @param input The GZIP compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun gzipDecompress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Creates a ZRAN index for a GZIP compressed stream, allowing for random access.
     * This is a complex operation that involves scanning the compressed stream.
     * @param gzipStream The GZIP compressed data as an Indexed<Byte>.
     * @return An object representing the ZRAN index.
     */
    fun createZranIndex(gzipStream: Indexed<Byte>): ZranIndex

    /**
     * Represents a ZRAN index, allowing random access into a GZIP stream.
     * The actual implementation details will be platform-specific or internal.
     */
    interface ZranIndex {
        /**
         * Decompresses a specific portion of the original data using the ZRAN index.
         * @param offset The offset in the original (uncompressed) data to start reading from.
         * @param length The number of bytes to decompress from the original data.
         * @return The decompressed data segment as an Indexed<Byte>.
         */
        fun decompressSegment(offset: Long, length: Int): Indexed<Byte>
    }
}
