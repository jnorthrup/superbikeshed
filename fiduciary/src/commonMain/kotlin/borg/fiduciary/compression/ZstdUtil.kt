package borg.fiduciary.compression

import java.io.InputStream // kotlin.io.InputStream
import java.io.OutputStream // kotlin.io.OutputStream

/**
 * Utility object for Zstandard (Zstd) compression and decompression.
 *
 * This object provides methods to compress and decompress data using the Zstd algorithm,
 * known for its excellent balance of speed and compression ratio.
 * Implementations are expected to handle large data streams efficiently via streaming.
 *
 * Example Usage (conceptual):
 * ```kotlin
 * val originalData: InputStream = // ... get your input stream
 * val compressedOutput: OutputStream = // ... get your output stream for compressed data
 * ZstdUtil.compress(originalData, compressedOutput, level = ZstdUtil.DEFAULT_COMPRESSION_LEVEL)
 *
 * val compressedData: InputStream = // ... get your input stream of compressed data
 * val decompressedOutput: OutputStream = // ... get your output stream for decompressed data
 * ZstdUtil.decompress(compressedData, decompressedOutput)
 * ```
 */
expect object ZstdUtil {
    /**
     * Default compression level for Zstd, offering a good balance between speed and compression ratio.
     * Typically, Zstd levels range from 1 (fastest) to 22 (highest compression), with
     * negative levels also available for even faster speeds. Level 3 is a common default.
     */
    val DEFAULT_COMPRESSION_LEVEL: Int // Platform actuals will define this, e.g., 3

    /**
     * Compresses data from the source [InputStream] and writes it to the destination [OutputStream] using Zstd.
     *
     * Data is read from the [source], compressed using Zstd at the specified [level],
     * and written to the [destination]. Both streams are processed sequentially.
     * The caller is responsible for closing the streams.
     *
     * @param source The [InputStream] to read uncompressed data from.
     * @param destination The [OutputStream] to write compressed Zstd data to.
     * @param level The Zstd compression level. Higher values mean better compression but are slower.
     *              Common default is 3. See [DEFAULT_COMPRESSION_LEVEL].
     * @throws java.io.IOException if an I/O error occurs during reading or writing.
     * @throws IllegalArgumentException if the compression level is invalid for the underlying Zstd library.
     */
    fun compress(source: InputStream, destination: OutputStream, level: Int = DEFAULT_COMPRESSION_LEVEL)

    /**
     * Decompresses Zstd data from the source [InputStream] and writes it to the destination [OutputStream].
     *
     * Reads Zstd-compressed data from the [source], decompresses it, and writes the original
     * uncompressed bytes to the [destination]. Both streams are processed sequentially.
     * The caller is responsible for closing the streams.
     *
     * @param source The [InputStream] to read compressed Zstd data from.
     * @param destination The [OutputStream] to write decompressed data to.
     * @throws java.io.IOException if an I/O error occurs, or if the input data is not valid Zstd format.
     */
    fun decompress(source: InputStream, destination: OutputStream)
}
