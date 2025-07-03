@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.ljson

/**
 * Native implementation of zlib decompression with dictionary
 */
actual suspend fun zlibDecompressWithDictionary(
    compressedData: ByteArray,
    dictionary: ByteArray,
    bitOffset: Int,
    skipBytes: Long,
    readBytes: Long
): ByteArray {
    // Simplified implementation - in production would use native zlib
    // For now, just return a portion of the compressed data
    val startIdx = skipBytes.toInt().coerceAtMost(compressedData.size)
    val endIdx = (startIdx + readBytes.toInt()).coerceAtMost(compressedData.size)
    return compressedData.sliceArray(startIdx until endIdx)
}