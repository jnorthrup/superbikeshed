package borg.trikeshed.lib.simd

import jdk.incubator.vector.*
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

/**
 * JVM implementation using the Vector API (JEP 338/414/417).
 * This provides actual hardware SIMD acceleration on JVM 17+.
 * 
 * Compile with: --add-modules jdk.incubator.vector
 * Run with: --add-modules jdk.incubator.vector
 */
class JvmSimdStrategy : SimdStrategy {
    // Use the preferred species for byte operations
    internal val SPECIES = ByteVector.SPECIES_PREFERRED
    internal val vectorLength = SPECIES.length()
    
    override fun findByte(data: ByteArray, target: Byte, offset: Int): IntArray {
        val positions = mutableListOf<Int>()
        val targetVector = ByteVector.broadcast(SPECIES, target)
        
        var i = offset
        val bound = data.size - vectorLength
        
        // Main vectorized loop
        while (i <= bound) {
            val vector = ByteVector.fromArray(SPECIES, data, i)
            val mask = vector.eq(targetVector)
            
            // Extract matching positions
            if (!mask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (mask.laneIsSet(lane)) {
                        positions.add(i + lane)
                    }
                }
            }
            
            i += vectorLength
        }
        
        // Scalar tail
        while (i < data.size) {
            if (data[i] == target) {
                positions.add(i)
            }
            i++
        }
        
        return positions.toIntArray()
    }
    
    override fun findAnyByte(data: ByteArray, targets: ByteArray, offset: Int): IntArray {
        val positions = mutableListOf<Int>()
        
        // Create vectors for all target bytes
        val targetVectors = targets.map { ByteVector.broadcast(SPECIES, it) }
        
        var i = offset
        val bound = data.size - vectorLength
        
        // Main vectorized loop
        while (i <= bound) {
            val vector = ByteVector.fromArray(SPECIES, data, i)
            
            // Check against all targets using SIMD OR
            var combinedMask = vector.eq(targetVectors[0])
            for (j in 1 until targetVectors.size) {
                combinedMask = combinedMask.or(vector.eq(targetVectors[j]))
            }
            
            // Extract matching positions
            if (!combinedMask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (combinedMask.laneIsSet(lane)) {
                        positions.add(i + lane)
                    }
                }
            }
            
            i += vectorLength
        }
        
        // Scalar tail
        while (i < data.size) {
            if (data[i] in targets) {
                positions.add(i)
            }
            i++
        }
        
        return positions.toIntArray()
    }
    
    override fun compareBytes(data: ByteArray, pattern: ByteArray, positions: IntArray): BooleanArray {
        val results = BooleanArray(positions.size)
        
        for ((idx, pos) in positions.withIndex()) {
            if (pos + pattern.size > data.size) {
                results[idx] = false
                continue
            }
            
            var match = true
            var i = 0
            val bound = pattern.size - vectorLength
            
            // Vectorized comparison
            while (i <= bound && match) {
                val dataVec = ByteVector.fromArray(SPECIES, data, pos + i)
                val patternVec = ByteVector.fromArray(SPECIES, pattern, i)
                val mask = dataVec.eq(patternVec)
                
                if (!mask.allTrue()) {
                    match = false
                    break
                }
                
                i += vectorLength
            }
            
            // Scalar tail
            while (i < pattern.size && match) {
                if (data[pos + i] != pattern[i]) {
                    match = false
                }
                i++
            }
            
            results[idx] = match
        }
        
        return results
    }
    
    override fun popcount(bitmap: IntArray): Int {
        var count = 0
        
        // Use int vectors for popcount
        val intSpecies = IntVector.SPECIES_PREFERRED
        val intVectorLength = intSpecies.length()
        
        var i = 0
        val bound = bitmap.size - intVectorLength
        
        // Vectorized popcount using bit manipulation
        while (i <= bound) {
            val vector = IntVector.fromArray(intSpecies, bitmap, i)
            
            // Java doesn't have direct popcount in Vector API yet
            // So we sum the bits using bit manipulation
            for (lane in 0 until intVectorLength) {
                count += Integer.bitCount(vector.lane(lane))
            }
            
            i += intVectorLength
        }
        
        // Scalar tail
        while (i < bitmap.size) {
            count += Integer.bitCount(bitmap[i])
            i++
        }
        
        return count
    }
    
    override fun gatherBytes(data: ByteArray, positions: IntArray): ByteArray {
        // Gather is complex with current Vector API
        // Fall back to optimized scalar for now
        return ByteArray(positions.size) { i ->
            if (positions[i] < data.size) data[positions[i]] else 0
        }
    }
    
    override fun getCapabilities(): SimdCapabilities {
        val vectorBits = vectorLength * 8
        val species = when (vectorBits) {
            512 -> "AVX-512"
            256 -> "AVX2"
            128 -> "SSE4.2"
            else -> "Vector-$vectorBits"
        }
        
        return SimdCapabilities(
            vectorBits = vectorBits,
            hasPopcount = true, // JVM has Integer.bitCount
            hasGather = false, // Not yet in Vector API
            hasMaskOps = true, // Vector API has excellent mask support
            hasVariableLength = false, // Fixed-width vectors
            name = species
        )
    }
}

/**
 * MemorySegment-based JSON scanner for zero-copy parsing.
 * Uses off-heap memory and SIMD operations.
 */
class MemorySegmentJsonScanner(
    internal val segment: MemorySegment
) {
    internal val SPECIES = ByteVector.SPECIES_PREFERRED
    internal val vectorLength = SPECIES.length()
    
    fun findStructuralChars(): IntArray {
        val positions = mutableListOf<Int>()
        
        // Structural characters to find
        val targets = byteArrayOf(
            '{'.code.toByte(), '}'.code.toByte(),
            '['.code.toByte(), ']'.code.toByte(),
            ':'.code.toByte(), ','.code.toByte(),
            '"'.code.toByte()
        )
        
        val targetVectors = targets.map { ByteVector.broadcast(SPECIES, it) }
        
        var offset = 0L
        val size = segment.byteSize()
        val bound = size - vectorLength
        
        // Process using MemorySegment and Vector API
        while (offset <= bound) {
            // Load vector directly from MemorySegment
            val vector = ByteVector.fromMemorySegment(
                SPECIES, segment, offset, ByteOrder.nativeOrder()
            )
            
            // Check all structural characters
            var combinedMask = vector.eq(targetVectors[0])
            for (j in 1 until targetVectors.size) {
                combinedMask = combinedMask.or(vector.eq(targetVectors[j]))
            }
            
            // Extract positions
            if (!combinedMask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (combinedMask.laneIsSet(lane)) {
                        positions.add((offset + lane).toInt())
                    }
                }
            }
            
            offset += vectorLength
        }
        
        // Handle tail
        while (offset < size) {
            val b = segment.get(ValueLayout.JAVA_BYTE, offset)
            if (b in targets) {
                positions.add(offset.toInt())
            }
            offset++
        }
        
        return positions.toIntArray()
    }
    
    /**
     * Ultra-fast string extraction using SIMD quote finding
     */
    fun extractStrings(): List<String> {
        val strings = mutableListOf<String>()
        val quotePositions = findQuotes()
        
        var i = 0
        while (i < quotePositions.size - 1) {
            val start = quotePositions[i] + 1
            val end = quotePositions[i + 1]
            
            // Check for escaped quotes
            if (isEscaped(start - 1)) {
                i++
                continue
            }
            
            // Extract string using MemorySegment slice
            val stringBytes = segment.asSlice(start.toLong(), (end - start).toLong())
                .toArray(ValueLayout.JAVA_BYTE)
            
            strings.add(String(stringBytes))
            i += 2
        }
        
        return strings
    }
    
    internal fun findQuotes(): IntArray {
        val positions = mutableListOf<Int>()
        val quoteVector = ByteVector.broadcast(SPECIES, '"'.code.toByte())
        
        var offset = 0L
        val size = segment.byteSize()
        val bound = size - vectorLength
        
        while (offset <= bound) {
            val vector = ByteVector.fromMemorySegment(
                SPECIES, segment, offset, ByteOrder.nativeOrder()
            )
            val mask = vector.eq(quoteVector)
            
            if (!mask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (mask.laneIsSet(lane)) {
                        positions.add((offset + lane).toInt())
                    }
                }
            }
            
            offset += vectorLength
        }
        
        // Tail
        while (offset < size) {
            if (segment.get(ValueLayout.JAVA_BYTE, offset) == '"'.code.toByte()) {
                positions.add(offset.toInt())
            }
            offset++
        }
        
        return positions.toIntArray()
    }
    
    internal fun isEscaped(pos: Int): Boolean {
        if (pos <= 0) return false
        var backslashCount = 0
        var p = pos - 1
        
        while (p >= 0 && segment.get(ValueLayout.JAVA_BYTE, p.toLong()) == '\\'.code.toByte()) {
            backslashCount++
            p--
        }
        
        return backslashCount % 2 == 1
    }
}

/**
 * Create SimdStrategy for JVM
 */
actual fun createSimdStrategy(): SimdStrategy = JvmSimdStrategy()