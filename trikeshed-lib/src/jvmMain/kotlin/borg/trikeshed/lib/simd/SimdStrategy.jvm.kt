package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
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
    
    private fun Indexed<Byte>.toByteArray(): ByteArray {
        return ByteArray(this.a) { i -> this.b(i) }
    }
    
    private fun Indexed<Int>.toIntArray(): IntArray {
        return IntArray(this.a) { i -> this.b(i) }
    }
    
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        val byteArray = data.toByteArray()
        val targetVector = ByteVector.broadcast(SPECIES, target)
        
        var i = offset
        val bound = byteArray.size - vectorLength
        
        // Main loop - full vectors
        while (i <= bound) {
            val vector = ByteVector.fromArray(SPECIES, byteArray, i)
            val mask = vector.eq(targetVector)
            
            if (mask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (mask.laneIsSet(lane)) {
                        positions.add(i + lane)
                    }
                }
            }
            
            i += vectorLength
        }
        
        // Tail handling
        while (i < byteArray.size) {
            if (byteArray[i] == target) {
                positions.add(i)
            }
            i++
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        val byteArray = data.toByteArray()
        val targetArray = targets.toByteArray()
        
        // Create target vectors for each target byte
        val targetVectors = targetArray.map { ByteVector.broadcast(SPECIES, it) }
        
        var i = offset
        val bound = byteArray.size - vectorLength
        
        // Main loop - full vectors
        while (i <= bound) {
            val vector = ByteVector.fromArray(SPECIES, byteArray, i)
            var combinedMask = VectorMask.fromValues(SPECIES, false, false, false, false, false, false, false, false)
            
            // Combine masks for all target bytes
            for (targetVector in targetVectors) {
                combinedMask = combinedMask.or(vector.eq(targetVector))
            }
            
            if (combinedMask.anyTrue()) {
                for (lane in 0 until vectorLength) {
                    if (combinedMask.laneIsSet(lane)) {
                        positions.add(i + lane)
                    }
                }
            }
            
            i += vectorLength
        }
        
        // Tail handling
        while (i < byteArray.size) {
            if (targetArray.contains(byteArray[i])) {
                positions.add(i)
            }
            i++
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        val dataArray = data.toByteArray()
        val patternArray = pattern.toByteArray()
        val positionArray = positions.toIntArray()
        
        return positionArray.size j { i ->
            val pos = positionArray[i]
            if (pos + patternArray.size > dataArray.size) {
                false
            } else {
                patternArray.indices.all { j ->
                    dataArray[pos + j] == patternArray[j]
                }
            }
        }
    }
    
    override fun popcount(bitmap: Indexed<Int>): Int {
        val intArray = bitmap.toIntArray()
        var total = 0
        
        // Use Integer.bitCount for each int
        for (value in intArray) {
            total += Integer.bitCount(value)
        }
        
        return total
    }
    
    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        val dataArray = data.toByteArray()
        val positionArray = positions.toIntArray()
        
        return positionArray.size j { i ->
            val pos = positionArray[i]
            if (pos >= 0 && pos < dataArray.size) {
                dataArray[pos]
            } else {
                0
            }
        }
    }
    
    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = vectorLength * 8,
            hasPopcount = true,
            hasGather = true,
            hasMaskOps = true,
            hasVariableLength = false,
            name = "JVM Vector API"
        )
    }
    
    // Memory segment operations for advanced use cases
    internal fun findQuotes(segment: MemorySegment): IntArray {
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
            
            if (mask.anyTrue()) {
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
    
    internal fun isEscaped(pos: Int, segment: MemorySegment): Boolean {
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
 * Factory function to create platform-specific SIMD strategy
 */
actual fun createSimdStrategy(): SimdStrategy = JvmSimdStrategy()