# TrikeDownloader Client Implementations

Complete client ecosystem for TrikeDownloader with advanced RPC transformation, distributed routing, and protocol interoperability.

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client Apps   │    │  RPC Transformer │    │  TrikeDownloader │
│                 │◄──►│                  │◄──►│                 │
│ • TrikeAria     │    │ • Protocol Conv  │    │ • HTTP/Torrent  │
│ • TrikeRpc      │    │ • Load Balancing │    │ • Progress      │
│ • TrikeCurl     │    │ • Distributed    │    │ • Session Mgmt  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Distributed      │
                       │   Objects        │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Node Coordinator │
                       └──────────────────┘
```

## 🚀 Client Types

### 1. TrikeAriaClient - Programmatic aria2c Client

**Purpose**: High-performance programmatic client with full aria2c API compatibility.

**Features**:
- Complete aria2c method coverage
- Async/await with Kotlin coroutines
- Real-time progress monitoring
- Batch operations
- Error handling with retries
- Type-safe responses

**Usage**:
```kotlin
val client = TrikeAriaClient("http://localhost:6800")

// Add HTTP download
val gid = client.addUri(
    uris = listOf("https://example.com/file.zip"),
    options = mapOf(
        "header" to listOf("User-Agent: TrikeAriaClient/1.0"),
        "timeout" to 30000L
    )
)

// Monitor progress
client.monitorProgress(gid).collect { status ->
    println("Progress: ${status["completedLength"]}/${status["totalLength"]}")
}

// Batch operations
val results = client.batch(listOf(
    { client.addUri(listOf("https://example.com/file1.zip")) },
    { client.addUri(listOf("https://example.com/file2.zip")) }
))
```

### 2. TrikeRpcClient - Universal RPC Client

**Purpose**: Universal client with protocol transformation and interactive shell.

**Features**:
- Protocol transformation (aria2c ↔ native ↔ REST ↔ gRPC)
- Interactive command-line shell
- Distributed routing and load balancing
- Real-time progress with visual bars
- Configuration management
- Batch and interactive modes

**Usage**:
```kotlin
val client = TrikeRpcClient(
    serverUrl = "http://localhost:6800",
    sourceProtocol = "aria2c",
    targetProtocol = "native"
)

// Interactive shell
client.interactiveShell()

// Programmatic usage
val gid = client.addUri(listOf("https://example.com/file.zip"))
val status = client.tellStatus(gid)
```

**Interactive Commands**:
```bash
trike> add http https://example.com/file.zip
trike> add torrent magnet:?xt=urn:btih:example
trike> status trike_1234567890_123
trike> list active
trike> monitor trike_1234567890_123
trike> config protocol aria2c native
trike> help
trike> exit
```

### 3. TrikeCurlClient - HTTP-Focused Client

**Purpose**: curl-compatible client for HTTP/HTTPS downloads with advanced features.

**Features**:
- curl-compatible command-line interface
- HTTP/HTTPS protocol support
- Advanced options (headers, auth, redirects)
- Progress reporting
- Resume capability
- Multiple output formats

**Usage**:
```kotlin
val client = TrikeCurlClient()

// Basic download
client.download("https://example.com/file.zip")

// Advanced options
client.download(
    url = "https://example.com/file.zip",
    options = CurlOptions(
        headers = mapOf("Authorization" to "Bearer token"),
        output = "downloaded_file.zip",
        resume = true,
        timeout = 30000L
    )
)
```

## 🔄 Protocol Transformation

### TorrentRpcTransformer

**Purpose**: Advanced RPC protocol transformation with distributed routing.

**Supported Protocols**:
- aria2c JSON-RPC
- HTTP REST
- gRPC
- WebSocket
- Native TrikeShed protocol
- Custom protocols

**Features**:
- Protocol versioning and migration
- Authentication transformation
- Method and parameter mapping
- Distributed routing with load balancing
- Custom protocol handlers
- Real-time stream transformation

**Usage**:
```kotlin
val transformer = TorrentRpcTransformer(requestFactory, "node-1")

// Transform request
val transformed = transformer.transformRequest(
    sourceProtocol = "aria2c",
    targetProtocol = "native",
    request = requestBytes,
    context = TransformContext(
        methodMapping = mapOf("aria2.addUri" to "download.add"),
        parameterMapping = mapOf("uris" to "urls")
    )
)

// Add routing rules
transformer.addRoute(
    method = "aria2.addUri",
    service = "aria2",
    targetNode = "http-node",
    loadBalancing = LoadBalancingStrategy.ROUND_ROBIN,
    failover = listOf("http-backup-1", "http-backup-2")
)

// Register custom protocol
transformer.registerProtocol("custom", CustomProtocolHandler())
```

### Load Balancing Strategies

1. **Round Robin** - Distribute requests evenly across nodes
2. **Least Connections** - Route to node with fewest active connections
3. **Weighted** - Route based on node capacity weights
4. **Consistent Hash** - Route based on request ID hash

## 📡 Real-Time Features

### Progress Monitoring

All clients support real-time progress monitoring:

```kotlin
// TrikeAriaClient
client.monitorProgress(gid).collect { status ->
    val completed = status["completedLength"] as? Long ?: 0L
    val total = status["totalLength"] as? Long ?: 0L
    val speed = status["downloadSpeed"] as? Long ?: 0L
    println("Progress: ${completed}/${total} bytes (${speed} B/s)")
}

// TrikeRpcClient with visual progress
client.monitorProgress(gid).collect { status ->
    val progress = "[████████░░░░░░░░░░░░] 40%"
    println("\r$progress ${completed}/${total} bytes (${speed} B/s)")
}
```

### Batch Operations

Efficient batch processing for multiple operations:

```kotlin
val results = client.batch(listOf(
    { client.addUri(listOf("https://example.com/file1.zip")) },
    { client.addUri(listOf("https://example.com/file2.zip")) },
    { client.addTorrent("magnet:?xt=urn:btih:example") },
    { client.getGlobalStat() }
))
```

## 🔧 Configuration

### Client Configuration

```kotlin
// TrikeAriaClient
val ariaClient = TrikeAriaClient(
    serverUrl = "http://localhost:6800",
    timeout = Duration.ofSeconds(30),
    maxRetries = 3
)

// TrikeRpcClient
val rpcClient = TrikeRpcClient(
    serverUrl = "http://localhost:6800",
    sourceProtocol = "aria2c",
    targetProtocol = "native",
    timeout = Duration.ofSeconds(30)
)

// TrikeCurlClient
val curlClient = TrikeCurlClient(
    defaultOptions = CurlOptions(
        timeout = 30000L,
        maxRedirects = 5,
        userAgent = "TrikeCurl/1.0"
    )
)
```

### Protocol Configuration

```kotlin
val context = TransformContext(
    sourceProtocol = "aria2c",
    targetProtocol = "native",
    sourceVersion = "1.0",
    targetVersion = "1.0",
    sourceAuth = AuthInfo("bearer", mapOf("token" to "secret")),
    targetAuth = AuthInfo("basic", mapOf("username" to "user", "password" to "pass")),
    methodMapping = mapOf(
        "aria2.addUri" to "download.add",
        "aria2.tellStatus" to "download.status"
    ),
    parameterMapping = mapOf(
        "uris" to "urls",
        "gid" to "id"
    )
)
```

## 🎯 Usage Examples

### Basic Download Management

```kotlin
val client = TrikeAriaClient()

// Add HTTP download
val httpGid = client.addUri(listOf("https://example.com/file.zip"))

// Add torrent download
val torrentGid = client.addTorrent(
    torrent = "magnet:?xt=urn:btih:example",
    uris = listOf("udp://tracker.example.com:1337")
)

// Monitor progress
client.monitorProgress(httpGid).collect { status ->
    println("HTTP: ${status["completedLength"]}/${status["totalLength"]}")
}

client.monitorProgress(torrentGid).collect { status ->
    println("Torrent: ${status["completedLength"]}/${status["totalLength"]}")
}

// Get status
val httpStatus = client.tellStatus(httpGid)
val torrentStatus = client.tellStatus(torrentGid)

// Pause/Resume
client.pause(httpGid)
client.resume(httpGid)

// Remove
client.remove(httpGid)
client.remove(torrentGid)
```

### Advanced Protocol Transformation

```kotlin
val transformer = TorrentRpcTransformer(requestFactory, "gateway-node")

// Transform aria2c to REST
val restRequest = transformer.transformRequest(
    sourceProtocol = "aria2c",
    targetProtocol = "rest",
    request = aria2cRequestBytes,
    context = TransformContext(
        methodMapping = mapOf("aria2.addUri" to "POST /downloads"),
        parameterMapping = mapOf("uris" to "urls", "options" to "config")
    )
)

// Transform REST to gRPC
val grpcRequest = transformer.transformRequest(
    sourceProtocol = "rest",
    targetProtocol = "grpc",
    request = restRequest,
    context = TransformContext(
        methodMapping = mapOf("POST /downloads" to "DownloadService.AddDownload"),
        parameterMapping = mapOf("urls" to "download_urls", "config" to "options")
    )
)
```

### Distributed Routing

```kotlin
val transformer = TorrentRpcTransformer(requestFactory, "router-node")

// Route HTTP downloads to HTTP nodes
transformer.addRoute(
    method = "aria2.addUri",
    service = "aria2",
    targetNode = "http-node-1",
    loadBalancing = LoadBalancingStrategy.ROUND_ROBIN,
    failover = listOf("http-node-2", "http-node-3")
)

// Route torrent downloads to torrent nodes
transformer.addRoute(
    method = "aria2.addTorrent",
    service = "aria2",
    targetNode = "torrent-node-1",
    loadBalancing = LoadBalancingStrategy.LEAST_CONNECTIONS,
    failover = listOf("torrent-node-2")
)

// Route status queries to status nodes
transformer.addRoute(
    method = "aria2.tellStatus",
    service = "aria2",
    targetNode = "status-node-1",
    loadBalancing = LoadBalancingStrategy.WEIGHTED,
    weights = mapOf("status-node-1" to 3, "status-node-2" to 1)
)
```

### Interactive Shell

```bash
# Start interactive shell
./TrikeRpcClient

# Available commands
trike> help
trike> add http https://example.com/file.zip
trike> add torrent magnet:?xt=urn:btih:example
trike> status trike_1234567890_123
trike> list active
trike> list waiting
trike> list stopped
trike> pause trike_1234567890_123
trike> resume trike_1234567890_123
trike> remove trike_1234567890_123
trike> monitor trike_1234567890_123
trike> config protocol aria2c native
trike> config server http://localhost:6800
trike> config timeout 30
trike> exit
```

## 🔒 Error Handling

### Exception Types

```kotlin
// RPC errors
class RpcException(val code: Int, override val message: String) : Exception()

// HTTP errors
class HttpException(override val message: String) : Exception()

// Protocol errors
class UnsupportedProtocolException(message: String) : Exception()
```

### Error Handling Patterns

```kotlin
try {
    val gid = client.addUri(listOf("https://example.com/file.zip"))
    println("Download added: $gid")
} catch (e: RpcException) {
    println("RPC Error ${e.code}: ${e.message}")
} catch (e: HttpException) {
    println("HTTP Error: ${e.message}")
} catch (e: Exception) {
    println("Unexpected error: ${e.message}")
}
```

### Retry Logic

```kotlin
// Automatic retries with exponential backoff
val client = TrikeAriaClient(
    maxRetries = 3,
    timeout = Duration.ofSeconds(30)
)

// Manual retry with custom logic
repeat(3) { attempt ->
    try {
        val result = client.addUri(listOf("https://example.com/file.zip"))
        break // Success
    } catch (e: Exception) {
        if (attempt == 2) throw e // Last attempt
        delay(1000L * (attempt + 1)) // Exponential backoff
    }
}
```

## 📊 Monitoring and Metrics

### Global Statistics

```kotlin
val stats = client.getGlobalStat()
println("Download Speed: ${stats["downloadSpeed"]} B/s")
println("Upload Speed: ${stats["uploadSpeed"]} B/s")
println("Active Downloads: ${stats["numActive"]}")
println("Waiting Downloads: ${stats["numWaiting"]}")
println("Stopped Downloads: ${stats["numStopped"]}")
```

### Download Lists

```kotlin
// Active downloads
val active = client.tellActive()
println("Active downloads: ${active.size}")

// Waiting downloads
val waiting = client.tellWaiting()
println("Waiting downloads: ${waiting.size}")

// Stopped downloads
val stopped = client.tellStopped()
println("Stopped downloads: ${stopped.size}")
```

## 🧪 Testing

### Unit Tests

```kotlin
@Test
fun `test addUri with TrikeAriaClient`() = runTest {
    val client = TrikeAriaClient("http://localhost:6800")
    
    val gid = client.addUri(listOf("https://example.com/file.zip"))
    
    assertNotNull(gid)
    assertTrue(gid.startsWith("trike_"))
}

@Test
fun `test protocol transformation`() = runTest {
    val transformer = TorrentRpcTransformer(requestFactory, "test-node")
    
    val transformed = transformer.transformRequest(
        sourceProtocol = "aria2c",
        targetProtocol = "native",
        request = testRequestBytes
    )
    
    assertNotNull(transformed)
    assertTrue(transformed.size > 0)
}
```

### Integration Tests

```kotlin
@Test
fun `test full download workflow`() = runTest {
    val client = TrikeAriaClient()
    
    // Add download
    val gid = client.addUri(listOf("https://example.com/file.zip"))
    
    // Monitor progress
    val progressUpdates = mutableListOf<Map<String, Any?>>()
    client.monitorProgress(gid).take(5).collect { status ->
        progressUpdates.add(status)
    }
    
    // Verify progress
    assertTrue(progressUpdates.size > 0)
    assertTrue(progressUpdates.any { it["status"] == "downloading" })
    
    // Cleanup
    client.remove(gid)
}
```

## 🚀 Performance Features

### Concurrent Downloads

- Multiple downloads run concurrently
- Each download uses its own coroutine
- Progress updates are non-blocking
- Automatic connection pooling

### Memory Efficiency

- Immutable request/response objects
- Lazy loading of download data
- Automatic cleanup of completed downloads
- Efficient serialization with kotlinx.serialization

### Network Optimization

- HTTP/2 support for multiplexing
- Connection reuse and keep-alive
- Automatic retry with exponential backoff
- Circuit breaker for failing endpoints

## 🔧 Extensibility

### Custom Protocol Handlers

```kotlin
class CustomProtocolHandler : ProtocolHandler {
    override fun parseRequest(data: ByteArray): TransformedRequest {
        // Parse custom protocol format
        return TransformedRequest(
            id = "custom_${System.currentTimeMillis()}",
            method = "custom.addUri",
            service = "custom",
            parameters = mapOf("url" to data.decodeToString()),
            metadata = mapOf("protocol" to "custom")
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        // Serialize to custom protocol format
        return request.parameters["url"]?.toString()?.encodeToByteArray() ?: ByteArray(0)
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        // Parse custom protocol response
        return TransformedResponse(
            id = "custom_response",
            result = data.decodeToString(),
            error = null,
            metadata = mapOf("protocol" to "custom")
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        // Serialize custom protocol response
        return response.result?.toString()?.encodeToByteArray() ?: ByteArray(0)
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "custom"
}

// Register custom handler
transformer.registerProtocol("custom", CustomProtocolHandler())
```

### Custom Load Balancing

```kotlin
class CustomLoadBalancer : LoadBalancer {
    override fun customStrategy(
        primary: String,
        failover: List<String>,
        context: LoadBalancingContext
    ): String {
        // Custom load balancing logic
        return when {
            context.requestType == "high_priority" -> primary
            context.nodeLoads[primary] ?: 0 > 80 -> failover.firstOrNull() ?: primary
            else -> primary
        }
    }
}
```

## 🎉 Benefits

### For Developers
- **Type Safety**: Full Kotlin type safety with compile-time checks
- **Async/Await**: Modern coroutine-based concurrency
- **Extensible**: Easy to add custom protocols and handlers
- **Testable**: Comprehensive test coverage and mocking support

### For Users
- **Familiar Interfaces**: aria2c and curl compatibility
- **Real-Time Progress**: Live progress monitoring with visual feedback
- **Interactive Shell**: Command-line interface for easy management
- **Batch Operations**: Efficient handling of multiple downloads

### For Operations
- **Distributed**: Scale across multiple nodes
- **Load Balanced**: Automatic distribution and failover
- **Protocol Agnostic**: Support for any RPC protocol
- **Monitoring**: Built-in metrics and statistics

This client ecosystem provides **enterprise-grade capabilities** for distributed download management with protocol transformation, load balancing, and real-time monitoring! 🎉 