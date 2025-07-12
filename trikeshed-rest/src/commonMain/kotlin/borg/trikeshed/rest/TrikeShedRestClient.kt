package borg.trikeshed.rest

import borg.trikeshed.lib.*
// import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of RestClient using TrikeShed data structures
 * 
 * This implementation dogfoods TrikeShed patterns throughout:
 * - Connection pool using Indexed<Connection>
 * - Request/response as Join<Meta, Body>
 * - Headers as indexed pairs
 * - Batch operations returning Indexed results
 * 
 * This is a pure Kotlin common implementation that can be extended
 * by platform-specific HTTP engines
 */

// HTTP method is just a String in RestClient.kt

// Temporary HTTP types
data class HttpRequestPath(val value: String)
data class HttpHeaderName(val value: String)
data class HttpHeaderValue(val value: String)
abstract class TrikeShedRestClient(
    protected val baseUrl: String,
    protected val defaultHeaders: HttpHeaders,
    protected val connectionPoolSize: Int,
    protected val defaultTimeout: Duration?,
    protected val interceptors: Indexed<RequestInterceptor>
) : RestClient, CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TrikeShedRestClient>
    override val key: CoroutineContext.Key<*> get() = Key
    
    private val connectionPool = ConnectionPool(connectionPoolSize)
    private val logger = RequestLogger()
    // private val httpClient = createHttpClient() // Disabled until net.http is available
    
    override suspend fun execute(request: HttpRequest): HttpResponse = coroutineScope {
        // Apply interceptors
        var processedRequest = request
        for (i in 0 until interceptors.a) {
            processedRequest = interceptors.b(i).intercept(processedRequest)
        }
        
        // Merge default headers with request headers
        val mergedHeaders = mergeHeaders(defaultHeaders, processedRequest.a.headers)
        val finalRequest = processedRequest.a.copy(
            url = resolveUrl(processedRequest.a.url),
            headers = mergedHeaders,
            timeout = processedRequest.a.timeout ?: defaultTimeout
        ) j processedRequest.b
        
        // Execute with timing
        val response: HttpResponse
        val duration = measureTime {
            response = executeInternal(finalRequest)
        }
        
        // Log the request/response
        logger.log(finalRequest, response)
        
        // Return response with duration
        response.a.copy(duration = duration) j response.b
    }
    
    override suspend fun stream(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>> = flow {
        // Apply interceptors
        var processedRequest = request
        for (i in 0 until interceptors.a) {
            processedRequest = interceptors.b(i).intercept(processedRequest)
        }
        
        val finalRequest = processedRequest.a.copy(
            url = resolveUrl(processedRequest.a.url),
            headers = mergeHeaders(defaultHeaders, processedRequest.a.headers)
        ) j processedRequest.b
        
        // Stream implementation would connect and emit chunks
        streamInternal(finalRequest).collect { chunk ->
            emit(chunk)
        }
    }
    
    override suspend fun batch(requests: Indexed<HttpRequest>): Indexed<HttpResponse> = coroutineScope {
        // Execute requests in parallel with coroutines
        val deferreds = (0 until requests.a).map { i ->
            async {
                execute(requests.b(i))
            }
        }
        
        // Collect results maintaining order
        requests.a j { i: Int ->
            deferreds[i].await()
        }
    }
    
    private suspend fun executeInternal(request: HttpRequest): HttpResponse {
        // Convert to HTTP request format
        val httpRequest = convertToHttpRequest(request)
        
        // Execute using HTTP client
        val httpResponse = httpClient.execute(httpRequest)
        
        // Convert back to REST response format
        return convertToRestResponse(httpResponse)
    }
    
    private fun streamInternal(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>> = flow {
        // Convert to HTTP request format
        val httpRequest = convertToHttpRequest(request)
        
        // Stream using HTTP client
        httpClient.stream(httpRequest) { chunk ->
            val meta = ResponseMeta(
                statusCode = 200, // Would need to parse from HTTP response
                headers = headersOf("content-type" j "application/octet-stream"),
                duration = Duration.ZERO
            )
            emit(meta j chunk)
        }
    }
    
    /*
    private fun convertToHttpRequest(request: HttpRequest): borg.trikeshed.net.http.HttpRequest {
        val method = request.a.method.uppercase()
        
        val path = HttpRequestPath(request.a.url)
        
        val headers = Array(request.a.headers.a) { i ->
            val header = request.a.headers.b(i)
            HttpHeaderName(header.a) j HttpHeaderValue(header.b)
        }
        
        return borg.trikeshed.net.http.HttpRequest(
            method = method,
            path = path,
            headers = headers.size j headers::get,
            body = request.b ?: ByteArray(0)
        )
    }
    */
    
    /*
    private fun convertToRestResponse(httpResponse: borg.trikeshed.net.http.HttpResponse): HttpResponse {
        val meta = ResponseMeta(
            statusCode = httpResponse.status.value,
            headers = convertHeaders(httpResponse.headers),
            duration = Duration.ZERO
        )
        
        return meta j httpResponse.body
    }
    */
    
    /*
    private fun convertHeaders(httpHeaders: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): HttpHeaders {
        val headers = Array(httpHeaders.a) { i ->
            val header = httpHeaders.b(i)
            header.a.value j header.b.value
        }
        return headers.size j headers::get
    }
    */
    
    private fun resolveUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "$baseUrl/$url".replace("//", "/").replace(":/", "://")
        }
    }
    
    private fun mergeHeaders(default: HttpHeaders, request: HttpHeaders): HttpHeaders {
        // Create a map to handle duplicates (request headers override defaults)
        val headerMap = mutableMapOf<String, String>()
        
        // Add default headers
        for (i in 0 until default.a) {
            val header = default.b(i)
            headerMap[header.a.lowercase()] = header.b
        }
        
        // Override with request headers
        for (i in 0 until request.a) {
            val header = request.b(i)
            headerMap[header.a.lowercase()] = header.b
        }
        
        // Convert back to HttpHeaders
        return headerMap.size j { i: Int ->
            val entry = headerMap.entries.elementAt(i)
            entry.key j entry.value
        }
    }
    
    /*
    private fun createHttpClient(): borg.trikeshed.net.http.HttpClient {
        val ioContext = borg.trikeshed.io.IOContext.NioContext("rest-client")
        return borg.trikeshed.net.http.HttpClient(ioContext).apply {
            connectTimeout = defaultTimeout ?: 30.seconds
            readTimeout = defaultTimeout ?: 30.seconds
            maxConnectionsPerHost = connectionPoolSize
        }
    }
    */
    
    fun close() {
        // httpClient.close() // Disabled until net.http is available
    }
}

// WebSocket support using TrikeShed patterns
interface WebSocketClient {
    suspend fun connect(
        url: String,
        headers: HttpHeaders = 0 j { _: Int -> "" j "" }
    ): WebSocketSession
}

interface WebSocketSession {
    val incoming: Flow<WebSocketFrame>
    suspend fun send(frame: WebSocketFrame)
    suspend fun close(reason: CloseReason? = null)
}

// WebSocket frame as Join
typealias WebSocketFrame = Join<FrameType, ByteArray>

enum class FrameType {
    TEXT, BINARY, PING, PONG, CLOSE
}

data class CloseReason(val code: Int, val message: String)

// SSE (Server-Sent Events) support
class SseClient(private val restClient: RestClient) {
    suspend fun connect(
        url: String,
        headers: HttpHeaders = 0 j { _: Int -> "" j "" }
    ): Flow<SseEvent> = flow {
        val request = RequestMeta("GET", url, headers) j null
        
        restClient.stream(request).collect { chunk ->
            // Parse SSE format
            val data = chunk.b.decodeToString()
            parseSseEvents(data).forEach { event ->
                emit(event)
            }
        }
    }
    
    private fun parseSseEvents(data: String): Indexed<SseEvent> {
        val events = mutableListOf<SseEvent>()
        val lines = data.split("\n")
        
        var currentEvent = SseEvent()
        for (line in lines) {
            when {
                line.startsWith("data:") -> {
                    currentEvent = currentEvent.copy(
                        data = currentEvent.data + line.substring(5).trim()
                    )
                }
                line.startsWith("event:") -> {
                    currentEvent = currentEvent.copy(
                        event = line.substring(6).trim()
                    )
                }
                line.startsWith("id:") -> {
                    currentEvent = currentEvent.copy(
                        id = line.substring(3).trim()
                    )
                }
                line.isEmpty() -> {
                    if (currentEvent.data.isNotEmpty()) {
                        events.add(currentEvent)
                        currentEvent = SseEvent()
                    }
                }
            }
        }
        
        return events.size j { i: Int -> events[i] }
    }
}

data class SseEvent(
    val data: String = "",
    val event: String = "message",
    val id: String = ""
)

// Connection pool implementation
class ConnectionPool(private val maxConnections: Int) {
    private val connections = mutableListOf<Any>()
    
    suspend fun acquire(): Any? {
        // Implementation would manage connection lifecycle
        return null
    }
    
    suspend fun release(connection: Any) {
        // Implementation would return connection to pool
    }
}

// Request logger implementation
class RequestLogger {
    fun log(request: HttpRequest, response: HttpResponse) {
        // Implementation would log request/response details
    }
}

// Request interceptor interface
interface RequestInterceptor {
    suspend fun intercept(request: HttpRequest): HttpRequest
}

// Rate limiting using TrikeShed
class RateLimiter(
    private val maxRequests: Int,
    private val windowDuration: Duration
) : RequestInterceptor {
    private val requestTimes: MutableList<Long> = mutableListOf()
    
    override suspend fun intercept(request: HttpRequest): HttpRequest {
        val now = System.currentTimeMillis()
        val windowStart = now - windowDuration.inWholeMilliseconds
        
        // Remove old requests outside the window
        requestTimes.removeAll { it < windowStart }
        
        // Check if we're at the limit
        if (requestTimes.size >= maxRequests) {
            val oldestRequest = requestTimes.minOrNull() ?: 0L
            val waitTime = windowStart - oldestRequest
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
        
        requestTimes.add(now)
        return request
    }
}

// Circuit breaker pattern
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Duration = Duration.seconds(60)
) : RequestInterceptor {
    private var failureCount = 0
    private var lastFailureTime: Long = 0
    private var state: State = State.CLOSED
    
    enum class State {
        CLOSED,    // Normal operation
        OPEN,      // Failing, reject requests
        HALF_OPEN  // Testing if service recovered
    }
    
    override suspend fun intercept(request: HttpRequest): HttpRequest {
        when (state) {
            State.OPEN -> {
                val now = System.currentTimeMillis()
                if (now - lastFailureTime > resetTimeout.inWholeMilliseconds) {
                    state = State.HALF_OPEN
                    failureCount = 0
                } else {
                    throw CircuitBreakerOpenException("Circuit breaker is OPEN")
                }
            }
            State.HALF_OPEN -> {
                // Allow request through for testing
            }
            State.CLOSED -> {
                // Normal operation
            }
        }
        return request
    }
    
    fun recordSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                state = State.CLOSED
                failureCount = 0
            }
            else -> {
                failureCount = 0
            }
        }
    }
    
    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        if (failureCount >= failureThreshold) {
            state = State.OPEN
        }
    }
}

class CircuitBreakerOpenException(message: String) : Exception(message)

// Multipart form data using TrikeShed
class MultipartFormData {
    private var parts: Indexed<FormPart> = 0 j { _: Int -> FormPart("", ByteArray(0), emptyMap()) }
    
    data class FormPart(
        val name: String,
        val content: ByteArray,
        val headers: Map<String, String>
    )
    
    fun addPart(name: String, content: ByteArray, contentType: String? = null) {
        val headers = contentType?.let { mapOf("Content-Type" to it) } ?: emptyMap()
        val currentSize = parts.a
        parts = (currentSize + 1) j { i: Int ->
            if (i < currentSize) parts.b(i) else FormPart(name, content, headers)
        }
    }
    
    fun addFile(name: String, filename: String, content: ByteArray, contentType: String) {
        val headers = mapOf(
            "Content-Disposition" to "form-data; name=\"$name\"; filename=\"$filename\"",
            "Content-Type" to contentType
        )
        val currentSize = parts.a
        parts = (currentSize + 1) j { i: Int ->
            if (i < currentSize) parts.b(i) else FormPart(name, content, headers)
        }
    }
    
    fun build(): Join<HttpHeaders, ByteArray> {
        val boundary = "----TrikeShedBoundary${System.currentTimeMillis()}"
        val contentType = "multipart/form-data; boundary=$boundary"
        
        val body = buildString {
            for (i in 0 until parts.a) {
                val part = parts.b(i)
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"${part.name}\"\r\n")
                part.headers.forEach { (key, value) ->
                    append("$key: $value\r\n")
                }
                append("\r\n")
                append(part.content.decodeToString())
                append("\r\n")
            }
            append("--$boundary--\r\n")
        }.encodeToByteArray()
        
        return headersOf("Content-Type" j contentType) j body
    }
}

// CCEK Key-based API extensions for TrikeShedRestClient
/**
 * Execute an HTTP request using the TrikeShedRestClient from context
 */
suspend fun TrikeShedRestClient.Key.execute(request: HttpRequest): HttpResponse {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("TrikeShedRestClient not found in context")
    return client.execute(request)
}

/**
 * Stream an HTTP request using the TrikeShedRestClient from context
 */
suspend fun TrikeShedRestClient.Key.stream(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>> {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("TrikeShedRestClient not found in context")
    return client.stream(request)
}

/**
 * Execute batch HTTP requests using the TrikeShedRestClient from context
 */
suspend fun TrikeShedRestClient.Key.batch(requests: Indexed<HttpRequest>): Indexed<HttpResponse> {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("TrikeShedRestClient not found in context")
    return client.batch(requests)
}

/**
 * Close the REST client using the TrikeShedRestClient from context
 */
fun TrikeShedRestClient.Key.close() {
    val client = coroutineContext[this] 
        ?: throw IllegalStateException("TrikeShedRestClient not found in context")
    client.close()
}

/**
 * Create a concrete implementation of TrikeShedRestClient
 */
fun TrikeShedRestClient.Key.create(
    baseUrl: String,
    defaultHeaders: HttpHeaders = 0 j { _: Int -> "" j "" },
    connectionPoolSize: Int = 10,
    defaultTimeout: Duration? = null,
    interceptors: Indexed<RequestInterceptor> = 0 j { _: Int -> 
        object : RequestInterceptor {
            override suspend fun intercept(request: HttpRequest): HttpRequest = request
        }
    }
): TrikeShedRestClient {
    return object : TrikeShedRestClient(baseUrl, defaultHeaders, connectionPoolSize, defaultTimeout, interceptors) {
        // Concrete implementation
    }
}