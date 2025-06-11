package borg.trikeshed.net.http.types

import borg.trikeshed.lib.CoreTensorCursorWithMeta
import borg.trikeshed.lib.DslHandle
import borg.trikeshed.lib.Series
import kotlin.jvm.JvmInline

/**
 * HTTP/1.1 implementation based on RFC 7230-7235 (HTTP/1.1)
 * RFC 7230: Message Syntax and Routing
 * RFC 7231: Semantics and Content  
 * RFC 7232: Conditional Requests
 * RFC 7233: Range Requests
 * RFC 7234: Caching
 * RFC 7235: Authentication
 * RFC 6265: HTTP State Management Mechanism (Cookies)
 */

/** RFC 7230 Section 2.6: Protocol Versioning */
@JvmInline
value class HttpVersion(val value: String) {
    companion object {
        val HTTP_1_0 = HttpVersion("HTTP/1.0") // RFC 1945
        val HTTP_1_1 = HttpVersion("HTTP/1.1") // RFC 7230
        val HTTP_2_0 = HttpVersion("HTTP/2.0") // RFC 7540
    }
}

/** RFC 7231 Section 4: Request Methods */
enum class HttpMethod {
    GET,     // RFC 7231 Section 4.3.1
    POST,    // RFC 7231 Section 4.3.3
    PUT,     // RFC 7231 Section 4.3.4
    DELETE,  // RFC 7231 Section 4.3.5
    HEAD,    // RFC 7231 Section 4.3.2
    OPTIONS, // RFC 7231 Section 4.3.7
    TRACE,   // RFC 7231 Section 4.3.8
    CONNECT, // RFC 7231 Section 4.3.6
    PATCH    // RFC 5789
}

/** RFC 7230 Section 5.3: Request Target */
@JvmInline
value class HttpRequestPath(val value: String)

/** RFC 7231 Section 6: Response Status Codes */
@JvmInline
value class HttpStatusCode(val value: Int) {
    fun isInformational(): Boolean = value in 100..199
    fun isSuccessful(): Boolean = value in 200..299
    fun isRedirection(): Boolean = value in 300..399
    fun isClientError(): Boolean = value in 400..499
    fun isServerError(): Boolean = value in 500..599

    companion object {
        // RFC 7231 Section 6.2: Informational 1xx
        val CONTINUE = HttpStatusCode(100)                    // RFC 7231 Section 6.2.1
        val SWITCHING_PROTOCOLS = HttpStatusCode(101)         // RFC 7231 Section 6.2.2
        
        // RFC 7231 Section 6.3: Successful 2xx  
        val OK = HttpStatusCode(200)                          // RFC 7231 Section 6.3.1
        val CREATED = HttpStatusCode(201)                     // RFC 7231 Section 6.3.2
        val ACCEPTED = HttpStatusCode(202)                    // RFC 7231 Section 6.3.3
        val NO_CONTENT = HttpStatusCode(204)                  // RFC 7231 Section 6.3.5
        
        // RFC 7231 Section 6.4: Redirection 3xx
        val MOVED_PERMANENTLY = HttpStatusCode(301)           // RFC 7231 Section 6.4.2
        val FOUND = HttpStatusCode(302)                       // RFC 7231 Section 6.4.3
        val SEE_OTHER = HttpStatusCode(303)                   // RFC 7231 Section 6.4.4
        val NOT_MODIFIED = HttpStatusCode(304)                // RFC 7232 Section 4.1
        
        // RFC 7231 Section 6.5: Client Error 4xx
        val BAD_REQUEST = HttpStatusCode(400)                 // RFC 7231 Section 6.5.1
        val UNAUTHORIZED = HttpStatusCode(401)                // RFC 7235 Section 3.1
        val FORBIDDEN = HttpStatusCode(403)                   // RFC 7231 Section 6.5.3
        val NOT_FOUND = HttpStatusCode(404)                   // RFC 7231 Section 6.5.4
        val METHOD_NOT_ALLOWED = HttpStatusCode(405)          // RFC 7231 Section 6.5.5
        
        // RFC 7231 Section 6.6: Server Error 5xx
        val INTERNAL_SERVER_ERROR = HttpStatusCode(500)       // RFC 7231 Section 6.6.1
        val NOT_IMPLEMENTED = HttpStatusCode(501)             // RFC 7231 Section 6.6.2
        val BAD_GATEWAY = HttpStatusCode(502)                 // RFC 7231 Section 6.6.3
        val SERVICE_UNAVAILABLE = HttpStatusCode(503)         // RFC 7231 Section 6.6.4
    }
}

/** RFC 7230 Section 3.1.2: Status Line */
@JvmInline
value class HttpReasonPhrase(val value: String)

/** RFC 7230 Section 3.2: Header Fields */
@JvmInline
value class HttpHeaderName(val value: String) {
    companion object {
        // RFC 7231 Section 5.3.2: Accept
        const val ACCEPT = "Accept"                           // RFC 7231 Section 5.3.2
        const val ACCEPT_CHARSET = "Accept-Charset"           // RFC 7231 Section 5.3.3
        const val ACCEPT_ENCODING = "Accept-Encoding"         // RFC 7231 Section 5.3.4
        const val ACCEPT_LANGUAGE = "Accept-Language"         // RFC 7231 Section 5.3.5
        
        // RFC 7235: Authentication
        const val AUTHORIZATION = "Authorization"             // RFC 7235 Section 4.2
        const val WWW_AUTHENTICATE = "WWW-Authenticate"       // RFC 7235 Section 4.1
        const val PROXY_AUTHENTICATE = "Proxy-Authenticate"   // RFC 7235 Section 4.3
        const val PROXY_AUTHORIZATION = "Proxy-Authorization" // RFC 7235 Section 4.4
        
        // RFC 7234: Caching
        const val CACHE_CONTROL = "Cache-Control"            // RFC 7234 Section 5.2
        const val EXPIRES = "Expires"                        // RFC 7234 Section 5.3
        const val PRAGMA = "Pragma"                          // RFC 7234 Section 5.4
        const val VARY = "Vary"                              // RFC 7234 Section 4.1
        
        // RFC 7230: Message Syntax and Routing
        const val CONNECTION = "Connection"                  // RFC 7230 Section 6.1
        const val HOST = "Host"                              // RFC 7230 Section 5.4
        const val TRANSFER_ENCODING = "Transfer-Encoding"   // RFC 7230 Section 3.3.1
        const val UPGRADE = "Upgrade"                       // RFC 7230 Section 6.7
        const val VIA = "Via"                               // RFC 7230 Section 5.7.1
        const val TE = "TE"                                 // RFC 7230 Section 4.3
        const val TRAILER = "Trailer"                       // RFC 7230 Section 4.4
        
        // RFC 7231: Semantics and Content
        const val CONTENT_ENCODING = "Content-Encoding"     // RFC 7231 Section 3.1.2.2
        const val CONTENT_LANGUAGE = "Content-Language"     // RFC 7231 Section 3.1.3.2
        const val CONTENT_LENGTH = "Content-Length"         // RFC 7230 Section 3.3.2
        const val CONTENT_LOCATION = "Content-Location"     // RFC 7231 Section 3.1.4.2
        const val CONTENT_TYPE = "Content-Type"             // RFC 7231 Section 3.1.1.5
        const val DATE = "Date"                             // RFC 7231 Section 7.1.1.2
        const val EXPECT = "Expect"                         // RFC 7231 Section 5.1.1
        const val FROM = "From"                             // RFC 7231 Section 5.5.1
        const val LOCATION = "Location"                     // RFC 7231 Section 7.1.2
        const val MAX_FORWARDS = "Max-Forwards"             // RFC 7231 Section 5.1.2
        const val REFERER = "Referer"                       // RFC 7231 Section 5.5.2
        const val RETRY_AFTER = "Retry-After"               // RFC 7231 Section 7.1.3
        const val SERVER = "Server"                         // RFC 7231 Section 7.4.2
        const val USER_AGENT = "User-Agent"                 // RFC 7231 Section 5.5.3
        
        // RFC 7232: Conditional Requests
        const val ETAG = "ETag"                             // RFC 7232 Section 2.3
        const val IF_MATCH = "If-Match"                     // RFC 7232 Section 3.1
        const val IF_MODIFIED_SINCE = "If-Modified-Since"   // RFC 7232 Section 3.3
        const val IF_NONE_MATCH = "If-None-Match"           // RFC 7232 Section 3.2
        const val IF_RANGE = "If-Range"                     // RFC 7233 Section 3.2
        const val IF_UNMODIFIED_SINCE = "If-Unmodified-Since" // RFC 7232 Section 3.4
        const val LAST_MODIFIED = "Last-Modified"           // RFC 7232 Section 2.2
        
        // RFC 7233: Range Requests
        const val CONTENT_RANGE = "Content-Range"           // RFC 7233 Section 4.2
        const val RANGE = "Range"                           // RFC 7233 Section 3.1
        
        // RFC 6265: HTTP State Management Mechanism (Cookies)
        const val COOKIE = "Cookie"                         // RFC 6265 Section 4.2
        const val SET_COOKIE = "Set-Cookie"                 // RFC 6265 Section 4.1
        
        // Obsolete/Legacy
        const val CONTENT_MD5 = "Content-MD5"               // RFC 1864 (obsoleted)
        const val WARNING = "Warning"                       // RFC 7234 Section 5.5 (obsolete)
        
        // Common Extensions
        const val X_FORWARDED_FOR = "X-Forwarded-For"       // De facto standard
    }
}

/** RFC 7230 Section 3.2: Header Fields */
@JvmInline
value class HttpHeaderValue(val value: String)

/** RFC 7230 Section 3.2: Header Fields - TrikeShed tensor representation */
typealias HttpHeaders = CoreTensorCursorWithMeta<String>

/** TrikeShed metadata for header processing context */
@JvmInline
value class HttpHeadersMeta(val value: DslHandle = DslHandle.NONE)

/** RFC 7230 Section 3.3: Message Body */
sealed interface HttpBody {
    object Empty : HttpBody
    data class Bytes(val data: Series<Byte>) : HttpBody    // Binary content
    data class Text(val data: Series<Char>) : HttpBody     // Text content
}

/** RFC 7230 Section 3: Message Format */
data class HttpRequest(
    val method: HttpMethod,        // RFC 7231 Section 4
    val path: HttpRequestPath,     // RFC 7230 Section 5.3 
    val version: HttpVersion,      // RFC 7230 Section 2.6
    val headers: HttpHeaders,      // RFC 7230 Section 3.2
    val body: HttpBody            // RFC 7230 Section 3.3
)

/** RFC 7230 Section 3: Message Format */  
data class HttpResponse(
    val version: HttpVersion,      // RFC 7230 Section 2.6
    val statusCode: HttpStatusCode, // RFC 7231 Section 6
    val reasonPhrase: HttpReasonPhrase, // RFC 7230 Section 3.1.2
    val headers: HttpHeaders,      // RFC 7230 Section 3.2
    val body: HttpBody            // RFC 7230 Section 3.3
)

// Ontological typealiases for content types
typealias ContentTypeApplicationJsonValue = String
typealias ContentTypeTextPlainValue = String
typealias ContentTypeTextHtmlValue = String

// Header indexing for fast lookups (relaxfactory pattern)
@JvmInline
value class HeaderIndex(val keyPositions: Series<Int>)

@JvmInline
value class CookieIndex(val cookiePositions: Series<Int>)

typealias HeaderRequestPattern = Series<HttpHeaderName>
typealias CookieRequestPattern = Series<String> // Cookie names we care about

// RFC 6265: HTTP State Management Mechanism (Cookies)
@JvmInline
value class CookieName(val value: String)         // RFC 6265 Section 4.1.1

@JvmInline  
value class CookieValue(val value: String)        // RFC 6265 Section 4.1.1

@JvmInline
value class CookieAttributes(val value: String)   // RFC 6265 Section 4.1.1 (Path, Domain, Secure, etc.)

/** RFC 6265 Section 4.1.1: Set-Cookie */
data class ParsedCookie(
    val name: CookieName,
    val value: CookieValue, 
    val attributes: CookieAttributes = CookieAttributes("")
)
