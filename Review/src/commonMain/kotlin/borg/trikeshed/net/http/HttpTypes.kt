package borg.trikeshed.net.http

import borg.trikeshed.core.Join
import borg.trikeshed.core.Series

typealias HttpAuthority = String              // Host:port authority
typealias HttpContentLength = ULong           // Content-Length header value
typealias HttpContentType = String            // Content-Type header value
typealias HttpHeader = Join<HttpHeaderName, HttpHeaderValue> // Remains from previous for now
typealias HttpHeaderName = String             // Header field name
// typealias HttpHeaders = Series<HttpHeader> // Replaced by simple Map typealias for now
typealias HttpHeaders = Map<String, List<String>> // Simplified as per current subtask instruction
typealias HttpHeaderValue = String            // Header field value

/**
 * Standard HTTP methods.
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, CONNECT
}
// typealias HttpMethod = String // Replaced by enum

typealias HttpReasonPhrase = String           // Status reason phrase
// HTTP request/response compositions
// typealias HttpRequestLine = Join<HttpMethod, Join<HttpRequestPath, HttpScheme>> // Will be part of HttpRequest
// Request/response structure
typealias HttpRequestPath = String            // Request target path
// typealias HttpResponseLine = Join<HttpStatusCode, HttpReasonPhrase> // Will be part of HttpResponse
typealias HttpScheme = String                 // http, https
typealias HttpStatusCode = UShort             // 1xx-5xx response codes
typealias HttpUserAgent = String              // User-Agent header value


// --- New Data Structures for QuicCurl ---

/**
 * Represents structured HTTP authentication schemes.
 */
sealed interface HttpAuthentication {
    /**
     * Represents HTTP Basic Authentication.
     * @property username The username.
     * @property password The password.
     */
    data class BasicAuth(val username: String, val password: String) : HttpAuthentication

    /**
     * Represents Bearer Token Authentication.
     * @property token The bearer token.
     */
    data class BearerToken(val token: String) : HttpAuthentication

    /**
     * Represents API Key based authentication, typically via a custom header.
     * @property headerName The name of the HTTP header to use for the API key.
     * @property keyValue The value of the API key.
     */
    data class ApiKeyAuth(val headerName: String, val keyValue: String) : HttpAuthentication
}

/**
 * Represents the body of an HTTP request.
 */
sealed class RequestBody {
    data object Empty : RequestBody()
    data class Bytes(val content: ByteArray) : RequestBody() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            return content.contentEquals(other.content)
        }
        override fun hashCode(): Int = content.contentHashCode()
    }
    // TODO: data class Streaming(val producer: suspend (FlowWriter) -> Unit) : RequestBody()
}

/**
 * Represents the body of an HTTP response.
 */
sealed class ResponseBody {
    data object Empty : ResponseBody()
    data class Bytes(val content: ByteArray) : ResponseBody() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            return content.contentEquals(other.content)
        }
        override fun hashCode(): Int = content.contentHashCode()
    }
    // TODO: data class Streaming(val consumer: suspend (FlowReader) -> Unit) : ResponseBody()

    /**
     * Convenience method to get the response body as a ByteArray.
     * For streaming bodies, this would consume the stream.
     */
    suspend fun bytes(): ByteArray {
        return when (this) {
            is Empty -> ByteArray(0)
            is Bytes -> this.content
            // is Streaming -> TODO: consume stream and return bytes and cache it
        }
    }
}

/**
 * Represents an HTTP request.
 * @property url The full URL for the request. Needs parsing for protocol, host, port, path, query.
 * @property method The HTTP method to use.
 * @property headers The HTTP headers for the request.
 * @property body The body of the request.
 */
data class HttpRequest(
    val url: String,
    val method: HttpMethod,
    val headers: HttpHeaders,
    val body: RequestBody,
    val authentication: HttpAuthentication? = null // New field
)

/**
 * Represents an HTTP response.
 * @property statusCode The HTTP status code of the response.
 * @property headers The HTTP headers from the response.
 * @property body The body of the response.
 */
data class HttpResponse(
    val statusCode: Int, // Changed from UShort to Int for commonality
    val headers: HttpHeaders,
    val body: ResponseBody
)

/**
 * Custom exception for QuicCurl operations.
 * @param message A descriptive message for the exception.
 * @param errorCode An optional numeric error code (e.g., from QUIC transport or HTTP/3).
 * @param cause The underlying cause of this exception, if any.
 */
class QuicCurlException(
    message: String,
    val errorCode: Long? = null, // Changed to Long to accommodate various error code types (e.g. QUIC varint error codes)
    cause: Throwable? = null
) : Exception(message, cause)
