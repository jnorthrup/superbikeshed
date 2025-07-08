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
typealias HttpMethod = String
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
    RequestMeta("GET", url, headers) j null
)

suspend fun RestClient.post(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta("POST", url, headers) j body
)

suspend fun RestClient.put(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta("PUT", url, headers) j body
)

suspend fun RestClient.delete(
    url: String,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta("DELETE", url, headers) j null
)

suspend fun RestClient.patch(
    url: String,
    body: ByteArray,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): HttpResponse = execute(
    RequestMeta("PATCH", url, headers) j body
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
        val currentSize = defaultHeaders.a
        defaultHeaders = (currentSize + 1) j { i: Int ->
            if (i < currentSize) defaultHeaders.b(i) else (key j value)
        }
    }
    
    fun connectionPoolSize(size: Int) = apply { connectionPoolSize = size }
    
    fun requestTimeout(timeout: Duration) = apply { requestTimeout = timeout }
    
    fun addInterceptor(interceptor: RequestInterceptor) = apply {
        val currentSize = interceptors.a
        interceptors = (currentSize + 1) j { i: Int ->
            if (i < currentSize) interceptors.b(i) else interceptor
        }
    }
    
    fun build(): RestClient = TrikeShedRestClient(
        baseUrl = baseUrl,
        defaultHeaders = defaultHeaders,
        connectionPoolSize = connectionPoolSize,
        defaultTimeout = requestTimeout,
        interceptors = interceptors
    )
}

// Request interceptor for middleware
interface RequestInterceptor {
    suspend fun intercept(request: HttpRequest): HttpRequest
}

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
        TODO("JSON deserialization")
    }
}

// Retry policy using TrikeShed structures
data class RetryPolicy(
    val maxAttempts: Int,
    val backoffStrategy: (Int) -> Duration,
    val retryOn: (HttpResponse) -> Boolean
)

// Connection pool using TrikeShed structures
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
        for (i in 0 until connections.a) {
            val conn = connections.b(i)
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

// Batch request executor
class BatchExecutor(private val client: RestClient) {
    suspend fun executeBatch(
        requests: Indexed<HttpRequest>,
        parallelism: Int = 5
    ): Indexed<HttpResponse> {
        // Execute requests with controlled parallelism
        return requests.a j { i: Int ->
            client.execute(requests.b(i))
        }
    }
}

// Request/Response logging using TrikeShed
data class RequestLog(
    val timestamp: Long,
    val request: HttpRequest,
    val response: HttpResponse?,
    val error: Throwable?
)

class RequestLogger {
    private val logs = mutableListOf<RequestLog>()
    
    fun log(request: HttpRequest, response: HttpResponse?, error: Throwable? = null) {
        logs.add(RequestLog(
            timestamp = System.currentTimeMillis(),
            request = request,
            response = response,
            error = error
        ))
    }
    
    fun getLogs(): Indexed<RequestLog> = logs.size j { i: Int -> logs[i] }
}

// URL builder using TrikeShed patterns
class UrlBuilder(private val baseUrl: String) {
    private var pathSegments: Indexed<String> = 0 j { _: Int -> "" }
    private var queryParams: Indexed<Join<String, String>> = 0 j { _: Int -> "" j "" }
    
    fun addPath(segment: String) = apply {
        val currentSize = pathSegments.a
        pathSegments = (currentSize + 1) j { i: Int ->
            if (i < currentSize) pathSegments.b(i) else segment
        }
    }
    
    fun addQueryParam(key: String, value: String) = apply {
        val currentSize = queryParams.a
        queryParams = (currentSize + 1) j { i: Int ->
            if (i < currentSize) queryParams.b(i) else (key j value)
        }
    }
    
    fun build(): String {
        val pathPart = buildString {
            append(baseUrl)
            for (i in 0 until pathSegments.a) {
                append("/").append(pathSegments.b(i))
            }
        }
        
        return if (queryParams.a > 0) {
            buildString {
                append(pathPart).append("?")
                for (i in 0 until queryParams.a) {
                    if (i > 0) append("&")
                    val param = queryParams.b(i)
                    append(param.a).append("=").append(param.b)
                }
            }
        } else pathPart
    }
}

// Helper functions
fun headersOf(vararg pairs: Join<String, String>): HttpHeaders =
    pairs.size j { i: Int -> pairs[i] }

fun headerOf(key: String, value: String): Join<String, String> = key j value

// Extension to convert headers to/from Map (for interop)
fun HttpHeaders.toMap(): Map<String, String> = buildMap {
    for (i in 0 until this@toMap.a) {
        val header = this@toMap.b(i)
        put(header.a, header.b)
    }
}

fun Map<String, String>.toHttpHeaders(): HttpHeaders =
    size j { i: Int ->
        val entry = entries.elementAt(i)
        entry.key j entry.value
    }