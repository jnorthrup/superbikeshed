package borg.trikeshed.lib

/**
 * Optimized ByteIndexed operations for BBCursive parsers
 * Unrolled loops and specialized fast paths for EA optimization
 */

// Unrolled whitespace skipping for common cases
@kotlin.internal.InlineOnly
inline fun ByteIndexed.skipWhitespaceUnrolled(): Int {
    var p = pos
    val lim = limit
    
    // Fast path: check 4 bytes at once when possible
    while (p + 3 < lim) {
        val b0 = buf.b(p)
        val b1 = buf.b(p + 1)
        val b2 = buf.b(p + 2)
        val b3 = buf.b(p + 3)
        
        // Common case: no whitespace in next 4 bytes
        if (b0 != ' '.code.toByte() && b0 != '\t'.code.toByte() && 
            b0 != '\n'.code.toByte() && b0 != '\r'.code.toByte() &&
            b1 != ' '.code.toByte() && b1 != '\t'.code.toByte() && 
            b1 != '\n'.code.toByte() && b1 != '\r'.code.toByte() &&
            b2 != ' '.code.toByte() && b2 != '\t'.code.toByte() && 
            b2 != '\n'.code.toByte() && b2 != '\r'.code.toByte() &&
            b3 != ' '.code.toByte() && b3 != '\t'.code.toByte() && 
            b3 != '\n'.code.toByte() && b3 != '\r'.code.toByte()) {
            pos = p
            return p
        }
        
        // Check each byte
        if (b0 == ' '.code.toByte() || b0 == '\t'.code.toByte() || 
            b0 == '\n'.code.toByte() || b0 == '\r'.code.toByte()) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (b1 == ' '.code.toByte() || b1 == '\t'.code.toByte() || 
            b1 == '\n'.code.toByte() || b1 == '\r'.code.toByte()) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (b2 == ' '.code.toByte() || b2 == '\t'.code.toByte() || 
            b2 == '\n'.code.toByte() || b2 == '\r'.code.toByte()) {
            p++
        } else {
            pos = p
            return p
        }
        
        if (b3 == ' '.code.toByte() || b3 == '\t'.code.toByte() || 
            b3 == '\n'.code.toByte() || b3 == '\r'.code.toByte()) {
            p++
        } else {
            pos = p
            return p
        }
    }
    
    // Handle remaining bytes
    while (p < lim) {
        val b = buf.b(p)
        if (b == ' '.code.toByte() || b == '\t'.code.toByte() || 
            b == '\n'.code.toByte() || b == '\r'.code.toByte()) {
            p++
        } else {
            break
        }
    }
    
    pos = p
    return p
}

// Specialized "key":value parser for JSON
@kotlin.internal.InlineOnly
inline fun ByteIndexed.parseKeyValue(): Join<String, Int>? {
    val startPos = pos
    
    // Expect opening quote
    if (!hasRemaining || get != '"'.code.toByte()) {
        pos = startPos
        return null
    }
    
    // Collect key bytes
    val keyStart = pos
    while (hasRemaining) {
        val b = get
        if (b == '"'.code.toByte()) {
            val key = String(ByteArray(pos - keyStart - 1) { buf.b(keyStart + it) })
            
            // Skip whitespace
            skipWhitespaceUnrolled()
            
            // Expect colon
            if (!hasRemaining || get != ':'.code.toByte()) {
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