package borg.trikeshed.lib

/**
 * Natural BBCursive - Let the compiler/runtime choose
 * 
 * Instead of manual tiling, write naturally vectorizable patterns
 * and trust the optimizer
 */

// Natural pattern 1: Table-driven dispatch
inline fun ByteArray.classifyJson(): IntArray {
    val classes = IntArray(size)
    for (i in indices) {
        classes[i] = JSON_CLASS_TABLE[this[i].toInt() and 0xFF]
    }
    return classes
}

// Lookup table - data dependency free
internal val JSON_CLASS_TABLE = IntArray(256).apply {
    this['{'.code] = 1  // OBJECT_START
    this['}'.code] = 2  // OBJECT_END
    this['['.code] = 3  // ARRAY_START
    this[']'.code] = 4  // ARRAY_END
    this['"'.code] = 5  // QUOTE
    this[':'.code] = 6  // COLON
    this[','.code] = 7  // COMMA
    this[' '.code] = 8  // WHITESPACE
    this['\t'.code] = 8
    this['\n'.code] = 8
    this['\r'.code] = 8
    for (i in '0'.code..'9'.code) this[i] = 9  // DIGIT
}

// Natural pattern 2: Parallel state machine
data class SimdState(
    val inString: BooleanArray,
    val escaped: BooleanArray,
    val depth: IntArray
)

inline fun ByteArray.parallelJsonScan(): SimdState {
    val inString = BooleanArray(size)
    val escaped = BooleanArray(size)
    
    // Forward pass - naturally vectorizable
    var wasEscaped = false
    for (i in indices) {
        escaped[i] = wasEscaped
        wasEscaped = !wasEscaped && this[i] == '\\'.code.toByte()
    }
    
    // String detection - data parallel
    var insideString = false
    for (i in indices) {
        if (this[i] == '"'.code.toByte() && !escaped[i]) {
            insideString = !insideString
        }
        inString[i] = insideString
    }
    
    return SimdState(inString, escaped, IntArray(0))
}

// Natural pattern 3: Reduction operations
inline fun ByteArray.countStructural(): Int {
    var count = 0
    for (b in this) {
        // Branchless increment
        count += (JSON_CLASS_TABLE[b.toInt() and 0xFF] != 0).toIntBranchless()
    }
    return count
}

// Helper for branchless boolean to int
private inline fun Boolean.toIntBranchless(): Int = if (this) 1 else 0

// Natural pattern 4: Gather/scatter for quotes
inline fun ByteArray.gatherQuotes(): IntArray {
    val quotes = IntArray(size / 4) // Pessimistic allocation
    var count = 0
    
    for (i in indices) {
        if (this[i] == '"'.code.toByte()) {
            quotes[count++] = i
        }
    }
    
    return quotes.copyOf(count)
}

// Natural pattern 5: SWAR-style multi-byte compare
inline fun ByteArray.findPattern32(pattern: Int, start: Int = 0): Int {
    if (size < 4) return -1
    
    // Load 4 bytes as Int - compiler may vectorize this loop
    for (i in start..size - 4) {
        val word = (this[i].toInt() and 0xFF shl 24) or
                   (this[i + 1].toInt() and 0xFF shl 16) or
                   (this[i + 2].toInt() and 0xFF shl 8) or
                   (this[i + 3].toInt() and 0xFF)
        if (word == pattern) return i
    }
    return -1
}

// The natural BBCursive parser using these patterns
object NaturalBBCursive {
    
    fun parse(data: ByteArray): Any? {
        // Step 1: Classify all bytes in parallel
        val classes = data.classifyJson()
        
        // Step 2: Parallel string/escape detection  
        val state = data.parallelJsonScan()
        
        // Step 3: Gather structural positions
        val quotes = data.gatherQuotes()
        
        // Step 4: Natural recursive descent with pre-computed data
        return parseWithClassification(data, classes, state, quotes, 0)?.a
    }
    
    private fun parseWithClassification(
        data: ByteArray,
        classes: IntArray,
        state: SimdState,
        quotes: IntArray,
        pos: Int
    ): Join<Any, Int>? {
        // Use pre-computed classifications instead of byte comparisons
        // Compiler can optimize this better
        return null // placeholder
    }
}

// Pattern 6: Natural string validation (no branches in loop)
inline fun ByteArray.isValidJsonString(start: Int, end: Int): Boolean {
    var valid = 1
    var escaped = 0
    
    for (i in start until end) {
        val b = this[i]
        // Branchless validity check
        valid = valid and ((b >= 0x20) or (b == '\t'.code.toByte())).toIntBranchless()
        // Update escape state without branches
        escaped = (1 - escaped) and (b == '\\'.code.toByte()).toIntBranchless()
    }
    
    return valid == 1 && escaped == 0
}

// Extension for branchless byte comparison
inline fun Byte.eq(other: Byte): Int = if (this == other) 1 else 0

// Extension for branchless operations
private inline fun Byte.toIntBranchless(): Int = this.toInt()
private inline fun Boolean.toIntBranchless(): Int = if (this) 1 else 0