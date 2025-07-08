# TrikeShed REST Client

A functional REST client library that dogfoods TrikeShed data structures throughout its implementation.

## Overview

TrikeShed REST Client demonstrates how to build a modern HTTP client using functional programming patterns with TrikeShed's core data structures:

- `Join<A,B>` instead of `Pair<A,B>` for request/response pairs
- `Indexed<T>` instead of `List<T>` for collections
- `Series<T>` for streaming responses
- `MetaSeries` for metadata-enriched responses

## Core Concepts

### Request/Response Model

```kotlin
// HTTP request is a Join of metadata and optional body
typealias HttpRequest = Join<RequestMeta, RequestBody?>

// HTTP response is a Join of metadata and body
typealias HttpResponse = Join<ResponseMeta, ResponseBody>

// Headers are indexed pairs
typealias HttpHeaders = Join<Int, (Int) -> Join<String, String>>
```

### Usage Examples

#### Simple GET Request

```kotlin
val client = RestClientBuilder()
    .baseUrl("https://api.example.com")
    .addHeader("Authorization", "Bearer token")
    .build()

val response = client.get("/users/123")
println("Status: ${response.a.statusCode}")
println("Body: ${response.b.decodeToString()}")
```

#### POST with JSON

```kotlin
val body = """{"name": "John Doe"}""".encodeToByteArray()
val headers = headersOf(
    "Content-Type" j "application/json"
)

val response = client.post("/users", body, headers)
```

#### Batch Requests

```kotlin
// Create multiple requests using Indexed
val requests = 10 j { i: Int ->
    RequestMeta("GET", "/users/$i", emptyHeaders()) j null
}

// Execute in parallel, returns Indexed<HttpResponse>
val responses = client.batch(requests)

// Process responses
for (i in 0 until responses.a) {
    val response = responses.b(i)
    println("User $i: ${response.b.decodeToString()}")
}
```

#### Streaming Responses

```kotlin
client.stream(request)
    .collect { chunk ->
        // chunk is Join<ResponseMeta, ByteArray>
        println("Received ${chunk.b.size} bytes")
    }
```

## Advanced Features

### Request Interceptors

```kotlin
class AuthInterceptor(private val token: String) : RequestInterceptor {
    override suspend fun intercept(request: HttpRequest): HttpRequest {
        val newHeaders = addAuthHeader(request.a.headers, token)
        return request.a.copy(headers = newHeaders) j request.b
    }
}

val client = RestClientBuilder()
    .addInterceptor(AuthInterceptor("secret-token"))
    .addInterceptor(LoggingInterceptor())
    .build()
```

### Rate Limiting

```kotlin
val rateLimiter = RateLimiter(
    maxRequests = 100,
    windowDuration = Duration.minutes(1)
)

val client = RestClientBuilder()
    .addInterceptor(rateLimiter)
    .build()
```

### Circuit Breaker

```kotlin
val circuitBreaker = CircuitBreaker(
    failureThreshold = 5,
    resetTimeout = Duration.seconds(60)
)

val client = RestClientBuilder()
    .addInterceptor(circuitBreaker)
    .build()
```

### Multipart Form Data

```kotlin
val form = MultipartFormData()
form.addPart("username", "johndoe".encodeToByteArray())
form.addFile(
    name = "avatar",
    filename = "avatar.png", 
    content = imageBytes,
    contentType = "image/png"
)

val (headers, body) = form.build()
val response = client.post("/upload", body, headers)
```

### Server-Sent Events (SSE)

```kotlin
val sseClient = SseClient(restClient)

sseClient.connect("/events")
    .collect { event ->
        println("Event: ${event.event}")
        println("Data: ${event.data}")
    }
```

## Platform Support

### JVM

The JVM implementation uses Java 11+ HttpClient:

```kotlin
// File upload (JVM only)
val response = client.uploadFile(
    url = "/upload",
    file = File("document.pdf"),
    additionalFields = mapOf("description" to "Important document")
)

// File download (JVM only)
client.downloadFile(
    url = "/files/report.pdf",
    destination = File("downloads/report.pdf")
)
```

### Native

Native implementation uses platform-specific HTTP libraries (ktor-client or curl bindings).

## Architecture

### Connection Pooling

Connection pooling uses `Indexed<Connection>` to manage a fixed-size pool:

```kotlin
class ConnectionPool(size: Int) {
    private val connections: Indexed<Connection> = size j { i: Int ->
        Connection(id = i, inUse = false)
    }
    
    suspend fun acquire(): Connection { ... }
    fun release(connection: Connection) { ... }
}
```

### URL Building

The URL builder uses TrikeShed patterns for composability:

```kotlin
val url = UrlBuilder("https://api.example.com")
    .addPath("v1")
    .addPath("users")
    .addQueryParam("sort", "name")
    .addQueryParam("limit", "50")
    .build()
// Result: https://api.example.com/v1/users?sort=name&limit=50
```

## Testing

Run the test client:

```bash
kotlin test-rest-client.kt
```

## Integration with Other TrikeShed Modules

### With Cursors

```kotlin
// Convert API response to cursor
val users = client.get("/users")
val cursor = jsonToCursor(users.b)

// Query cursor data
val names = cursor.column("name")
val activeUsers = cursor.filter { row ->
    row.getString(cursor.colIdx["active"]) == "true"
}
```

### With Fiduciary

```kotlin
// Use REST client in fiduciary attention navigator
class ApiNavigator(private val client: RestClient) : AttentionNavigator {
    override suspend fun search(query: String): Flow<NavigableNode> {
        val response = client.get("/search?q=$query")
        return parseSearchResults(response.b)
    }
}
```

## Performance Considerations

1. **Connection Reuse**: The client reuses connections through pooling
2. **Parallel Execution**: Batch operations use coroutines for parallelism
3. **Streaming**: Large responses can be streamed to avoid memory issues
4. **Interceptor Chain**: Interceptors are applied efficiently in order

## Best Practices

1. **Use builders** for complex configurations
2. **Add interceptors** for cross-cutting concerns
3. **Handle errors** with circuit breakers
4. **Stream large responses** instead of loading into memory
5. **Use batch operations** for multiple similar requests

## Comparison with Traditional Clients

| Feature | Traditional | TrikeShed REST |
|---------|------------|----------------|
| Request/Response | `Pair<Request, Response>` | `Join<RequestMeta, Body>` |
| Headers | `Map<String, String>` | `Indexed<Join<String, String>>` |
| Batch Operations | `List<Response>` | `Indexed<HttpResponse>` |
| Streaming | Iterator/Stream | `Flow<Join<Meta, ByteArray>>` |
| Type Safety | Limited | Full with TrikeShed types |

## Future Enhancements

- GraphQL support with TrikeShed query builders
- WebSocket implementation using Series<Frame>
- HTTP/3 support
- Protobuf/gRPC integration
- Automatic retry strategies
- Request/response caching with TTL