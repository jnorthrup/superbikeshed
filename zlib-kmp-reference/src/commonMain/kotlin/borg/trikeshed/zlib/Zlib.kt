package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed
import borg.trikeshed.zlib.internal.BitStream
import borg.trikeshed.zlib.internal.DeflateDecoder
import borg.trikeshed.zlib.internal.ZlibException

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
     * This function expects a raw DEFLATE stream (without ZLIB or GZIP headers/footers).
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun decompress(input: Indexed<Byte>): Indexed<Byte> {
        val bitStream = BitStream(input)
        val decoder = DeflateDecoder(bitStream)
        val (decompressedData, _) = decoder.decode() // We ignore the Adler32 checksum for raw DEFLATE decompression
        return decompressedData
    }

    /**
     * Decompresses data from ZLIB format.
     * This function expects a ZLIB stream (with ZLIB header and Adler32 checksum).
     * @param input The ZLIB compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    fun zlibDecompress(input: Indexed<Byte>): Indexed<Byte> {
        val bitStream = BitStream(input)

        // Read ZLIB header (CMF and FLG)
        val cmf = bitStream.readByte()
        val flg = bitStream.readByte()

        // CMF: Compression Method and CINFO
        val cm = cmf and 0x0F // Compression Method (8 for deflate)
        val cinfo = (cmf shr 4) and 0x0F // Compression Info (log2(window size) - 8)

        // FLG: FCHECK, FDICT, FLEVEL
        val fcheck = flg and 0x1F // FCHECK (checksum for CMF and FLG)
        val fdict = (flg shr 5) and 0x01 // FDICT (preset dictionary)
        val flevel = (flg shr 6) and 0x03 // FLEVEL (compression level)

        // Basic header validation
        if (cm != 8) {
            throw ZlibException("Unsupported compression method: $cm. Only DEFLATE (8) is supported.")
        }
        if (cinfo > 7) { // Window size 2^(cinfo+8) should not exceed 32KB (cinfo=7)
            throw ZlibException("Invalid window size information: $cinfo.")
        }
        if (((cmf shl 8) + flg) % 31 != 0) {
            throw ZlibException("ZLIB header checksum (FCHECK) failed.")
        }
        if (fdict != 0) {
            throw ZlibException("Preset dictionary (FDICT) is not supported.")
        }

        val decoder = DeflateDecoder(bitStream)
        val (decompressedData, calculatedAdler32) = decoder.decode()

        // Read Adler32 checksum from stream
        val storedAdler32 = bitStream.readBits(32).toUInt()

        // Verify checksum
        if (calculatedAdler32 != storedAdler32) {
            throw ZlibException("Adler32 checksum mismatch. Expected $storedAdler32, got $calculatedAdler32.")
        }

        return decompressedData
    }

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
