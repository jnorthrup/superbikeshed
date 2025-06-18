@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.http

import borg.trikeshed.lib.*

// Ontological HTTP Type Aliases
@JvmInline value class HttpHeaderName(val value: String)
@JvmInline value class HttpHeaderValue(val value: String)
@JvmInline value class HttpRequestPath(val value: String)
@JvmInline value class HttpStatusCode(val value: Int)
@JvmInline value class HttpReasonPhrase(val value: String)
@JvmInline value class HttpVersion(val value: String)

// HTTP Method enumeration
enum class HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, COPY, TRACE, CONNECT
}

// HTTP Request data class (RFC 7230 compliant)
data class HttpRequest(
    val method: HttpMethod,
    val path: HttpRequestPath,
    val headers: Series2<HttpHeaderName, HttpHeaderValue>,
    val body: Series<Byte> = 0 j { 0.toByte() },
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    // Convert to RFC 7230 message format
    fun toHttpMessage(): HttpMessage = HttpMessage(
        startLine = HttpRequestLine(
            method = method,
            requestTarget = HttpRequestTarget(path.value),
            httpVersion = version
        ),
        headerFields = headers.α { join -> HttpFieldName(join.a.value) j HttpFieldValue(join.b.value) },
        messageBody = body
    )
    
    suspend fun send(): HttpResponse = TODO("HTTP client implementation needed")
}

// HTTP Response data class (RFC 7230 compliant)
data class HttpResponse(
    val status: HttpStatusCode,
    val reasonPhrase: HttpReasonPhrase = HttpReasonPhrase("OK"),
    val headers: Series2<HttpHeaderName, HttpHeaderValue>,
    val body: Series<Byte> = 0 j { 0.toByte() },
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    val isSuccess: Boolean get() = status.value in 200..299
} 