@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http


import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

/**
 * RFC 7230 compliant HTTP parser
 */
object HttpParser {
    fun parse(buffer: PlatformByteBuffer): HttpMessage? {
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return parseHttpMessage(bytes.decodeToString())
    }
    
    fun parseHttpMessage(message: String): HttpMessage? {
        val lines = message.split("\r\n")
        if (lines.isEmpty()) return null
        
        val startLine = parseRequestLine(lines[0]) ?: return null
        val headers = parseHeaders(lines.drop(1))
        val body = extractBody(message)
        
        return HttpMessageImpl(startLine, headers, body)
    }
    
    internal fun parseRequestLine(line: String): HttpRequestLine? {
        val parts = line.split(" ", limit = 3)
        if (parts.size != 3) return null
        
        return HttpRequestLineImpl(
            method = parts[0],
            requestTarget = parts[1],
            httpVersion = parts[2]
        )
    }
    
    internal fun parseHeaders(lines: List<String>): Indexed<Join<String, String>> {
        val headers = mutableListOf<Join<String, String>>()
        var i = 0
        
        while (i < lines.size && lines[i].isNotBlank()) {
            val line = lines[i]
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val name = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers.add(Join(name, value))
            }
            i++
        }
        
        return headers.size j { headers[it] }
    }
    
    internal fun extractBody(message: String): String {
        val bodyStart = message.indexOf("\r\n\r\n")
        return if (bodyStart >= 0) {
            message.substring(bodyStart + 4)
        } else {
            ""
        }
    }
}

/**
 * HTTP message implementation
 */
data class HttpMessageImpl(
    override val startLine: HttpRequestLine,
    override val headerFields: Indexed<Join<String, String>>,
    override val messageBody: String
) : HttpMessage

/**
 * HTTP request line implementation
 */
data class HttpRequestLineImpl(
    override val method: String,
    override val requestTarget: String,
    override val httpVersion: String
) : HttpRequestLine

/**
 * HTTP message interface
 */
interface HttpMessage {
    val startLine: HttpRequestLine
    val headerFields: Indexed<Join<String, String>>
    val messageBody: String
}

/**
 * HTTP request line interface
 */
interface HttpRequestLine {
    val method: String
    val requestTarget: String
    val httpVersion: String
}