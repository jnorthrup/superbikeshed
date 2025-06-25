package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*

/**
 * HTTP/0.9 Server Implementation
 * The simplest HTTP protocol - GET requests only, no headers
 * Request format: GET /path\r\n
 * Response format: raw content (no status line or headers)
 */
class Http09Server(
    private val quicServer: QuicServer,
    private val defaultCharset: String = "UTF-8"
) {
    private val routes = mutableMapOf<String, Http09Handler>()
    private var isRunning = false
    
    /**
     * Simple handler for HTTP/0.9 - returns raw content
     */
    typealias Http09Handler = suspend (path: String) -> String
    
    /**
     * Add a route handler
     */
    fun route(path: String, handler: Http09Handler) {
        routes[path] = handler
    }
    
    /**
     * Start the HTTP/0.9 server
     */
    suspend fun start() {
        if (isRunning) return
        
        isRunning = true
        println("HTTP/0.9 Server starting")
        
        // Set up QUIC stream handler
        quicServer.onConnection { connection ->
            GlobalScope.launch {
                handleConnection(connection)
            }
        }
    }
    
    /**
     * Stop the HTTP/0.9 server
     */
    suspend fun stop() {
        isRunning = false
        println("HTTP/0.9 Server stopped")
    }
    
    /**
     * Handle incoming QUIC connection
     */
    private suspend fun handleConnection(connection: QuicConnection) {
        while (isRunning && connection.isActive()) {
            val stream = connection.acceptStream() ?: continue
            
            GlobalScope.launch {
                handleHttp09Stream(stream)
            }
        }
    }
    
    /**
     * Handle HTTP/0.9 request on QUIC stream
     */
    private suspend fun handleHttp09Stream(stream: QuicStream) {
        try {
            // Read request line (GET /path)
            val requestLine = readRequestLine(stream)
            
            if (requestLine != null) {
                val parts = requestLine.split(" ")
                
                if (parts.size >= 2 && parts[0] == "GET") {
                    val path = parts[1]
                    
                    // Find handler
                    val handler = findHandler(path)
                    
                    if (handler != null) {
                        // Execute handler and send response
                        val response = handler(path)
                        sendResponse(stream, response)
                    } else {
                        // Send simple error message (no status codes in HTTP/0.9)
                        sendResponse(stream, "Not Found")
                    }
                } else {
                    // Invalid request format
                    sendResponse(stream, "Bad Request")
                }
            }
            
        } catch (e: Exception) {
            println("Error handling HTTP/0.9 request: ${e.message}")
            try {
                sendResponse(stream, "Error")
            } catch (_: Exception) {
                // Best effort error response
            }
        } finally {
            stream.close()
        }
    }
    
    /**
     * Read request line from stream
     */
    private suspend fun readRequestLine(stream: QuicStream): String? {
        val buffer = mutableListOf<Byte>()
        var previousByte: Byte = 0
        
        // Read until we find \r\n or just \n
        while (stream.hasData() && buffer.size < 1024) { // Limit request line size
            val byte = stream.readByte()
            
            if (byte == '\n'.code.toByte()) {
                // Found end of line
                if (previousByte == '\r'.code.toByte()) {
                    // Remove \r from buffer
                    buffer.removeAt(buffer.size - 1)
                }
                break
            }
            
            buffer.add(byte)
            previousByte = byte
        }
        
        return if (buffer.isNotEmpty()) {
            String(buffer.toByteArray(), charset(defaultCharset))
        } else {
            null
        }
    }
    
    /**
     * Send HTTP/0.9 response (just the content, no headers)
     */
    private suspend fun sendResponse(stream: QuicStream, content: String) {
        val contentBytes = content.encodeToByteArray()
        val indexed = contentBytes.size j { i: Int -> contentBytes[i] }
        stream.writeBytes(indexed)
    }
    
    /**
     * Find handler for path
     */
    private fun findHandler(path: String): Http09Handler? {
        // Try exact match first
        routes[path]?.let { return it }
        
        // Try wildcard patterns
        for ((pattern, handler) in routes) {
            if (pattern.endsWith("*")) {
                val prefix = pattern.dropLast(1)
                if (path.startsWith(prefix)) {
                    return handler
                }
            }
        }
        
        // Try default handler
        return routes["*"]
    }
}

/**
 * Create a simple HTTP/0.9 server with default routes
 */
fun createHttp09Server(quicServer: QuicServer): Http09Server {
    return Http09Server(quicServer).apply {
        // Add some default routes for testing
        
        // Root route
        route("/") { _ ->
            """
            Welcome to HTTP/0.9 Server
            
            This is the simplest HTTP protocol.
            No headers, no status codes, just content.
            """.trimIndent()
        }
        
        // Echo route
        route("/echo/*") { path ->
            val message = path.removePrefix("/echo/")
            "Echo: $message"
        }
        
        // Time route
        route("/time") { _ ->
            "Current time: ${getCurrentTimeMillis()}"
        }
        
        // Default 404 handler
        route("*") { path ->
            "404 - Path not found: $path"
        }
    }
}

/**
 * HTTP/0.9 client for testing
 */
class Http09Client(
    private val defaultCharset: String = "UTF-8"
) {
    /**
     * Make a simple GET request
     */
    suspend fun get(
        connection: QuicConnection,
        path: String
    ): String? {
        val stream = connection.createStream() ?: return null
        
        return try {
            // Send request line
            val request = "GET $path\r\n"
            val requestBytes = request.encodeToByteArray()
            stream.writeBytes(requestBytes.size j { i: Int -> requestBytes[i] })
            
            // Read response until stream closes
            val responseBuffer = mutableListOf<Byte>()
            while (stream.hasData()) {
                val available = stream.getAvailableBytes()
                val data = stream.readBytes(available)
                for (i in 0 until data.a) {
                    responseBuffer.add(data[i])
                }
            }
            
            String(responseBuffer.toByteArray(), charset(defaultCharset))
            
        } catch (e: Exception) {
            println("HTTP/0.9 request failed: ${e.message}")
            null
        } finally {
            stream.close()
        }
    }
}