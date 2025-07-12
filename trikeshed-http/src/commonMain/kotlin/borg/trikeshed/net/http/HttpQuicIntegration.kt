@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http


import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*

/**
 * HTTP Server Configuration
 */
data class HttpServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val maxHeaderSize: Int = 8192
)

/**
 * Integrates HTTP protocols with QUIC transport
 * Supports HTTP/0.9, 1.0, 1.1 over QUIC streams
 */
class HttpQuicIntegration(
    internal val quicServer: QuicServer,
    internal val config: HttpServerConfig = HttpServerConfig()
) {
    internal val routes = mutableMapOf<String, HttpHandler>()
    internal val middlewares = mutableListOf<HttpMiddleware>()
    
    /**
     * Add a route handler
     */
    fun route(path: String, handler: HttpHandler) {
        routes[path] = handler
    }
    
    /**
     * Add middleware
     */
    fun use(middleware: HttpMiddleware) {
        middlewares.add(middleware)
    }
    
    /**
     * Start HTTP over QUIC server
     */
    suspend fun start() {
        println("Starting HTTP over QUIC server on ${config.host}:${config.port}")
        
        // Set up QUIC connection handler
        quicServer.onConnection(object : ConnectionHandler {
            override suspend fun onConnect(connection: QuicConnection) {
                GlobalScope.launch {
                    handleHttpOverQuic(connection)
                }
            }
            
            override suspend fun onDisconnect(connectionId: ConnectionId) {
                // Handle disconnection
                println("HTTP connection disconnected: $connectionId")
            }
        })
    }
    
    /**
     * Handle HTTP over QUIC connection
     */
    internal suspend fun handleHttpOverQuic(connection: QuicConnection) {
        while (connection.isActive()) {
            val stream = connection.acceptStream() ?: continue
            
            GlobalScope.launch {
                handleHttpStream(stream)
            }
        }
    }
    
    /**
     * Handle HTTP stream
     */
    internal suspend fun handleHttpStream(stream: QuicStream) {
        try {
            // Read initial bytes to determine HTTP version
            val buffer = mutableListOf<Byte>()
            var httpVersion: String? = null
            
            // Read request line
            while (stream.hasData() && buffer.size < config.maxHeaderSize) {
                val byte = stream.readByte()
                buffer.add(byte)
                
                // Check for end of request line
                if (buffer.size >= 2 && 
                    buffer[buffer.size - 2] == '\r'.code.toByte() && 
                    buffer[buffer.size - 1] == '\n'.code.toByte()) {
                    
                    val requestLine = buffer.dropLast(2).toByteArray().decodeToString()
                    httpVersion = detectHttpVersion(requestLine)
                    break
                }
            }
            
            when (httpVersion) {
                "0.9" -> handleHttp09(stream, buffer)
                "1.0" -> handleHttp10(stream, buffer)
                "1.1" -> handleHttp11(stream, buffer)
                else -> handleUnknownVersion(stream)
            }
            
        } catch (e: Exception) {
            println("Error handling HTTP stream: ${e.message}")
            sendErrorResponse(stream, HttpStatus.INTERNAL_SERVER_ERROR)
        } finally {
            stream.close()
        }
    }
    
    /**
     * Detect HTTP version from request line
     */
    internal fun detectHttpVersion(requestLine: String): String? {
        return when {
            requestLine.endsWith("HTTP/0.9") -> "0.9"
            requestLine.endsWith("HTTP/1.0") -> "1.0"
            requestLine.endsWith("HTTP/1.1") -> "1.1"
            !requestLine.contains("HTTP/") -> "0.9" // Assume 0.9 for simple GET requests
            else -> null
        }
    }
    
    /**
     * Handle HTTP/0.9 request
     */
    internal suspend fun handleHttp09(stream: QuicStream, buffer: MutableList<Byte>) {
        val requestLine = buffer.toByteArray().decodeToString().trim()
        val parts = requestLine.split(" ")
        
        if (parts.isNotEmpty() && parts[0] == "GET") {
            val path = if (parts.size > 1) parts[1] else "/"
            
            val handler = findHandler(path)
            if (handler != null) {
                val request = HttpRequest(
                    method = HttpMethod.GET,
                    path = HttpRequestPath(path),
                    headers = 0 j { HttpHeaderName("") j HttpHeaderValue("") },
                    body = byteArrayOf(),
                    version = HttpVersion("HTTP/0.9")
                )
                
                val response = handler(request)
                
                // HTTP/0.9 only sends the body
                stream.writeBytes(response.body.size j { i: Int -> response.body[i] })
            } else {
                val notFound = "Not Found".encodeToByteArray()
                stream.writeBytes(notFound.size j { i: Int -> notFound[i] })
            }
        }
    }
    
    /**
     * Handle HTTP/1.0 request
     */
    internal suspend fun handleHttp10(stream: QuicStream, buffer: MutableList<Byte>) {
        // Continue reading headers
        val fullRequest = readFullRequest(stream, buffer)
        val request = HttpRequest.parse(fullRequest)
        
        // Apply middlewares
        val processedRequest = applyMiddlewares(request)
        
        // Find and execute handler
        val handler = findHandler(processedRequest.path.value)
        val response = if (handler != null) {
            handler(processedRequest)
        } else {
            HttpResponse(
                status = HttpStatus.NOT_FOUND,
                reasonPhrase = HttpStatus.NOT_FOUND.defaultReasonPhrase(),
                headers = 1 j { HttpHeaders.CONTENT_TYPE j HttpHeaderValue("text/plain") },
                body = "Not Found".encodeToByteArray(),
                version = HttpVersion("HTTP/1.0")
            )
        }
        
        // Send response
        val responseBytes = response.toByteArray()
        stream.writeBytes(responseBytes.size j { i: Int -> responseBytes[i] })
        
        // HTTP/1.0 defaults to closing connection unless Keep-Alive
        val keepAlive = hasKeepAlive(request.headers)
        if (!keepAlive) {
            stream.close()
        }
    }
    
    /**
     * Handle HTTP/1.1 request
     */
    internal suspend fun handleHttp11(stream: QuicStream, buffer: MutableList<Byte>) {
        // Continue reading headers
        val fullRequest = readFullRequest(stream, buffer)
        val request = HttpRequest.parse(fullRequest)
        
        // Apply middlewares
        val processedRequest = applyMiddlewares(request)
        
        // Find and execute handler
        val handler = findHandler(processedRequest.path.value)
        val response = if (handler != null) {
            handler(processedRequest)
        } else {
            HttpResponse(
                status = HttpStatus.NOT_FOUND,
                reasonPhrase = HttpStatus.NOT_FOUND.defaultReasonPhrase(),
                headers = 1 j { HttpHeaders.CONTENT_TYPE j HttpHeaderValue("text/plain") },
                body = "Not Found".encodeToByteArray(),
                version = HttpVersion("HTTP/1.1")
            )
        }
        
        // Send response
        val responseBytes = response.toByteArray()
        stream.writeBytes(responseBytes.size j { i: Int -> responseBytes[i] })
        
        // HTTP/1.1 defaults to keeping connection open unless Connection: close
        val closeConnection = hasConnectionClose(request.headers)
        if (closeConnection) {
            stream.close()
        }
    }
    
    /**
     * Handle unknown HTTP version
     */
    internal suspend fun handleUnknownVersion(stream: QuicStream) {
        sendErrorResponse(stream, HttpStatus.HTTP_VERSION_NOT_SUPPORTED)
    }
    
    /**
     * Read full request including headers and body
     */
    internal suspend fun readFullRequest(stream: QuicStream, buffer: MutableList<Byte>): ByteArray {
        // Continue reading until we find end of headers
        var foundEndOfHeaders = false
        
        while (stream.hasData() && buffer.size < config.maxHeaderSize && !foundEndOfHeaders) {
            val byte = stream.readByte()
            buffer.add(byte)
            
            // Check for \r\n\r\n
            if (buffer.size >= 4) {
                val last4 = buffer.takeLast(4)
                if (last4[0] == '\r'.code.toByte() && 
                    last4[1] == '\n'.code.toByte() &&
                    last4[2] == '\r'.code.toByte() && 
                    last4[3] == '\n'.code.toByte()) {
                    foundEndOfHeaders = true
                }
            }
        }
        
        // Read body if Content-Length header present
        val headers = buffer.toByteArray().decodeToString().substringBefore("\r\n\r\n")
        val contentLength = extractContentLength(headers)
        
        if (contentLength > 0) {
            val bodyBuffer = mutableListOf<Byte>()
            while (stream.hasData() && bodyBuffer.size < contentLength) {
                bodyBuffer.add(stream.readByte())
            }
            buffer.addAll(bodyBuffer)
        }
        
        return buffer.toByteArray()
    }
    
    /**
     * Apply middlewares to request
     */
    internal fun applyMiddlewares(request: HttpRequest): HttpRequest {
        var processedRequest = request
        for (middleware in middlewares) {
            processedRequest = middleware.process(processedRequest)
        }
        return processedRequest
    }
    
    /**
     * Find handler for path
     */
    internal fun findHandler(path: String): HttpHandler? {
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
    
    /**
     * Send error response
     */
    internal suspend fun sendErrorResponse(stream: QuicStream, status: HttpStatusCode) {
        val response = HttpResponse(
            status = status,
            reasonPhrase = status.defaultReasonPhrase(),
            headers = 1 j { HttpHeaders.CONTENT_TYPE j HttpHeaderValue("text/plain") },
            body = status.defaultReasonPhrase().value.encodeToByteArray(),
            version = HttpVersion("HTTP/1.1")
        )
        
        val responseBytes = response.toByteArray()
        stream.writeBytes(responseBytes.size j { i: Int -> responseBytes[i] })
    }
    
    /**
     * Check if request has Keep-Alive header
     */
    internal fun hasKeepAlive(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): Boolean {
        for (i in 0 until headers.component1()) {
            val header = headers[i]
            if (header.component1().value.equals("Connection", ignoreCase = true) &&
                header.component2().value.equals("Keep-Alive", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
    
    /**
     * Check if request has Connection: close header
     */
    internal fun hasConnectionClose(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): Boolean {
        for (i in 0 until headers.component1()) {
            val header = headers[i]
            if (header.component1().value.equals("Connection", ignoreCase = true) &&
                header.component2().value.equals("close", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
    
    /**
     * Extract Content-Length from headers
     */
    internal fun extractContentLength(headers: String): Int {
        val lines = headers.split("\r\n")
        for (line in lines) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                val value = line.substringAfter(":").trim()
                return value.toIntOrNull() ?: 0
            }
        }
        return 0
    }
}

/**
 * HTTP middleware interface
 */
interface HttpMiddleware {
    fun process(request: HttpRequest): HttpRequest
}

/**
 * Create default HTTP over QUIC server
 */
fun createHttpQuicServer(quicServer: QuicServer): HttpQuicIntegration {
    return HttpQuicIntegration(quicServer).apply {
        // Add default routes
        route("/") { request ->
            HttpResponse(
                status = HttpStatus.OK,
                headers = 1 j { HttpHeaders.CONTENT_TYPE j HttpHeaderValue("text/html") },
                body = """
                    <html>
                    <body>
                        <h1>HTTP over QUIC Server</h1>
                        <p>Protocol: ${request.version.value}</p>
                        <p>Method: ${request.method.name}</p>
                        <p>Path: ${request.path.value}</p>
                    </body>
                    </html>
                """.trimIndent().encodeToByteArray()
            )
        }
        
        route("/api/*") { request ->
            HttpResponse(
                status = HttpStatus.OK,
                headers = 1 j { HttpHeaders.CONTENT_TYPE j HttpHeaderValue("application/json") },
                body = """{"message": "API endpoint", "path": "${request.path.value}"}""".encodeToByteArray()
            )
        }
    }
}