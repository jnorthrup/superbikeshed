package borg.trikeshed.reactor.http

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.datetime.formatRfc1123
import borg.trikeshed.lib.datetime.getCurrentDateTime
import borg.trikeshed.lib.j
import borg.trikeshed.nio.ByteBuffer
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.reactor.*

class HttpStateMachine(private val socket: ClientChannel, private val buffer: ByteBuffer) {
    private val responseHeaders = mutableMapOf<String, String>()

    suspend fun parseRequest(): Join<Interest, UnaryAsyncReaction>? {
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

    private suspend fun writeResponse(response: String): Join<Interest, UnaryAsyncReaction>? {
        val responseBuffer = PlatformByteBuffer.wrap(response.toByteArray(), 0, response.toByteArray().size)
        socket.write(responseBuffer)
        socket.close()
        return null
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
