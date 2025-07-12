#!/usr/bin/env kotlin

/**
 * Test client for TrikeShed REST Client
 * 
 * Run with: kotlin test-rest-client.kt
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

import borg.trikeshed.rest.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Mock implementations for testing
fun <A, B> A.j(b: B): Join<A, B> = object : Join<A, B> {
    override val a: A = this@j
    override val b: B = b
}

infix fun <T> Int.j(f: (Int) -> T): Indexed<T> = object : Indexed<T> {
    override val a: Int = this@j
    override fun b(index: Int): T = f(index)
}

interface Join<out A, out B> {
    val a: A
    val b: B
}

interface Indexed<out T> {
    val a: Int
    fun b(index: Int): T
}

// Test scenarios
suspend fun main() {
    println("🚀 TrikeShed REST Client Test Suite")
    println("===================================\n")
    
    // Create client
    val client = RestClientBuilder()
        .baseUrl("https://api.example.com")
        .addHeader("User-Agent", "TrikeShed/1.0")
        .addHeader("Accept", "application/json")
        .connectionPoolSize(5)
        .addInterceptor(LoggingInterceptor())
        .addInterceptor(RateLimiter(10, Duration.seconds(60)))
        .build()
    
    runBlocking {
        // Test 1: Simple GET request
        println("Test 1: GET Request")
        testGetRequest(client)
        
        // Test 2: POST with JSON body
        println("\nTest 2: POST Request")
        testPostRequest(client)
        
        // Test 3: Batch requests
        println("\nTest 3: Batch Requests")
        testBatchRequests(client)
        
        // Test 4: Streaming response
        println("\nTest 4: Streaming Response")
        testStreaming(client)
        
        // Test 5: Headers manipulation
        println("\nTest 5: Headers Test")
        testHeaders(client)
        
        // Test 6: URL building
        println("\nTest 6: URL Building")
        testUrlBuilder()
        
        // Test 7: Multipart form data
        println("\nTest 7: Multipart Form Data")
        testMultipartForm()
        
        // Test 8: SSE (Server-Sent Events)
        println("\nTest 8: Server-Sent Events")
        testSse(client)
    }
    
    println("\n✅ All tests completed!")
}

suspend fun testGetRequest(client: RestClient) {
    val response = client.get("/users/123")
    println("Status: ${response.component1().statusCode}")
    println("Body: ${response.component2().decodeToString()}")
    println("Headers: ${headersToString(response.component1().headers)}")
}

suspend fun testPostRequest(client: RestClient) {
    val body = """{"name": "John Doe", "email": "john@example.com"}""".encodeToByteArray()
    val headers = headersOf(
        "Content-Type" j "application/json"
    )
    
    val response = client.post("/users", body, headers)
    println("Status: ${response.component1().statusCode}")
    println("Response: ${response.component2().decodeToString()}")
}

suspend fun testBatchRequests(client: RestClient) {
    // Create 5 requests using TrikeShed pattern
    val requests = 5 j { i: Int ->
        RequestMeta(
            method = "GET",
            url = "/users/$i",
            headers = 0 j { _: Int -> "" j "" }
        ) j null
    }
    
    val responses = client.batch(requests)
    println("Batch size: ${responses.component1()}")
    for (i in 0 until responses.component1()) {
        val response = responses.component2()(i)
        println("Response $i: ${response.component1().statusCode} - ${response.component2().decodeToString()}")
    }
}

suspend fun testStreaming(client: RestClient) {
    val request = RequestMeta(
        method = "GET",
        url = "/stream",
        headers = headersOf("Accept" j "text/event-stream")
    ) j null
    
    client.stream(request)
        .take(3)
        .collect { chunk ->
            println("Received chunk: ${chunk.component2().decodeToString()}")
        }
}

fun testHeaders(client: RestClient) {
    // Test header creation using TrikeShed patterns
    val headers = headersOf(
        "Authorization" j "Bearer token123",
        "X-Custom-Header" j "CustomValue",
        "Accept-Language" j "en-US"
    )
    
    println("Headers count: ${headers.component1()}")
    for (i in 0 until headers.component1()) {
        val header = headers.component2()(i)
        println("  ${header.component1()}: ${header.component2()}")
    }
    
    // Test header merging
    val map = headers.toMap()
    println("\nConverted to map: $map")
    
    val backToHeaders = map.toHttpHeaders()
    println("Converted back: ${headersToString(backToHeaders)}")
}

fun testUrlBuilder() {
    val url = UrlBuilder("https://api.example.com")
        .addPath("v1")
        .addPath("users")
        .addPath("search")
        .addQueryParam("q", "john")
        .addQueryParam("limit", "10")
        .addQueryParam("offset", "0")
        .build()
    
    println("Built URL: $url")
}

fun testMultipartForm() {
    val form = MultipartFormData()
    form.addPart("username", "johndoe".encodeToByteArray())
    form.addPart("bio", "Software developer".encodeToByteArray())
    form.addFile(
        name = "avatar",
        filename = "avatar.png",
        content = "fake image data".encodeToByteArray(),
        contentType = "image/png"
    )
    
    val (headers, body) = form.build()
    println("Multipart headers: ${headersToString(headers)}")
    println("Body preview: ${body.decodeToString().take(200)}...")
}

suspend fun testSse(client: RestClient) {
    val sseClient = SseClient(client)
    
    sseClient.connect("/events")
        .take(3)
        .collect { event ->
            println("SSE Event:")
            println("  ID: ${event.id}")
            println("  Event: ${event.event}")
            println("  Data: ${event.data}")
        }
}

// Helper functions
fun headersToString(headers: HttpHeaders): String {
    return buildString {
        append("{")
        for (i in 0 until headers.component1()) {
            if (i > 0) append(", ")
            val header = headers.component2()(i)
            append("${header.component1()}=${header.component2()}")
        }
        append("}")
    }
}

// Test interceptor
class LoggingInterceptor : RequestInterceptor {
    override suspend fun intercept(request: HttpRequest): HttpRequest {
        println("[Interceptor] ${request.component1().method} ${request.component1().url}")
        return request
    }
}

// Mock implementations for Duration
class Duration private constructor(val milliseconds: Long) {
    companion object {
        fun seconds(seconds: Int) = Duration(seconds * 1000L)
        fun milliseconds(ms: Long) = Duration(ms)
    }
}

fun Duration.inWholeMilliseconds() = milliseconds