@file:Suppress("NonAsciiCharacters") // For potential future use of symbols if desired

package borg.trikeshed.net.http.types

import kotlin.jvm.JvmInline
import borg.trikeshed.core.Join // For Join
import borg.trikeshed.core.Series // For Series (e.g. if HttpBody.Bytes used Series<Byte>)
import borg.trikeshed.core.Tensor // For HttpBody.Bytes using Tensor<Byte>
import borg.trikeshed.core.CoreTensorCursor // For HttpHeadersCursor
import borg.trikeshed.core.CoreTensorCursorWithMeta // For HttpHeaders (Join of cursor and meta)
import borg.trikeshed.core.CursorMeta // For HttpHeadersMeta
import borg.trikeshed.core.IOMemento // For HttpHeadersMeta ColumnMeta
import borg.trikeshed.core.ColumnMeta // For HttpHeadersMeta
import borg.trikeshed.core.j // For infix j constructor for Join

// --- Foundational Typealiases and Value Classes ---

@JvmInline
value class HttpVersion(val value: String) {
    companion object {
        val HTTP_1_0 = HttpVersion("HTTP/1.0")
        val HTTP_1_1 = HttpVersion("HTTP/1.1")
        val HTTP_2_0 = HttpVersion("HTTP/2.0")
    }
}

@JvmInline
value class HttpMethod(val name: String) {
    companion object {
        // Common HTTP methods (RFC 7231 & RFC 5789)
        val GET = HttpMethod("GET")
        val HEAD = HttpMethod("HEAD")
        val POST = HttpMethod("POST")
        val PUT = HttpMethod("PUT")
        val DELETE = HttpMethod("DELETE")
        val CONNECT = HttpMethod("CONNECT")
        val OPTIONS = HttpMethod("OPTIONS")
        val TRACE = HttpMethod("TRACE")
        val PATCH = HttpMethod("PATCH")
    }
}

@JvmInline
value class HttpRequestPath(val value: String) // Includes path and query string

@JvmInline
value class HttpStatusCode(val code: UShort) {
    // Informational 1xx
    fun isInformational(): Boolean = code in 100u..199u
    // Successful 2xx
    fun isSuccessful(): Boolean = code in 200u..299u
    // Redirection 3xx
    fun isRedirection(): Boolean = code in 300u..399u
    // Client Error 4xx
    fun isClientError(): Boolean = code in 400u..499u
    // Server Error 5xx
    fun isServerError(): Boolean = code in 500u..599u

    companion object {
        // Common Status Codes (non-exhaustive)
        val CONTINUE = HttpStatusCode(100u)
        val SWITCHING_PROTOCOLS = HttpStatusCode(101u)
        val OK = HttpStatusCode(200u)
        val CREATED = HttpStatusCode(201u)
        val ACCEPTED = HttpStatusCode(202u)
        val NO_CONTENT = HttpStatusCode(204u)
        val MOVED_PERMANENTLY = HttpStatusCode(301u)
        val FOUND = HttpStatusCode(302u)
        val SEE_OTHER = HttpStatusCode(303u)
        val NOT_MODIFIED = HttpStatusCode(304u)
        val BAD_REQUEST = HttpStatusCode(400u)
        val UNAUTHORIZED = HttpStatusCode(401u)
        val FORBIDDEN = HttpStatusCode(403u)
        val NOT_FOUND = HttpStatusCode(404u)
        val METHOD_NOT_ALLOWED = HttpStatusCode(405u)
        val INTERNAL_SERVER_ERROR = HttpStatusCode(500u)
        val NOT_IMPLEMENTED = HttpStatusCode(501u)
        val BAD_GATEWAY = HttpStatusCode(502u)
        val SERVICE_UNAVAILABLE = HttpStatusCode(503u)
    }
}

@JvmInline
value class HttpReasonPhrase(val value: String)

@JvmInline
value class HttpHeaderName(val name: String) {
    /** Returns a normalized (lowercase) version of the header name for case-insensitive comparisons. */
    fun normalized(): String = name.lowercase()
    // Consider if equals/hashCode should use normalized form, though JvmInline might handle this.
    // For maps, if this is a key, the map itself should handle case-insensitivity if needed,
    // or always store/lookup normalized names.
}

@JvmInline
value class HttpHeaderValue(val value: String)

// --- HTTP Headers Representation ---

/**
 * Metadata for HTTP Headers when represented as a `CoreTensorCursor<String>`.
 * It's a 1D Tensor of `ColumnMeta`, typically with two columns: "Name" and "Value".
 */
typealias HttpHeadersMeta = CursorMeta // Tensor<ColumnMeta> (rank 1)

/**
 * HTTP Headers represented as a `CoreTensorCursor<String>`.
 * Rows represent individual header lines, columns are typically "Name" and "Value".
 * This allows leveraging tensor operations for header manipulation if beneficial.
 */
typealias HttpHeadersCursor = CoreTensorCursor<String> // Tensor<String> (rank 2)

/**
 * HTTP Headers as a `CoreTensorCursorWithMeta<String>`, combining the data cursor and its metadata.
 * This is `Join<HttpHeadersCursor, HttpHeadersMeta>`.
 */
typealias HttpHeaders = CoreTensorCursorWithMeta<String>

/**
 * Factory function to create an empty `HttpHeadersMeta`.
 * Assumes header names are "Name" and "Value".
 */
fun emptyHttpHeadersMeta(): HttpHeadersMeta {
    val nameCol = HttpHeaderName("Name").name j IOMemento.IoString
    val valueCol = HttpHeaderName("Value").name j IOMemento.IoString
    return borg.trikeshed.core.TensorSeries(2) { i -> if (i == 0) nameCol else valueCol } // Use core.TensorSeries
}

/**
 * Factory function to create an empty `HttpHeadersCursor`.
 */
fun emptyHttpHeadersCursor(): HttpHeadersCursor =
    borg.trikeshed.core.TensorCursor(0, 2) { _, _ -> "" } // Use core.TensorCursor

/**
 * Factory function to create empty `HttpHeaders`.
 */
fun emptyHttpHeaders(): HttpHeaders =
    emptyHttpHeadersCursor() j emptyHttpHeadersMeta()

// --- HTTP Body Representation ---

/**
 * Represents the body of an HTTP message.
 * It can be empty, a Tensor of bytes (for binary data), or a Tensor of chars (for text).
 */
sealed interface HttpBody {
    data object Empty : HttpBody

    /** A body represented by a rank-1 `Tensor<Byte>`. */
    @JvmInline
    value class Bytes(val data: Tensor<Byte>) : HttpBody { // Ensure Tensor<Byte> is rank 1 for typical byte stream
        init { require(data.rank <= 1) { "HttpBody.Bytes data Tensor must be rank 0 or 1." } }
    }

    /** A body represented by a rank-1 `Tensor<Char>`. */
    @JvmInline
    value class Text(val data: Tensor<Char>) : HttpBody { // Ensure Tensor<Char> is rank 1
        init { require(data.rank <= 1) { "HttpBody.Text data Tensor must be rank 0 or 1." } }
    }
    // Consider:
    // data class Structured<T>(val data: CoreTensorCursor<T>, val meta: CursorMeta) : HttpBody
    // data class Streaming(val producer: Flow<Tensor<Byte>>) : HttpBody // For true streaming
}

// --- HTTP Request/Response Structures (using Join) ---

/**
 * Represents the request line: `METHOD Path HTTP-Version`.
 */
typealias HttpRequestLine = Join<HttpMethod, Join<HttpRequestPath, HttpVersion>>

/**
 * Represents the status line: `HTTP-Version StatusCode Reason-Phrase`.
 */
typealias HttpResponseLine = Join<HttpVersion, Join<HttpStatusCode, HttpReasonPhrase>>

/**
 * Represents a full HTTP Request message.
 * `Join<HttpRequestLine, Join<HttpHeaders, HttpBody>>`
 */
typealias HttpRequest = Join<HttpRequestLine, Join<HttpHeaders, HttpBody>>

/**
 * Represents a full HTTP Response message.
 * `Join<HttpResponseLine, Join<HttpHeaders, HttpBody>>`
 */
typealias HttpResponse = Join<HttpResponseLine, Join<HttpHeaders, HttpBody>>

// --- Utility functions for creating instances (examples) ---

fun HttpRequest(
    method: HttpMethod,
    path: HttpRequestPath,
    version: HttpVersion,
    headers: HttpHeaders,
    body: HttpBody
): HttpRequest {
    val requestLine = method j (path j version)
    return requestLine j (headers j body)
}

fun HttpResponse(
    version: HttpVersion,
    statusCode: HttpStatusCode,
    reasonPhrase: HttpReasonPhrase,
    headers: HttpHeaders,
    body: HttpBody
): HttpResponse {
    val statusLine = version j (statusCode j reasonPhrase)
    return statusLine j (headers j body)
}
