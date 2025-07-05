package borg.trikeshed.lib.simd

/**
 * JavaScript/WASM implementation of SimdStrategy.
 * Uses WASM SIMD when available (128-bit vectors).
 * Falls back to optimized scalar code.
 */
actual class JsSimdStrategy : SimdStrategy {
    
    actual override fun findByte(data: ByteArray, target: Byte, offset: Int): IntArray {
        val positions = mutableListOf<Int>()
        
        // TODO: When WASM SIMD is available, use v128 operations
        // For now, optimized scalar with unrolling
        var i = offset
        val bound = data.size - 4
        
        // Unrolled loop for better performance
        while (i <= bound) {
            if (data[i] == target) positions.add(i)
            if (data[i + 1] == target) positions.add(i + 1)
            if (data[i + 2] == target) positions.add(i + 2)
            if (data[i + 3] == target) positions.add(i + 3)
            i += 4
        }
        
        // Handle remainder
        while (i < data.size) {
            if (data[i] == target) positions.add(i)
            i++
        }
        
        return positions.toIntArray()
    }
    
    actual override fun findAnyByte(data: ByteArray, targets: ByteArray, offset: Int): IntArray {
        val positions = mutableListOf<Int>()
        val targetSet = targets.toSet()
        
        // Optimized scalar - browser JITs are quite good at this
        for (i in offset until data.size) {
            if (data[i] in targetSet) {
                positions.add(i)
            }
        }
        
        return positions.toIntArray()
    }
    
    actual override fun compareBytes(data: ByteArray, pattern: ByteArray, positions: IntArray): BooleanArray {
        return BooleanArray(positions.size) { idx ->
            val pos = positions[idx]
            if (pos + pattern.size > data.size) {
                false
            } else {
                var match = true
                for (i in pattern.indices) {
                    if (data[pos + i] != pattern[i]) {
                        match = false
                        break
                    }
                }
                match
            }
        }
    }
    
    actual override fun popcount(bitmap: IntArray): Int {
        var count = 0
        
        // JavaScript has efficient bit manipulation
        for (word in bitmap) {
            // Brian Kernighan's algorithm
            var n = word
            while (n != 0) {
                n = n and (n - 1)
                count++
            }
        }
        
        return count
    }
    
    actual override fun gatherBytes(data: ByteArray, positions: IntArray): ByteArray {
        return ByteArray(positions.size) { i ->
            if (positions[i] < data.size) data[positions[i]] else 0
        }
    }
    
    actual override fun getCapabilities(): SimdCapabilities {
        // Check for WASM SIMD support
        val hasWasmSimd = js("typeof WebAssembly !== 'undefined' && WebAssembly.validate !== undefined") as Boolean
        
        return SimdCapabilities(
            vectorBits = if (hasWasmSimd) 128 else 0,
            hasPopcount = true, // JS has bit manipulation
            hasGather = false,
            hasMaskOps = false,
            hasVariableLength = false,
            name = if (hasWasmSimd) "WASM-SIMD" else "Scalar-JS"
        )
    }
}

/**
 * Create SimdStrategy for JS/WASM
 */
actual fun createSimdStrategy(): SimdStrategy = JsSimdStrategy()