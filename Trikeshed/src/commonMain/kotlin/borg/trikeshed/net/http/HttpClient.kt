package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.io.AsyncIOEngine
import borg.trikeshed.reactor.SelectableChannel
import borg.trikeshed.reactor.Reactor
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import borg.trikeshed.ccek.AsyncChannelContext

/**
 * HTTP Client implementation with attention-based request handling
 * Supports HTTP/1.1 with keep-alive, pipelining, and range requests
 */
class HttpClient(
    private val ioContext: IOContext,
    private val reactor: Reactor? = null
) {
    private val connections = mutableMapOf<String, HttpConnection>()
    
    // Configuration
    var connectTimeout: Duration = 30.seconds
    var readTimeout: Duration = 30.seconds
    var maxConnectionsPerHost: Int = 6
    var keepAliveTimeout: Duration = 90.seconds
    
    /**
     * Execute an HTTP request with attention-based optimization
     */
    suspend fun execute(request: HttpRequest): HttpResponse {
        val host = extractHost(request)
        val connection = getOrCreateConnection(host)
        
        return connection.sendRequest(request)
    }
    
    /**
     * Execute a range request for efficient partial content retrieval
     */
    suspend fun executeRange(
        url: String,
        start: Long,
        end: Long
    ): HttpResponse {
        val request = buildRangeRequest(url, start, end)
        return execute(request)
    }
    
    /**
     * Execute multiple range requests in parallel (for sparse attention)
     */
    suspend fun executeMultiRange(
        url: String,
        ranges: Indexed<Twin<Long>>
    ): Indexed<HttpResponse> = coroutineScope {
        val deferreds = Array(ranges.a) { i ->
            async {
                val range = ranges.b(i)
                executeRange(url, range.a, range.b)
            }
        }
        
        val responses = deferreds.map { it.await() }.toTypedArray()
        responses.size j responses::get
    }
    
    /**
     * Stream response body for large transfers
     */
    suspend fun stream(
        request: HttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ) {
        val host = extractHost(request)
        val connection = getOrCreateConnection(host)
        
        connection.streamRequest(request, onChunk)
    }
    
    /**
     * Close all connections
     */
    fun close() {
        connections.values.forEach { it.close() }
        connections.clear()
    }
    
    private suspend fun getOrCreateConnection(host: String): HttpConnection {
        val existing = connections[host]
        if (existing?.isAlive() == true) {
            return existing
        }
        
        // Remove dead connection
        existing?.close()
        
        // Create new connection
        val connection = HttpConnection(host, ioContext, reactor)
        connection.connect(connectTimeout)
        connections[host] = connection
        
        return connection
    }
    
    private fun extractHost(request: HttpRequest): String {
        for (i in 0 until request.headers.a) {
            val header = request.headers.b(i)
            if (header.a.value.equals("Host", ignoreCase = true)) {
                return header.b.value
            }
        }
        throw IllegalArgumentException("No Host header in request")
    }
    
    private fun buildRangeRequest(url: String, start: Long, end: Long): HttpRequest {
        val (host, path) = parseUrl(url)
        
        val headers = arrayOf(
            HttpHeaderName("Host") j HttpHeaderValue(host),
            HttpHeaderName("Range") j HttpHeaderValue("bytes=$start-$end"),
            HttpHeaderName("Connection") j HttpHeaderValue("keep-alive"),
            HttpHeaderName("Accept") j HttpHeaderValue("*/*"),
            HttpHeaderName("User-Agent") j HttpHeaderValue("TrikeShed/1.0")
        )
        
        return HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(path),
            headers = headers.size j headers::get
        )
    }
    
    private fun parseUrl(url: String): Pair<String, String> {
        val withoutProtocol = url.removePrefix("http://").removePrefix("https://")
        val firstSlash = withoutProtocol.indexOf('/')
        
        return if (firstSlash == -1) {
            withoutProtocol to "/"
        } else {
            withoutProtocol.substring(0, firstSlash) to withoutProtocol.substring(firstSlash)
        }
    }
    
    // Use context-driven channel for all I/O
    private suspend fun getChannel(): AsyncChannelContext? = coroutineContext[AsyncChannelContext.AsyncChannelKey]

    suspend fun sendRequest(request: HttpRequest): HttpResponse {
        val deferred = CompletableDeferred<HttpResponse>()
        requestQueue.add(deferred)
        val channel = getChannel()
        val requestBytes = request.toByteArray()
        channel?.let {
            it.write(requestBytes)
        } ?: channel?.write(requestBytes)
        lastActivity = System.currentTimeMillis()
        return deferred.await()
    }

    suspend fun streamRequest(
        request: HttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ) {
        val channel = getChannel()
        val requestBytes = request.toByteArray()
        channel?.write(requestBytes)
        val buffer = ByteArray(8192)
        while (true) {
            val read = channel?.read(buffer) ?: break
            if (read <= 0) break
            onChunk(buffer.sliceArray(0 until read))
            lastActivity = System.currentTimeMillis()
        }
    }
}

/**
 * HTTP connection with keep-alive and pipelining support
 */
private class HttpConnection(
    private val host: String,
    private val ioContext: IOContext,
    private val reactor: Reactor?
) {
    private var channel: SelectableChannel? = null
    private var lastActivity: Long = System.currentTimeMillis()
    private val requestQueue = mutableListOf<CompletableDeferred<HttpResponse>>()
    private var pipelineJob: Job? = null
    
    suspend fun connect(timeout: Duration) {
        val (hostname, port) = parseHostPort(host)
        
        // Create socket based on IOContext type
        channel = when (ioContext) {
            is IOContext.UringContext -> createUringSocket(hostname, port)
            is IOContext.NioContext -> createNioSocket(hostname, port)
            is IOContext.KQueueContext -> createKQueueSocket(hostname, port)
            is IOContext.EpollContext -> createEpollSocket(hostname, port)
            else -> createDefaultSocket(hostname, port)
        }
        
        // Start pipeline processor
        pipelineJob = GlobalScope.launch {
            processPipeline()
        }
    }
    
    suspend fun sendRequest(request: HttpRequest): HttpResponse {
        val deferred = CompletableDeferred<HttpResponse>()
        requestQueue.add(deferred)
        val channel = getChannel()
        val requestBytes = request.toByteArray()
        channel?.let {
            it.write(requestBytes)
        } ?: channel?.write(requestBytes)
        lastActivity = System.currentTimeMillis()
        return deferred.await()
    }
    
    suspend fun streamRequest(
        request: HttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ) {
        val channel = getChannel()
        val requestBytes = request.toByteArray()
        channel?.write(requestBytes)
        val buffer = ByteArray(8192)
        while (true) {
            val read = channel?.read(buffer) ?: break
            if (read <= 0) break
            onChunk(buffer.sliceArray(0 until read))
            lastActivity = System.currentTimeMillis()
        }
    }
    
    fun isAlive(): Boolean {
        val idle = System.currentTimeMillis() - lastActivity
        return channel != null && idle < 90_000  // 90 second timeout
    }
    
    fun close() {
        pipelineJob?.cancel()
        channel?.close()
        channel = null
        
        // Fail pending requests
        requestQueue.forEach { 
            it.completeExceptionally(Exception("Connection closed"))
        }
        requestQueue.clear()
    }
    
    private suspend fun processPipeline() {
        val buffer = ByteArray(65536)
        var accumulated = ByteArray(0)
        
        while (isActive) {
            try {
                val read = channel?.read(buffer) ?: break
                if (read <= 0) break
                
                accumulated += buffer.sliceArray(0 until read)
                
                // Try to parse response
                val response = tryParseResponse(accumulated)
                if (response != null) {
                    // Deliver to waiting request
                    val deferred = requestQueue.removeFirstOrNull()
                    deferred?.complete(response.first)
                    
                    // Keep remaining bytes
                    accumulated = response.second
                }
                
            } catch (e: Exception) {
                break
            }
        }
    }
    
    private fun tryParseResponse(bytes: ByteArray): Pair<HttpResponse, ByteArray>? {
        return try {
            val eoh = findEndOfHeaders(bytes)
            if (eoh == -1) return null
            
            val response = HttpResponse.parse(bytes)
            val totalLength = eoh + 4 + response.body.size
            
            if (bytes.size >= totalLength) {
                val remaining = bytes.sliceArray(totalLength until bytes.size)
                response to remaining
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseHostPort(host: String): Pair<String, Int> {
        val colonIndex = host.lastIndexOf(':')
        return if (colonIndex != -1) {
            host.substring(0, colonIndex) to host.substring(colonIndex + 1).toInt()
        } else {
            host to 80
        }
    }
    
    private suspend fun createUringSocket(host: String, port: Int): SelectableChannel {
        // Platform-specific io_uring implementation
        TODO("io_uring socket creation")
    }
    
    private suspend fun createNioSocket(host: String, port: Int): SelectableChannel {
        // NIO socket implementation
        TODO("NIO socket creation") 
    }
    
    private suspend fun createKQueueSocket(host: String, port: Int): SelectableChannel {
        // macOS kqueue implementation
        TODO("kqueue socket creation")
    }
    
    private suspend fun createEpollSocket(host: String, port: Int): SelectableChannel {
        // Linux epoll implementation
        TODO("epoll socket creation")
    }
    
    private suspend fun createDefaultSocket(host: String, port: Int): SelectableChannel {
        // Fallback implementation
        TODO("default socket creation")
    }
}

/**
 * HTTP client builder with fluent API
 */
class HttpClientBuilder {
    private var ioContext: IOContext? = null
    private var reactor: Reactor? = null
    private var connectTimeout: Duration = 30.seconds
    private var readTimeout: Duration = 30.seconds
    private var maxConnectionsPerHost: Int = 6
    
    fun ioContext(context: IOContext) = apply { this.ioContext = context }
    fun reactor(reactor: Reactor) = apply { this.reactor = reactor }
    fun connectTimeout(timeout: Duration) = apply { this.connectTimeout = timeout }
    fun readTimeout(timeout: Duration) = apply { this.readTimeout = timeout }
    fun maxConnectionsPerHost(max: Int) = apply { this.maxConnectionsPerHost = max }
    
    fun build(): HttpClient {
        val context = ioContext ?: IOContext.createDefault()
        return HttpClient(context, reactor).apply {
            this.connectTimeout = this@HttpClientBuilder.connectTimeout
            this.readTimeout = this@HttpClientBuilder.readTimeout
            this.maxConnectionsPerHost = this@HttpClientBuilder.maxConnectionsPerHost
        }
    }
}

// Extension to create default IOContext
private fun IOContext.Companion.createDefault(): IOContext {
    return IOContext.NioContext("default-http-client")
}

// Helper functions
private fun findEndOfHeaders(bytes: ByteArray): Int {
    val pattern = "\r\n\r\n".toByteArray()
    for (i in 0 until bytes.size - pattern.size) {
        if (bytes.sliceArray(i until i + pattern.size).contentEquals(pattern)) {
            return i
        }
    }
    return -1
}