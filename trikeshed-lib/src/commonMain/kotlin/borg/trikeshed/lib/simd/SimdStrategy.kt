package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed

/**
 * Scan strategies for register-at-a-time scanning
 */
enum class ScanStrategy {
    SCALAR, SIMD, VECTOR, AUTOVEC
}

/**
 * SIMD Strategy: Take any register size we can get and maximize throughput.
 * 
 * Register sizes by platform:
 * - ARM NEON: 128-bit (16 bytes)
 * - SSE4.2: 128-bit (16 bytes) 
 * - AVX2: 256-bit (32 bytes)
 * - AVX-512: 512-bit (64 bytes)
 * - ARM SVE: 128-2048 bits (scalable!)
 * - RISC-V V: 128-65536 bits (ultra-scalable!)
 * - WASM SIMD: 128-bit (16 bytes)
 * 
 * Strategy: Write algorithms that naturally scale with register width.
 */
interface SimdStrategy {
    
    /**
     * Core SIMD operations we need for parsing/scanning
     */
    
    /**
     * Find all occurrences of a byte value in parallel.
     * This is THE fundamental operation for scanning.
     */
    fun findByte(data: Indexed<Byte>, target: Byte, offset: Int = 0): Indexed<Int>
    
    /**
     * Find any of multiple byte values (e.g., '{', '[', '"' for JSON).
     * Uses SIMD OR operations to combine comparisons.
     */
    fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int = 0): Indexed<Int>
    
    /**
     * Parallel string comparison - check multiple positions at once.
     * Critical for header field matching.
     */
    fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean>
    
    /**
     * Population count - count set bits in parallel.
     * Useful for counting structural characters.
     */
    fun popcount(bitmap: Indexed<Int>): Int
    
    /**
     * Parallel extraction - gather bytes from multiple positions.
     * Perfect for extracting field values after scanning.
     */
    fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte>
    
    /**
     * Get characteristics of this SIMD implementation
     */
    fun getCapabilities(): SimdCapabilities
}

/**
 * SIMD capabilities detection
 */
data class SimdCapabilities(
    val vectorBits: Int,        // 64, 128, 256, 512, etc.
    val hasPopcount: Boolean,    // POPCNT instruction
    val hasGather: Boolean,      // Gather/scatter operations
    val hasMaskOps: Boolean,     // AVX-512 style masking
    val hasVariableLength: Boolean, // ARM SVE or RISC-V V
    val name: String            // "NEON", "AVX2", "AVX-512", etc.
) {
    val bytesPerVector: Int get() = vectorBits / 8
    val intsPerVector: Int get() = vectorBits / 32
}

/**
 * Adaptive chunking based on SIMD width
 */
interface SimdChunker {
    /**
     * Split data into SIMD-friendly chunks.
     * Handles alignment and overlap for pattern detection.
     */
    fun chunkForSimd(data: ByteArray, overlap: Int = 0): List<ByteArrayChunk>
    
    /**
     * Merge results from parallel SIMD operations
     */
    fun mergeResults(chunks: List<ChunkResult>): IntArray
}

data class ByteArrayChunk(
    val data: ByteArray,
    val globalOffset: Int,
    val isAligned: Boolean
)

data class ChunkResult(
    val positions: IntArray,
    val chunkOffset: Int
)

/**
 * Platform-agnostic SIMD algorithms that scale with vector width
 */
abstract class ScalableSimdAlgorithm {
    
    /**
     * Generic pattern: Process as many elements as fit in a vector
     */
    internal inline fun <T> processInVectorChunks(
        dataSize: Int,
        vectorSize: Int,
        processChunk: (offset: Int, size: Int) -> T,
        combine: (results: List<T>) -> T
    ): T {
        val results = mutableListOf<T>()
        var offset = 0
        
        // Main loop - full vectors
        while (offset + vectorSize <= dataSize) {
            results.add(processChunk(offset, vectorSize))
            offset += vectorSize
        }
        
        // Tail handling - partial vector
        if (offset < dataSize) {
            results.add(processChunk(offset, dataSize - offset))
        }
        
        return combine(results)
    }
    
    /**
     * Adaptive algorithm that uses wider vectors when available
     */
    abstract fun <T> adaptiveProcess(
        data: ByteArray,
        capabilities: SimdCapabilities
    ): T
}

/**
 * Factory function to create platform-specific SIMD strategy
 */
expect fun createSimdStrategy(): SimdStrategy