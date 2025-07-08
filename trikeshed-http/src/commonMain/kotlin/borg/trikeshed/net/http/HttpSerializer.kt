@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http


import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

/**
 * RFC 7230 compliant HTTP serializer
 */
object HttpSerializer {
    fun serializeHttpMessage(message: HttpMessage): ByteArray {
        val startLine = "${message.startLine.method} ${message.startLine.requestTarget} ${message.startLine.httpVersion}\r\n"
        val headers = serializeHeaders(message.headerFields)
        val body = message.messageBody
        
        return (startLine + headers + "\r\n" + body).encodeToByteArray()
    }
    
    fun serializeHttpResponse(response: HttpResponse): ByteArray {
        return response.toByteArray()
    }
    
    fun serializeHttpRequest(request: HttpRequest): ByteArray {
        return request.toByteArray()
    }
    
    internal fun serializeHeaders(headers: Indexed<Join<String, String>>): String {
        return (0 until headers.size).joinToString("\r\n") { i ->
            val header = headers[i]
            "${header.a}: ${header.b}"
        }
    }
    
    fun createHttpMessage(
        method: String,
        requestTarget: String,
        httpVersion: String,
        headers: Indexed<Join<String, String>>,
        body: String
    ): HttpMessage {
        val startLine = HttpRequestLineImpl(method, requestTarget, httpVersion)
        return HttpMessageImpl(startLine, headers, body)
    }
}