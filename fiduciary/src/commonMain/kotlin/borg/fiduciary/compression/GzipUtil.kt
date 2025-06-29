package borg.fiduciary.compression

import java.io.InputStream
import java.io.OutputStream

/**
 * Utility object for Gzip compression and decompression.
 *
 * This object provides methods to compress and decompress data using the Gzip format.
 * Implementations are expected to handle large data streams efficiently, typically by
 * using streaming techniques and appropriate internal buffering.
 *
 * Example Usage (conceptual):
 * ```kotlin
 * val originalData: InputStream = // ... get your input stream
 * val compressedOutput: OutputStream = // ... get your output stream for compressed data
 * GzipUtil.compress(originalData, compressedOutput)
 *
 * val compressedData: InputStream = // ... get your input stream of compressed data
 * val decompressedOutput: OutputStream = // ... get your output stream for decompressed data
 * GzipUtil.decompress(compressedData, decompressedOutput)
 * ```
 */
expect object GzipUtil {
    /**
     * Compresses data from the source [InputStream] and writes it to the destination [OutputStream] using Gzip.
     *
     * This method reads data from the [source], compresses it using the Gzip algorithm,
     * and writes the compressed bytes to the [destination]. Both streams are processed
     * sequentially and should be closed by the caller.
     *
     * @param source The [InputStream] to read uncompressed data from.
     * @param destination The [OutputStream] to write compressed Gzip data to.
     * @throws java.io.IOException if an I/O error occurs during reading or writing.
     * @throws SecurityException if a security manager exists and its `checkRead` or `checkWrite` method denies access.
     */
    fun compress(source: InputStream, destination: OutputStream)

    /**
     * Decompresses Gzip data from the source [InputStream] and writes it to the destination [OutputStream].
     *
     * This method reads Gzip-compressed data from the [source], decompresses it,
     * and writes the original uncompressed bytes to the [destination]. Both streams are processed
     * sequentially and should be closed by the caller.
     *
     * @param source The [InputStream] to read compressed Gzip data from.
     * @param destination The [OutputStream] to write decompressed data to.
     * @throws java.io.IOException if an I/O error occurs, such as the input not being in Gzip format.
     * @throws SecurityException if a security manager exists and its `checkRead` or `checkWrite` method denies access.
     */
    fun decompress(source: InputStream, destination: OutputStream)
}
