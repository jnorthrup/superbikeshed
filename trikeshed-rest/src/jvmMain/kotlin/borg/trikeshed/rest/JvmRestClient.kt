package borg.trikeshed.rest

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * JVM implementation of TrikeShedRestClient using Java 11+ HttpClient
 * 
 * This implementation bridges TrikeShed data structures with Java's HttpClient
 * while maintaining the functional patterns throughout
 */
actual class PlatformRestClient actual constructor(
    private val baseUrl: String,
    private val defaultHeaders: HttpHeaders,
    private val connectionPoolSize: Int,
    private val defaultTimeout: Duration?,
    private val interceptors: Indexed<RequestInterceptor>
) : RestClient {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(JavaDuration.ofSeconds(30))
        .build()
    
    private val logger = RequestLogger()
    
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        // Apply interceptors
        var processedRequest = request
        for (i in 0 until interceptors.component1()) {
            processedRequest = interceptors.component2()(i).intercept(processedRequest)
        }
        
        // Build Java HTTP request
        val javaRequest = buildJavaRequest(processedRequest)
        
        try {
            // Execute request
            val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val javaResponse = httpClient.send(javaRequest, JavaHttpResponse.BodyHandlers.ofByteArray())
            val duration = kotlin.time.Duration.parse("${kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime}ms")
            
            // Convert to TrikeShed response
            val response = convertResponse(javaResponse, duration)
            
            // Log the request/response
            logger.log(processedRequest, response)
            
            response
        } catch (e: Exception) {
            // Log error - create error response
            val errorResponse = ResponseMeta(500, 0 j { _: Int -> "" j "" }, Duration.ZERO) j ByteArray(0)
            logger.log(processedRequest, errorResponse)
            throw e
        }
    }
    
    override suspend fun stream(request: HttpRequest): Flow<Join<ResponseMeta, ByteArray>> = flow {
        val processedRequest = applyInterceptors(request)
        val javaRequest = buildJavaRequest(processedRequest)
        
        val response = httpClient.send(javaRequest, JavaHttpResponse.BodyHandlers.ofInputStream())
        val meta = ResponseMeta(
            statusCode = response.statusCode(),
            headers = convertHeaders(response.headers()),
            duration = Duration.ZERO
        )
        
        response.body().buffered().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) break
                emit(meta j buffer.copyOf(bytesRead))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun batch(requests: Indexed<HttpRequest>): Indexed<HttpResponse> {
        // Simplified batch implementation - execute sequentially for now
        return requests.component1() j { i: Int ->
            runBlocking { execute(requests.component2()(i)) }
        }
    }
    
    private suspend fun applyInterceptors(request: HttpRequest): HttpRequest {
        var processedRequest = request
        for (i in 0 until interceptors.component1()) {
            processedRequest = interceptors.component2()(i).intercept(processedRequest)
        }
        return processedRequest
    }
    
    private fun buildJavaRequest(request: HttpRequest): JavaHttpRequest {
        val builder = JavaHttpRequest.newBuilder()
            .uri(URI.create(resolveUrl(request.component1().url)))
        
        // Set method and body
        when (request.component1().method) {
            HttpMethod.GET -> builder.GET()
            HttpMethod.POST -> builder.POST(JavaHttpRequest.BodyPublishers.ofByteArray(request.component2() ?: ByteArray(0)))
            HttpMethod.PUT -> builder.PUT(JavaHttpRequest.BodyPublishers.ofByteArray(request.component2() ?: ByteArray(0)))
            HttpMethod.DELETE -> builder.DELETE()
            HttpMethod.PATCH -> builder.method("PATCH", JavaHttpRequest.BodyPublishers.ofByteArray(request.component2() ?: ByteArray(0)))
            else -> builder.method(request.component1().method.name, JavaHttpRequest.BodyPublishers.ofByteArray(request.component2() ?: ByteArray(0)))
        }
        
        // Set headers
        val mergedHeaders = mergeHeaders(defaultHeaders, request.component1().headers)
        for (i in 0 until mergedHeaders.component1()) {
            val header = mergedHeaders.component2()(i)
            builder.header(header.component1(), header.component2())
        }
        
        // Set timeout
        request.component1().timeout?.let { timeout ->
            builder.timeout(timeout.toJavaDuration())
        }
        
        return builder.build()
    }
    
    private fun convertResponse(javaResponse: JavaHttpResponse<ByteArray>, duration: Duration): HttpResponse {
        val headers = convertHeaders(javaResponse.headers())
        val meta = ResponseMeta(
            statusCode = javaResponse.statusCode(),
            headers = headers,
            duration = duration
        )
        return meta j javaResponse.body()
    }
    
    private fun convertHeaders(javaHeaders: java.net.http.HttpHeaders): HttpHeaders {
        val headerList = mutableListOf<Join<String, String>>()
        javaHeaders.map().forEach { (key, values) ->
            values.forEach { value ->
                headerList.add(key j value)
            }
        }
        return headerList.size j { i: Int -> headerList[i] }
    }
    
    private fun resolveUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "$baseUrl/$url".replace("//", "/").replace(":/", "://")
        }
    }
    
    private fun mergeHeaders(default: HttpHeaders, request: HttpHeaders): HttpHeaders {
        val headerMap = mutableMapOf<String, String>()
        
        // Add default headers
        for (i in 0 until default.component1()) {
            val header = default.component2()(i)
            headerMap[header.component1().lowercase()] = header.component2()
        }
        
        // Override with request headers
        for (i in 0 until request.component1()) {
            val header = request.component2()(i)
            headerMap[header.component1().lowercase()] = header.component2()
        }
        
        // Convert back to HttpHeaders
        return headerMap.size j { i: Int ->
            val entry = headerMap.entries.elementAt(i)
            entry.key j entry.value
        }
    }
}

// JVM WebSocket implementation
actual class PlatformWebSocketClient actual constructor() : WebSocketClient {
    override suspend fun connect(url: String, headers: HttpHeaders): WebSocketSession {
        // Placeholder WebSocket session
        return object : WebSocketSession {
            override val incoming: Flow<WebSocketFrame> = emptyFlow()
            override suspend fun send(frame: WebSocketFrame) {}
            override suspend fun close(reason: CloseReason?) {}
        }
    }
}

// File upload support for JVM
suspend fun RestClient.uploadFile(
    url: String,
    file: java.io.File,
    fieldName: String = "file",
    additionalFields: Map<String, String> = emptyMap()
): HttpResponse {
    val multipart = MultipartFormData()
    
    // Add file
    multipart.addFile(
        name = fieldName,
        filename = file.name,
        content = file.readBytes(),
        contentType = java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    )
    
    // Add additional fields
    additionalFields.forEach { (key, value) ->
        multipart.addPart(key, value.encodeToByteArray())
    }
    
    val (headers, body) = multipart.build()
    
    return execute(
        RequestMeta(HttpMethod.POST, url, headers) j body
    )
}

// Download file support
suspend fun RestClient.downloadFile(
    url: String,
    destination: java.io.File,
    headers: HttpHeaders = 0 j { _: Int -> "" j "" }
): java.io.File {
    val response = get(url, headers)
    destination.writeBytes(response.component2())
    return destination
}

// Progress tracking for large transfers
class ProgressTracker(
    private val totalBytes: Long,
    private val onProgress: (Long, Long) -> Unit
) {
    private var bytesTransferred: Long = 0
    
    fun update(bytes: Long) {
        bytesTransferred += bytes
        onProgress(bytesTransferred, totalBytes)
    }
}

// Extension for streaming with progress
suspend fun RestClient.streamWithProgress(
    request: HttpRequest,
    onProgress: (Long, Long) -> Unit
): Flow<ByteArray> = flow {
    var totalBytes = 0L
    stream(request).collect { chunk ->
        val bytes = chunk.component2()
        totalBytes += bytes.size
        onProgress(totalBytes, -1) // Unknown total size for streams
        emit(bytes)
    }
}