package nexus.http

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.KSerializer
import java.net.http.HttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration
import java.net.URI

@Serializable
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
)

@Serializable
data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray
)

object HttpMuxer {
    private val client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun String.GET(block: HttpRequest.() -> Unit = {}): Flow<HttpResponse> = 
        executeRequest(HttpRequest("GET", this).apply(block))

    fun String.POST(body: String): Flow<HttpResponse> = 
        executeRequest(HttpRequest("POST", this, body = body.toByteArray()))

    fun String.PUT(body: String): Flow<HttpResponse> = 
        executeRequest(HttpRequest("PUT", this, body = body.toByteArray()))

    fun String.DELETE(block: HttpRequest.() -> Unit = {}): Flow<HttpResponse> = 
        executeRequest(HttpRequest("DELETE", this).apply(block))

    infix fun HttpRequest.header(pair: Pair<String, String>): HttpRequest = 
        copy(headers = headers + pair)

    fun <T> HttpRequest.json(data: T, serializer: KSerializer<T>): HttpRequest = 
        copy(body = json.encodeToString(serializer, data).toByteArray())

    private fun executeRequest(request: HttpRequest): Flow<HttpResponse> = flow {
        val builder = JavaHttpRequest.newBuilder()
            .uri(URI(request.url))
            .method(request.method, request.body?.let { 
                JavaHttpRequest.BodyPublishers.ofByteArray(it)
            } ?: JavaHttpRequest.BodyPublishers.noBody())

        request.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        val response = client.send(builder.build(), JavaHttpResponse.BodyHandlers.ofByteArray())
        
        emit(HttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map().mapValues { it.value.first() },
            body = response.body()
        ))
    }
} 