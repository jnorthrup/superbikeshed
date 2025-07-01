package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray
import com.github.luben.zstd.Zstd as ZstdJni

/**
 * Actual JVM implementation of the Zstd object.
 */
actual object Zstd {

    /**
     * Compresses the input data using the Zstd algorithm.
     * @param input The data to compress as an Indexed<Byte>.
     * @return The compressed data as an Indexed<Byte>.
     */
    actual fun compress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val outputArray = ZstdJni.compress(inputArray)
        return outputArray.toIndexed()
    }

    /**
     * Decompresses the input data using the Zstd algorithm.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed data as an Indexed<Byte>.
     */
    actual fun decompress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val decompressedSize = ZstdJni.decompressedSize(inputArray).toInt()
        val outputArray = ZstdJni.decompress(inputArray, decompressedSize)
        return outputArray.toIndexed()
    }

    /**
     * Gets the decompressed size of a Zstd frame.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The decompressed size.
     */
    actual fun getDecompressedSize(input: Indexed<Byte>): Long {
        return ZstdJni.decompressedSize(input.toByteArray())
    }

    /**
     * Finds the compressed size of a Zstd frame.
     * @param input The compressed data as an Indexed<Byte>.
     * @return The compressed size.
     */
    actual fun findFrameCompressedSize(input: Indexed<Byte>): Long {
        return ZstdJni.findFrameCompressedSize(input.toByteArray())
    }
}
