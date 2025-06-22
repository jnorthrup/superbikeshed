package borg.trikeshed.reactor.http

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.nio.ByteBuffer
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.reactor.*

class HttpStateMachine(private val socket: ClientChannel, private val buffer: ByteBuffer) {
    private val responseHeaders = mutableMapOf<String, String>()

    fun parseRequest(): Join<Interest, UnaryAsyncReaction>? {
        buffer.flip()

        // Parse request line
        val requestLine = extractLineFromBuffer(buffer, 0, buffer.position) 
            ?: return 1 j object : UnaryAsyncReaction {
                override fun invoke(key: SelectionKey): Join<Int, UnaryAsyncReaction>? = parseRequest()
            }
        
        // Parse headers
        val headers = parseHeaders(buffer)

        // Generate MOTD response
        val response = generateMotdResponse(headers)
        return writeResponse(response)
    }

    private fun writeResponse(response: String): Join<Interest, UnaryAsyncReaction>? {
        val responseBytes = response.encodeToByteArray()
        val responseBuffer = PlatformByteBuffer.wrap(responseBytes, 0, responseBytes.size)
        // TODO: Handle suspend write and close properly
        return null
    }

    private fun generateMotdResponse(headers: Map<String, String>): String {
        val headersEcho = headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        val responseBody = """
            <html>
            <body>
                <h1>Message of the Day</h1>
                <p>Current time: ${System.currentTimeMillis()}</p>
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

    private fun extractLineFromBuffer(buffer: ByteBuffer, start: Int, end: Int): String? {
        val bytes = buffer.array()
        val lineBuilder = StringBuilder()
        for (i in start until minOf(end, bytes.size)) {
            val byte = bytes[i]
            if (byte.toInt().toChar() == '\n') {
                if (lineBuilder.isNotEmpty() && lineBuilder.last() == '\r') {
                    lineBuilder.setLength(lineBuilder.length - 1)
                }
                return lineBuilder.toString()
            }
            lineBuilder.append(byte.toInt().toChar())
        }
        return null
    }

    private fun parseHeaders(buffer: ByteBuffer): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var line: String?
        var lineStart = buffer.position

        while (true) {
            line = extractLineFromBuffer(buffer, lineStart, buffer.limit())
            if (line.isNullOrEmpty()) {
                // End of headers
                buffer.position(lineStart + (line?.length ?: 0) + 2) //+2 for \r\n
                break
            }

            val headerParts = line.split(":", limit = 2)
            if (headerParts.size == 2) {
                headers[headerParts[0].trim()] = headerParts[1].trim()
            }
            lineStart += line.length + 1 // +1 for \n
        }
        return headers
    }
}
