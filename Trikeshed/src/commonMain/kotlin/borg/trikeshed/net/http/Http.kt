@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.http


import borg.trikeshed.lib.*
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Indexed2
import borg.trikeshed.lib.j
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
    val headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>,
    val body: ByteArray = byteArrayOf(),
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    fun toByteArray(): ByteArray {
        val startLine = "${method.name} ${path.value} ${version.value}\r\n"
        val headersStr = StringBuilder()
        (0 until headers.a).forEach { i ->
            val header = headers.b(i)
            headersStr.append("${header.a.value}: ${header.b.value}\r\n")
        }
        val finalHeaders = headersStr.toString()
        val head = (startLine + finalHeaders + "\r\n").encodeToByteArray()
        return head + body
    }

    companion object {
        fun parse(bytes: ByteArray): HttpRequest {
            val eoh = findEndOfHeaders(bytes)
            val headerBytes = bytes.sliceArray(0 until eoh)
            val bodyBytes = bytes.sliceArray(eoh + 4 until bytes.size)

            val headerLines = headerBytes.decodeToString().split("\r\n")
            val startLineParts = headerLines[0].split(" ", limit = 3)

            val method = HttpMethod.valueOf(startLineParts[0])
            val path = HttpRequestPath(startLineParts[1])
            val version = HttpVersion(startLineParts[2])

            val headersList = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
            for (i in 1 until headerLines.size) {
                if (headerLines[i].isBlank()) continue
                val headerParts = headerLines[i].split(":", limit = 2)
                headersList.add(Join(HttpHeaderName(headerParts[0].trim()), HttpHeaderValue(headerParts[1].trim())))
            }
            val headers: Indexed2<HttpHeaderName, HttpHeaderValue> =   (headersList.size)j { it:Int->headersList[it] }

            return HttpRequest(method, path, headers, bodyBytes, version)
        }

        private fun findEndOfHeaders(bytes: ByteArray): Int {
            for (i in 0 until bytes.size - 3) {
                if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte() &&
                    bytes[i + 2] == '\r'.code.toByte() && bytes[i + 3] == '\n'.code.toByte()
                ) {
                    return i
                }
            }
            return -1
        }
    }
}

// HTTP Response data class (RFC 7230 compliant)
data class HttpResponse(
    val status: HttpStatusCode,
    val reasonPhrase: HttpReasonPhrase = HttpReasonPhrase("OK"),
    val headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>,
    val body: ByteArray = byteArrayOf(),
    val version: HttpVersion = HttpVersion("HTTP/1.1")
) {
    val isSuccess: Boolean get() = status.value in 200..299

    fun toByteArray(): ByteArray {
        val startLine = "${version.value} ${status.value} ${reasonPhrase.value}\r\n"
        val headersStr = StringBuilder()
        (0 until headers.a).forEach { i ->
            val header = headers.b(i)
            headersStr.append("${header.a.value}: ${header.b.value}\r\n")
        }
        val finalHeaders = headersStr.toString()
        val head = (startLine + finalHeaders + "\r\n").encodeToByteArray()
        return head + body
    }
    
    fun toHttpMessage(): ByteArray = toByteArray()
    
    companion object {
        fun parse(bytes: ByteArray): HttpResponse {
            val eoh = findEndOfHeaders(bytes)
            val headerBytes = bytes.sliceArray(0 until eoh)
            val bodyBytes = bytes.sliceArray(eoh + 4 until bytes.size)

            val headerLines = headerBytes.decodeToString().split("\r\n")
            val startLineParts = headerLines[0].split(" ", limit = 3)

            val version = HttpVersion(startLineParts[0])
            val status = HttpStatusCode(startLineParts[1].toInt())
            val reason = HttpReasonPhrase(startLineParts[2])

            val headersList = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
            for (i in 1 until headerLines.size) {
                if (headerLines[i].isBlank()) continue
                val headerParts = headerLines[i].split(":", limit = 2)
                headersList.add(Join(HttpHeaderName(headerParts[0].trim()), HttpHeaderValue(headerParts[1].trim())))
            }
            val headers =  (headersList.size) j {it :Int-> headersList[it] }

            return HttpResponse(status, reason, headers, bodyBytes, version)
        }

        private fun findEndOfHeaders(bytes: ByteArray): Int {
            for (i in 0 until bytes.size - 3) {
                if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte() &&
                    bytes[i + 2] == '\r'.code.toByte() && bytes[i + 3] == '\n'.code.toByte()
                ) {
                    return i
                }
            }
            return -1
        }
    }
}

fun Map<String, String>.toHttpHeaders(): Indexed<Join<HttpHeaderName, HttpHeaderValue>> =
    this.map { HttpHeaderName(it.key) j HttpHeaderValue(it.value) }.toIdx()

suspend fun HttpRequest.send(): HttpResponse = TODO("HTTP client implementation needed")

// Protocol upgrade types
@JvmInline value class ProtocolName(val value: String)
@JvmInline value class ProtocolVersion(val value: String)

data class UpgradeProtocol(
    val name: ProtocolName,
    val version: ProtocolVersion
)

object HttpUpgrade {
    fun canUpgrade(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>, protocol: ProtocolName): Boolean {
        val upgradeHeader = (0 until headers.a).asSequence().map { headers.b(it) }
            .find { it.a.value.equals("Upgrade", ignoreCase = true) }?.b?.value
        val connectionHeader = (0 until headers.a).asSequence().map { headers.b(it) }
            .find { it.a.value.equals("Connection", ignoreCase = true) }?.b?.value
        
        return upgradeHeader?.equals(protocol.value, ignoreCase = true) == true &&
               connectionHeader?.contains("upgrade", ignoreCase = true) == true
    }
}

// HTTP utilities
object HttpUtils {
    fun parseHeaders(headerString: String): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headerLines = headerString.lines().filter { it.contains(":") }
        return (headerLines.size) j { i:Int ->
            val line = headerLines[i]
            val parts = line.split(":", limit = 2)
            Join(HttpHeaderName(parts[0].trim()), HttpHeaderValue(parts[1].trim()))
        }
    }
    
    fun buildHeaderString(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): String {
        return (0 until headers.a).joinToString("\r\n") { i ->
            val join = headers.b(i)
            "${join.a.value}: ${join.b.value}"
        }
    }
}