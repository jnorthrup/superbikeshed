package com.rtsgame.storage

import net.jpountz.lz4.LZ4Factory
import com.github.luben.zstd.Zstd
import java.nio.ByteBuffer

/**
 * Utility class for compression operations
 */
object CompressionUtils {
    private val lz4Factory = LZ4Factory.fastestInstance()
    private val lz4Compressor = lz4Factory.fastCompressor()
    private val lz4Decompressor = lz4Factory.fastDecompressor()

    /**
     * Compress data using the specified algorithm
     */
    fun compress(data: ByteArray, algorithm: CompressionAlgorithm): ByteArray {
        return when (algorithm) {
            CompressionAlgorithm.NONE -> data
            CompressionAlgorithm.LZ4 -> compressLZ4(data)
            CompressionAlgorithm.ZSTD -> compressZSTD(data)
            CompressionAlgorithm.ZRAN -> compressZRAN(data)
        }
    }

    /**
     * Decompress data using the specified algorithm
     */
    fun decompress(data: ByteArray, algorithm: CompressionAlgorithm): ByteArray {
        return when (algorithm) {
            CompressionAlgorithm.NONE -> data
            CompressionAlgorithm.LZ4 -> decompressLZ4(data)
            CompressionAlgorithm.ZSTD -> decompressZSTD(data)
            CompressionAlgorithm.ZRAN -> decompressZRAN(data)
        }
    }

    /**
     * LZ4 compression
     */
    private fun compressLZ4(data: ByteArray): ByteArray {
        val maxCompressedLength = lz4Compressor.maxCompressedLength(data.size)
        val compressed = ByteArray(maxCompressedLength)
        val compressedLength = lz4Compressor.compress(data, 0, data.size, compressed, 0, maxCompressedLength)
        return compressed.copyOf(compressedLength)
    }

    /**
     * LZ4 decompression
     */
    private fun decompressLZ4(data: ByteArray): ByteArray {
        val decompressedLength = data.size * 2 // Initial guess
        val decompressed = ByteArray(decompressedLength)
        val actualLength = lz4Decompressor.decompress(data, 0, data.size, decompressed, 0, decompressedLength)
        return decompressed.copyOf(actualLength)
    }

    /**
     * ZSTD compression
     */
    private fun compressZSTD(data: ByteArray): ByteArray {
        return Zstd.compress(data)
    }

    /**
     * ZSTD decompression
     */
    private fun decompressZSTD(data: ByteArray): ByteArray {
        val decompressedLength = Zstd.decompressedSize(data).toInt()
        val decompressed = ByteArray(decompressedLength)
        Zstd.decompress(decompressed, data)
        return decompressed
    }

    /**
     * ZRAN compression (random access)
     */
    private fun compressZRAN(data: ByteArray): ByteArray {
        // TODO: Implement ZRAN compression with index
        return compressZSTD(data) // Fallback to ZSTD for now
    }

    /**
     * ZRAN decompression (random access)
     */
    private fun decompressZRAN(data: ByteArray): ByteArray {
        // TODO: Implement ZRAN decompression with index
        return decompressZSTD(data) // Fallback to ZSTD for now
    }
} 