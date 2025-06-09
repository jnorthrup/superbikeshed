package evolution

import borg.trikeshed.lib.Join

typealias HttpAuthority = String              // Host:port authority
typealias HttpContentLength = ULong           // Content-Length header value
typealias HttpContentType = String            // Content-Type header value
typealias HttpHeader = Join<HttpHeaderName, HttpHeaderValue>
typealias HttpHeaderName = String             // Header field name
typealias HttpHeaders = Series<HttpHeader>
typealias HttpHeaderValue = String            // Header field value
// HTTP methods and headers
typealias HttpMethod = String                 // GET, POST, PUT, DELETE, etc.
typealias HttpReasonPhrase = String           // Status reason phrase
// HTTP request/response compositions
typealias HttpRequestLine = Join<HttpMethod, Join<HttpRequestPath, HttpScheme>>
// Request/response structure
typealias HttpRequestPath = String            // Request target path
typealias HttpResponseLine = Join<HttpStatusCode, HttpReasonPhrase>
typealias HttpScheme = String                 // http, https
typealias HttpStatusCode = UShort             // 1xx-5xx response codes
typealias HttpUserAgent = String              // User-Agent header value