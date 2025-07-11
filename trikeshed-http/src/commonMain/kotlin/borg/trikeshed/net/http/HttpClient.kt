@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.io.AsyncIOEngine
import borg.trikeshed.reactor.SelectableChannel
import borg.trikeshed.reactor.Reactor
import borg.trikeshed.reactor.SocketFactory
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.ccek.AsyncChannelContext
import java.nio.ByteBuffer

/**
 * HTTP Client implementation with attention-based request handling
 * Supports HTTP/1.1 with keep-alive, pipelining, and range requests
 */
class HttpClient(
    internal val ioContext: IOContext,
    internal val reactor: Reactor? = null
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<HttpClient>
    override val key: CoroutineContext.Key<*> get() = Key
    internal val connections = mutableMapOf<String, HttpConnection>()
    
    // Configuration
    var connectTimeout: Duration = 30.seconds
    var readTimeout: Duration = 30.seconds
    var maxConnectionsPerHost: Int = 6
    var keepAliveTimeout: Duration = 90.seconds
    var maxRetries: Int = 3
    var retryDelay: Duration = 1.seconds
    
    /**
     * Execute an HTTP request with attention-based optimization
     */
    suspend fun execute(request: HttpRequest): HttpResponse {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val host = extractHost(request)
                val connection = getOrCreateConnection(host)
                return connection.sendRequest(request)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(retryDelay)
                    // Remove failed connection from pool
                    val host = extractHost(request)
                    connections.remove(host)?.close()
                }
            }
        }
        
        throw lastException ?: NetworkException("Request failed after $maxRetries attempts")
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
    
    /**
     * Get active connection count for testing
     */
    fun getActiveConnectionCount(): Int = connections.size
    
    internal suspend fun getOrCreateConnection(host: String): HttpConnection {
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
    
    internal fun extractHost(request: HttpRequest): String {
        for (i in 0 until request.headers.a) {
            val header = request.headers.b(i)
            if (header.a.value.equals("Host", ignoreCase = true)) {
                return header.b.value
            }
        }
        throw IllegalArgumentException("No Host header in request")
    }
    
    internal fun buildRangeRequest(url: String, start: Long, end: Long): HttpRequest {
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
    
    internal fun parseUrl(url: String): Pair<String, String> {
        val withoutProtocol = url.removePrefix("http://").removePrefix("https://")
        val firstSlash = withoutProtocol.indexOf('/')
        
        return if (firstSlash == -1) {
            withoutProtocol to "/"
        } else {
            withoutProtocol.substring(0, firstSlash) to withoutProtocol.substring(firstSlash)
        }
    }
    
    // Use context-driven channel for all I/O
    internal suspend fun getChannel(): AsyncChannelContext? = coroutineContext[AsyncChannelContext.AsyncChannelKey]
}

/**
 * HTTP connection with keep-alive and pipelining support
 */
internal class HttpConnection(
    internal val host: String,
    internal val ioContext: IOContext,
    internal val reactor: Reactor?
) {
    internal var channel: SelectableChannel? = null
    internal var lastActivity: Long = System.currentTimeMillis()
    internal val requestQueue = mutableListOf<CompletableDeferred<HttpResponse>>()
    internal var pipelineJob: Job? = null
    
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
        
        val requestBytes = request.toByteArray()
        val buffer = ByteBuffer.wrap(requestBytes)
        
        channel?.let { ch ->
            ch.write(buffer)
            lastActivity = System.currentTimeMillis()
        } ?: throw NetworkException("No active channel")
        
        return deferred.await()
    }
    
    suspend fun streamRequest(
        request: HttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ) {
        val requestBytes = request.toByteArray()
        val buffer = ByteBuffer.wrap(requestBytes)
        
        channel?.let { ch ->
            ch.write(buffer)
            lastActivity = System.currentTimeMillis()
            
            val readBuffer = ByteBuffer.allocate(8192)
            while (true) {
                val read = ch.read(readBuffer)
                if (read <= 0) break
                
                val chunk = readBuffer.array().sliceArray(0 until read)
                onChunk(chunk)
                readBuffer.clear()
                lastActivity = System.currentTimeMillis()
            }
        } ?: throw NetworkException("No active channel")
    }
    
    fun isAlive(): Boolean {
        val idle = System.currentTimeMillis() - lastActivity
        return channel != null && idle < keepAliveTimeout.inWholeMilliseconds
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
    
    internal suspend fun processPipeline() {
        val buffer = ByteBuffer.allocate(65536)
        var accumulated = ByteArray(0)
        
        while (isActive) {
            try {
                val read = channel?.read(buffer) ?: break
                if (read <= 0) break
                
                val newData = buffer.array().sliceArray(0 until read)
                accumulated += newData
                buffer.clear()
                
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
    
    internal fun tryParseResponse(bytes: ByteArray): Pair<HttpResponse, ByteArray>? {
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
    
    internal fun parseHostPort(host: String): Pair<String, Int> {
        val colonIndex = host.lastIndexOf(':')
        return if (colonIndex != -1) {
            host.substring(0, colonIndex) to host.substring(colonIndex + 1).toInt()
        } else {
            host to 80
        }
    }
    
    internal suspend fun createUringSocket(host: String, port: Int): SelectableChannel {
        // Platform-specific io_uring implementation
        TODO("io_uring socket creation")
    }
    
    internal suspend fun createNioSocket(host: String, port: Int): SelectableChannel {
        // Use JVM NIO implementation
        return SocketFactory.createClientSocket(host, port)
    }
    
    internal suspend fun createKQueueSocket(host: String, port: Int): SelectableChannel {
        // macOS kqueue implementation
        TODO("kqueue socket creation")
    }
    
    internal suspend fun createEpollSocket(host: String, port: Int): SelectableChannel {
        // Linux epoll implementation
        TODO("epoll socket creation")
    }
    
    internal suspend fun createDefaultSocket(host: String, port: Int): SelectableChannel {
        // Fallback to NIO implementation
        return createNioSocket(host, port)
    }
}

/**
 * HTTP client builder with fluent API
 */
class HttpClientBuilder {
    internal var ioContext: IOContext? = null
    internal var reactor: Reactor? = null
    internal var connectTimeout: Duration = 30.seconds
    internal var readTimeout: Duration = 30.seconds
    internal var maxConnectionsPerHost: Int = 6
    internal var maxRetries: Int = 3
    internal var retryDelay: Duration = 1.seconds
    
    fun ioContext(context: IOContext) = apply { this.ioContext = context }
    fun reactor(reactor: Reactor) = apply { this.reactor = reactor }
    fun connectTimeout(timeout: Duration) = apply { this.connectTimeout = timeout }
    fun readTimeout(timeout: Duration) = apply { this.readTimeout = timeout }
    fun maxConnectionsPerHost(max: Int) = apply { this.maxConnectionsPerHost = max }
    fun maxRetries(retries: Int) = apply { this.maxRetries = retries }
    fun retryDelay(delay: Duration) = apply { this.retryDelay = delay }
    
    fun build(): HttpClient {
        val context = ioContext ?: IOContext.createDefault()
        return HttpClient(context, reactor).apply {
            this.connectTimeout = this@HttpClientBuilder.connectTimeout
            this.readTimeout = this@HttpClientBuilder.readTimeout
            this.maxConnectionsPerHost = this@HttpClientBuilder.maxConnectionsPerHost
            this.maxRetries = this@HttpClientBuilder.maxRetries
            this.retryDelay = this@HttpClientBuilder.retryDelay
        }
    }
}

// Extension to create default IOContext
internal fun IOContext.Companion.createDefault(): IOContext {
    return IOContext.NioContext("default-http-client")
}

// Helper functions
internal fun findEndOfHeaders(bytes: ByteArray): Int {
    val pattern = "\r\n\r\n".toByteArray()
    for (i in 0 until bytes.size - pattern.size) {
        if (bytes.sliceArray(i until i + pattern.size).contentEquals(pattern)) {
            return i
        }
    }
    return -1
}

// Exception classes
class NetworkException(message: String) : Exception(message)
class ConnectionException(message: String) : Exception(message)
class TimeoutException(message: String) : Exception(message)

// CCEK Key-based API extensions
/**
 * Execute an HTTP request using the HttpClient from context
 */
suspend fun HttpClient.Key.execute(request: HttpRequest): HttpResponse {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("HttpClient not found in context")
    return client.execute(request)
}

/**
 * Execute a range request using the HttpClient from context
 */
suspend fun HttpClient.Key.executeRange(
    url: String,
    start: Long,
    end: Long
): HttpResponse {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("HttpClient not found in context")
    return client.executeRange(url, start, end)
}

/**
 * Execute multiple range requests in parallel using the HttpClient from context
 */
suspend fun HttpClient.Key.executeMultiRange(
    url: String,
    ranges: Indexed<Twin<Long>>
): Indexed<HttpResponse> {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("HttpClient not found in context")
    return client.executeMultiRange(url, ranges)
}

/**
 * Stream response body using the HttpClient from context
 */
suspend fun HttpClient.Key.stream(
    request: HttpRequest,
    onChunk: suspend (ByteArray) -> Unit
) {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("HttpClient not found in context")
    client.stream(request, onChunk)
}

/**
 * Create and configure HttpClient in context
 */
fun HttpClient.Key.create(
    ioContext: IOContext,
    reactor: Reactor? = null,
    configure: HttpClient.() -> Unit = {}
): HttpClient {
    return HttpClient(ioContext, reactor).apply(configure)
}