package borg.trikeshed.lib

/**
 * HTTP header enum following RelaxFactory's patterns:
 * - Encoded enum names with $ for special characters
 * - Pre-computed byte arrays for fast scanning
 * - Zero-allocation header recognition
 */
enum class HttpHeaders(private val encoded: String) {
    // Standard headers with encoded names
    Accept("Accept"),
    `Accept-Charset`("Accept-Charset"),
    `Accept-Encoding`("Accept-Encoding"),
    `Accept-Language`("Accept-Language"),
    `Accept-Ranges`("Accept-Ranges"),
    `Access-Control-Allow-Credentials`("Access-Control-Allow-Credentials"),
    `Access-Control-Allow-Headers`("Access-Control-Allow-Headers"),
    `Access-Control-Allow-Methods`("Access-Control-Allow-Methods"),
    `Access-Control-Allow-Origin`("Access-Control-Allow-Origin"),
    `Access-Control-Expose-Headers`("Access-Control-Expose-Headers"),
    `Access-Control-Max-Age`("Access-Control-Max-Age"),
    `Access-Control-Request-Headers`("Access-Control-Request-Headers"),
    `Access-Control-Request-Method`("Access-Control-Request-Method"),
    Age("Age"),
    Allow("Allow"),
    Authorization("Authorization"),
    `Cache-Control`("Cache-Control"),
    Connection("Connection"),
    `Content-Disposition`("Content-Disposition"),
    `Content-Encoding`("Content-Encoding"),
    `Content-Language`("Content-Language"),
    `Content-Length`("Content-Length"),
    `Content-Location`("Content-Location"),
    `Content-MD5`("Content-MD5"),
    `Content-Range`("Content-Range"),
    `Content-Type`("Content-Type"),
    Cookie("Cookie"),
    Date("Date"),
    ETag("ETag"),
    Expect("Expect"),
    Expires("Expires"),
    From("From"),
    Host("Host"),
    `If-Match`("If-Match"),
    `If-Modified-Since`("If-Modified-Since"),
    `If-None-Match`("If-None-Match"),
    `If-Range`("If-Range"),
    `If-Unmodified-Since`("If-Unmodified-Since"),
    `Last-Modified`("Last-Modified"),
    Location("Location"),
    `Max-Forwards`("Max-Forwards"),
    Origin("Origin"),
    Pragma("Pragma"),
    `Proxy-Authenticate`("Proxy-Authenticate"),
    `Proxy-Authorization`("Proxy-Authorization"),
    Range("Range"),
    Referer("Referer"),
    `Retry-After`("Retry-After"),
    Server("Server"),
    `Set-Cookie`("Set-Cookie"),
    TE("TE"),
    Trailer("Trailer"),
    `Transfer-Encoding`("Transfer-Encoding"),
    Upgrade("Upgrade"),
    `User-Agent`("User-Agent"),
    Vary("Vary"),
    Via("Via"),
    Warning("Warning"),
    `WWW-Authenticate`("WWW-Authenticate"),
    `X-Forwarded-For`("X-Forwarded-For");
    

    // Convert encoded name back to actual header name
    val header: String = encoded.replace("-", "-")
    
    // Pre-computed token bytes for fast scanning
    val token: ByteArray by lazy { header.encodeToByteArray() }
    
    // Token length for efficient bounds checking
    val tokenLen: Int get() = token.size
    
    /**
     * Check if this header is present at the given buffer position.
     * Returns true if the header name matches (case-sensitive) and is followed by ':'
     */
    fun recognize(buffer: ByteArray, offset: Int): Boolean {
        if (offset + tokenLen + 1 > buffer.size) return false
        if (buffer[offset + tokenLen] != ':'.code.toByte()) return false
        
        for (i in 0 until tokenLen) {
            if (token[i] != buffer[offset + i]) return false
        }
        return true
    }
    
    /**
     * Parse header value and return start/end indices in the buffer.
     * Returns null if header not recognized at this position.
     */
    fun parse(buffer: ByteArray, offset: Int): Pair<Int, Int>? {
        if (!recognize(buffer, offset)) return null
        
        // Skip past header name and colon
        var start = offset + tokenLen + 1
        while (start < buffer.size && buffer[start] == ' '.code.toByte()) start++
        
        // Find end of value (before CRLF)
        var end = start
        while (end < buffer.size && buffer[end] != '\r'.code.toByte() && buffer[end] != '\n'.code.toByte()) {
            end++
        }
        
        // Trim trailing whitespace
        while (end > start && buffer[end - 1] == ' '.code.toByte()) end--
        
        return Pair(start, end)
    }
    
    companion object {
        /**
         * Get all headers from a buffer, returning a map of header names to
         * value slice indices (start, end) for zero-allocation access.
         */
        fun getHeaders(buffer: ByteArray, limit: Int): Map<String, IntArray> {
            val headers = LinkedHashMap<String, IntArray>()
            var pos = 0
            
            // Skip request/status line
            while (pos < limit && buffer[pos] != '\n'.code.toByte()) pos++
            pos++ // Skip LF
            
            // Parse headers until empty line
            while (pos < limit) {
                val lineStart = pos
                
                // Find colon
                var colonPos = pos
                while (colonPos < limit && buffer[colonPos] != ':'.code.toByte()) {
                    if (buffer[colonPos] == '\r'.code.toByte() || buffer[colonPos] == '\n'.code.toByte()) {
                        // Empty line - end of headers
                        return headers
                    }
                    colonPos++
                }
                
                if (colonPos >= limit) break
                
                // Extract header name
                val headerName = buffer.decodeToString(pos, colonPos).trim()
                
                // Skip colon and whitespace
                pos = colonPos + 1
                while (pos < limit && buffer[pos] == ' '.code.toByte()) pos++
                
                val valueStart = pos
                
                // Find end of line
                while (pos < limit && buffer[pos] != '\r'.code.toByte() && buffer[pos] != '\n'.code.toByte()) {
                    pos++
                }
                
                val valueEnd = pos
                
                // Store header value indices
                headers[headerName] = intArrayOf(valueStart, valueEnd)
                
                // Skip CRLF
                if (pos < limit && buffer[pos] == '\r'.code.toByte()) pos++
                if (pos < limit && buffer[pos] == '\n'.code.toByte()) pos++
                
                // Check for end of headers (empty line)
                if (pos < limit && (buffer[pos] == '\r'.code.toByte() || buffer[pos] == '\n'.code.toByte())) {
                    break
                }
            }
            
            return headers
        }
    }
}