package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed

/**
 * Expected API for a Zstd implementation in Kotlin Multiplatform.
 */
expect object Zstd {

    /**
     * Compresses the input data using the Zstd algorithm.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed data as an Indexed<Byte>.
     */
    fun compress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Decompresses the input data using the Zstd algorithm.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun decompress(input: Indexed<Byte>): Indexed<Byte>

    /**
     * Gets the decompressed size of a Zstd frame.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed size.
     */
    fun getDecompressedSize(input: Indexed<Byte>): Long

    /**
     * Finds the compressed size of a Zstd frame.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The compressed size.
     */
    fun findFrameCompressedSize(input: Indexed<Byte>): Long
}
