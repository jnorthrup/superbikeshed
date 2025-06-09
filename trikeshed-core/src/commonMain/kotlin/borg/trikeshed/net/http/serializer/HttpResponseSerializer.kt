package borg.trikeshed.net.http.serializer

package borg.trikeshed.net.http.serializer // Ensure package declaration is present

import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.asString
import borg.trikeshed.foundation.common.series.size // Assuming Series.size property
import borg.trikeshed.foundation.common.series.toSeries // Assuming ByteArray.toSeries extension
// import borg.trikeshed.foundation.common.brandt.CoreTensorCursor // Not directly used if iterating via CoreTensorCursorWithMeta
import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta // Used for HttpHeaders type
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
        // response.headers is CoreTensorCursorWithMeta<String>
        // Each string in the cursor is expected to be a full "Name: Value" header line.
        // Assuming 'cursor' is the property to get the CoreTensorCursor<String>
        // or that CoreTensorCursorWithMeta can be iterated directly if it wraps a single Series.
        // For this change, let's assume response.headers.cursor gives a CoreTensorCursor<String>
        // where each row is a header line string in the first column.
        // This depends on the actual API of CoreTensorCursorWithMeta.
        // A common pattern might be: val actualCursor = response.headers.cursor
        // If response.headers *is* the cursor (e.g. typealias CoreTensorCursorWithMeta<T> = CoreTensorCursor<T>),
        // then it would be `response.headers.rows` and `response.headers[r,0]`.
        // Let's assume a hypothetical primary series/cursor access:
        val headersCoreCursor = response.headers.cursor // This is an assumption on CoreTensorCursorWithMeta structure
        for (r in 0 until headersCoreCursor.rows) {
            val headerLine = headersCoreCursor[r, 0] // Assuming header line is in the first column
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
                        byteList.add(body.data[i]) // Direct access for Series<Byte>
                    }
                }
            }
            is HttpBody.Text -> {
                // Convert Series<Char> to String, then to UTF-8 bytes
                val textContent = body.data.asString() // Uses foundation's Series<Char>.asString()
                byteList.addAll(textContent.encodeToByteArray().toList())
                // Still a placeholder if direct Series<Char> to UTF-8 Series<Byte> is desired
                println("HttpResponseSerializer: HttpBody.Text serialization uses String conversion (UTF-8 assumed).")
            }
            // TODO: Handle HttpBody.Streaming (e.g., for chunked encoding)
        }

        return byteList.toByteArray().toSeries() // Convert List<Byte> to ByteArray, then to Series<Byte>
    }
}
// Removed commented out asString() as it's expected from foundation
