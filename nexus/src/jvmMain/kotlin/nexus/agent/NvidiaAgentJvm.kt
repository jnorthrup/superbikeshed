package nexus.agent

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration

/**
 * JVM-specific implementation for NvidiaAgent HTTP calls
 */

private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

internal actual suspend fun NvidiaAgent.callApi(
    apiKey: String,
    requestBody: ChatCompletionRequest
): HttpResponse = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/chat/completions"))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(ChatCompletionRequest.serializer(), requestBody)))
        .build()
    
    val response = httpClient.send(request, JavaHttpResponse.BodyHandlers.ofString())
    
    HttpResponse(
        statusCode = response.statusCode(),
        body = response.body()
    )
}