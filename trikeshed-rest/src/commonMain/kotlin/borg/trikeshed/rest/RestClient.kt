package borg.trikeshed.rest

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * TrikeShed REST Client - A functional REST client using TrikeShed data structures
 * 
 * Dogfooding TrikeShed patterns:
 * - Join<A,B> instead of Pair
 * - Indexed<T> instead of List<T>
 * - Series<T> for streaming responses
 * - MetaSeries for metadata-enriched responses
 */

// Core types using TrikeShed structures  
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}
typealias HttpHeaders = Join<Int, (Int) -> Join<String, String>>
typealias HttpRequest = Join<RequestMeta, RequestBody?>
typealias HttpResponse = Join<ResponseMeta, ResponseBody>
typealias RequestBody = ByteArray
typealias ResponseBody = ByteArray

data class RequestMeta(
    val method: HttpMethod,
    val url: String,
    val headers: HttpHeaders,
    val timeout: Duration? = null
)

data class ResponseMeta(
    val statusCode: Int,
    val headers: HttpHeaders,
    val duration: Duration
)

// REST client interface
interface RestClient {
    suspend fun execute(request: HttpRequest): HttpResponse
    suspend fun stream(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>>
    suspend fun batch(requests: Indexed<HttpRequest>): Indexed<HttpResponse>
}

// Extension functions for common HTTP methods
suspend fun RestClient.get(
    url: String,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta(HttpMethod.GET, url, headers) j null
)

suspend fun RestClient.post(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta(HttpMethod.POST, url, headers) j body
)

suspend fun RestClient.put(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta(HttpMethod.PUT, url, headers) j body
)

suspend fun RestClient.delete(
    url: String,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta(HttpMethod.DELETE, url, headers) j null
)

suspend fun RestClient.patch(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta(HttpMethod.PATCH, url, headers) j body
)

// Builder pattern using TrikeShed structures
class RestClientBuilder {
    private var baseUrl: String = ""
    private var defaultHeaders: HttpHeaders = 0 j { _: Int -> "" j "" }
    private var connectionPoolSize: Int = 10
    private var requestTimeout: Duration? = null
    private var interceptors: Indexed<RequestInterceptor> = 0 j { _: Int -> IdentityInterceptor }
    
    fun baseUrl(url: String) = apply { baseUrl = url }
    
    fun defaultHeaders(headers: HttpHeaders) = apply { defaultHeaders = headers }
    
    fun addHeader(key: String, value: String) = apply {
        val currentSize = defaultHeaders.component1()
        defaultHeaders = (currentSize + 1) j { i: Int ->
            if (i < currentSize) defaultHeaders.component2()(i) else (key j value)
        }
    }
    
    fun connectionPoolSize(size: Int) = apply { connectionPoolSize = size }
    
    fun requestTimeout(timeout: Duration) = apply { requestTimeout = timeout }
    
    fun addInterceptor(interceptor: RequestInterceptor) = apply {
        val currentSize = interceptors.component1()
        interceptors = (currentSize + 1) j { i: Int ->
            if (i < currentSize) interceptors.component2()(i) else interceptor
        }
    }
    
    fun build(): RestClient = PlatformRestClient(
        baseUrl, defaultHeaders, connectionPoolSize, requestTimeout, interceptors
    )
}

// Request interceptor defined in TrikeShedRestClient.kt
/*
interface RequestInterceptor {
    suspend fun intercept(request: HttpRequest): HttpRequest
}
*/

object IdentityInterceptor : RequestInterceptor {
    override suspend fun intercept(request: HttpRequest): HttpRequest = request
}

// Response transformers using TrikeShed patterns
interface ResponseTransformer<T> {
    fun transform(response: HttpResponse): T
}

// JSON response transformer (placeholder - would use actual JSON library)
class JsonTransformer<T> : ResponseTransformer<T> {
    override fun transform(response: HttpResponse): T {
        // Would use kotlinx.serialization or similar
        @Suppress("UNCHECKED_CAST")
        return response.component2().decodeToString() as T
    }
}

// Retry policy using TrikeShed structures
data class RetryPolicy(
    val maxAttempts: Int,
    val backoffStrategy: (Int) -> Duration,
    val retryOn: (HttpResponse) -> Boolean
)

// Connection pool using TrikeShed structures
// Moved to TrikeShedRestClient.kt to avoid duplication
/*
class ConnectionPool(size: Int) {
    private val connections: Indexed<Connection> = size j { i: Int ->
        Connection(id = i, inUse = false)
    }
    
    data class Connection(
        val id: Int,
        var inUse: Boolean
    )
    
    suspend fun acquire(): Connection {
        // Find first available connection
        for (i in 0 until connections.component1()) {
            val conn = connections.component2()(i)
            if (!conn.inUse) {
                conn.inUse = true
                return conn
            }
        }
        // Wait for connection to be available
        TODO("Implement connection waiting")
    }
    
    fun release(connection: Connection) {
        connection.inUse = false
    }
}
*/

// Batch request executor
class BatchExecutor(private val client: RestClient) {
    suspend fun executeBatch(
        requests: Indexed<HttpRequest>,
        parallelism: Int = 5
    ): Indexed<HttpResponse> {
        // Execute requests with controlled parallelism
        return client.batch(requests)
    }
}

// Request/Response logging using TrikeShed
data class RequestLog(
    val timestamp: Long,
    val request: HttpRequest,
    val response: HttpResponse?,
    val error: Throwable?
)

// Moved to TrikeShedRestClient.kt to avoid duplication
/*
class RequestLogger {
    private val logs = mutableListOf<RequestLog>()
    
    fun log(request: HttpRequest, response: HttpResponse?, error: Throwable? = null) {
        logs.add(RequestLog(
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            request = request,
            response = response,
            error = error
        ))
    }
    
    fun getLogs(): Indexed<RequestLog> = logs.size j { i: Int -> logs[i] }
}
*/

// URL builder using TrikeShed patterns
class UrlBuilder(private val baseUrl: String) {
    private var pathSegments: Indexed<String> = 0 j { _: Int -> "" }
    private var queryParams: Indexed<Join<String, String>> = 0 j { _: Int -> "" j "" }
    
    fun addPath(segment: String) = apply {
        val currentSize = pathSegments.component1()
        pathSegments = (currentSize + 1) j { i: Int ->
            if (i < currentSize) pathSegments.component2()(i) else segment
        }
    }
    
    fun addQueryParam(key: String, value: String) = apply {
        val currentSize = queryParams.component1()
        queryParams = (currentSize + 1) j { i: Int ->
            if (i < currentSize) queryParams.component2()(i) else (key j value)
        }
    }
    
    fun build(): String {
        val pathPart = buildString {
            append(baseUrl)
            for (i in 0 until pathSegments.component1()) {
                append("/").append(pathSegments.component2()(i))
            }
        }
        
        return if (queryParams.component1() > 0) {
            buildString {
                append(pathPart).append("?")
                for (i in 0 until queryParams.component1()) {
                    if (i > 0) append("&")
                    val param = queryParams.component2()(i)
                    append(param.component1()).append("=").append(param.component2())
                }
            }
        } else pathPart
    }
}

// Helper functions
fun headersOf(vararg pairs: Join<String, String>): HttpHeaders =
    pairs.size j { i: Int -> pairs[i] }

fun headerOf(key: String, value: String): Join<String, String> = key j value

// Platform-specific implementations
expect class PlatformRestClient(
    baseUrl: String,
    defaultHeaders: HttpHeaders,
    connectionPoolSize: Int,
    defaultTimeout: Duration?,
    interceptors: Indexed<RequestInterceptor>
) : RestClient

expect class PlatformWebSocketClient() : WebSocketClient

// Extension to convert headers to/from Map (for interop)
fun HttpHeaders.toMap(): Map<String, String> = buildMap {
    for (i in 0 until this@toMap.component1()) {
        val header = this@toMap.component2()(i)
        put(header.component1(), header.component2())
    }
}

fun Map<String, String>.toHttpHeaders(): HttpHeaders =
    size j { i: Int ->
        val entry = entries.elementAt(i)
        entry.key j entry.value
    }