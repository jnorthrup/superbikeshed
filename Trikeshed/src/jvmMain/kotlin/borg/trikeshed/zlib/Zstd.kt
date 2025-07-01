package borg.trikeshed.zlib

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray

/**
 * JVM implementation of the Zstd object, using exec for compression/decompression and in-code for framing.
 */
actual object Zstd {
    /**
     * Compresses the input data using the system zstd tool.
     */
    actual fun compress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("zstd", "-c").start()
        process.outputStream.write(inputArray)
        process.outputStream.close()
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
    }

    /**
     * Decompresses the input data using the system zstd tool.
     */
    actual fun decompress(input: Indexed<Byte>): Indexed<Byte> {
        val inputArray = input.toByteArray()
        val process = ProcessBuilder("zstd", "-d", "-c").start()
        process.outputStream.write(inputArray)
        process.outputStream.close()
        val output = process.inputStream.readBytes()
        process.waitFor()
        return output.toIndexed()
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
