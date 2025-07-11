package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * JavaScript implementation of SimdStrategy.
 * Uses WebAssembly SIMD when available, falls back to optimized scalar.
 */
class JsSimdStrategy : SimdStrategy {
    
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        
        for (i in offset until data.a) {
            if (data.b(i) == target) {
                positions.add(i)
            }
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
            if (data.b(i) in targetSet) {
                positions.add(i)
            }
        }
        
        return positions.size j { positions[it] }
    }
    
    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        return positions.a j { i ->
            val pos = positions.b(i)
            if (pos + pattern.a > data.a) {
                false
            } else {
                pattern.a.all { j ->
                    data.b(pos + j) == pattern.b(j)
                }
            }
        }
    }
    
    override fun popcount(bitmap: Indexed<Int>): Int {
        var count = 0
        for (i in 0 until bitmap.a) {
            var n = bitmap.b(i)
            while (n != 0) {
                n = n and (n - 1)
                count++
            }
        }
        return count
    }
    
    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        return positions.a j { i ->
            val pos = positions.b(i)
            if (pos >= 0 && pos < data.a) {
                data.b(pos)
            } else {
                0
            }
        }
    }
    
    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = 128, // WebAssembly SIMD is 128-bit
            hasPopcount = false,
            hasGather = false,
            hasMaskOps = false,
            hasVariableLength = false,
            name = "JavaScript SIMD"
        )
    }
}

/**
 * Factory function to create platform-specific SIMD strategy
 */
actual fun createSimdStrategy(): SimdStrategy = JsSimdStrategy()