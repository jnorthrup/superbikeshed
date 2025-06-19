@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.consumers.couchdbipfs.net.http

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

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
    
    // Convert to RFC 7230 message format
    fun toHttpMessage(): HttpMessage = HttpMessage(
        startLine = HttpStatusLine(
            httpVersion = version,
            statusCode = status,
            reasonPhrase = reasonPhrase
        ),
        headerFields = headers.α { join -> HttpFieldName(join.a.value) j HttpFieldValue(join.b.value) },
        messageBody = body
    )
}

// HTTP utilities
object HttpUtils {
    fun parseHeaders(headerString: String): Series2<HttpHeaderName, HttpHeaderValue> {
        val headerLines = headerString.lines().filter { it.contains(":") }
        return headerLines.size j { i ->
            val line = headerLines[i]
            val parts = line.split(":", limit = 2)
            HttpHeaderName(parts[0].trim()) j HttpHeaderValue(parts[1].trim())
        }
    }
    
    fun buildHeaderString(headers: Series2<HttpHeaderName, HttpHeaderValue>): String {
        return headers.`▶`.joinToString("\r\n") { join ->
            "${join.a.value}: ${join.b.value}"
        }
    }
}