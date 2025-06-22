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

        // Simplified parsing - just return a basic response
        val response = generateMotdResponse(emptyMap())
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
}
