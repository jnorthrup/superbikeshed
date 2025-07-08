package borg.trikeshed.rest

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.measureTime

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
abstract class TrikeShedRestClient(
    protected val baseUrl: String,
    protected val defaultHeaders: HttpHeaders,
    protected val connectionPoolSize: Int,
    protected val defaultTimeout: Duration?,
    protected val interceptors: Indexed<RequestInterceptor>
) : RestClient {
    
    private val connectionPool = ConnectionPool(connectionPoolSize)
    private val logger = RequestLogger()
    
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
        // This would use actual HTTP client (ktor, okhttp, etc)
        // For now, return a mock response
        delay(100) // Simulate network delay
        
        return ResponseMeta(
            statusCode = 200,
            headers = headersOf(
                "content-type" j "application/json",
                "content-length" j "42"
            ),
            duration = Duration.ZERO
        ) j "Mock response for ${request.a.url}".encodeToByteArray()
    }
    
    private fun streamInternal(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>> = flow {
        // Mock streaming implementation
        val meta = ResponseMeta(
            statusCode = 200,
            headers = headersOf("content-type" j "text/event-stream"),
            duration = Duration.ZERO
        )
        
        repeat(5) { i ->
            emit(meta j "Chunk $i\n".encodeToByteArray())
            delay(500)
        }
    }
    
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
                    currentEvent = currentEvent.copy(data = line.substring(5).trim())
                }
                line.startsWith("event:") -> {
                    currentEvent = currentEvent.copy(event = line.substring(6).trim())
                }
                line.startsWith("id:") -> {
                    currentEvent = currentEvent.copy(id = line.substring(3).trim())
                }
                line.isEmpty() && currentEvent.data != null -> {
                    events.add(currentEvent)
                    currentEvent = SseEvent()
                }
            }
        }
        
        return events.size j { i: Int -> events[i] }
    }
}

data class SseEvent(
    val data: String? = null,
    val event: String? = null,
    val id: String? = null,
    val retry: Long? = null
)

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