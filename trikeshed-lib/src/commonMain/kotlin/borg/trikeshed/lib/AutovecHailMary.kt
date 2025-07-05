package borg.trikeshed.lib

/**
 * Hail Mary autovectorization patterns for Kotlin Common
 * These patterns MAY trigger SIMD on some platforms/compilers
 */

// Pattern 1: Simple array fills - most likely to vectorize
inline fun ByteArray.fillPattern(pattern: Byte) {
    for (i in indices) {
        this[i] = pattern
    }
}

// Pattern 2: Array comparison - vectorizable on many platforms
inline fun ByteArray.allMatch(other: ByteArray): Boolean {
    if (size != other.size) return false
    for (i in indices) {
        if (this[i] != other[i]) return false
    }
    return true
}

// Pattern 3: Branchless min/max - compiler loves these
inline fun ByteArray.minMaxBranchless(): Join<Byte, Byte> {
    var min = this[0]
    var max = this[0]
    for (i in 1 until size) {
        val v = this[i]
        min = if (v < min) v else min
        max = if (v > max) v else max
    }
    return min j max
}

// Pattern 4: Parallel accumulation - autovec gold
inline fun ByteArray.sumAsInt(): Int {
    var sum = 0
    for (element in this) {
        sum += element.toInt() and 0xFF
    }
    return sum
}

// Pattern 5: Stride access - sometimes vectorizes
inline fun ByteArray.extractEveryFourth(): ByteArray {
    val result = ByteArray(size / 4)
    for (i in result.indices) {
        result[i] = this[i * 4]
    }
    return result
}

// Pattern 6: Bitwise operations - SIMD friendly
inline fun ByteArray.xorWith(mask: Byte) {
    for (i in indices) {
        this[i] = (this[i].toInt() xor mask.toInt()).toByte()
    }
}

// Pattern 7: Count matches - reduction pattern
inline fun ByteArray.countByte(target: Byte): Int {
    var count = 0
    for (b in this) {
        if (b == target) count++
    }
    return count
}

// Pattern 8: Find first - early exit but still vectorizable
inline fun ByteArray.findFirst(target: Byte): Int {
    for (i in indices) {
        if (this[i] == target) return i
    }
    return -1
}

// BBCursive Integration - Hail Mary SIMD JSON
object AutovecJsonParser {
    
    // Pattern 9: Multi-byte pattern detection
        inline fun ByteArray.hasPattern4At(pos: Int, b0: Byte, b1: Byte, b2: Byte, b3: Byte): Boolean {
        return pos + 3 < size && 
               this[pos] == b0 && 
               this[pos + 1] == b1 && 
               this[pos + 2] == b2 && 
               this[pos + 3] == b3
    }
    
    // Pattern 10: Bulk whitespace skip - unrolled for SIMD
        inline fun ByteArray.skipWhitespace(start: Int): Int {
        var i = start
        // Unroll by 8 for potential 8-wide SIMD
        while (i + 7 < size) {
            if (this[i] != ' '.code.toByte() && this[i] != '\t'.code.toByte() && 
                this[i] != '\n'.code.toByte() && this[i] != '\r'.code.toByte()) return i
            if (this[i+1] != ' '.code.toByte() && this[i+1] != '\t'.code.toByte() && 
                this[i+1] != '\n'.code.toByte() && this[i+1] != '\r'.code.toByte()) return i+1
            if (this[i+2] != ' '.code.toByte() && this[i+2] != '\t'.code.toByte() && 
                this[i+2] != '\n'.code.toByte() && this[i+2] != '\r'.code.toByte()) return i+2
            if (this[i+3] != ' '.code.toByte() && this[i+3] != '\t'.code.toByte() && 
                this[i+3] != '\n'.code.toByte() && this[i+3] != '\r'.code.toByte()) return i+3
            if (this[i+4] != ' '.code.toByte() && this[i+4] != '\t'.code.toByte() && 
                this[i+4] != '\n'.code.toByte() && this[i+4] != '\r'.code.toByte()) return i+4
            if (this[i+5] != ' '.code.toByte() && this[i+5] != '\t'.code.toByte() && 
                this[i+5] != '\n'.code.toByte() && this[i+5] != '\r'.code.toByte()) return i+5
            if (this[i+6] != ' '.code.toByte() && this[i+6] != '\t'.code.toByte() && 
                this[i+6] != '\n'.code.toByte() && this[i+6] != '\r'.code.toByte()) return i+6
            if (this[i+7] != ' '.code.toByte() && this[i+7] != '\t'.code.toByte() && 
                this[i+7] != '\n'.code.toByte() && this[i+7] != '\r'.code.toByte()) return i+7
            i += 8
        }
        // Handle tail
        while (i < size) {
            val b = this[i]
            if (b != ' '.code.toByte() && b != '\t'.code.toByte() && 
                b != '\n'.code.toByte() && b != '\r'.code.toByte()) return i
            i++
        }
        return size
    }
}

// The ultimate Hail Mary - hope compiler recognizes memchr pattern
inline fun ByteArray.memchr(target: Byte, start: Int = 0): Int {
    for (i in start until size) {
        if (this[i] == target) return i
    }
    return -1
}