package borg.trikeshed.net.http

/**
 * HTTP Status Code constants following RFC 7231
 * These work with the HttpStatusCode value class
 */
object HttpStatus {
    // 1xx Informational
    val CONTINUE = HttpStatusCode(100)
    val SWITCHING_PROTOCOLS = HttpStatusCode(101)
    val PROCESSING = HttpStatusCode(102)
    
    // 2xx Success
    val OK = HttpStatusCode(200)
    val CREATED = HttpStatusCode(201)
    val ACCEPTED = HttpStatusCode(202)
    val NON_AUTHORITATIVE_INFORMATION = HttpStatusCode(203)
    val NO_CONTENT = HttpStatusCode(204)
    val RESET_CONTENT = HttpStatusCode(205)
    val PARTIAL_CONTENT = HttpStatusCode(206)
    val MULTI_STATUS = HttpStatusCode(207)
    
    // 3xx Redirection
    val MULTIPLE_CHOICES = HttpStatusCode(300)
    val MOVED_PERMANENTLY = HttpStatusCode(301)
    val FOUND = HttpStatusCode(302)
    val SEE_OTHER = HttpStatusCode(303)
    val NOT_MODIFIED = HttpStatusCode(304)
    val USE_PROXY = HttpStatusCode(305)
    val TEMPORARY_REDIRECT = HttpStatusCode(307)
    val PERMANENT_REDIRECT = HttpStatusCode(308)
    
    // 4xx Client Error
    val BAD_REQUEST = HttpStatusCode(400)
    val UNAUTHORIZED = HttpStatusCode(401)
    val PAYMENT_REQUIRED = HttpStatusCode(402)
    val FORBIDDEN = HttpStatusCode(403)
    val NOT_FOUND = HttpStatusCode(404)
    val METHOD_NOT_ALLOWED = HttpStatusCode(405)
    val NOT_ACCEPTABLE = HttpStatusCode(406)
    val PROXY_AUTHENTICATION_REQUIRED = HttpStatusCode(407)
    val REQUEST_TIMEOUT = HttpStatusCode(408)
    val CONFLICT = HttpStatusCode(409)
    val GONE = HttpStatusCode(410)
    val LENGTH_REQUIRED = HttpStatusCode(411)
    val PRECONDITION_FAILED = HttpStatusCode(412)
    val PAYLOAD_TOO_LARGE = HttpStatusCode(413)
    val URI_TOO_LONG = HttpStatusCode(414)
    val UNSUPPORTED_MEDIA_TYPE = HttpStatusCode(415)
    val RANGE_NOT_SATISFIABLE = HttpStatusCode(416)
    val EXPECTATION_FAILED = HttpStatusCode(417)
    val UNPROCESSABLE_ENTITY = HttpStatusCode(422)
    val LOCKED = HttpStatusCode(423)
    val FAILED_DEPENDENCY = HttpStatusCode(424)
    val UPGRADE_REQUIRED = HttpStatusCode(426)
    val PRECONDITION_REQUIRED = HttpStatusCode(428)
    val TOO_MANY_REQUESTS = HttpStatusCode(429)
    val REQUEST_HEADER_FIELDS_TOO_LARGE = HttpStatusCode(431)
    
    // 5xx Server Error
    val INTERNAL_SERVER_ERROR = HttpStatusCode(500)
    val NOT_IMPLEMENTED = HttpStatusCode(501)
    val BAD_GATEWAY = HttpStatusCode(502)
    val SERVICE_UNAVAILABLE = HttpStatusCode(503)
    val GATEWAY_TIMEOUT = HttpStatusCode(504)
    val HTTP_VERSION_NOT_SUPPORTED = HttpStatusCode(505)
    val VARIANT_ALSO_NEGOTIATES = HttpStatusCode(506)
    val INSUFFICIENT_STORAGE = HttpStatusCode(507)
    val LOOP_DETECTED = HttpStatusCode(508)
    val NOT_EXTENDED = HttpStatusCode(510)
    val NETWORK_AUTHENTICATION_REQUIRED = HttpStatusCode(511)
}

/**
 * Common HTTP header name constants
 */
object HttpHeaders {
    // General headers
    val CACHE_CONTROL = HttpHeaderName("Cache-Control")
    val CONNECTION = HttpHeaderName("Connection")
    val DATE = HttpHeaderName("Date")
    val PRAGMA = HttpHeaderName("Pragma")
    val TRAILER = HttpHeaderName("Trailer")
    val TRANSFER_ENCODING = HttpHeaderName("Transfer-Encoding")
    val UPGRADE = HttpHeaderName("Upgrade")
    val VIA = HttpHeaderName("Via")
    val WARNING = HttpHeaderName("Warning")
    
    // Request headers
    val ACCEPT = HttpHeaderName("Accept")
    val ACCEPT_CHARSET = HttpHeaderName("Accept-Charset")
    val ACCEPT_ENCODING = HttpHeaderName("Accept-Encoding")
    val ACCEPT_LANGUAGE = HttpHeaderName("Accept-Language")
    val AUTHORIZATION = HttpHeaderName("Authorization")
    val COOKIE = HttpHeaderName("Cookie")
    val EXPECT = HttpHeaderName("Expect")
    val FROM = HttpHeaderName("From")
    val HOST = HttpHeaderName("Host")
    val IF_MATCH = HttpHeaderName("If-Match")
    val IF_MODIFIED_SINCE = HttpHeaderName("If-Modified-Since")
    val IF_NONE_MATCH = HttpHeaderName("If-None-Match")
    val IF_RANGE = HttpHeaderName("If-Range")
    val IF_UNMODIFIED_SINCE = HttpHeaderName("If-Unmodified-Since")
    val MAX_FORWARDS = HttpHeaderName("Max-Forwards")
    val PROXY_AUTHORIZATION = HttpHeaderName("Proxy-Authorization")
    val RANGE = HttpHeaderName("Range")
    val REFERER = HttpHeaderName("Referer")
    val TE = HttpHeaderName("TE")
    val USER_AGENT = HttpHeaderName("User-Agent")
    
    // Response headers
    val ACCEPT_RANGES = HttpHeaderName("Accept-Ranges")
    val AGE = HttpHeaderName("Age")
    val ETAG = HttpHeaderName("ETag")
    val LOCATION = HttpHeaderName("Location")
    val PROXY_AUTHENTICATE = HttpHeaderName("Proxy-Authenticate")
    val RETRY_AFTER = HttpHeaderName("Retry-After")
    val SERVER = HttpHeaderName("Server")
    val SET_COOKIE = HttpHeaderName("Set-Cookie")
    val VARY = HttpHeaderName("Vary")
    val WWW_AUTHENTICATE = HttpHeaderName("WWW-Authenticate")
    
    // Entity headers
    val ALLOW = HttpHeaderName("Allow")
    val CONTENT_ENCODING = HttpHeaderName("Content-Encoding")
    val CONTENT_LANGUAGE = HttpHeaderName("Content-Language")
    val CONTENT_LENGTH = HttpHeaderName("Content-Length")
    val CONTENT_LOCATION = HttpHeaderName("Content-Location")
    val CONTENT_MD5 = HttpHeaderName("Content-MD5")
    val CONTENT_RANGE = HttpHeaderName("Content-Range")
    val CONTENT_TYPE = HttpHeaderName("Content-Type")
    val EXPIRES = HttpHeaderName("Expires")
    val LAST_MODIFIED = HttpHeaderName("Last-Modified")
    
    // WebSocket headers
    val SEC_WEBSOCKET_KEY = HttpHeaderName("Sec-WebSocket-Key")
    val SEC_WEBSOCKET_ACCEPT = HttpHeaderName("Sec-WebSocket-Accept")
    val SEC_WEBSOCKET_VERSION = HttpHeaderName("Sec-WebSocket-Version")
    val SEC_WEBSOCKET_PROTOCOL = HttpHeaderName("Sec-WebSocket-Protocol")
    
    // CORS headers
    val ACCESS_CONTROL_ALLOW_ORIGIN = HttpHeaderName("Access-Control-Allow-Origin")
    val ACCESS_CONTROL_ALLOW_METHODS = HttpHeaderName("Access-Control-Allow-Methods")
    val ACCESS_CONTROL_ALLOW_HEADERS = HttpHeaderName("Access-Control-Allow-Headers")
    val ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpHeaderName("Access-Control-Allow-Credentials")
    val ACCESS_CONTROL_EXPOSE_HEADERS = HttpHeaderName("Access-Control-Expose-Headers")
    val ACCESS_CONTROL_MAX_AGE = HttpHeaderName("Access-Control-Max-Age")
    val ACCESS_CONTROL_REQUEST_METHOD = HttpHeaderName("Access-Control-Request-Method")
    val ACCESS_CONTROL_REQUEST_HEADERS = HttpHeaderName("Access-Control-Request-Headers")
    val ORIGIN = HttpHeaderName("Origin")
}

/**
 * Default reason phrases for common status codes
 */
fun HttpStatusCode.defaultReasonPhrase(): HttpReasonPhrase = when (this.value) {
    100 -> HttpReasonPhrase("Continue")
    101 -> HttpReasonPhrase("Switching Protocols")
    200 -> HttpReasonPhrase("OK")
    201 -> HttpReasonPhrase("Created")
    202 -> HttpReasonPhrase("Accepted")
    204 -> HttpReasonPhrase("No Content")
    301 -> HttpReasonPhrase("Moved Permanently")
    302 -> HttpReasonPhrase("Found")
    304 -> HttpReasonPhrase("Not Modified")
    400 -> HttpReasonPhrase("Bad Request")
    401 -> HttpReasonPhrase("Unauthorized")
    403 -> HttpReasonPhrase("Forbidden")
    404 -> HttpReasonPhrase("Not Found")
    405 -> HttpReasonPhrase("Method Not Allowed")
    500 -> HttpReasonPhrase("Internal Server Error")
    501 -> HttpReasonPhrase("Not Implemented")
    502 -> HttpReasonPhrase("Bad Gateway")
    503 -> HttpReasonPhrase("Service Unavailable")
    else -> HttpReasonPhrase("Unknown")
}