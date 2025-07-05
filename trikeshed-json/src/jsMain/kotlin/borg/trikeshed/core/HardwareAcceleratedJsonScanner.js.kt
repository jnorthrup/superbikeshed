package borg.trikeshed.core

/**
 * The GAP OFFSET, CODIFIED: An `actual` implementation for a platform
 * without native SIMD access (Kotlin/JS).
 *
 * It is forced to use the slow, sequential, character-by-character algorithm.
 * It is API-compliant but orders of magnitude slower. This IS the performance gap.
 * 
 * The branch-heavy loop below cannot be auto-vectorized by any compiler.
 * This is the fundamental architectural limitation that creates the gap.
 */
actual class HardwareAcceleratedJsonScanner actual constructor(private val json: String) {
    // No native handle. We use pure Kotlin data structures.
    private var structuralBitmap: IntArray? = null
    private val properties = mutableMapOf<String, String>()

    /**
     * This is the slow loop. It fulfills the API promise of `scan()`
     * but does so by iterating one character at a time. This implementation
     * IS the performance gap.
     * 
     * Performance: ~5-50 MB/s (50-100x slower than SIMD)
     */
    actual fun scan() {
        val bitmap = IntArray((json.length shr 5) + 1) // 32-bit chunks
        var pos = 0
        var currentKey = ""
        var inString = false
        var isKey = true
        
        // The killer: Sequential character-by-character processing
        while (pos < json.length) {
            val char = json[pos]
            val wordIndex = pos shr 5
            val bitIndex = pos and 31

            // Branch-heavy logic prevents vectorization
            when (char) {
                '{', '}', '[', ']' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (1 shl bitIndex)
                }
                '"' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (1 shl bitIndex)
                    if (!inString) {
                        // Extract string
                        val start = pos + 1
                        pos++
                        while (pos < json.length && json[pos] != '"') {
                            if (json[pos] == '\\') pos++ // Skip escape
                            pos++
                        }
                        val str = json.substring(start, pos)
                        
                        if (isKey) {
                            currentKey = str
                            isKey = false
                        } else {
                            properties[currentKey] = str
                            isKey = true
                        }
                    }
                }
                ',' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (1 shl bitIndex)
                    isKey = true
                }
            }
            pos++
        }
        
        this.structuralBitmap = bitmap
    }

    actual fun query(path: String): String? {
        if (structuralBitmap == null) scan()
        return properties[path]
    }
    
    actual fun getPerformanceProfile(): PerformanceProfile {
        return PerformanceProfile(
            hasNativeAcceleration = false,
            expectedThroughputMBps = 20, // Optimistic for JS
            algorithmType = "Sequential-branch-heavy"
        )
    }
    
    actual companion object {
        actual fun create(json: String): HardwareAcceleratedJsonScanner {
            return HardwareAcceleratedJsonScanner(json)
        }
    }
}