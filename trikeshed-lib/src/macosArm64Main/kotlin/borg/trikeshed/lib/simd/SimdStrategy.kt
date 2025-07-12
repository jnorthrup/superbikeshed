package borg.trikeshed.lib.simd

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toByteArray
import borg.trikeshed.lib.toIntArray

// External declarations for C interop
private external fun simd_find_byte(data: ByteArray, len: Int, target: Byte, offset: Int, positions: IntArray, count: IntArray): Int
private external fun simd_find_any_byte(data: ByteArray, dataLen: Int, targets: ByteArray, targetsLen: Int, offset: Int, positions: IntArray, count: IntArray): Int
private external fun simd_compare_bytes(data: ByteArray, dataLen: Int, pattern: ByteArray, patternLen: Int, positions: IntArray, positionsLen: Int, results: BooleanArray): Int
private external fun simd_popcount(bitmap: IntArray, len: Int): Int
private external fun simd_gather_bytes(data: ByteArray, dataLen: Int, positions: IntArray, positionsLen: Int, results: ByteArray): Int
private external fun simd_get_capabilities(vectorBits: IntArray, hasPopcount: BooleanArray, hasGather: BooleanArray, hasMaskOps: BooleanArray, hasVariableLength: BooleanArray, name: ByteArray)

/**
 * Apple Silicon SIMD implementation (NEON/AMX)
 * Uses C interop for NEON/AMX intrinsics.
 */
class AppleSimdStrategy : SimdStrategy {
    override fun findByte(data: Indexed<Byte>, target: Byte, offset: Int): Indexed<Int> {
        val dataArray = data.toByteArray()
        val positions = IntArray(dataArray.size) // Max possible size
        val count = IntArray(1)
        
        simd_find_byte(dataArray, dataArray.size, target, offset, positions, count)
        
        return Indexed(count[0]) { positions[it] }
    }

    override fun findAnyByte(data: Indexed<Byte>, targets: Indexed<Byte>, offset: Int): Indexed<Int> {
        val dataArray = data.toByteArray()
        val targetsArray = targets.toByteArray()
        val positions = IntArray(dataArray.size) // Max possible size
        val count = IntArray(1)
        
        simd_find_any_byte(dataArray, dataArray.size, targetsArray, targetsArray.size, offset, positions, count)
        
        return Indexed(count[0]) { positions[it] }
    }

    override fun compareBytes(data: Indexed<Byte>, pattern: Indexed<Byte>, positions: Indexed<Int>): Indexed<Boolean> {
        val dataArray = data.toByteArray()
        val patternArray = pattern.toByteArray()
        val positionsArray = positions.toIntArray()
        val results = BooleanArray(positionsArray.size)
        
        simd_compare_bytes(dataArray, dataArray.size, patternArray, patternArray.size, positionsArray, positionsArray.size, results)
        
        return Indexed(results.size) { results[it] }
    }

    override fun popcount(bitmap: Indexed<Int>): Int {
        val bitmapArray = bitmap.toIntArray()
        return simd_popcount(bitmapArray, bitmapArray.size)
    }

    override fun gatherBytes(data: Indexed<Byte>, positions: Indexed<Int>): Indexed<Byte> {
        val dataArray = data.toByteArray()
        val positionsArray = positions.toIntArray()
        val results = ByteArray(positionsArray.size)
        
        simd_gather_bytes(dataArray, dataArray.size, positionsArray, positionsArray.size, results)
        
        return Indexed(results.size) { results[it] }
    }

    override fun getCapabilities(): SimdCapabilities {
        val vectorBits = IntArray(1)
        val hasPopcount = BooleanArray(1)
        val hasGather = BooleanArray(1)
        val hasMaskOps = BooleanArray(1)
        val hasVariableLength = BooleanArray(1)
        val name = ByteArray(64)
        
        simd_get_capabilities(vectorBits, hasPopcount, hasGather, hasMaskOps, hasVariableLength, name)
        
        return SimdCapabilities(
            vectorBits = vectorBits[0],
            hasPopcount = hasPopcount[0],
            hasGather = hasGather[0],
            hasMaskOps = hasMaskOps[0],
            hasVariableLength = hasVariableLength[0],
            name = name.decodeToString().trim('\u0000')
        )
    }
}

/**
 * Factory function to create platform-specific SIMD strategy
 */
actual fun createSimdStrategy(): SimdStrategy = AppleSimdStrategy() 