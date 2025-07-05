# BBCursive Auto-Vectorization Setup

## Cost-Free SIMD Opportunities

### 1. ByteArray Operations
```kotlin
// Current: byte-by-byte
while (p < limit) {
    if (buf.b(p) == target) return p
    p++
}

// Autovec-friendly: operate on chunks
@kotlin.ExperimentalUnsignedTypes
inline fun findByte(data: ByteArray, target: Byte): Int {
    // Compiler can vectorize this pattern
    for (i in data.indices) {
        if (data[i] == target) return i
    }
    return -1
}
```

### 2. Whitespace Detection SIMD
```kotlin
// Pack whitespace checks into SIMD-friendly pattern
private val WS_MASK = 0x20090A0DL // space, tab, LF, CR

@kotlin.internal.InlineOnly
inline fun isWhitespace8(bytes: Long): Boolean {
    // Each byte checked in parallel
    val masked = bytes and 0x7F7F7F7F7F7F7F7FL
    return (masked - 0x0101010101010101L) and WS_MASK != 0L
}
```

### 3. String Matching via SWAR
```kotlin
// SWAR (SIMD Within A Register) for 4/8 byte patterns
@kotlin.internal.InlineOnly
inline fun match4(buffer: ByteArray, pos: Int, pattern: Int): Boolean {
    if (pos + 3 >= buffer.size) return false
    val packed = (buffer[pos].toInt() shl 24) or
                 (buffer[pos+1].toInt() shl 16) or
                 (buffer[pos+2].toInt() shl 8) or
                 buffer[pos+3].toInt()
    return packed == pattern
}
```

### 4. Bulk Operations Setup
```kotlin
// Ensure alignment for vectorization
fun ByteArray.toAlignedPyramid(): BytePyramid {
    val aligned = ByteArray((size + 7) and -8) // 8-byte aligned
    copyInto(aligned)
    return aligned.size j { i -> if (i < size) aligned[i] else 0 }
}
```

### 5. Compiler Hints
```kotlin
// Help compiler recognize vectorizable patterns
@Target(AnnotationTarget.FUNCTION)
annotation class Vectorizable

@Vectorizable
inline fun countBytes(data: ByteArray, byte: Byte): Int {
    var count = 0
    // Simple, countable loop = vectorizable
    for (b in data) {
        if (b == byte) count++
    }
    return count
}
```

### Requirements for Auto-Vectorization:
1. **No side effects** in loop body
2. **Simple bounds** (0..n, indices)
3. **No early returns** inside loop (use break)
4. **Primitive operations** only
5. **Regular access patterns** (i, i+1, i+2...)

### BBCursive Integration:
```kotlin
// Before parsing, prepare data for SIMD
val alignedBuffer = input.toAlignedPyramid()
val parser = JsonBBCursive.withSIMD(alignedBuffer)
```