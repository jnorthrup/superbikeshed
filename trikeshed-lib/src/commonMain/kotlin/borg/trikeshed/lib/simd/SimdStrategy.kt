package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed

/**
 * SIMD Strategy Pattern - Architectural Decision Record (ADR-001)
 * 
 * CONTEXT: High-performance parsing requires platform-specific SIMD optimization
 * DECISION: Use expect/actual pattern with C interop for native SIMD
 * CONSEQUENCES: Platform-specific implementations (Apple NEON/AMX, Linux SSE/AVX)
 * 
 * DO NOT CHANGE: This interface must remain stable for bbcursive integration
 * DO NOT REPLACE: C interop with pure Kotlin implementations
 * 
 * ADR-002 Compliance: All methods use Indexed<T> instead of String-based operations
 * 
 * Related: BBCursiveSimdAutovec, cinterop/simd.h, ADR-001, ADR-002
 */
interface SimdStrategy {
    /**
     * Find all occurrences of a byte value in a buffer
     * 
     * ARCHITECTURAL CONSTRAINT: Must use native SIMD for performance
     * DO NOT: Implement with scalar fallback in production
     * ADR-002 Compliance: Uses Indexed<Int> instead of String-based results
     */
    fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int>

    /**
     * Find any of multiple byte values in a buffer
     * 
     * ARCHITECTURAL CONSTRAINT: Must use native SIMD for performance
     * DO NOT: Implement with scalar fallback in production
     * ADR-002 Compliance: Uses Indexed<Int> instead of String-based results
     */
    fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int>

    /**
     * Compare bytes at multiple positions against a pattern
     * 
     * ARCHITECTURAL CONSTRAINT: Must use native SIMD for performance
     * DO NOT: Implement with scalar fallback in production
     * ADR-002 Compliance: Uses Indexed<Boolean> instead of String-based results
     */
    fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean>

    /**
     * Count set bits in bitmap
     * 
     * ARCHITECTURAL CONSTRAINT: Must use native popcount instruction
     * DO NOT: Implement with manual bit counting
     * ADR-002 Compliance: Returns primitive Int, no String allocation
     */
    fun popcount(bitmap: Indexed<Int>): Int

    /**
     * Gather bytes from specified positions
     * 
     * ARCHITECTURAL CONSTRAINT: Must use native gather instruction
     * DO NOT: Implement with manual array indexing
     * ADR-002 Compliance: Uses Indexed<Byte> instead of String-based results
     */
    fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte>

    /**
     * Get platform-specific SIMD capabilities
     * 
     * ARCHITECTURAL CONSTRAINT: Must reflect actual hardware capabilities
     * DO NOT: Return generic capabilities for all platforms
     * ADR-002 Compliance: Uses structured data class instead of String-based capabilities
     */
    fun getCapabilities(): SimdCapabilities
}

/**
 * SIMD capabilities for the current platform
 * 
 * ARCHITECTURAL CONSTRAINT: Must be accurate for platform-specific SIMD
 * DO NOT: Use generic values across all platforms
 * ADR-002 Compliance: Uses structured data class with enum-like name field
 */
data class SimdCapabilities(
    val vectorBits: Int,           // Vector register size in bits
    val hasPopcount: Boolean,      // Hardware popcount support
    val hasGather: Boolean,        // Hardware gather support
    val hasMaskOps: Boolean,       // Hardware mask operations
    val hasVariableLength: Boolean, // Variable length vector support
    val name: String               // Platform-specific name (e.g., "Apple NEON", "Linux AVX2")
)

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
 * 
 * ARCHITECTURAL CONSTRAINT: Must return platform-specific implementation
 * DO NOT: Return generic implementation for all platforms
 * ADR-002 Compliance: No String allocation in factory function
 * 
 * Related: ADR-001, ADR-002, AppleSimdStrategy, LinuxSimdStrategy
 */
expect fun createSimdStrategy(): SimdStrategy 