@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.impl.http

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.channel.impl.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * HTTP Protocol Adapter for channelization.
 * Normalizes HTTP protocol to use unified channel abstractions.
 */
class HttpProtocolAdapter(
    override val config: HttpConfig
) : AbstractProtocolAdapter<HttpMessage, HttpConfig>(config) {
    
    override val protocolName: String = "HTTP"
    override val key: CoroutineContext.Key<*> = Key
    
    companion object Key : CoroutineContext.Key<HttpProtocolAdapter>
    
    /**
     * Parse HTTP message from byte data using BBCursive-style parsing.
     */
    override suspend fun parseMessage(data: ByteIndexed, position: Int): Join<HttpMessage?, Int>? {
        if (position >= data.size) return null
        
        // Find end of headers
        val eoh = findEndOfHeaders(data, position)
        if (eoh == -1) return null // Need more data
        
        val headerBytes = data.slice(position until eoh)
        val bodyBytes = if (eoh + 4 < data.size) {
            data.slice(eoh + 4 until data.size)
        } else {
            ByteArray(0).toIndexed()
        }
        
        // Parse start line
        val startLine = headerBytes.decodeToString().split("\r\n")[0]
        val parts = startLine.split(" ", limit = 3)
        
        return when {
            // HTTP Request
            parts[0] in HttpMethod.values().map { it.name } -> {
                val method = HttpMethod.valueOf(parts[0])
                val path = HttpRequestPath(parts[1])
                val version = HttpVersion(parts[2])
                
                val headers = parseHeaders(headerBytes)
                val request = HttpRequest(method, path, headers, bodyBytes.toByteArray(), version)
                
                (HttpMessage.Request(request) as HttpMessage?) j (eoh + 4 + bodyBytes.size)
            }
            
            // HTTP Response
            parts[0].startsWith("HTTP/") -> {
                val version = HttpVersion(parts[0])
                val status = HttpStatusCode(parts[1].toInt())
                val reason = HttpReasonPhrase(parts[2])
                
                val headers = parseHeaders(headerBytes)
                val response = HttpResponse(status, reason, headers, bodyBytes.toByteArray(), version)
                
                (HttpMessage.Response(response) as HttpMessage?) j (eoh + 4 + bodyBytes.size)
            }
            
            else -> null // Invalid HTTP message
        }
    }
    
    /**
     * Serialize HTTP message to bytes.
     */
    override suspend fun serializeMessage(message: HttpMessage): ByteIndexed {
        val bytes = when (message) {
            is HttpMessage.Request -> message.request.toByteArray()
            is HttpMessage.Response -> message.response.toByteArray()
        }
        return bytes.toIndexed()
    }
    
    /**
     * Handle HTTP message processing with protocol context.
     */
    override suspend fun handleMessage(message: HttpMessage, context: ProtocolContext): Flow<HttpMessage> = flow {
        when (message) {
            is HttpMessage.Request -> {
                handleWithServiceFlow<HttpRequestHandler>(
                    context = context,
                    message = message,
                    defaultResponse = HttpMessage.Response(createDefaultResponse(message.request))
                ) { handler ->
                    HttpMessage.Response(handler.handleRequest(message.request))
                }.collect { emit(it) }
            }
            
            is HttpMessage.Response -> {
                handleWithServiceFlow<HttpResponseHandler>(
                    context = context,
                    message = message,
                    defaultResponse = message
                ) { handler ->
                    HttpMessage.Response(handler.handleResponse(message.response))
                }.collect { emit(it) }
            }
        }
    }
    
    /**
     * Create HTTP protocol channel wrapper.
     */
    override fun wrapChannel(channel: Channel): ProtocolChannel<HttpMessage> {
        return HttpProtocolChannel(this, channel)
    }
    
    internal fun findEndOfHeaders(data: ByteIndexed, start: Int): Int {
        for (i in start until data.size - 3) {
            if (data[i] == '\r'.code.toByte() && 
                data[i + 1] == '\n'.code.toByte() &&
                data[i + 2] == '\r'.code.toByte() && 
                data[i + 3] == '\n'.code.toByte()) {
                return i
            }
        }
        return -1
    }
    
    internal fun parseHeaders(headerBytes: ByteIndexed): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headerLines = headerBytes.decodeToString().split("\r\n")
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        
        for (i in 1 until headerLines.size) {
            val line = headerLines[i]
            if (line.isBlank()) continue
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val name = HttpHeaderName(line.substring(0, colonIndex).trim())
                val value = HttpHeaderValue(line.substring(colonIndex + 1).trim())
                headers.add(name j value)
            }
        }
        
        return headers.size j { i: Int -> headers[i] }
    }
    
    internal fun createDefaultResponse(request: HttpRequest): HttpResponse {
        return when (request.method) {
            HttpMethod.GET -> HttpResponse(
                status = HttpStatusCode(200),
                reasonPhrase = HttpReasonPhrase("OK"),
                headers = EmptyIndexed,
                body = "Default response for ${request.path.value}".encodeToByteArray()
            )
            else -> HttpResponse(
                status = HttpStatusCode(405),
                reasonPhrase = HttpReasonPhrase("Method Not Allowed"),
                headers = EmptyIndexed,
                body = "Method ${request.method} not supported".encodeToByteArray()
            )
        }
    }
}

/**
 * HTTP protocol channel implementation.
 */
class HttpProtocolChannel(
    internal val adapter: HttpProtocolAdapter,
    internal val channel: Channel
) : EnhancedProtocolChannel<HttpMessage>(adapter, channel) {
    
    /**
     * Send HTTP request.
     */
    suspend fun sendRequest(request: HttpRequest) {
        sendMessage(HttpMessage.Request(request))
    }
    
    /**
     * Send HTTP response.
     */
    suspend fun sendResponse(response: HttpResponse) {
        sendMessage(HttpMessage.Response(response))
    }
    
    /**
     * Get incoming HTTP requests.
     */
    fun incomingRequests(): Flow<HttpRequest> = filterAndTransform<HttpMessage.Request, HttpRequest> { it.request }
    
    /**
     * Get incoming HTTP responses.
     */
    fun incomingResponses(): Flow<HttpResponse> = filterAndTransform<HttpMessage.Response, HttpResponse> { it.response }
}

/**
 * HTTP message types for channelization.
 */
sealed class HttpMessage {
    data class Request(val request: HttpRequest) : HttpMessage()
    data class Response(val response: HttpResponse) : HttpMessage()
}

/**
 * HTTP configuration for protocol adapter.
 */
data class HttpConfig(
    val maxHeaderSize: Int = 8192,
    val maxBodySize: Int = 1024 * 1024, // 1MB
    val keepAlive: Boolean = true,
    val compression: Boolean = false
)

/**
 * HTTP request handler interface.
 */
interface HttpRequestHandler : CoroutineContext.Element {
    suspend fun handleRequest(request: HttpRequest): HttpResponse
    
    companion object Key : CoroutineContext.Key<HttpRequestHandler>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * HTTP response handler interface.
 */
interface HttpResponseHandler : CoroutineContext.Element {
    suspend fun handleResponse(response: HttpResponse): HttpResponse
    
    companion object Key : CoroutineContext.Key<HttpResponseHandler>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Default HTTP request handler.
 */
class DefaultHttpRequestHandler(
    internal val routes: Map<String, suspend (HttpRequest) -> HttpResponse>
) : HttpRequestHandler {
    
    override suspend fun handleRequest(request: HttpRequest): HttpResponse {
        val route = routes[request.path.value]
        return route?.invoke(request) ?: createNotFoundResponse(request)
    }
    
    internal fun createNotFoundResponse(request: HttpRequest): HttpResponse {
        return HttpResponse(
            status = HttpStatusCode(404),
            reasonPhrase = HttpReasonPhrase("Not Found"),
            headers = EmptyIndexed,
            body = "Route ${request.path.value} not found".encodeToByteArray()
        )
    }
}

/**
 * HTTP protocol factory for creating channelized HTTP servers.
 */
class HttpProtocolFactory {
    
    /**
     * Create HTTP server with channelized protocol handling.
     */
    suspend fun createServer(
        port: Int,
        config: HttpConfig = HttpConfig(),
        routes: Map<String, suspend (HttpRequest) -> HttpResponse> = emptyMap()
    ): HttpProtocolServer {
        val adapter = HttpProtocolAdapter(config)
        val handler = DefaultHttpRequestHandler(routes)
        
        return HttpProtocolServer(port, adapter, handler)
    }
}

/**
 * HTTP protocol server using channelized architecture.
 */
class HttpProtocolServer(
    internal val port: Int,
    internal val adapter: HttpProtocolAdapter,
    internal val handler: HttpRequestHandler
) {
    internal val registry = ProtocolRegistry()
    internal val monitor = ProtocolMonitor()
    
    init {
        registry.register(adapter)
    }
    
    /**
     * Start the HTTP server.
     */
    suspend fun start() = coroutineScope {
        println("HTTP Protocol Server starting on port $port")
        
        // In real implementation, would bind to socket
        // For now, simulate connection handling
        launch { acceptConnections() }
    }
    
    internal suspend fun acceptConnections() {
        // Simulate connection acceptance
        while (true) {
            delay(100)
            // Create mock channel for demonstration
            val mockChannel = createMockChannel()
            val protocolChannel = registry.createChannel<HttpMessage, HttpConfig>("HTTP", mockChannel, adapter.config)
            
            if (protocolChannel != null) {
                val monitoredChannel = monitor.monitorChannel(protocolChannel, "HTTP")
                launch { handleHttpConnection(monitoredChannel) }
            }
        }
    }
    
    internal suspend fun handleHttpConnection(channel: ProtocolChannel<HttpMessage>) {
        val context = ProtocolContext(coroutineContext + handler)
        
        channel.incomingMessages()
            .flatMapLatest { message ->
                adapter.handleMessage(message, context)
            }
            .collect { response ->
                channel.sendMessage(response)
            }
    }
    
    internal fun createMockChannel(): Channel {
        // Mock channel implementation for demonstration
        return object : Channel {
            override val id: ChannelId = ChannelId.random()
            override val isOpen: Boolean = true
            
            override suspend fun read(buffer: ByteBuffer): Int {
                // Mock read implementation
                return 0
            }
            
            override suspend fun write(buffer: ByteBuffer): Int {
                // Mock write implementation
                return buffer.remaining()
            }
            
            override suspend fun flush() {
                // Mock flush implementation
            }
            
            override suspend fun close() {
                // Mock close implementation
            }
        }
    }
} 