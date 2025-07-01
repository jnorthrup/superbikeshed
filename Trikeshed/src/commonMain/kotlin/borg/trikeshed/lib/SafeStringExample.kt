package borg.trikeshed.lib

/**
 * Example demonstrating how CharIndexed provides safe sliced strings without String class overhead
 * 
 * Key benefits:
 * 1. Zero-copy string operations
 * 2. No String allocation for protocol parsing
 * 3. Safe bounds checking
 * 4. Direct CharIndexed views into underlying data
 */
object SafeStringExample {
    
    /**
     * Example: HTTP header parsing without String allocation
     */
    fun parseHttpHeader(headerData: CharIndexed): HttpHeader {
        val buffer = headerData.toCharIndexedBuffer()
        
        // Parse method (GET, POST, etc.) - returns CharIndexed view, no String
        val methodView = buffer.substring(0, buffer.seekTo(' '))
        
        // Parse path - returns CharIndexed view, no String  
        buffer.seekTo(' ')
        val pathView = buffer.substring(0, buffer.seekTo(' '))
        
        // Parse version - returns CharIndexed view, no String
        buffer.seekTo(' ')
        val versionView = buffer.substring(0, buffer.seekTo('\n'))
        
        return HttpHeader(methodView, pathView, versionView)
    }
    
    /**
     * Example: Protocol token extraction with safe slicing
     */
    fun extractTokens(data: CharIndexed): Indexed<CharIndexed> {
        val buffer = data.toCharIndexedBuffer()
        
        // Split by space delimiter - returns CharIndexed views, no Strings
        return buffer.splitView(' ')
    }
    
    /**
     * Example: Safe string comparison without String allocation
     */
    fun isHttpRequest(data: CharIndexed): Boolean {
        val buffer = data.toCharIndexedBuffer()
        
        // Compare with "GET" without creating String
        val getMethod = "GET".toIndexed()
        return buffer.startsWith(getMethod)
    }
    
    /**
     * Example: Trimming whitespace without String allocation
     */
    fun trimWhitespace(data: CharIndexed): CharIndexed {
        val buffer = data.toCharIndexedBuffer()
        
        // Returns CharIndexed view with whitespace removed, no String allocation
        return buffer.trimmedView
    }
}

/**
 * HTTP Header using CharIndexed views instead of Strings
 */
data class HttpHeader(
    val method: CharIndexed,    // View into original data, no String allocation
    val path: CharIndexed,      // View into original data, no String allocation  
    val version: CharIndexed    // View into original data, no String allocation
) {
    /**
     * Convert to String only when absolutely necessary (e.g., for logging)
     */
    fun toDebugString(): String = 
        "HttpHeader(method=${method.toArray().joinToString("")}, " +
        "path=${path.toArray().joinToString("")}, " +
        "version=${version.toArray().joinToString("")})"
}

/**
 * Extension functions for safe string operations
 */

fun CharIndexed.substring(start: Int, end: Int): CharIndexed {
    val safeStart = start.coerceIn(0, a)
    val safeEnd = end.coerceIn(safeStart, a)
    return (safeEnd - safeStart) j { i -> b(safeStart + i) }
}

fun CharIndexed.seekTo(target: Char): Int {
    for (i in 0 until a) {
        if (b(i) == target) return i
    }
    return a
}

fun CharIndexed.startsWith(prefix: CharIndexed): Boolean {
    if (prefix.a > a) return false
    for (i in 0 until prefix.a) {
        if (b(i) != prefix[i]) return false
    }
    return true
}

fun CharIndexed.splitView(delimiter: Char): Indexed<CharIndexed> {
    val positions = mutableListOf<Int>()
    
    // Find all delimiter positions
    for (i in 0 until a) {
        if (b(i) == delimiter) positions.add(i)
    }
    
    // Create views for each segment
    return (positions.size + 1) j { segmentIndex ->
        val segmentStart = if (segmentIndex == 0) 0 else positions[segmentIndex - 1] + 1
        val segmentEnd = if (segmentIndex == positions.size) a else positions[segmentIndex]
        (segmentEnd - segmentStart) j { i -> b(segmentStart + i) }
    }
}

val CharIndexed.trimmedView: CharIndexed
    get() {
        var start = 0
        var end = a
        
        // Trim leading whitespace
        while (start < end && b(start).isWhitespace()) start++
        
        // Trim trailing whitespace
        while (end > start && b(end - 1).isWhitespace()) end--
        
        return (end - start) j { i -> b(start + i) }
    } 