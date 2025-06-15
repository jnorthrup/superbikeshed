package org.trikeshed.core.http

import org.trikeshed.core.Series
import kotlin.jvm.JvmInline

// File-related value classes
@JvmInline
value class FilePath(val value: String)

@JvmInline
value class FileExtension(val value: String)

// HTTP Protocol Enums
enum class HttpVersion(val value: String) {
    HTTP_0_9("HTTP/0.9"),
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2_0("HTTP/2.0"),
    HTTP_3_0("HTTP/3.0")
}

enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE"),
    CONNECT("CONNECT"),
    PATCH("PATCH")
}

enum class HttpStatus(val code: Int, val reason: String) {
    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NO_CONTENT(204, "No Content"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),
    SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),
    PERMANENT_REDIRECT(308, "Permanent Redirect"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    URI_TOO_LONG(414, "URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    RANGE_NOT_SATISFIABLE(416, "Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    UPGRADE_REQUIRED(426, "Upgrade Required"),
    PRECONDITION_REQUIRED(428, "Precondition Required"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, "Request Header Fields Too Large"),
    UNAVAILABLE_FOR_LEGAL_REASONS(451, "Unavailable For Legal Reasons"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
    VARIANT_ALSO_NEGOTIATES(506, "Variant Also Negotiates"),
    INSUFFICIENT_STORAGE(507, "Insufficient Storage"),
    LOOP_DETECTED(508, "Loop Detected"),
    NOT_EXTENDED(510, "Not Extended"),
    NETWORK_AUTHENTICATION_REQUIRED(511, "Network Authentication Required")
}

enum class HttpHeader(val value: String) {
    CONTENT_TYPE("Content-Type"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_ENCODING("Content-Encoding"),
    TRANSFER_ENCODING("Transfer-Encoding"),
    CONNECTION("Connection"),
    HOST("Host"),
    USER_AGENT("User-Agent"),
    ACCEPT("Accept"),
    ACCEPT_ENCODING("Accept-Encoding"),
    ACCEPT_LANGUAGE("Accept-Language"),
    AUTHORIZATION("Authorization"),
    CACHE_CONTROL("Cache-Control"),
    COOKIE("Cookie"),
    DATE("Date"),
    ETAG("ETag"),
    EXPIRES("Expires"),
    IF_MATCH("If-Match"),
    IF_MODIFIED_SINCE("If-Modified-Since"),
    IF_NONE_MATCH("If-None-Match"),
    IF_RANGE("If-Range"),
    IF_UNMODIFIED_SINCE("If-Unmodified-Since"),
    LAST_MODIFIED("Last-Modified"),
    LOCATION("Location"),
    PRAGMA("Pragma"),
    PROXY_AUTHORIZATION("Proxy-Authorization"),
    RANGE("Range"),
    REFERER("Referer"),
    RETRY_AFTER("Retry-After"),
    SERVER("Server"),
    SET_COOKIE("Set-Cookie"),
    TE("TE"),
    TRAILER("Trailer"),
    UPGRADE("Upgrade"),
    VIA("Via"),
    WARNING("Warning"),
    WWW_AUTHENTICATE("WWW-Authenticate")
}

enum class ContentType(val value: String) {
    TEXT_PLAIN("text/plain"),
    TEXT_HTML("text/html"),
    TEXT_CSS("text/css"),
    TEXT_JAVASCRIPT("text/javascript"),
    APPLICATION_JSON("application/json"),
    APPLICATION_XML("application/xml"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    IMAGE_PNG("image/png"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_GIF("image/gif"),
    IMAGE_SVG("image/svg+xml"),
    AUDIO_MPEG("audio/mpeg"),
    AUDIO_WAV("audio/wav"),
    VIDEO_MP4("video/mp4"),
    VIDEO_WEBM("video/webm"),
    FONT_TTF("font/ttf"),
    FONT_WOFF("font/woff"),
    FONT_WOFF2("font/woff2"),
    UNKNOWN("application/octet-stream")
}

enum class ContentEncoding(val value: String) {
    GZIP("gzip"),
    DEFLATE("deflate"),
    BR("br"),
    IDENTITY("identity")
}

enum class TransferEncoding(val value: String) {
    CHUNKED("chunked"),
    IDENTITY("identity"),
    COMPRESS("compress"),
    DEFLATE("deflate"),
    GZIP("gzip")
}

// Type aliases for collections using Series<T>
typealias HttpVersions = Series<HttpVersion>
typealias HttpMethods = Series<HttpMethod>
typealias HttpStatuses = Series<HttpStatus>
typealias HttpHeaders = Series<HttpHeader>
typealias ContentTypes = Series<ContentType>
typealias ContentEncodings = Series<ContentEncoding>
typealias TransferEncodings = Series<TransferEncoding>

// Type aliases for specific values
typealias GzipEncoding = ContentEncoding
typealias ChunkedEncoding = TransferEncoding

// Type aliases for operations
typealias ContentEncoder = (ByteArray) -> ByteArray
typealias ContentDecoder = (ByteArray) -> ByteArray
typealias ContentValidator = (ByteArray) -> Boolean
typealias VersionNegotiator = (HttpVersions) -> HttpVersion
typealias ContentNegotiator = (ContentEncodings, ContentTypes) -> ContentEncoding?
typealias UpgradeNegotiator = (Series<String>) -> Join<String, Map<String, String>>?

// Type aliases for file operations
typealias FileReader = (FilePath) -> ByteArray
typealias FileWriter = (FilePath, ByteArray) -> Unit
typealias FileExists = (FilePath) -> Boolean

// Type aliases for lazy operations
typealias LazyGzipCreator = (FilePath) -> GzipPath
typealias LazyGzipValidator = (GzipPath) -> Boolean
typealias LazyGzipReader = (GzipPath) -> ByteArray

// Type aliases for content negotiation
typealias AcceptEncoding = ContentEncoding
typealias AcceptEncodings = Series<AcceptEncoding>

// Type aliases for transfer encoding
typealias ChunkSize = Int
typealias ChunkData = ByteArray
typealias ChunkTrailer = Map<String, String>
typealias Chunk = Triple<ChunkSize, ChunkData, ChunkTrailer>
typealias Chunks = Series<Chunk>

// Type aliases for 101 status
typealias UpgradeProtocol = String
typealias UpgradeProtocols = Series<UpgradeProtocol>
typealias ConnectionUpgrade = Pair<UpgradeProtocol, Map<String, String>>
typealias UpgradeProtocolsNegotiator = (UpgradeProtocols) -> ConnectionUpgrade?

// Type aliases for type aliases
typealias HeaderValue = String
typealias HeaderValues = Series<HeaderValue> 