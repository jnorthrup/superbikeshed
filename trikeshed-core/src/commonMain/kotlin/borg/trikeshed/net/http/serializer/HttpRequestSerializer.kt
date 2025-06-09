package borg.trikeshed.net.http.serializer

import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.asString
import borg.trikeshed.foundation.common.series.plus
import borg.trikeshed.foundation.common.series.toByteArray
import borg.trikeshed.foundation.common.series.toSeries
import borg.trikeshed.net.http.types.* // All our Http types

// --- HttpRequestSerializationException ---
class HttpRequestSerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Serializes an HttpRequest object into a byte stream (Series<Byte>) for network transmission.
 */
class HttpRequestSerializer {

    private companion object {
        val CRLF = byteArrayOf(0x0D, 0x0A) // CR LF
        val SP = byteArrayOf(0x20)         // Space
        // val COLON_SP = byteArrayOf(0x3A, 0x20) // ": " // Not needed if headers are pre-formatted
    }

    /**
     * Serializes the given HttpRequest into a Series<Byte>.
     *
     * @param request The HttpRequest object to serialize.
     * @return A Series<Byte> representing the serialized HTTP request.
     * @throws HttpRequestSerializationException if an error occurs during serialization.
     */
    fun serialize(request: HttpRequest): Series<Byte> {
        val byteList = mutableListOf<Byte>()

        // 1. Request Line: Method SP Path SP Version CRLF
        byteList.addAll(request.method.name.encodeToByteArray().toList()) // HttpMethod is an enum
        byteList.addAll(SP.toList())
        byteList.addAll(request.path.value.encodeToByteArray().toList())
        byteList.addAll(SP.toList())
        byteList.addAll(request.version.value.encodeToByteArray().toList())
        byteList.addAll(CRLF.toList())

        // 2. Headers
        // Assuming request.headers (CoreTensorCursorWithMeta<String>) contains
        // pre-formatted "Name: Value" strings in its cursor.
        val headersCoreCursor = request.headers.cursor // Adjust if property name is different
        for (r in 0 until headersCoreCursor.rows) {
            val headerLine = headersCoreCursor[r, 0] // Assuming single column of "Name: Value" strings
            byteList.addAll(headerLine.encodeToByteArray().toList())
            byteList.addAll(CRLF.toList())
        }

        // End of headers
        byteList.addAll(CRLF.toList())

        // 3. Body
        when (val body = request.body) {
            is HttpBody.Empty -> {
                // No body to append. Ensure Content-Length: 0 was set if method expects a body.
            }
            is HttpBody.Bytes -> {
                if (body.data.size > 0) {
                    for (i in 0 until body.data.size) {
                        byteList.add(body.data[i])
                    }
                }
            }
            is HttpBody.Text -> {
                // Convert Series<Char> to String, then to UTF-8 bytes
                val textContent = body.data.asString() // Uses foundation's Series<Char>.asString()
                byteList.addAll(textContent.encodeToByteArray().toList())
            }
        }

        return byteList.toByteArray().toSeries() // Convert List<Byte> to ByteArray, then to Series<Byte>
    }
}
