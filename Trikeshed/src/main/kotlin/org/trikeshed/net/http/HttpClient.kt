package org.trikeshed.net.http

import org.trikeshed.util.Logger
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class HttpClient(
    private val timeout: Duration = Duration.ofSeconds(30)
) {
    private val logger = Logger.getLogger<HttpClient>()
    private val client = JavaHttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    suspend fun post(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val javaRequest = JavaHttpRequest.newBuilder()
            .uri(request.url.toURI())
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(JavaHttpRequest.BodyPublishers.ofString(json.encodeToString(request.body)))
            .build()
            
        val response = client.send(javaRequest, JavaHttpResponse.BodyHandlers.ofString())
        
        HttpResponse(
            statusCode = response.statusCode(),
            body = json.decodeFromString(response.body())
        )
    }
    
    suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
        val javaRequest = JavaHttpRequest.newBuilder()
            .uri(url.toURI())
            .timeout(timeout)
            .GET()
            .build()
            
        val response = client.send(javaRequest, JavaHttpResponse.BodyHandlers.ofString())
        
        HttpResponse(
            statusCode = response.statusCode(),
            body = json.decodeFromString(response.body())
        )
    }
} 