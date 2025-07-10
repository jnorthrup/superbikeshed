@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.sumo.simd

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * SIMD Bitmap Engine for KIF Structural Detection
 * 
 * Provides platform-specific SIMD optimizations for bitmap operations
 * used in KIF parsing and SUMO ontology processing.
 */

// === SIMD CAPABILITIES ===

/**
 * SIMD capabilities detection and configuration
 */
data class SimdCapabilities(
    val vectorBits: Int,           // Vector width in bits (128, 256, 512, etc.)
    val bytesPerVector: Int,       // Bytes per vector (16, 32, 64, etc.)
    val lanes: Int,                // Number of parallel lanes
    val platform: SimdPlatform,    // Platform-specific optimizations
    val features: Set<SimdFeature> // Available SIMD features
)

enum class SimdPlatform {
    X86_SSE, X86_AVX2, X86_AVX512, ARM_NEON, ARM_SVE, RISC_V, WASM, GENERIC
}

enum class SimdFeature {
    POPCNT, BMI, AVX2, AVX512, NEON, SVE, VECTOR_EXTENSIONS
}

// === SIMD STRATEGY INTERFACE ===

/**
 * Platform-agnostic SIMD strategy interface
 */
interface SimdStrategy {
    
    /**
     * Get SIMD capabilities for this platform
     */
    fun getCapabilities(): SimdCapabilities
    
    /**
     * Find all occurrences of a byte value in parallel
     */
    fun findByte(data: ByteArray, target: Byte, offset: Int = 0): IntArray
    
    /**
     * Find any of multiple byte values (e.g., structural characters)
     */
    fun findAnyByte(data: ByteArray, targets: ByteArray, offset: Int = 0): IntArray
    
    /**
     * Parallel character classification
     */
    fun classifyBytes(data: ByteArray, classificationTable: IntArray): IntArray
    
    /**
     * Population count (count set bits) in parallel
     */
    fun popcount(bitmap: LongArray): Int
    
    /**
     * Find first set bit using SIMD
     */
    fun findFirstSet(bitmap: LongArray): Int
    
    /**
     * Parallel bitmap operations
     */
    fun bitmapOr(a: LongArray, b: LongArray): LongArray
    fun bitmapAnd(a: LongArray, b: LongArray): LongArray
    fun bitmapXor(a: LongArray, b: LongArray): LongArray
    fun bitmapNot(a: LongArray): LongArray
}

// === GENERIC SIMD STRATEGY ===

/**
 * Generic SIMD strategy that works on all platforms
 * Falls back to scalar operations when SIMD is not available
 */
class GenericSimdStrategy : SimdStrategy {
    
    override fun getCapabilities(): SimdCapabilities {
        return SimdCapabilities(
            vectorBits = 64, // Use 64-bit words as baseline
            bytesPerVector = 8,
            lanes = 8,
            platform = SimdPlatform.GENERIC,
            features = setOf(SimdFeature.VECTOR_EXTENSIONS)
        )
    }
    
    override fun findByte(data: ByteArray, target: Byte, offset: Int): IntArray {
        val positions = mutableListOf<Int>()
        for (i in offset until data.size) {
            if (data[i] == target) {
                positions.add(i)
            }
        }
        return positions.toIntArray()
    }
    
    override fun findAnyByte(data: ByteArray, targets: ByteArray, offset: Int): IntArray {
        val targetSet = targets.toSet()
        val positions = mutableListOf<Int>()
        for (i in offset until data.size) {
            if (data[i] in targetSet) {
                positions.add(i)
            }
        }
        return positions.toIntArray()
    }
    
    override fun classifyBytes(data: ByteArray, classificationTable: IntArray): IntArray {
        val classes = IntArray(data.size)
        for (i in data.indices) {
            classes[i] = classificationTable[data[i].toInt() and 0xFF]
        }
        return classes
    }
    
    override fun popcount(bitmap: LongArray): Int {
        var count = 0
        for (word in bitmap) {
            count += word.countOneBits()
        }
        return count
    }
    
    override fun findFirstSet(bitmap: LongArray): Int {
        for (i in bitmap.indices) {
            if (bitmap[i] != 0L) {
                return (i shl 6) + bitmap[i].countTrailingZeroBits()
            }
        }
        return -1
    }
    
    override fun bitmapOr(a: LongArray, b: LongArray): LongArray {
        val result = LongArray(a.size)
        for (i in a.indices) {
            result[i] = a[i] or b[i]
        }
        return result
    }
    
    override fun bitmapAnd(a: LongArray, b: LongArray): LongArray {
        val result = LongArray(a.size)
        for (i in a.indices) {
            result[i] = a[i] and b[i]
        }
        return result
    }
    
    override fun bitmapXor(a: LongArray, b: LongArray): LongArray {
        val result = LongArray(a.size)
        for (i in a.indices) {
            result[i] = a[i] xor b[i]
        }
        return result
    }
    
    override fun bitmapNot(a: LongArray): LongArray {
        val result = LongArray(a.size)
        for (i in a.indices) {
            result[i] = a[i].inv()
        }
        return result
    }
}

// === SIMD BITMAP ENGINE ===

/**
 * Main SIMD bitmap engine for KIF structural detection
 */
class SimdBitmapEngine(
    internal val strategy: SimdStrategy = GenericSimdStrategy()
) {
    
    internal val capabilities = strategy.getCapabilities()
    
    /**
     * Build structural bitmap for KIF elements
     */
    fun buildStructuralBitmap(
        data: ByteArray,
        structuralChars: ByteArray
    ): LongArray {
        val bitmap = LongArray((data.size + 63) shr 6)
        
        // Find all structural character positions
        val positions = strategy.findAnyByte(data, structuralChars)
        
        // Set bits in bitmap for each position
        for (pos in positions) {
            val wordIndex = pos shr 6
            val bitIndex = pos and 63
            bitmap[wordIndex] = bitmap[wordIndex] or (1L shl bitIndex)
        }
        
        return bitmap
    }
    
    /**
     * Extract structural positions from bitmap
     */
    fun extractStructuralPositions(bitmap: LongArray): IntArray {
        val positions = mutableListOf<Int>()
        
        for (wordIndex in bitmap.indices) {
            var word = bitmap[wordIndex]
            val basePos = wordIndex shl 6
            
            while (word != 0L) {
                val bitIndex = word.countTrailingZeroBits()
                positions.add(basePos + bitIndex)
                word = word and (word - 1) // Clear lowest set bit
            }
        }
        
        return positions.toIntArray()
    }
    
    /**
     * Find next structural position after given index
     */
    fun findNextStructural(bitmap: LongArray, afterIndex: Int): Int {
        val startWord = afterIndex shr 6
        val startBit = afterIndex and 63
        
        // Check current word first
        if (startWord < bitmap.size) {
            val word = bitmap[startWord]
            val masked = word and ((-1L) shl startBit)
            if (masked != 0L) {
                return (startWord shl 6) + masked.countTrailingZeroBits()
            }
        }
        
        // Check subsequent words
        for (wordIdx in (startWord + 1) until bitmap.size) {
            val word = bitmap[wordIdx]
            if (word != 0L) {
                return (wordIdx shl 6) + word.countTrailingZeroBits()
            }
        }
        
        return -1
    }
    
    /**
     * Count structural elements in range
     */
    fun countStructuralInRange(bitmap: LongArray, start: Int, end: Int): Int {
        val startWord = start shr 6
        val endWord = (end - 1) shr 6
        var count = 0
        
        if (startWord == endWord) {
            // Single word case
            val word = bitmap[startWord]
            val startBit = start and 63
            val endBit = end and 63
            val mask = if (endBit == 0) (-1L shl startBit) else ((-1L shl startBit) and (-1L ushr (64 - endBit)))
            count = (word and mask).countOneBits()
        } else {
            // Multiple words case
            for (wordIdx in startWord..endWord) {
                val word = bitmap[wordIdx]
                when {
                    wordIdx == startWord -> {
                        val startBit = start and 63
                        count += (word and (-1L shl startBit)).countOneBits()
                    }
                    wordIdx == endWord -> {
                        val endBit = end and 63
                        count += (word and (-1L ushr (64 - endBit))).countOneBits()
                    }
                    else -> {
                        count += word.countOneBits()
                    }
                }
            }
        }
        
        return count
    }
    
    /**
     * Parallel state tracking for KIF parsing
     */
    data class KifParseState(
        val inComment: BooleanArray,
        val inString: BooleanArray,
        val parenDepth: IntArray,
        val escaped: BooleanArray,
        val structuralBitmap: LongArray
    )
    
    fun parallelKifScan(
        data: ByteArray,
        structuralChars: ByteArray
    ): KifParseState {
        val inComment = BooleanArray(data.size)
        val inString = BooleanArray(data.size)
        val parenDepth = IntArray(data.size)
        val escaped = BooleanArray(data.size)
        val structuralBitmap = buildStructuralBitmap(data, structuralChars)
        
        var comment = false
        var string = false
        var depth = 0
        var wasEscaped = false
        
        // Use SIMD classification for character types
        val classificationTable = IntArray(256).apply {
            this[';'.code] = 1  // Comment start
            this['\n'.code] = 2 // Newline
            this['"'.code] = 3  // Quote
            this['('.code] = 4  // Open paren
            this[')'.code] = 5  // Close paren
            this['\\'.code] = 6 // Escape
        }
        
        val classes = strategy.classifyBytes(data, classificationTable)
        
        for (i in data.indices) {
            val charClass = classes[i]
            
            escaped[i] = wasEscaped
            wasEscaped = !wasEscaped && charClass == 6 // Escape character
            
            when (charClass) {
                1 -> { // Semicolon
                    if (!string && !wasEscaped) {
                        comment = true
                    }
                }
                2 -> { // Newline
                    if (comment) {
                        comment = false
                    }
                }
                3 -> { // Quote
                    if (!comment && !wasEscaped) {
                        string = !string
                    }
                }
                4 -> { // Open paren
                    if (!comment && !string) {
                        depth++
                    }
                }
                5 -> { // Close paren
                    if (!comment && !string) {
                        depth--
                    }
                }
            }
            
            inComment[i] = comment
            inString[i] = string
            parenDepth[i] = depth
        }
        
        return KifParseState(inComment, inString, parenDepth, escaped, structuralBitmap)
    }
    
    /**
     * Extract KIF tokens using SIMD-accelerated scanning
     */
    fun extractTokens(data: ByteArray): Flow<KifToken> = flow {
        val structuralChars = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        )
        
        val state = parallelKifScan(data, structuralChars)
        val structuralPositions = extractStructuralPositions(state.structuralBitmap)
        
        var currentToken = StringBuilder()
        var tokenStart = 0
        var inComment = false
        var inString = false
        
        for (i in data.indices) {
            val byte = data[i]
            
            inComment = state.inComment[i]
            inString = state.inString[i]
            
            when {
                inComment -> {
                    // Skip until newline
                    if (byte == '\n'.code.toByte()) {
                        inComment = false
                    }
                }
                inString -> {
                    currentToken.append(byte.toInt().toChar())
                    if (byte == '"'.code.toByte() && !state.escaped[i]) {
                        // End of string
                        emit(KifToken.String(currentToken.toString(), tokenStart, i + 1))
                        currentToken.clear()
                        inString = false
                    }
                }
                byte == ' '.code.toByte() || byte == '\t'.code.toByte() || byte == '\r'.code.toByte() -> {
                    if (currentToken.isNotEmpty()) {
                        emit(KifToken.Symbol(currentToken.toString(), tokenStart, i))
                        currentToken.clear()
                    }
                }
                byte == '('.code.toByte() -> {
                    if (currentToken.isNotEmpty()) {
                        emit(KifToken.Symbol(currentToken.toString(), tokenStart, i))
                        currentToken.clear()
                    }
                    emit(KifToken.ParenthesisOpen(i))
                }
                byte == ')'.code.toByte() -> {
                    if (currentToken.isNotEmpty()) {
                        emit(KifToken.Symbol(currentToken.toString(), tokenStart, i))
                        currentToken.clear()
                    }
                    emit(KifToken.ParenthesisClose(i))
                }
                byte == '"'.code.toByte() -> {
                    if (currentToken.isNotEmpty()) {
                        emit(KifToken.Symbol(currentToken.toString(), tokenStart, i))
                        currentToken.clear()
                    }
                    tokenStart = i
                    inString = true
                }
                else -> {
                    if (currentToken.isEmpty()) {
                        tokenStart = i
                    }
                    currentToken.append(byte.toInt().toChar())
                }
            }
        }
        
        // Emit final token if any
        if (currentToken.isNotEmpty()) {
            emit(KifToken.Symbol(currentToken.toString(), tokenStart, data.size))
        }
    }
}

// === KIF TOKEN TYPES ===

sealed class KifToken(val start: Int, val end: Int) {
    data class Symbol(val value: String, override val start: Int, override val end: Int) : KifToken(start, end)
    data class String(val value: String, override val start: Int, override val end: Int) : KifToken(start, end)
    data class ParenthesisOpen(override val start: Int) : KifToken(start, start + 1)
    data class ParenthesisClose(override val start: Int) : KifToken(start, start + 1)
}

// === PLATFORM-SPECIFIC IMPLEMENTATIONS ===

/**
 * Platform-specific SIMD strategy factory
 */
object SimdStrategyFactory {
    
    fun createStrategy(): SimdStrategy {
        return when {
            // Platform detection would go here
            // For now, return generic strategy
            else -> GenericSimdStrategy()
        }
    }
} 