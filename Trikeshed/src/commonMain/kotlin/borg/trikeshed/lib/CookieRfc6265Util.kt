package borg.trikeshed.lib

import kotlin.jvm.JvmInline

/**
 * RFC 6265 cookie parsing following RelaxFactory's zero-allocation patterns.
 * This utility works with byte arrays and returns index positions instead of creating string objects.
 */
object CookieRfc6265Util {

    @JvmInline
    value class CookieName(val value: String)

    @JvmInline
    value class CookieValue(val value: String)

    /**
     * Parses a "Cookie" header value (e.g., "name1=value1; name2=value2")
     * and returns a map of cookie names to their value indices in the buffer.
     *
     * @param buffer The byte array containing the HTTP header.
     * @param offset The starting offset of the cookie header value in the buffer.
     * @param limit The ending limit of the cookie header value in the buffer.
     * @return A map where keys are cookie names (String) and values are IntArray of [start, end] indices
     *         within the buffer for the cookie value.
     */
    fun parseCookieHeader(buffer: ByteArray, offset: Int, limit: Int): Map<String, IntArray> {
        val cookies = LinkedHashMap<String, IntArray>()
        var pos = offset

        while (pos < limit) {
            // Skip leading whitespace
            while (pos < limit && (buffer[pos] == ' '.code.toByte() || buffer[pos] == '\t'.code.toByte())) {
                pos++
            }

            if (pos >= limit) break

            // Find name start
            val nameStart = pos
            while (pos < limit && buffer[pos] != '='.code.toByte() && buffer[pos] != ';'.code.toByte()) {
                pos++
            }
            val nameEnd = pos

            if (nameStart == nameEnd) { // Empty name, skip
                while (pos < limit && buffer[pos] != ';'.code.toByte()) pos++
                if (pos < limit && buffer[pos] == ';'.code.toByte()) pos++
                continue
            }

            val cookieName = buffer.decodeToString(nameStart, nameEnd).trim()

            var valueStart = -1
            var valueEnd = -1

            if (pos < limit && buffer[pos] == '='.code.toByte()) {
                pos++ // Skip '='

                // Skip leading whitespace for value
                while (pos < limit && (buffer[pos] == ' '.code.toByte() || buffer[pos] == '\t'.code.toByte())) {
                    pos++
                }

                valueStart = pos
                while (pos < limit && buffer[pos] != ';'.code.toByte()) {
                    pos++
                }
                valueEnd = pos

                // Trim trailing whitespace for value
                while (valueEnd > valueStart && (buffer[valueEnd - 1] == ' '.code.toByte() || buffer[valueEnd - 1] == '\t'.code.toByte())) {
                    valueEnd--
                }
            }

            if (valueStart != -1 && valueEnd != -1) {
                cookies[cookieName] = intArrayOf(valueStart, valueEnd)
            } else {
                // Handle cases like "cookieName;" with no value
                cookies[cookieName] = intArrayOf(nameEnd, nameEnd) // Value is empty
            }

            // Skip ';'
            if (pos < limit && buffer[pos] == ';'.code.toByte()) {
                pos++
            }
        }
        return cookies
    }
}