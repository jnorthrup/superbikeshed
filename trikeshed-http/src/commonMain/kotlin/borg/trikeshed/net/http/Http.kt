@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http


import borg.trikeshed.lib.*
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Indexed2
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

// Ontological HTTP Type Aliases

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
        (0 until headers.component1()).forEach { i ->
            val header = headers.component2()(i)
            headersStr.append("${header.component1().value}: ${header.component2().value}\r\n")
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
                headersList.add(HttpHeaderName(headerParts[0].trim()) j HttpHeaderValue(headerParts[1].trim()))
            }
            val headers: Indexed2<HttpHeaderName, HttpHeaderValue> =   (headersList.size)j { it:Int->headersList[it] }

            return HttpRequest(method, path, headers, bodyBytes, version)
        }

        internal fun findEndOfHeaders(bytes: ByteArray): Int {
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
        (0 until headers.component1()).forEach { i ->
            val header = headers.component2()(i)
            headersStr.append("${header.component1().value}: ${header.component2().value}\r\n")
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
                headersList.add(HttpHeaderName(headerParts[0].trim()) j HttpHeaderValue(headerParts[1].trim()))
            }
            val headers =  (headersList.size) j {it :Int-> headersList[it] }

            return HttpResponse(status, reason, headers, bodyBytes, version)
        }

        internal fun findEndOfHeaders(bytes: ByteArray): Int {
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

data class UpgradeProtocol(
    val name: ProtocolName,
    val version: ProtocolVersion
)

object HttpUpgrade {
    fun canUpgrade(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>, protocol: ProtocolName): Boolean {
        val upgradeHeader = (0 until headers.component1()).asSequence().map { headers.component2()(it) }
            .find { it.component1().value.equals("Upgrade", ignoreCase = true) }?.component2()?.value
        val connectionHeader = (0 until headers.component1()).asSequence().map { headers.component2()(it) }
            .find { it.component1().value.equals("Connection", ignoreCase = true) }?.component2()?.value
        
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
            HttpHeaderName(parts[0].trim()) j HttpHeaderValue(parts[1].trim())
        }
    }
    
    fun buildHeaderString(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): String {
        return (0 until headers.component1()).joinToString("\r\n") { i ->
            val join = headers.component2()(i)
            "${join.component1().value}: ${join.component2().value}"
        }
    }
}