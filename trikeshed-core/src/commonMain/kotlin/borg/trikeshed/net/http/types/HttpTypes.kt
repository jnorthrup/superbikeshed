package borg.trikeshed.net.http.types

import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta
import borg.trikeshed.foundation.common.brandt.DslHandle
// import borg.trikeshed.foundation.common.brandt.Join // Not used in final version
import borg.trikeshed.foundation.common.series.Series
import kotlin.jvm.JvmInline // Ensure JvmInline is imported if not automatically by context

// Ontological Typealiases & Core Enums based on IETF RFCs and rxf analysis

/** Represents HTTP protocol versions. e.g., "HTTP/1.1", "HTTP/2.0" */
@JvmInline value class HttpVersion(val value: String)

/** Standard HTTP Methods based on one.xio.HttpMethod and RFC 7231/5789 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, CONNECT, PATCH;
    // Consider if a 'custom' or 'other(String)' case is needed if rxf allowed arbitrary method strings
}

/** Represents the path component of a URI, including query string. e.g., "/users/profile?id=123" */
@JvmInline value class HttpRequestPath(val value: String)

/** Standard HTTP Status Codes (RFC 7231). e.g., 200, 404 */
@JvmInline value class HttpStatusCode(val value: Int)

/** Standard HTTP Reason Phrases. e.g., "OK", "Not Found" - Less critical now with HTTP/2 */
@JvmInline value class HttpReasonPhrase(val value: String)

/**
 * Represents an HTTP Header Name.
 * TrikeShed uses inline classes for type safety for known headers and allows strings for custom ones.
 * Common header names are provided as const vals for convenience and to mirror rxf's HttpHeaders enum approach.
 */
@JvmInline value class HttpHeaderName(val value: String) {
    companion object {
        const val ACCEPT = "Accept"
        const val ACCEPT_CHARSET = "Accept-Charset"
        const val ACCEPT_ENCODING = "Accept-Encoding"
        const val ACCEPT_LANGUAGE = "Accept-Language"
        const val AUTHORIZATION = "Authorization"
        const val CACHE_CONTROL = "Cache-Control"
        const val CONNECTION = "Connection"
        const val CONTENT_ENCODING = "Content-Encoding"
        const val CONTENT_LANGUAGE = "Content-Language"
        const val CONTENT_LENGTH = "Content-Length"
        const val CONTENT_LOCATION = "Content-Location"
        const val CONTENT_MD5 = "Content-MD5"
        const val CONTENT_RANGE = "Content-Range"
        const val CONTENT_TYPE = "Content-Type"
        const val COOKIE = "Cookie"
        const val DATE = "Date"
        const val ETAG = "ETag"
        const val EXPECT = "Expect"
        const val EXPIRES = "Expires"
        const val FROM = "From"
        const val HOST = "Host"
        const val IF_MATCH = "If-Match"
        const val IF_MODIFIED_SINCE = "If-Modified-Since"
        const val IF_NONE_MATCH = "If-None-Match"
        const val IF_RANGE = "If-Range"
        const val IF_UNMODIFIED_SINCE = "If-Unmodified-Since"
        const val LAST_MODIFIED = "Last-Modified"
        const val LOCATION = "Location"
        const val MAX_FORWARDS = "Max-Forwards"
        const val PRAGMA = "Pragma"
        const val PROXY_AUTHENTICATE = "Proxy-Authenticate"
        const val PROXY_AUTHORIZATION = "Proxy-Authorization"
        const val RANGE = "Range"
        const val REFERER = "Referer"
        const val RETRY_AFTER = "Retry-After"
        const val SERVER = "Server"
        const val SET_COOKIE = "Set-Cookie"
        const val TE = "TE"
        const val TRAILER = "Trailer"
        const val TRANSFER_ENCODING = "Transfer-Encoding"
        const val UPGRADE = "Upgrade"
        const val USER_AGENT = "User-Agent"
        const val VARY = "Vary"
        const val VIA = "Via"
        const val WARNING = "Warning"
        const val WWW_AUTHENTICATE = "WWW-Authenticate"
        const val X_FORWARDED_FOR = "X-Forwarded-For"
        // Add other common headers as needed
    }
}

/** Represents an HTTP Header Value. */
@JvmInline value class HttpHeaderValue(val value: String)

// Complex Types using Join and CoreTensorCursorWithMeta

/**
 * Represents HTTP Headers.
 * Modeled as a CoreTensorCursorWithMeta<String>. Each string in the cursor
 * is expected to be a full "Name: Value" header line.
 * This aligns with the tensor-first philosophy and rxf's Rfc822HeaderState's header storage.
 * HttpHeadersMeta can provide metadata, e.g., about parsing or case-insensitivity lookups.
 */
typealias HttpHeaders = CoreTensorCursorWithMeta<String>

/** Metadata for HttpHeaders, potentially for caching parsed structures or original case. */
@JvmInline value class HttpHeadersMeta(val value: DslHandle = DslHandle.NONE) // Placeholder

/**
 * Represents the body of an HTTP message.
 * Can be empty, a sequence of bytes (Series<Byte>), or text (Series<Char>).
 * Using Series aligns with TrikeShed's tensor-like data handling.
 */
sealed interface HttpBody {
    object Empty : HttpBody
    data class Bytes(val data: Series<Byte>) : HttpBody
    data class Text(val data: Series<Char>) : HttpBody
}

/**
 * Represents an HTTP Request.
 * Inspired by rxf.server.Rfc822HeaderState.HttpRequest.
 */
data class HttpRequest(
    val method: HttpMethod,
    val path: HttpRequestPath, // Includes query parameters
    val version: HttpVersion,
    val headers: HttpHeaders,
    val body: HttpBody
)

/**
 * Represents an HTTP Response.
 * Inspired by rxf.server.Rfc822HeaderState.HttpResponse.
 */
data class HttpResponse(
    val version: HttpVersion,
    val statusCode: HttpStatusCode,
    val reasonPhrase: HttpReasonPhrase,
    val headers: HttpHeaders,
    val body: HttpBody
)

// Example of an ontological typealias for a specific content type string
typealias ContentTypeApplicationJsonValue = HttpHeaderValue("application/json")
typealias ContentTypeTextPlainValue = HttpHeaderValue("text/plain")
typealias ContentTypeTextHtmlValue = HttpHeaderValue("text/html")

// Example of ontological typealiases for specific header name + value pairs
// These would be more complex to implement directly as simple typealiases if they
// are meant to represent a complete header. For now, value typealiases are more direct.
// typealias HeaderContentTypeJson = Pair<HttpHeaderName(HttpHeaderName.CONTENT_TYPE), ContentTypeApplicationJsonValue>
