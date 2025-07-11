# CCEK (CoroutineContextElementKey) Architecture

CCEK is a powerful pattern where CoroutineContext.Key objects hold functionality directly, eliminating module dependencies and enabling true compositional architecture.

## Core Concept

Instead of using Keys just for dependency injection, the Key itself IS the implementation:

```kotlin
object CouchDBKey : CoroutineContext.Key<CouchDBKey> {
    // The Key holds the functions directly
    suspend fun createDatabase(name: String) { ... }
    suspend fun getDocument(db: String, id: String) { ... }
    suspend fun putDocument(db: String, doc: Document) { ... }
}
```

## Benefits

1. **No Module Dependencies** - Functions live on Keys, not in separate modules
2. **True Composition** - Combine Keys to combine capabilities
3. **Runtime Flexibility** - Swap implementations by changing context
4. **Follows Kotlin Design** - Similar to how Dispatchers.Default works

## Patterns

### 1. Basic Key with Functions

```kotlin
object CouchDBKey : CoroutineContext.Key<CouchDBKey> {
    suspend fun createDatabase(name: String) { 
        println("Creating database: $name")
    }
    
    suspend fun getDocument(db: String, id: String): Document? {
        // Implementation here
        return null
    }
}

object ChannelKey : CoroutineContext.Key<ChannelKey> {
    fun createChannel(): Channel = Channel()
    suspend fun send(channel: Channel, data: Any) { /* ... */ }
    suspend fun receive(channel: Channel): Any? { /* ... */ }
}

// Usage:
withContext(CouchDBKey + ChannelKey) {
    CouchDBKey.createDatabase("fiduciary")
    val channel = ChannelKey.createChannel()
    ChannelKey.send(channel, "data")
}
```

### 2. Key Inheritance/Composition

```kotlin
interface StorageKey : CoroutineContext.Key<StorageKey> {
    suspend fun store(key: String, value: ByteArray)
    suspend fun retrieve(key: String): ByteArray?
}

object CouchDBKey : StorageKey {
    // Inherits storage contract, adds CouchDB specifics
    override suspend fun store(key: String, value: ByteArray) { /* ... */ }
    override suspend fun retrieve(key: String): ByteArray? { /* ... */ }
    
    // CouchDB-specific functions
    suspend fun createView(design: String, map: String) { /* ... */ }
    suspend fun queryView(design: String, view: String): List<Row> { /* ... */ }
}

object IPFSKey : StorageKey {
    // Different implementation of same contract
    override suspend fun store(key: String, value: ByteArray) { /* IPFS impl */ }
    override suspend fun retrieve(key: String): ByteArray? { /* IPFS impl */ }
}
```

### 3. Key with State Machine

```kotlin
object FiduciaryKey : CoroutineContext.Key<FiduciaryKey> {
    sealed class State {
        object Initial : State()
        object Active : State()
        data class Processing(val count: Int) : State()
        object Stopped : State()
    }
    
    private val state = atomic<State>(State.Initial)
    
    suspend fun start() {
        state.value = State.Active
    }
    
    suspend fun ingest(data: ByteArray): Result<Unit> = 
        when (val current = state.value) {
            is State.Active -> {
                state.value = State.Processing(current.count + 1)
                process(data)
                state.value = State.Active
                Result.success(Unit)
            }
            else -> Result.failure(IllegalStateException("Not active: $current"))
        }
}
```

### 4. Keys as Event Emitters

```kotlin
object MonitoringKey : CoroutineContext.Key<MonitoringKey> {
    sealed class Event {
        data class Metric(val name: String, val value: Double) : Event()
        data class Error(val error: Throwable) : Event()
        data class StateChange(val from: String, val to: String) : Event()
    }
    
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()
    
    suspend fun recordMetric(name: String, value: Double) {
        _events.emit(Event.Metric(name, value))
    }
    
    suspend fun recordError(error: Throwable) {
        _events.emit(Event.Error(error))
    }
    
    // Monitor other keys
    suspend fun <T> monitored(name: String, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - start
                recordMetric("$name.duration", duration.toDouble())
            }
        } catch (e: Exception) {
            recordError(e)
            throw e
        }
    }
}
```

### 5. Composite Keys Pattern

```kotlin
object NetworkStackKey : CoroutineContext.Key<NetworkStackKey> {
    // Combines multiple protocols in one key
    inner class HttpProtocol {
        suspend fun get(url: String): Response { /* ... */ }
        suspend fun post(url: String, body: ByteArray): Response { /* ... */ }
    }
    
    inner class QuicProtocol {
        suspend fun connect(host: String, port: Int): QuicConnection { /* ... */ }
        suspend fun stream(conn: QuicConnection): QuicStream { /* ... */ }
    }
    
    inner class WebSocketProtocol {
        suspend fun connect(url: String): WebSocket { /* ... */ }
        suspend fun send(ws: WebSocket, data: ByteArray) { /* ... */ }
    }
    
    val http = HttpProtocol()
    val quic = QuicProtocol()
    val websocket = WebSocketProtocol()
    
    // Unified handler
    suspend fun handle(connection: Connection) = when {
        connection.isQuic -> quic.handle(connection)
        connection.isWebSocket -> websocket.handle(connection)
        else -> http.handle(connection)
    }
}

// Usage:
withContext(NetworkStackKey) {
    val response = NetworkStackKey.http.get("https://example.com")
    val quicConn = NetworkStackKey.quic.connect("example.com", 443)
}
```

### 6. Key Interceptors/Middleware

```kotlin
object TracingKey : CoroutineContext.Key<TracingKey> {
    data class Span(
        val name: String,
        val startTime: Long,
        var endTime: Long? = null,
        val tags: MutableMap<String, String> = mutableMapOf()
    )
    
    private val activeSpans = mutableMapOf<String, Span>()
    
    suspend fun <T> trace(name: String, block: suspend Span.() -> T): T {
        val span = Span(name, System.nanoTime())
        activeSpans[name] = span
        
        return try {
            span.block().also {
                span.endTime = System.nanoTime()
                println("TRACE: $name took ${(span.endTime!! - span.startTime) / 1_000_000}ms")
            }
        } catch (e: Exception) {
            span.tags["error"] = e.message ?: "Unknown error"
            throw e
        } finally {
            activeSpans.remove(name)
        }
    }
}

// Usage:
TracingKey.trace("database.write") {
    tag("db", "fiduciary")
    tag("doc_count", "1")
    CouchDBKey.putDocument(doc)
}
```

### 7. Reactor Pattern with Keys

```kotlin
object ReactorKey : CoroutineContext.Key<ReactorKey> {
    private val selectionKeys = mutableMapOf<Channel, SelectionKey>()
    
    suspend fun register(channel: Channel, ops: Int): SelectionKey {
        val key = SelectionKey(channel, ops)
        selectionKeys[channel] = key
        return key
    }
    
    suspend fun select(timeout: Duration): Set<SelectionKey> {
        // Reactor pattern implementation
        return selectionKeys.values.filter { it.isReady }.toSet()
    }
    
    fun wakeup() {
        // Wake up selector
    }
}
```

## Complete Example: Fiduciary Service

```kotlin
// All functionality on Keys
object FiduciaryServiceKey : CoroutineContext.Key<FiduciaryServiceKey> {
    private var running = false
    
    suspend fun start(port: Int = 5984) = coroutineScope {
        running = true
        
        // Compose with other keys
        withContext(CouchDBKey + ChannelKey + MonitoringKey + TracingKey) {
            // Create database
            TracingKey.trace("init.database") {
                CouchDBKey.createDatabase("fiduciary")
            }
            
            // Create channels
            val requestChannel = ChannelKey.createChannel()
            val responseChannel = ChannelKey.createChannel()
            
            // Start monitoring
            launch {
                MonitoringKey.events.collect { event ->
                    println("Monitor: $event")
                }
            }
            
            // Process requests
            launch {
                while (running) {
                    val request = ChannelKey.receive(requestChannel)
                    
                    TracingKey.trace("request.process") {
                        val response = processRequest(request)
                        ChannelKey.send(responseChannel, response)
                        MonitoringKey.recordMetric("requests.processed", 1.0)
                    }
                }
            }
            
            println("Fiduciary service started on port $port")
        }
    }
    
    suspend fun stop() {
        running = false
    }
    
    private suspend fun processRequest(request: Any): Any {
        // Process using composed keys
        return when (request) {
            is GetDocument -> CouchDBKey.getDocument(request.db, request.id)
            is PutDocument -> CouchDBKey.putDocument(request.db, request.doc)
            else -> error("Unknown request type")
        }
    }
}

// Usage:
suspend fun main() = coroutineScope {
    withContext(FiduciaryServiceKey) {
        FiduciaryServiceKey.start(5984)
        
        // Keep running
        delay(Long.MAX_VALUE)
    }
}
```

## Best Practices

1. **Keep Keys Focused** - Each Key should have a single responsibility
2. **Use Composition** - Combine Keys rather than creating mega-Keys
3. **State on Keys is OK** - Keys can hold state, they're singletons
4. **Leverage Context** - Use `currentCoroutineContext()` to access Keys
5. **Test with Mock Keys** - Easy to swap implementations for testing

## Anti-Patterns to Avoid

1. **Don't create instance Keys** - Keys should be objects/singletons
2. **Don't use Keys for data** - Keys are for functionality, not data storage
3. **Don't create circular dependencies** - Keys can compose but shouldn't depend on each other circularly

## Migration from Modules

Traditional module approach:
```kotlin
// In trikeshed-couchdb module
class CouchDBService {
    suspend fun createDatabase(name: String) { ... }
}

// In trikeshed-net module  
class NetworkService {
    suspend fun listen(port: Int) { ... }
}

// Dependency hell with build.gradle.kts
```

CCEK approach:
```kotlin
// No modules needed!
object CouchDBKey : CoroutineContext.Key<CouchDBKey> {
    suspend fun createDatabase(name: String) { ... }
}

object NetworkKey : CoroutineContext.Key<NetworkKey> {
    suspend fun listen(port: Int) { ... }
}

// Just combine in context
withContext(CouchDBKey + NetworkKey) {
    NetworkKey.listen(5984)
    CouchDBKey.createDatabase("test")
}
```

## Conclusion

CCEK pattern provides a powerful alternative to traditional module-based architecture. By putting functionality directly on CoroutineContext.Keys, we achieve:

- Zero module dependencies
- Maximum composability  
- Runtime flexibility
- Clean, idiomatic Kotlin code

This pattern is particularly powerful for services like CouchDB, network stacks, and monitoring systems where you want to mix and match capabilities without dependency hell.