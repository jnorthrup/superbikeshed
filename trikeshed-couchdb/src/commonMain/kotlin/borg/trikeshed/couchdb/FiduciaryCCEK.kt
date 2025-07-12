package borg.trikeshed.couchdb

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fiduciary CouchDB Service implemented with CCEK pattern
 * All functionality lives on the Keys themselves
 */

// CouchDB functionality on the Key itself
object CouchDBKey : CoroutineContext.Element, CoroutineContext.Key<CouchDBKey> {
    override val key: CoroutineContext.Key<*> get() = CouchDBKey
    private val databases = mutableMapOf<String, Database>()
    private val documents = mutableMapOf<String, MutableMap<String, Document>>()
    
    data class Database(val name: String, var docCount: Int = 0)
    data class Document(val id: String, val rev: String, val data: Map<String, Any>)
    
    suspend fun createDatabase(name: String): Result<Database> {
        if (databases.containsKey(name)) {
            return Result.failure(Exception("Database already exists"))
        }
        val db = Database(name)
        databases[name] = db
        documents[name] = mutableMapOf()
        return Result.success(db)
    }
    
    suspend fun deleteDatabase(name: String): Result<Unit> {
        databases.remove(name) ?: return Result.failure(Exception("Database not found"))
        documents.remove(name)
        return Result.success(Unit)
    }
    
    suspend fun getAllDatabases(): List<String> = databases.keys.toList()
    
    suspend fun putDocument(db: String, id: String, data: Map<String, Any>): Result<Document> {
        val database = databases[db] ?: return Result.failure(Exception("Database not found"))
        val docs = documents[db]!!
        
        val rev = "1-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val doc = Document(id, rev, data)
        docs[id] = doc
        database.docCount++
        
        MonitoringKey.recordMetric("documents.created", 1.0)
        return Result.success(doc)
    }
    
    suspend fun getDocument(db: String, id: String): Result<Document> {
        val docs = documents[db] ?: return Result.failure(Exception("Database not found"))
        val doc = docs[id] ?: return Result.failure(Exception("Document not found"))
        
        MonitoringKey.recordMetric("documents.read", 1.0)
        return Result.success(doc)
    }
    
    suspend fun getAllDocuments(db: String): Result<List<Document>> {
        val docs = documents[db] ?: return Result.failure(Exception("Database not found"))
        return Result.success(docs.values.toList())
    }
}

// Channel functionality on the Key
object ChannelKey : CoroutineContext.Element, CoroutineContext.Key<ChannelKey> {
    override val key: CoroutineContext.Key<*> get() = ChannelKey
    data class Channel(val name: String, val queue: MutableList<Any> = mutableListOf())
    
    private val channels = mutableMapOf<String, Channel>()
    
    fun createChannel(name: String): Channel {
        val channel = Channel(name)
        channels[name] = channel
        return channel
    }
    
    suspend fun send(channel: Channel, data: Any) {
        channel.queue.add(data)
        MonitoringKey.recordMetric("channel.${channel.name}.sent", 1.0)
    }
    
    suspend fun receive(channel: Channel): Any? {
        val data = channel.queue.removeFirstOrNull()
        if (data != null) {
            MonitoringKey.recordMetric("channel.${channel.name}.received", 1.0)
        }
        return data
    }
    
    fun getQueueSize(channel: Channel): Int = channel.queue.size
}

// Reactor state on the Key
object ReactorKey : CoroutineContext.Element, CoroutineContext.Key<ReactorKey> {
    override val key: CoroutineContext.Key<*> get() = ReactorKey
    sealed class State {
        object Initial : State()
        object Starting : State()
        object Active : State()
        object Stopping : State()
        object Stopped : State()
        data class Error(val message: String) : State()
    }
    
    var state: State = State.Initial
        private set
    
    suspend fun transition(newState: State) {
        val oldState = state
        state = newState
        MonitoringKey.recordStateChange(oldState.toString(), newState.toString())
    }
    
    suspend fun ensureActive(): Result<Unit> {
        return if (state is State.Active) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Reactor not active: $state"))
        }
    }
}

// Monitoring functionality on the Key
object MonitoringKey : CoroutineContext.Element, CoroutineContext.Key<MonitoringKey> {
    override val key: CoroutineContext.Key<*> get() = MonitoringKey
    sealed class Event {
        data class Metric(val name: String, val value: Double, val timestamp: Long) : Event()
        data class StateChange(val from: String, val to: String, val timestamp: Long) : Event()
        data class Error(val error: Throwable, val timestamp: Long) : Event()
    }
    
    private val _events = MutableSharedFlow<Event>(replay = 100)
    val events: SharedFlow<Event> = _events.asSharedFlow()
    
    private val metrics = mutableMapOf<String, Double>()
    
    suspend fun recordMetric(name: String, value: Double) {
        metrics[name] = (metrics[name] ?: 0.0) + value
        _events.emit(Event.Metric(name, value, kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    }
    
    suspend fun recordStateChange(from: String, to: String) {
        _events.emit(Event.StateChange(from, to, kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    }
    
    suspend fun recordError(error: Throwable) {
        _events.emit(Event.Error(error, kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    }
    
    fun getMetric(name: String): Double = metrics[name] ?: 0.0
    
    fun getAllMetrics(): Map<String, Double> = metrics.toMap()
}

// Network functionality on the Key
object NetworkKey : CoroutineContext.Element, CoroutineContext.Key<NetworkKey> {
    override val key: CoroutineContext.Key<*> get() = NetworkKey
    data class Request(val method: String, val path: String, val body: String? = null)
    data class Response(val status: Int, val body: String)
    
    private var port: Int = 0
    private var handler: suspend (Request) -> Response = { Response(404, "Not found") }
    
    suspend fun listen(port: Int, handler: suspend (Request) -> Response) {
        this.port = port
        this.handler = handler
        ReactorKey.transition(ReactorKey.State.Active)
        println("🌐 Network listening on port $port")
    }
    
    suspend fun simulateRequest(request: Request): Response {
        ReactorKey.ensureActive().getOrThrow()
        MonitoringKey.recordMetric("network.requests", 1.0)
        return handler(request)
    }
    
    fun getPort(): Int = port
}

// Tracing functionality on the Key
object TracingKey : CoroutineContext.Element, CoroutineContext.Key<TracingKey> {
    override val key: CoroutineContext.Key<*> get() = TracingKey
    data class Span(
        val name: String,
        val startTime: Long,
        var endTime: Long? = null,
        val tags: MutableMap<String, String> = mutableMapOf()
    )
    
    private val activeSpans = mutableMapOf<String, Span>()
    
    suspend fun <T> trace(name: String, block: suspend Span.() -> T): T {
        val span = Span(name, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        activeSpans[name] = span
        
        return try {
            span.block().also {
                span.endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val duration = (span.endTime!! - span.startTime) / 1_000_000
                MonitoringKey.recordMetric("trace.$name.duration_ms", duration.toDouble())
                println("TRACE: $name took ${duration}ms")
            }
        } catch (e: Exception) {
            span.tags["error"] = e.message ?: "Unknown error"
            MonitoringKey.recordError(e)
            throw e
        } finally {
            activeSpans.remove(name)
        }
    }
}

// The main Fiduciary Service Key that orchestrates everything
object FiduciaryServiceKey : CoroutineContext.Element, CoroutineContext.Key<FiduciaryServiceKey> {
    override val key: CoroutineContext.Key<*> get() = FiduciaryServiceKey
    private var running = false
    private lateinit var scope: CoroutineScope
    
    suspend fun start(port: Int = 5984) {
        scope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
        running = true
        
        println("🚀 Starting Fiduciary CouchDB Service (CCEK Edition)")
        
        // Initialize reactor
        ReactorKey.transition(ReactorKey.State.Starting)
        
        // Create databases
        TracingKey.trace("init.databases") {
            CouchDBKey.createDatabase("fiduciary")
            CouchDBKey.createDatabase("patrick_devine_agent")
            CouchDBKey.createDatabase("channelized_data")
        }
        
        // Create channels
        val requestChannel = ChannelKey.createChannel("requests")
        val responseChannel = ChannelKey.createChannel("responses")
        
        // Start network handler
        NetworkKey.listen(port) { request ->
            TracingKey.trace("request.${request.method}.${request.path}") {
                handleRequest(request)
            }
        }
        
        // Start monitoring
        scope.launch {
            var eventCount = 0
            MonitoringKey.events.collect { event ->
                eventCount++
                if (eventCount % 100 == 0) {
                    println("📊 Monitoring: ${MonitoringKey.getAllMetrics()}")
                }
            }
        }
        
        // Start ingestion simulator
        scope.launch {
            var ingestCount = 0
            while (running) {
                delay(2.seconds)
                
                TracingKey.trace("ingest.document") {
                    val doc = mapOf(
                        "type" to "fiduciary_data",
                        "timestamp" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        "value" to ingestCount++
                    )
                    CouchDBKey.putDocument("fiduciary", "doc_$ingestCount", doc)
                }
                
                if (ingestCount % 10 == 0) {
                    println("💓 Fiduciary heartbeat - $ingestCount documents ingested")
                }
            }
        }
        
        ReactorKey.transition(ReactorKey.State.Active)
        println("✅ Fiduciary service running on port $port")
    }
    
    private suspend fun handleRequest(request: NetworkKey.Request): NetworkKey.Response {
        return when {
            request.path == "/" -> {
                NetworkKey.Response(200, """{"couchdb":"Fiduciary CCEK","version":"3.0.0"}""")
            }
            request.path == "/_all_dbs" -> {
                val dbs = CouchDBKey.getAllDatabases()
                NetworkKey.Response(200, dbs.joinToString(",", "[", "]") { "\"$it\"" })
            }
            request.method == "PUT" && request.path.startsWith("/") -> {
                val dbName = request.path.trim('/')
                CouchDBKey.createDatabase(dbName).fold(
                    onSuccess = { NetworkKey.Response(201, """{"ok":true}""") },
                    onFailure = { NetworkKey.Response(409, """{"error":"${it.message}"}""") }
                )
            }
            request.method == "GET" && request.path.count { it == '/' } == 2 -> {
                val parts = request.path.trim('/').split('/')
                val db = parts[0]
                val docId = parts[1]
                CouchDBKey.getDocument(db, docId).fold(
                    onSuccess = { doc ->
                        NetworkKey.Response(200, """{"_id":"${doc.id}","_rev":"${doc.rev}","data":${doc.data}}""")
                    },
                    onFailure = { NetworkKey.Response(404, """{"error":"not_found"}""") }
                )
            }
            else -> NetworkKey.Response(404, """{"error":"not_found"}""")
        }
    }
    
    suspend fun stop() {
        running = false
        ReactorKey.transition(ReactorKey.State.Stopping)
        scope.cancel()
        ReactorKey.transition(ReactorKey.State.Stopped)
        println("⏹️ Fiduciary service stopped")
    }
}

// Main function using composed CCEK
suspend fun main() = coroutineScope {
    // Compose all the Keys into the context
    val fiduciaryContext = 
        CouchDBKey + 
        ChannelKey + 
        ReactorKey + 
        MonitoringKey + 
        NetworkKey + 
        TracingKey + 
        FiduciaryServiceKey
    
    withContext(fiduciaryContext) {
        FiduciaryServiceKey.start(5984)
        
        // Simulate some requests after startup
        delay(3.seconds)
        
        println("\n📋 Testing the service:")
        
        // Test some requests
        val testRequests = listOf(
            NetworkKey.Request("GET", "/"),
            NetworkKey.Request("GET", "/_all_dbs"),
            NetworkKey.Request("PUT", "/test_db"),
            NetworkKey.Request("GET", "/fiduciary/doc_1")
        )
        
        for (request in testRequests) {
            val response = NetworkKey.simulateRequest(request)
            println("  ${request.method} ${request.path} -> ${response.status}: ${response.body}")
        }
        
        // Keep running
        delay(Long.MAX_VALUE)
    }
}