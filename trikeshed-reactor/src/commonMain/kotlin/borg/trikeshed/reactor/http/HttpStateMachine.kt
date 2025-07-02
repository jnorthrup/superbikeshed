package borg.trikeshed.reactor.http

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.datetime.formatRfc1123
import borg.trikeshed.lib.datetime.getCurrentDateTime
import borg.trikeshed.lib.j
import borg.trikeshed.nio.ByteBuffer
import borg.trikeshed.nio.ByteBufferFactory
import borg.trikeshed.reactor.*

class HttpStateMachine(private val socket: ClientChannel, private val buffer: ByteBuffer) {
    private val responseHeaders = mutableMapOf<String, String>()

    fun parseRequest(): Join<Interest, UnaryAsyncReaction>? {
        buffer.flip()

        // Parse request line
        val requestLine = extractLineFromBuffer(buffer, 0, buffer.position) 
            ?: return OP_READ j { parseRequest() }
        
        // Parse headers
        val headers = parseHeaders(buffer)

        // Generate MOTD response
        val response = generateMotdResponse(headers)
        return writeResponse(response)
    }

    private fun writeResponse(response: String): Join<Interest, UnaryAsyncReaction>? {
        val responseBuffer = ByteBufferFactory.wrap(response.toByteArray())
        return OP_WRITE j { key:Int ->
            socket.write(responseBuffer)
            if (!responseBuffer.hasRemaining()) {
                socket.close()
                null
            } else {
                writeResponse(response)
            }
        }
    }

    private fun generateMotdResponse(headers: Map<String, String>): String {
        val currentTime = getCurrentDateTime()
        val iso8601 = "${currentTime.year}-${currentTime.month}-${currentTime.day}T${currentTime.hour}:${currentTime.minute}:${currentTime.second}Z"
        val rfc1123 = formatRfc1123(currentTime)

        val headersEcho = headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        val responseBody = """
            <html>
            <body>
                <h1>Message of the Day</h1>
                <p>ISO 8601: $iso8601</p>
                <p>RFC 1123: $rfc1123</p>
                <h2>Received Headers:</h2>
                <pre>$headersEcho</pre>
            </body>
            </html>
        """.trimIndent()

        responseHeaders["Content-Type"] = "text/html"
        responseHeaders["Content-Length"] = responseBody.length.toString()
        responseHeaders["Connection"] = "close"

        val responseHeadersFormatted = responseHeaders.entries.joinToString("\r\n") { (k, v) -> "$k: $v" }
        return "HTTP/1.1 200 OK\r\n$responseHeadersFormatted\r\n\r\n$responseBody"
    }

    // Helper function to extract a line from the buffer
    private fun extractLineFromBuffer(buffer: ByteBuffer, start: Int, end: Int): String? {
        val bytes = ByteArray(buffer.remaining())
        val originalPosition = buffer.position
        buffer.position = start
        
        var lineEnd = -1
        for (i in start until minOf(end, buffer.limit)) {
            val b = buffer.get()
            bytes[i - start] = b
            if (i > start && bytes[i - start - 1] == '\r'.code.toByte() && b == '\n'.code.toByte()) {
                lineEnd = i - start - 1
                break
            }
        }
        
        buffer.position = originalPosition
        
        return if (lineEnd >= 0) {
            bytes.decodeToString(0, lineEnd)
        } else {
            null
        }
    }

    // Helper function to parse headers from the buffer
    private fun parseHeaders(buffer: ByteBuffer): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val lines = bytes.decodeToString().split("\r\n")
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val name = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[name] = value
            }
        }
        
        return headers
    }
}