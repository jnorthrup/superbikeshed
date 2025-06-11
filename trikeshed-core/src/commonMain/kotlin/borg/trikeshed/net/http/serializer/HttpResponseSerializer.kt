package borg.trikeshed.net.http.serializer

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.asString
import borg.trikeshed.lib.size
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.CoreTensorCursorWithMeta
import borg.trikeshed.net.http.types.*

// --- HttpResponseSerializationException ---
class HttpResponseSerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Serializes an HttpResponse object into a byte stream (Series<Byte>) for network transmission.
 */
class HttpResponseSerializer {

    private companion object {
        val CRLF = byteArrayOf(0x0D, 0x0A) // CR LF
        val SP = byteArrayOf(0x20)         // Space
        val COLON_SP = byteArrayOf(0x3A, 0x20) // ": "
    }

    /**
     * Serializes the given HttpResponse into a Series<Byte>.
     *
     * @param response The HttpResponse object to serialize.
     * @return A Series<Byte> representing the serialized HTTP response.
     * @throws HttpResponseSerializationException if an error occurs during serialization.
     */
    fun serialize(response: HttpResponse): Series<Byte> {
        val byteList = mutableListOf<Byte>()

        // 1. Status Line: HTTP-Version SP StatusCode SP ReasonPhrase CRLF
        byteList.addAll(response.version.value.encodeToByteArray().toList())
        byteList.addAll(SP.toList())
        byteList.addAll(response.statusCode.value.toString().encodeToByteArray().toList())
        byteList.addAll(SP.toList())
        byteList.addAll(response.reasonPhrase.value.encodeToByteArray().toList())
        byteList.addAll(CRLF.toList())

        // 2. Headers
        val headersCoreCursor = response.headers.cursor
        for (r in 0 until headersCoreCursor.rows) {
            val headerLine = headersCoreCursor[r, 0]
            byteList.addAll(headerLine.encodeToByteArray().toList())
            byteList.addAll(CRLF.toList())
        }

        // End of headers
        byteList.addAll(CRLF.toList())

        // 3. Body
        when (val body = response.body) {
            is HttpBody.Empty -> {
                // No body to append
            }
            is HttpBody.Bytes -> {
                // Append raw bytes from Series<Byte>
                if (body.data.size > 0) {
                    for (i in 0 until body.data.size) {
                        byteList.add(body.data[i])
                    }
                }
            }
            is HttpBody.Text -> {
                // Convert Series<Char> to String, then to UTF-8 bytes
                val textContent = body.data.asString()
                byteList.addAll(textContent.encodeToByteArray().toList())
                println("HttpResponseSerializer: HttpBody.Text serialization uses String conversion (UTF-8 assumed).")
            }
            // TODO: Handle HttpBody.Streaming (e.g., for chunked encoding)
        }

        return byteList.toByteArray().toSeries()
    }
}
