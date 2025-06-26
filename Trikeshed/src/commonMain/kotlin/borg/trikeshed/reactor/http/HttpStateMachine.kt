@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.http


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.* 
import borg.trikeshed.nio.* 
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
        
        // Return write interest with async reaction
        return Join(
            1 shl 2, // OP_WRITE
            object : UnaryAsyncReaction {
                override fun invoke(key: SelectionKey): Join<Int, UnaryAsyncReaction>? {
                    return try {
                        socket.write(responseBuffer)
                        
                        if (responseBuffer.hasRemaining()) {
                            // More data to write, register for write again
                            key.interestOps(1 shl 2) // OP_WRITE
                            Join(1 shl 2, this)
                        } else {
                            // All data written, close connection
                            socket.close()
                            null
                        }
                    } catch (e: Exception) {
                        println("Error writing response: ${e.message}")
                        socket.close()
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
                <p>Current time: ${currentTimeMillis()}</p>
                <h2>Received Headers:</h2>
                <pre>$headersEcho</pre>
            </body>
            </html>
        """.trimIndent()

        responseHeaders["Content-Type"] = "text/html"
        responseHeaders["Content-Length"] = responseBody.length.toString()
        responseHeaders["Connection"] = "close"

        val responseHeadersFormatted = responseHeaders.entries.joinToString("\r\n") { entry: Map.Entry<String, String> -> "${entry.key}: ${entry.value}" }
        return "HTTP/1.1 200 OK\r\n$responseHeadersFormatted\r\n\r\n$responseBody"
    }
}