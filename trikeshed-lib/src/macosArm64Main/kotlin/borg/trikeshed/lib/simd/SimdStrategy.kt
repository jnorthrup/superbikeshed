package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * Native implementation of SimdStrategy
 * Provides scalar fallback for platforms without SIMD support
 */
class NativeSimdStrategy : SimdStrategy {
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
        for (i in offset until data.a) {
            val byte = data.b(i)
            for (j in 0 until targets.a) {
                if (byte == targets.b(j)) {
                    positions.add(i)
                    break
                }
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
                var matches = true
                for (j in 0 until pattern.a) {
                    if (data.b(pos + j) != pattern.b(j)) {
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
            var word = bitmap.b(i)
            while (word != 0) {
                count += word and 1
                word = word ushr 1
            }
        }
        return count
    }

    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        return positions.a j { i ->
            val pos = positions.b(i)
            if (pos < data.a) data.b(pos) else 0
        }
    }

    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = 64,  // Scalar fallback
            hasPopcount = false,
            hasGather = false,
            hasMaskOps = false,
            hasVariableLength = false,
            name = "Native (Scalar)"
        )
    }
}

/**
 * Factory function to create platform-specific SIMD strategy
 */
actual fun createSimdStrategy(): SimdStrategy = NativeSimdStrategy() 