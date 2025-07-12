package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * JVM implementation of SimdStrategy
 * Provides scalar fallback for JVM platform
 */
class JvmSimdStrategy : SimdStrategy {
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        for (i in offset until data.component1()) {
            if (data.component2()(i) == target) {
                positions.add(i)
            }
        }
        return positions.size j { positions[it] }
    }

    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        val positions = mutableListOf<Int>()
        for (i in offset until data.component1()) {
            val byte = data.component2()(i)
            for (j in 0 until targets.component1()) {
                if (byte == targets.component2()(j)) {
                    positions.add(i)
                    break
                }
            }
        }
        return positions.size j { positions[it] }
    }

    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        return \1 j { \2: Int ->
            val pos = positions.component2()(i)
            if (pos + pattern.component1() > data.component1()) {
                false
            } else {
                var matches = true
                for (j in 0 until pattern.component1()) {
                    if (data.component2()(pos + j) != pattern.component2()(j)) {
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
        for (i in 0 until bitmap.component1()) {
            var word = bitmap.component2()(i)
            while (word != 0) {
                count += word and 1
                word = word ushr 1
            }
        }
        return count
    }

    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        return \1 j { \2: Int ->
            val pos = positions.component2()(i)
            if (pos < data.component1()) data.component2()(pos) else 0
        }
    }

    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = 64,  // Scalar fallback
            hasPopcount = false,
            hasGather = false,
            hasMaskOps = false,
            hasVariableLength = false,
            name = "JVM (Scalar)"
        )
    }
}

/**
 * Factory function to create platform-specific SIMD strategy
 */
actual fun createSimdStrategy(): SimdStrategy = JvmSimdStrategy() 