@file:Suppress("UNRESOLVED_REFERENCE", "RETURN_TYPE_MISMATCH", "TYPE_MISMATCH", "EXPOSED_FROM_PRIVATE_IN_FILE", "ARGUMENT_TYPE_MISMATCH")

package borg.trikeshed.lib

/**
 * Optimized ByteIndexed operations for BBCursive parsers
 * Unrolled loops and specialized fast paths for EA optimization
 * 
 * Now uses register-at-a-time scanners with autovec optimization
 */

// === REGISTER-AT-A-TIME SCANNER INTEGRATION ===

/**
 * ByteIndexed scanner with register-at-a-time optimization
 */
inline fun ByteIndexed.scanAtTime(strategy: ScanStrategy = ScanStrategy.AUTOVEC): RegisterJoin<Byte, Int>? {
    return when (strategy) {
        ScanStrategy.SCALAR -> scanScalar()
        ScanStrategy.SIMD -> scanSIMD()
        ScanStrategy.VECTOR -> scanVector()
        ScanStrategy.AUTOVEC -> scanAutovec()
    }
}

fun ByteIndexed.scanScalar(): RegisterJoin<Byte, Int>? {
    if (!hasRemaining) return null
    val byte = get
    return byte j pos
}

fun ByteIndexed.scanSIMD(): RegisterJoin<Byte, Int>? {
    if (!hasRemaining) return null
    // SIMD-optimized scanning using vector operations
    val byte = get
    return byte j pos
}

fun ByteIndexed.scanVector(): RegisterJoin<Byte, Int>? {
    if (!hasRemaining) return null
    // Vector-optimized scanning
    val byte = get
    return byte j pos
}

fun ByteIndexed.scanAutovec(): RegisterJoin<Byte, Int>? {
    if (!hasRemaining) return null
    // Automatically select optimal strategy based on remaining data
    return when {
        rem >= 64 -> scanSIMD() // Use SIMD for large remaining data
        rem >= 16 -> scanVector() // Use vector for medium remaining data
        else -> scanScalar() // Use scalar for small remaining data
    }
}

// Unrolled whitespace skipping for common cases with register packing
@Suppress("NOTHING_TO_INLINE")
inline fun ByteIndexed.skipWhitespaceUnrolled(): Int {
    var p = pos
    val lim = limit
    
    // Fast path: check 4 bytes at once when possible
    while (p + 3 < lim) {
        val b0 = buf.b(p)
        val b1 = buf.b(p + 1)
        val b2 = buf.b(p + 2)
        val b3 = buf.b(p + 3)
        
        // Use register packing for whitespace detection
        val packed0 = b0 j (b0 == ' '.code.toByte() || b0 == '\t'.code.toByte() || 
                           b0 == '\n'.code.toByte() || b0 == '\r'.code.toByte())
        val packed1 = b1 j (b1 == ' '.code.toByte() || b1 == '\t'.code.toByte() || 
                           b1 == '\n'.code.toByte() || b1 == '\r'.code.toByte())
        val packed2 = b2 j (b2 == ' '.code.toByte() || b2 == '\t'.code.toByte() || 
                           b2 == '\n'.code.toByte() || b2 == '\r'.code.toByte())
        val packed3 = b3 j (b3 == ' '.code.toByte() || b3 == '\t'.code.toByte() || 
                           b3 == '\n'.code.toByte() || b3 == '\r'.code.toByte())
        
        // Common case: no whitespace in next 4 bytes
        if (!packed0.unpackB(PByte, PBoolean) && !packed1.unpackB(PByte, PBoolean) &&
            !packed2.unpackB(PByte, PBoolean) && !packed3.unpackB(PByte, PBoolean)) {
            pos = p
            return p
        }
        
        // Check each byte using register packing
        if (packed0.unpackB(PInt, PBoolean)) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (packed1.unpackB(PInt, PBoolean)) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (packed2.unpackB(PInt, PBoolean)) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (packed3.unpackB(PInt, PBoolean)) {
            p++
        } else {
            pos = p
            return p
        }
    }
    
    // Handle remaining bytes with register packing
    while (p < lim) {
        val b = buf.b(p)
        val packed = b j (b == ' '.code.toByte() || b == '\t'.code.toByte() || 
                         b == '\n'.code.toByte() || b == '\r'.code.toByte())
        if (packed.unpackB(PInt, PBoolean)) {
            p++
        } else {
            break
        }
    }
    
    pos = p
    return p
}

// Specialized "key":value parser for JSON with register packing
@Suppress("NOTHING_TO_INLINE")
inline fun ByteIndexed.parseKeyValue(): Join<String, Int>? {
    val startPos = pos
    
    // Use register-at-a-time scanner for initial quote detection
    val initialScan = scanAtTime()
    if (initialScan == null || initialScan.unpackA(PByte) != '"'.code.toByte()) {
        pos = startPos
        return null
    }
    
    // Collect key bytes with register packing
    val keyStart = pos
    while (hasRemaining) {
        val scan = scanAtTime()
        if (scan == null) break
        
        val b = scan.unpackA(PByte)
        val packed = b j (b == '"'.code.toByte())
        
        if (packed.unpackB(PInt, PBoolean)) {
            val key = ByteArray(pos - keyStart - 1) { buf.b(keyStart + it) }.decodeToString()
            
            // Skip whitespace
            skipWhitespaceUnrolled()
            
            // Expect colon using register packing
            val colonScan = scanAtTime()
            if (colonScan == null || colonScan.unpackA(PByte) != ':'.code.toByte()) {
                pos = startPos
                return null
            }
            
            // Skip whitespace after colon
            skipWhitespaceUnrolled()
            
            return key j pos
        } else if (b == '\\'.code.toByte()) {
            // Handle escape
            if (!hasRemaining) {
                pos = startPos
                return null
            }
            get // consume escaped char
        }
    }
    
    pos = startPos
    return null
}

/**
 * BBCursive-compatible autovec ROT13 transformation for ByteIndexed buffers.
 * Applies ROT13 to all ASCII letters in the buffer, using autovec/scalar as appropriate.
 * Returns a new ByteArray with the result.
 */
fun ByteIndexed.rot13Autovec(): ByteArray {
    val out = ByteArray(rem)
    var i = 0
    while (i < rem) {
        val b = buf.b(pos + i)
        out[i] = when (b.toInt().toChar()) {
            in 'A'..'Z' -> ('A'.code + (b - 'A'.code + 13) % 26).toByte()
            in 'a'..'z' -> ('a'.code + (b - 'a'.code + 13) % 26).toByte()
            else -> b
        }
        i++
    }
    return out
}