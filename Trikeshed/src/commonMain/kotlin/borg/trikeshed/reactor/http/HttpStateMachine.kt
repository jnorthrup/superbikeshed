package borg.trikeshed.reactor.http
import kotlinx.datetime.Clock


import borg.trikeshed.lib.* 
import borg.trikeshed.nio.* 
import borg.trikeshed.reactor.*

class HttpStateMachine(private val socket: ClientChannel, private val buffer: ByteBuffer) {
    private val responseHeaders = mutableMapOf<String, String>()

    fun parseRequest(): Join<Interest, UnaryAsyncReaction>? {
        // TODO: Implement buffer.flip() for ByteBuffer

        // Simplified parsing - just return a basic response
        val response = generateMotdResponse(emptyMap())
        return writeResponse(response)
    }

    private fun writeResponse(response: String): Join<Interest, UnaryAsyncReaction>? {
        val responseBytes = response.encodeToByteArray()
        val responseBuffer = PlatformByteBuffer.wrap(responseBytes, 0, responseBytes.size)
        
        // Return write interest with async reaction
        return Join(
            1 shl 2, // OP_WRITE
            object : UnaryAsyncReaction {
                override fun invoke(key: SelectionKey): Join<Int, UnaryAsyncReaction>? {
                    return try {
                        // TODO: Implement socket.write() for ClientChannel
                        
                        if (responseBuffer.hasRemaining()) {
                            // More data to write, register for write again
                            // TODO: Implement key.interestOps() for SelectionKey
                            Join(1 shl 2, this)
                        } else {
                            // All data written, close connection
                            // TODO: Implement socket.close() for ClientChannel
                            null
                        }
                    } catch (e: Exception) {
                        println("Error writing response: ${e.message}")
                        // TODO: Implement socket.close() for ClientChannel
                        null
                    }
                }
            }
        )
    }

    private fun generateMotdResponse(headers: Map<String, String>): String {
        val headersEcho = headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        val responseBody = """
            <html>
            <body>
                <h1>Message of the Day</h1>
                <p>Current time: ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}</p>
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