package borg.trikeshed.net.http.serializer

import borg.trikeshed.core.Series
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.asString // For Series<Char>.asString() if used for HttpBody.Text
import borg.trikeshed.core.get // For Tensor access
import borg.trikeshed.core.plus // For Series concatenation
import borg.trikeshed.core.size // For Series/Tensor size
import borg.trikeshed.core.toSeries // For ByteArray.toSeries()
import borg.trikeshed.net.http.types.* // All our Http types

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
        val (version, status) = response.a // HttpResponseLine = Join<HttpVersion, Join<HttpStatusCode, HttpReasonPhrase>>
        val (statusCode, reasonPhrase) = status

        byteList.addAll(version.value.encodeToByteArray().toList())
        byteList.addAll(SP.toList())
        byteList.addAll(statusCode.code.toString().encodeToByteArray().toList())
        byteList.addAll(SP.toList())
        byteList.addAll(reasonPhrase.value.encodeToByteArray().toList())
        byteList.addAll(CRLF.toList())

        // 2. Headers
        val (headersCursor, headersMeta) = response.b.a // HttpHeaders = Join<HttpHeadersCursor, HttpHeadersMeta>
        // Assuming headersCursor is CoreTensorCursor<String> with 2 columns: Name, Value
        for (r in 0 until headersCursor.rows) {
            val headerName = headersCursor[r, 0]  // Accessing name from tensor
            val headerValue = headersCursor[r, 1] // Accessing value from tensor

            byteList.addAll(headerName.encodeToByteArray().toList())
            byteList.addAll(COLON_SP.toList())
            byteList.addAll(headerValue.encodeToByteArray().toList())
            byteList.addAll(CRLF.toList())
        }

        // End of headers
        byteList.addAll(CRLF.toList())

        // 3. Body
        val body = response.b.b // HttpBody
        when (body) {
            is HttpBody.Empty -> {
                // No body to append
            }
            is HttpBody.Bytes -> {
                // Append raw bytes from Tensor<Byte>
                // Assuming Tensor<Byte> is rank 1
                if (body.data.rank > 1 && !(body.data.rank ==1 && body.data.shape[0] == 0) && body.data.totalSize > 0) { // check for empty tensor
                    // This check is a bit off. Rank 0 is a scalar, rank 1 is a vector.
                    // totalSize > 0 covers empty rank 1 tensors.
                    // If rank is 0, and totalSize is 1, it's a single byte.
                    // If rank is 1, shape[0] is its length.
                    if (body.data.rank == 1) {
                        for (i in 0 until body.data.shape[0]) {
                            byteList.add(body.data[intArrayOf(i)]) // Tensor<Byte>[index]
                        }
                    } else if (body.data.rank == 0 && body.data.totalSize == 1) { // Scalar byte tensor
                         byteList.add(body.data[intArrayOf()])
                    } else if (body.data.totalSize > 0) { // Higher rank tensor, flatten conceptually
                        // This simple flattening might not be what's intended for multi-dim byte tensors as HTTP body.
                        // Usually, HTTP body is a linear stream of bytes.
                        // For now, iterate through all elements if higher rank.
                        // A better approach would be to require HttpBody.Bytes to always hold a rank-1 Tensor.
                        // The HttpBody.Bytes value class now enforces rank <= 1.
                        // This part of the code should not be reached if that validation holds.
                        throw HttpResponseSerializationException("HttpBody.Bytes data Tensor has unexpected rank: \${body.data.rank}")
                    }
                }
            }
            is HttpBody.Text -> {
                // TODO: Convert Tensor<Char> to UTF-8 Tensor<Byte> then serialize
                // For now, placeholder:
                val textContent = body.data.shape.takeIf { it.isNotEmpty() && it[0] > 0 }?.let { shape ->
                    (0 until shape[0]).map { body.data[intArrayOf(it)] }.joinToString("")
                } ?: ""
                byteList.addAll(textContent.encodeToByteArray().toList())
                 println("HttpResponseSerializer: HttpBody.Text serialization is a placeholder (UTF-8 assumed).")
            }
            // TODO: Handle HttpBody.Streaming (e.g., for chunked encoding)
        }

        return byteList.toByteArray().toSeries() // Convert List<Byte> to ByteArray, then to Series<Byte>
    }
}

// --- Helper to convert Series<Char> to String (if not already available/efficient in core) ---
// This is already in core as an extension: Series<Char>.asString()
// internal fun Series<Char>.asString(): String = buildString {
//    for (i in 0 until this@asString.size) {
//        append(this@asString[i])
//    }
// }
