@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// Type alias for HTTP handler
typealias HttpHandler = suspend (HttpRequest) -> HttpResponse

/**
 * HTTP/3 Server implementation
 */
class HttpQuicServer(
    internal val quicServer: QuicServer,
    internal val config: HttpServerConfig = HttpServerConfig()
) {
    internal val routes = mutableMapOf<String, HttpHandler>()
    internal val middlewares = mutableListOf<HttpMiddleware>()
    
    /**
     * Add route handler
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
     * Start HTTP/3 server
     */
    suspend fun start() {
        quicServer.start()
        
        // Set up QUIC connection handler
        quicServer.onConnection(object : ConnectionHandler {
            override suspend fun onConnect(connection: QuicConnection) {
                kotlinx.coroutines.GlobalScope.launch {
                    handleConnection(connection)
                }
            }
            
            override suspend fun onDisconnect(connectionId: ConnectionId) {
                // Handle disconnection
                println("HTTP/3 connection disconnected: $connectionId")
            }
        })
    }
    
    /**
     * Handle QUIC connection
     */
    internal suspend fun handleConnection(connection: QuicConnection) {
        try {
            // Initialize HTTP/3 connection
            val http3Connection = Http3Connection(connection, Http3Connection.Role.SERVER)
            http3Connection.initialize()
            
            // Process streams
            while (connection.isActive()) {
                val stream = connection.acceptStream() ?: continue
                
                kotlinx.coroutines.GlobalScope.launch {
                    handleStream(stream)
                }
            }
        } catch (e: Exception) {
            println("Error handling connection: ${e.message}")
            connection.close()
        }
    }
    
    /**
     * Handle QUIC stream
     */
    internal suspend fun handleStream(stream: QuicStream) {
        try {
            val frames = mutableListOf<Http3Frame>()
            
            // Read frames from stream
            while (stream.hasData()) {
                val frameData = stream.readBytes(1024) // Read chunk
                val (frame, _) = Http3Frame.decode(frameData)
                if (frame != null) {
                    frames.add(frame)
                }
            }
            
            // Parse request
            val request = parseFramesToRequest(frames)
            
            // Apply middlewares
            val processedRequest = applyMiddlewares(request)
            
            // Find and execute handler
            val handler = findHandler(processedRequest.path.value)
            val response = if (handler != null) {
                handler(processedRequest)
            } else {
                HttpResponse(
                    status = HttpStatus.NOT_FOUND,
                    headers = emptyIndexed(),
                    body = "Not Found".encodeToByteArray()
                )
            }
            
            // Send response
            sendHttp3Response(stream, response)
            
        } catch (e: Exception) {
            println("Error handling stream: ${e.message}")
            stream.close()
        }
    }
    
    /**
     * Parse HTTP/3 frames to extract headers
     */
    internal fun parseHeaders(frameData: Indexed<Byte>): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        var offset = 0
        
        while (offset < frameData.size) {
            val nameLength = frameData[offset].toInt()
            offset++
            
            val name = frameData.play.drop(offset).take(nameLength).play.joinToString("") { it.toChar().toString() }
            offset += nameLength
            
            val valueLength = frameData[offset].toInt()
            offset++
            
            val value = frameData.play.drop(offset).take(valueLength).play.joinToString("") { it.toChar().toString() }
            offset += valueLength
            
            headers.add(HttpHeaderName(name) j HttpHeaderValue(value))
        }
        
        return headers.toIdx()
    }
    
    /**
     * Parse frames into HTTP request
     */
    internal fun parseFramesToRequest(frames: List<Http3Frame>): HttpRequest {
        var method = HttpMethod.GET
        var path = HttpRequestPath("/")
        var headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>> = emptyIndexed()
        var body: ByteArray = byteArrayOf()

        for (frame in frames) {
            when (frame.type) {
                Http3Protocol.FrameTypes.HEADERS -> {
                    headers = parseHeaders(frame.payload)
                    // Parse pseudo-headers
                    val methodHeader = headers.play.find { it.a.value == ":method" }?.b?.value
                    method = if(methodHeader != null) HttpMethod.valueOf(methodHeader) else HttpMethod.GET

                    val pathHeader = headers.play.find { it.a.value == ":path" }?.b?.value
                    path = if(pathHeader != null) HttpRequestPath(pathHeader) else HttpRequestPath("/")
                }
                Http3Protocol.FrameTypes.DATA -> {
                    body = frame.payload.play.toList().toByteArray()
                }
                else -> {}
            }
        }
        
        return HttpRequest(method, path, headers, body)
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
        // Simple exact match routing
        return routes[path] ?: routes["*"]
    }
    
    /**
     * Send HTTP/3 response
     */
    internal suspend fun sendHttp3Response(stream: QuicStream, response: HttpResponse) {
        // Send headers frame
        val headersFrame = createHeadersFrame(response)
        stream.writeBytes(headersFrame)
        
        // Send data frame
        val dataFrame = createDataFrame(response.body)
        stream.writeBytes(dataFrame)
    }
    
    /**
     * Create headers frame
     */
    internal fun createHeadersFrame(response: HttpResponse): Indexed<Byte> {
        val headersMap = mutableMapOf<String, String>()
        headersMap[":status"] = response.status.value.toString()
        response.headers.play.forEach { headersMap[it.a.value] = it.b.value }

        // Serialize headers
        val headerData = mutableListOf<Byte>()
        for ((name, value) in headersMap) {
            headerData.add(name.length.toByte())
            headerData.addAll(name.encodeToByteArray().toList())
            headerData.add(value.length.toByte())
            headerData.addAll(value.encodeToByteArray().toList())
        }
        
        val frameData = mutableListOf<Byte>()
        frameData.add(0x01) // HEADERS frame type
        frameData.addAll(encodeVarInt(headerData.size.toLong()))
        frameData.addAll(headerData)
        
        return frameData.toIdx()
    }
    
    /**
     * Create data frame
     */
    internal fun createDataFrame(body: ByteArray): Indexed<Byte> {
        val frameData = mutableListOf<Byte>()
        frameData.add(0x00) // DATA frame type
        frameData.addAll(encodeVarInt(body.size.toLong()))
        frameData.addAll(body.toList())
        
        return frameData.toIdx()
    }
    
    /**
     * Encode variable-length integer
     */
    internal fun encodeVarInt(value: Long): List<Byte> {
        // Simplified VarInt encoding
        if (value < 64) return listOf(value.toByte())
        // Add more complex cases if needed
        return listOf()
    }
}

// === QUIC STREAM EXTENSIONS ===

suspend fun QuicStream.readByte(): Byte {
    val bytes = readBytes(1)
    return bytes[0]
}

suspend fun QuicStream.readVarInt(): Long {
    // Simplified VarInt decoding
    return readByte().toLong()
}

suspend fun QuicStream.readBytes(length: Int): Indexed<Byte> {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[i] = readByte()
    }
    return length j { bytes[it] }
}

suspend fun QuicStream.writeBytes(data: Indexed<Byte>) {
    for (i in 0 until data.size) {
        writeByte(data[i])
    }
}

suspend fun QuicStream.writeByte(byte: Byte) {
    // Placeholder implementation
}

fun QuicStream.hasData(): Boolean {
    // Placeholder implementation
    return false
}

fun QuicConnection.isActive(): Boolean {
    // Placeholder implementation
    return true
}

suspend fun QuicConnection.acceptStream(): QuicStream? {
    // Placeholder implementation
    return null
} 