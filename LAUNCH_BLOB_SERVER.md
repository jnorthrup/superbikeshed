# LAUNCH BLOB SERVER - NORMALIZED IMPLEMENTATION

## Current Reality Check (From Brain Dump)

**What EXISTS**:
- ✅ ChannelizedBlobService (working in-memory storage)
- ✅ CouchDBServer (mock HTTP with channels)
- ✅ Main.kt (demo that works)
- ✅ UringCouchDBServer (advanced but not connected)

**What's BROKEN**:
- ❌ Gradle builds hang (daemon issues)
- ❌ No simple way to just run the server
- ❌ Too much architecture, not enough execution

## IMMEDIATE SOLUTION

### Step 1: Run the Working Demo
The `trikeshed-couchdb/src/jvmMain/kotlin/borg/trikeshed/couchdb/Main.kt` already works.

```bash
# Just compile and run it directly
kotlinc -cp "$(find . -name '*.jar' | tr '\n' ':')" \
  trikeshed-couchdb/src/jvmMain/kotlin/borg/trikeshed/couchdb/Main.kt \
  -include-runtime -d blob-server.jar

java -jar blob-server.jar
```

### Step 2: Add HTTP Bridge
The current server uses channels. Add HTTP bridge:

```kotlin
// HttpBridge.kt
suspend fun startHttpServer(couchServer: CouchDBServer) {
    val httpServer = HttpServer.create(InetSocketAddress(5984), 0)
    
    httpServer.createContext("/") { exchange ->
        runBlocking {
            val request = MockHttpRequest(
                exchange.requestMethod,
                exchange.requestURI.path,
                emptyMap(),
                exchange.requestBody.readBytes().decodeToString()
            )
            
            couchServer.httpRequestChannel.send(request)
            val response = couchServer.httpResponseChannel.receive()
            
            exchange.sendResponseHeaders(response.status, response.body?.length?.toLong() ?: 0)
            exchange.responseBody.write((response.body ?: "").toByteArray())
            exchange.close()
        }
    }
    
    httpServer.start()
    println("🚀 CouchDB running on http://localhost:5984")
}
```

### Step 3: Modify Main.kt
```kotlin
fun main() = runBlocking {
    val blobService = ChannelizedBlobService()
    val serverContext = newSingleThreadContext("CouchDBServerThread")
    val couchdbServer = CouchDBServer(blobService, serverContext)

    // Start server
    launch { couchdbServer.start() }
    
    // Start HTTP bridge
    launch { startHttpServer(couchdbServer) }
    
    // Keep running
    awaitCancellation()
}
```

## PURE KMP APPROACH (Per CLAUDE.md)

### Why Current Implementation is Good:
1. **No external dependencies** - Pure Kotlin
2. **Channels for communication** - KMP compatible
3. **Coroutines throughout** - KMP standard
4. **In-memory storage** - No JVM-specific persistence

### What to Keep:
- ChannelizedBlobService (perfect KMP design)
- Channel-based communication
- Coroutine-based processing
- MockHttpRequest/Response (KMP compatible)

### What to Fix:
- Replace `HttpServer` with KMP HTTP server (ktor-server)
- Use expect/actual for platform-specific networking
- Keep storage abstractions platform-neutral

## EXECUTION PLAN

1. **IMMEDIATE**: Get current Main.kt running with HTTP bridge
2. **SHORT-TERM**: Replace with ktor-server for true KMP
3. **MEDIUM-TERM**: Connect to UringCouchDBServer features
4. **LONG-TERM**: Full distributed agent network

## THE BOTTOM LINE

**STOP OVERTHINKING. RUN THE WORKING CODE.**

The ChannelizedBlobService + CouchDBServer is already a working blob server. Just add HTTP binding and launch it.

Everything else is optimization.