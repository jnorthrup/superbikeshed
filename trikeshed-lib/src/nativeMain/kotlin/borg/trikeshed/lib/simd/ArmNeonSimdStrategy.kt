package borg.trikeshed.lib.simd

import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
// Conversion utilities

/**
 * ARM NEON SIMD implementation for ARM64 processors.
 * This includes Apple M-series, Snapdragon, AWS Graviton, etc.
 * 
 * NEON provides:
 * - 128-bit SIMD registers (32 registers on ARM64)
 * - Integer and floating-point operations
 * - Excellent for byte-level operations needed in parsing
 */
class ArmNeonSimdStrategy : SimdStrategy {
    
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        
        // Convert Indexed<Byte> to ByteArray for usePinned
        val byteArray = ByteArray(data.a) { i -> data.b(i) }
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        byteArray.usePinned { pinned ->
            @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
            val ptr = pinned.addressOf(0)
            var i = offset
            
            // Process 16-byte chunks with NEON
            val bound = data.a - 16
            while (i <= bound) {
                // In real implementation, we'd use NEON intrinsics:
                // vld1q_u8 - Load 16 bytes
                // vdupq_n_u8 - Duplicate target byte
                // vceqq_u8 - Compare for equality
                // vmovmaskq_u8 - Extract mask
                
                // For now, simulated NEON operation
                val chunk = ByteArray(16) { j -> data.b(i + j) }
                for (j in 0 until 16) {
                    if (chunk[j] == target) {
                        positions.add(i + j)
                    }
                }
                
                i += 16
            }
            
            // Process remaining bytes
            while (i < data.a) {
                if (data.b(i) == target) {
                    positions.add(i)
                }
                i++
            }
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        
        // Create lookup table for O(1) checks
        val isTarget = BooleanArray(256)
        for (i in 0 until targets.a) {
            isTarget[targets.b(i).toInt() and 0xFF] = true
        }
        
        // Convert Indexed<Byte> to ByteArray for usePinned
        val byteArray = ByteArray(data.a) { i -> data.b(i) }
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        byteArray.usePinned { pinned ->
            var i = offset
            val bound = data.a - 16
            
            // NEON processing of 16-byte chunks
            while (i <= bound) {
                // Real NEON would use:
                // - Multiple vceqq_u8 comparisons
                // - vorrq_u8 to OR the results
                // - Process 16 bytes in parallel
                
                for (j in 0 until 16) {
                    if (isTarget[data.b(i + j).toInt() and 0xFF]) {
                        positions.add(i + j)
                    }
                }
                
                i += 16
            }
            
            // Scalar remainder
            while (i < data.a) {
                if (isTarget[data.b(i).toInt() and 0xFF]) {
                    positions.add(i)
                }
                i++
            }
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        return Indexed(positions.a) { idx ->
            val pos = positions.b(idx)
            if (pos + pattern.a > data.a) {
                false
            } else {
                // Convert Indexed<Byte> to ByteArray for usePinned
                val dataArray = ByteArray(data.a) { i -> data.b(i) }
                val patternArray = ByteArray(pattern.a) { i -> pattern.b(i) }
                @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
                dataArray.usePinned { dataPinned ->
                    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
                    patternArray.usePinned { patternPinned ->
                        // Use memcmp for now, NEON would vectorize this
                        platform.posix.memcmp(
                            dataPinned.addressOf(pos),
                            patternPinned.addressOf(0),
                            pattern.a.toULong()
                        ) == 0
                    }
                }
            }
        }
    }
    
    override fun popcount(bitmap: Indexed<Int>): Int {
        var count = 0
        
        // ARM has efficient popcount
        for (i in 0 until bitmap.a) {
            // __builtin_popcount is available on ARM
            var w = bitmap.b(i)
            while (w != 0) {
                count++
                w = w and (w - 1)
            }
        }
        
        return count
    }
    
    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        // NEON doesn't have gather, but we can optimize with prefetch
        return Indexed(positions.a) { i ->
            if (positions.b(i) < data.a) data.b(positions.b(i)) else 0
        }
    }
    
    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = 128,
            hasPopcount = true,
            hasGather = false,
            hasMaskOps = false,
            hasVariableLength = false, // SVE would have this
            name = "NEON"
        )
    }
}

/**
 * ARM SVE (Scalable Vector Extension) implementation.
 * Available on newer ARM processors (ARMv9+).
 * Vector length is flexible: 128-2048 bits!
 */
class ArmSveSimdStrategy : SimdStrategy {
    internal val vectorLength = getSveVectorLength()
    
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        
        // SVE can process variable-length vectors
        val bytesPerVector = vectorLength / 8
        
        // SVE can process variable-length vectors without usePinned for now
        var i = offset
        val bound = data.a - bytesPerVector
        
        while (i <= bound) {
            // SVE instructions:
            // - LD1B: Load bytes with predication
            // - CMPEQ: Compare with predication
            // - COMPACT: Compress matching indices
            
            // Simulated SVE operation
            for (j in 0 until bytesPerVector) {
                if (i + j < data.a && data.b(i + j) == target) {
                    positions.add(i + j)
                }
            }
            
            i += bytesPerVector
        }
        
        // Process remainder with predication (SVE feature)
        while (i < data.a) {
            if (data.b(i) == target) {
                positions.add(i)
            }
            i++
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        // Similar to NEON but with flexible vector length
        return ArmNeonSimdStrategy().findAnyByte(data, targets, offset)
    }
    
    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        // SVE can use predicated loads for efficient comparison
        return ArmNeonSimdStrategy().compareBytes(data, pattern, positions)
    }
    
    override fun popcount(bitmap: Indexed<Int>): Int {
        // SVE has CNT instruction for population count
        return ArmNeonSimdStrategy().popcount(bitmap)
    }
    
    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        // SVE2 has gather load instructions!
        return Indexed(positions.a) { i ->
            if (positions.b(i) < data.a) data.b(positions.b(i)) else 0
        }
    }
    
    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = vectorLength,
            hasPopcount = true,
            hasGather = true, // SVE2 feature
            hasMaskOps = true, // Predication
            hasVariableLength = true,
            name = "SVE-$vectorLength"
        )
    }
    
    internal fun getSveVectorLength(): Int {
        // Would use prctl or sysfs to get actual SVE length
        // For now, assume minimum SVE length
        return 128
    }
}

// Note: createSimdStrategy() is defined in SimdStrategy.native.kt

internal fun hasArmNeon(): Boolean {
    // Check CPU features - all ARM64 has NEON
    return true
}

// Note: hasArmSve() is defined in SimdHelpers.kt

/**
 * Fallback for older ARM or when SIMD unavailable
 */
class FallbackSimdStrategy : SimdStrategy {
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        for (i in offset until data.a) {
            if (data.b(i) == target) positions.add(i)
        }
        return positions.size j { positions[it] }
    }
    
    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        val targetSet = mutableSetOf<Byte>()
        for (i in 0 until targets.a) {
            targetSet.add(targets.b(i))
        }
        for (i in offset until data.a) {
            if (data.b(i) in targetSet) positions.add(i)
        }
        return positions.size j { positions[it] }
    }
    
    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        return Indexed(positions.a) { idx ->
            val pos = positions.b(idx)
            if (pos + pattern.a > data.a) false
            else {
                var matches = true
                for (i in 0 until pattern.a) {
                    if (data.b(pos + i) != pattern.b(i)) {
                        matches = false
                        break
                    }
                }
                matches
            }
        }
    }
    
    override fun popcount(bitmap: Indexed<Int>): Int {
        var count = 0
        for (i in 0 until bitmap.a) {
            count += bitmap.b(i).countOneBits()
        }
        return count
    }
    
    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        return Indexed(positions.a) { i ->
            if (positions.b(i) < data.a) data.b(positions.b(i)) else 0
        }
    }
    
    override fun getCapabilities() = SimdCapabilities(
        vectorBits = 0,
        hasPopcount = false,
        hasGather = false,
        hasMaskOps = false,
        hasVariableLength = false,
        name = "Scalar"
    )
}