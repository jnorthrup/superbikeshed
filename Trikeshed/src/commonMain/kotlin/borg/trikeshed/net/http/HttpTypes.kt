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

// HTTP Request data class
data class HttpRequest(
    val method: HttpMethod,
    val path: HttpRequestPath,
    val headers: Map<HttpHeaderName, HttpHeaderValue> = emptyMap(),
    val body: String = "",
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    suspend fun send(): HttpResponse = TODO("HTTP client implementation needed")
}

// HTTP Response data class  
data class HttpResponse(
    val status: HttpStatusCode,
    val reasonPhrase: HttpReasonPhrase = HttpReasonPhrase("OK"),
    val headers: Map<HttpHeaderName, HttpHeaderValue> = emptyMap(),
    val body: String = "",
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    val isSuccess: Boolean get() = status.value in 200..299
}

// HTTP utilities
object HttpUtils {
    fun parseHeaders(headerString: String): Map<HttpHeaderName, HttpHeaderValue> {
        return headerString.lines()
            .filter { it.contains(":") }
            .associate { line ->
                val parts = line.split(":", limit = 2)
                HttpHeaderName(parts[0].trim()) to HttpHeaderValue(parts[1].trim())
            }
    }
    
    fun buildHeaderString(headers: Map<HttpHeaderName, HttpHeaderValue>): String {
        return headers.entries.joinToString("\r\n") { (name, value) ->
            "${name.value}: ${value.value}"
        }
    }
}