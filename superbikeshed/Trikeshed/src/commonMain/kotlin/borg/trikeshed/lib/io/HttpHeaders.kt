package borg.trikeshed.lib

/**
 * HTTP header enum following RelaxFactory's patterns:
 * - Encoded enum names with $ for special characters
 * - Pre-computed byte arrays for fast scanning
 * - Zero-allocation header recognition
 */
enum class HttpHeaders(private val encoded: String) {
    // Standard headers with encoded names
    `Accept`("Accept"),
    `Accept$2dCharset`("Accept-Charset"),
    `Accept$2dEncoding`("Accept-Encoding"),
    `Accept$2dLanguage`("Accept-Language"),
    `Accept$2dRanges`("Accept-Ranges"),
    `Access$2dControl$2dAllow$2dCredentials`("Access-Control-Allow-Credentials"),
    `Access$2dControl$2dAllow$2dHeaders`("Access-Control-Allow-Headers"),
    `Access$2dControl$2dAllow$2dMethods`("Access-Control-Allow-Methods"),
    `Access$2dControl$2dAllow$2dOrigin`("Access-Control-Allow-Origin"),
    `Access$2dControl$2dExpose$2dHeaders`("Access-Control-Expose-Headers"),
    `Access$2dControl$2dMax$2dAge`("Access-Control-Max-Age"),
    `Access$2dControl$2dRequest$2dHeaders`("Access-Control-Request-Headers"),
    `Access$2dControl$2dRequest$2dMethod`("Access-Control-Request-Method"),
    `Age`("Age"),
    `Allow`("Allow"),
    `Authorization`("Authorization"),
    `Cache$2dControl`("Cache-Control"),
    `Connection`("Connection"),
    `Content$2dDisposition`("Content-Disposition"),
    `Content$2dEncoding`("Content-Encoding"),
    `Content$2dLanguage`("Content-Language"),
    `Content$2dLength`("Content-Length"),
    `Content$2dLocation`("Content-Location"),
    `Content$2dMD5`("Content-MD5"),
    `Content$2dRange`("Content-Range"),
    `Content$2dType`("Content-Type"),
    `Cookie`("Cookie"),
    `Date`("Date"),
    `ETag`("ETag"),
    `Expect`("Expect"),
    `Expires`("Expires"),
    `From`("From"),
    `Host`("Host"),
    `If$2dMatch`("If-Match"),
    `If$2dModified$2dSince`("If-Modified-Since"),
    `If$2dNone$2dMatch`("If-None-Match"),
    `If$2dRange`("If-Range"),
    `If$2dUnmodified$2dSince`("If-Unmodified-Since"),
    `Last$2dModified`("Last-Modified"),
    `Location`("Location"),
    `Max$2dForwards`("Max-Forwards"),
    `Origin`("Origin"),
    `Pragma`("Pragma"),
    `Proxy$2dAuthenticate`("Proxy-Authenticate"),
    `Proxy$2dAuthorization`("Proxy-Authorization"),
    `Range`("Range"),
    `Referer`("Referer"),
    `Retry$2dAfter`("Retry-After"),
    `Server`("Server"),
    `Set$2dCookie`("Set-Cookie"),
    `TE`("TE"),
    `Trailer`("Trailer"),
    `Transfer$2dEncoding`("Transfer-Encoding"),
    `Upgrade`("Upgrade"),
    `User$2dAgent`("User-Agent"),
    `Vary`("Vary"),
    `Via`("Via"),
    `Warning`("Warning"),
    `WWW$2dAuthenticate`("WWW-Authenticate"),
    `X$2dForwarded$2dFor`("X-Forwarded-For");

    // Convert encoded name back to actual header name
    val header: String = encoded.replace("$2d", "-")

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