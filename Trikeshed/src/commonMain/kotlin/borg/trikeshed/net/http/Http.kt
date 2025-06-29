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

/**
 * Sends this HTTP request and returns the received HTTP response.
 *
 * This is a suspending function that performs network I/O. It relies on platform-specific
 * actual implementations of `borg.trikeshed.reactor.ClientChannel` to perform
 * the actual socket operations.
 *
 * Current implementation details and limitations:
 * - Extracts host and port from the "Host" header. Assumes port 80 if not specified.
 *   Does not currently handle scheme (http/https) to determine default port or initiate TLS.
 * - HTTPS is NOT supported.
 * - Creates a new connection for each call; does not support HTTP keep-alive or connection pooling.
 * - Response body reading is simplified: it performs a single read attempt. For large bodies,
 *   or chunked encoding, this will be insufficient.
 * - Error handling is basic (throws exceptions).
 *
 * @return The [HttpResponse] received from the server.
 * @throws IllegalArgumentException if the "Host" header is missing.
 * @throws RuntimeException for various I/O errors or if no response data is received (specific exceptions depend on platform actuals of ClientChannel).
 */
suspend fun HttpRequest.send(): HttpResponse {
    val hostHeader = headers.b((0 until headers.a).firstOrNull { headers.b(it).a.value.equals("Host", ignoreCase = true) } ?: -1)
        ?: throw IllegalArgumentException("Host header is missing in HttpRequest")

    val hostValue = hostHeader.b.value
    val (host, port) = when {
        hostValue.contains(":") -> hostValue.split(":").let { it[0] to it[1].toInt() }
        // TODO: Add scheme (http/https) detection to determine default port
        else -> hostValue to 80 // Default to port 80 for HTTP
    }

    val clientChannel = borg.trikeshed.reactor.ClientChannel()

    try {
        clientChannel.connect(host, port)

        // Send request
        val requestBytes = this.toByteArray()
        val sendBuffer = borg.trikeshed.nio.PlatformByteBuffer.wrap(requestBytes)
        while (sendBuffer.hasRemaining()) {
            clientChannel.write(sendBuffer)
        }

        // Read response
        // Initial read for headers, then potentially more for body based on Content-Length or chunking
        // This is a simplified version; a full implementation needs to handle various body types and sizes.
        val readBuffer = borg.trikeshed.nio.PlatformByteBuffer.allocate(8192) // 8KB buffer
        val responseBytesList = mutableListOf<ByteArray>()
        var totalBytesRead = 0
        var bytesRead: Int

        // Simplified read loop: reads until no more data or buffer is full once.
        // A proper implementation would loop based on Content-Length or chunked encoding.
        bytesRead = clientChannel.read(readBuffer)
        if (bytesRead > 0) {
            readBuffer.flip()
            val receivedData = ByteArray(bytesRead)
            readBuffer.get(receivedData)
            responseBytesList.add(receivedData)
            totalBytesRead += bytesRead
            readBuffer.clear()
        } else if (bytesRead == -1 && totalBytesRead == 0) {
            // Connection closed before any data, or error
            throw RuntimeException("Failed to read response, connection closed or error.")
        }


        // Combine all read byte arrays
        val combinedResponseBytes = ByteArray(totalBytesRead)
        var currentPosition = 0
        responseBytesList.forEach {
            System.arraycopy(it, 0, combinedResponseBytes, currentPosition, it.size)
            currentPosition += it.size
        }

        if (combinedResponseBytes.isEmpty()) {
            // This case might happen if the server closes connection immediately after headers without body,
            // or if read returned 0 and we didn't loop.
            // For now, let's throw an error if nothing was read, as HttpResponse.parse expects data.
             throw RuntimeException("No response data received from server.")
        }

        return HttpResponse.parse(combinedResponseBytes)

    } finally {
        if (clientChannel.isConnected()) {
            clientChannel.close()
        }
    }
}

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