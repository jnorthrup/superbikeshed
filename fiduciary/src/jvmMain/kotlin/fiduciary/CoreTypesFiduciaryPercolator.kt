package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * CoreTypes Fiduciary Percolator - CCEK Pattern Implementation
 * 
 * This implementation consolidates the CouchDB functionality into a percolator
 * pattern that processes fiduciary data through a series of transformations.
 * All functionality lives on the CoroutineContext.Keys themselves.
 */

// Core types for the percolator pattern
sealed class PercolatorEvent {
    data class Ingest(val source: String, val data: Map<String, Any>) : PercolatorEvent()
    data class Transform(val stage: String, val data: Map<String, Any>) : PercolatorEvent()
    data class Store(val database: String, val docId: String, val data: Map<String, Any>) : PercolatorEvent()
    data class Emit(val channel: String, val data: Any) : PercolatorEvent()
}

// Database Key with percolator-aware storage
object DatabasePercolatorKey : CoroutineContext.Element, CoroutineContext.Key<DatabasePercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = DatabasePercolatorKey
    
    data class Database(val name: String, val documents: MutableMap<String, Document> = mutableMapOf())
    data class Document(val id: String, val rev: String, val data: Map<String, Any>, val percolatorStage: String? = null)
    
    private val databases = mutableMapOf<String, Database>()
    private val percolatorFlow = MutableSharedFlow<PercolatorEvent>(replay = 100)
    
    suspend fun createDatabase(name: String): Database {
        val db = Database(name)
        databases[name] = db
        percolatorFlow.emit(PercolatorEvent.Store(name, "_metadata", mapOf("created" to System.currentTimeMillis())))
        return db
    }
    
    suspend fun storeWithPercolation(db: String, id: String, data: Map<String, Any>, stage: String? = null): Document {
        val database = databases[db] ?: throw Exception("Database $db not found")
        val rev = "1-${System.currentTimeMillis()}"
        val doc = Document(id, rev, data, stage)
        database.documents[id] = doc
        
        percolatorFlow.emit(PercolatorEvent.Store(db, id, data))
        return doc
    }
    
    fun getDatabase(name: String): Database? = databases[name]
    fun getPercolatorFlow(): SharedFlow<PercolatorEvent> = percolatorFlow.asSharedFlow()
}

// Channel Key for percolator pipelines
object ChannelPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<ChannelPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = ChannelPercolatorKey
    
    private val channels = mutableMapOf<String, Channel<Any>>()
    private val channelFlows = mutableMapOf<String, MutableSharedFlow<Any>>()
    
    fun createChannel(name: String, capacity: Int = Channel.UNLIMITED): Channel<Any> {
        val channel = Channel<Any>(capacity)
        channels[name] = channel
        channelFlows[name] = MutableSharedFlow(replay = 10)
        return channel
    }
    
    suspend fun sendToChannel(name: String, data: Any) {
        channels[name]?.send(data)
        channelFlows[name]?.emit(data)
        DatabasePercolatorKey.getPercolatorFlow().let {
            (it as MutableSharedFlow).emit(PercolatorEvent.Emit(name, data))
        }
    }
    
    fun getChannelFlow(name: String): SharedFlow<Any>? = channelFlows[name]?.asSharedFlow()
}

// Transform Key for data transformations
object TransformPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<TransformPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = TransformPercolatorKey
    
    private val transformers = mutableMapOf<String, suspend (Map<String, Any>) -> Map<String, Any>>()
    
    fun registerTransformer(name: String, transformer: suspend (Map<String, Any>) -> Map<String, Any>) {
        transformers[name] = transformer
    }
    
    suspend fun transform(stage: String, data: Map<String, Any>): Map<String, Any> {
        val transformer = transformers[stage] ?: return data
        val result = transformer(data)
        
        DatabasePercolatorKey.getPercolatorFlow().let {
            (it as MutableSharedFlow).emit(PercolatorEvent.Transform(stage, result))
        }
        
        return result
    }
    
    init {
        // Register default transformers
        registerTransformer("normalize") { data ->
            data + mapOf("normalized_at" to System.currentTimeMillis())
        }
        
        registerTransformer("enrich") { data ->
            data + mapOf(
                "enriched" to true,
                "processing_node" to "fiduciary-percolator",
                "version" to "1.0.0"
            )
        }
        
        registerTransformer("validate") { data ->
            if (data["type"] == null) {
                data + mapOf("type" to "fiduciary_generic")
            } else data
        }
    }
}

// Reactor Key for percolator state management
object ReactorPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<ReactorPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = ReactorPercolatorKey
    
    sealed class State {
        object Idle : State()
        data class Processing(val stage: String, val itemCount: Int) : State()
        data class Backpressure(val reason: String) : State()
        object Flushing : State()
    }
    
    private var state: State = State.Idle
    private val stateFlow = MutableStateFlow(state)
    
    suspend fun updateState(newState: State) {
        state = newState
        stateFlow.emit(newState)
    }
    
    fun getStateFlow(): StateFlow<State> = stateFlow.asStateFlow()
}

// Monitor Key for percolator metrics
object MonitorPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<MonitorPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = MonitorPercolatorKey
    
    data class Metrics(
        val eventsProcessed: Long = 0,
        val transformations: Map<String, Long> = emptyMap(),
        val channelActivity: Map<String, Long> = emptyMap(),
        val errors: Long = 0
    )
    
    private var metrics = Metrics()
    private val metricsFlow = MutableStateFlow(metrics)
    
    suspend fun recordEvent(type: String) {
        metrics = when (type) {
            "event" -> metrics.copy(eventsProcessed = metrics.eventsProcessed + 1)
            "error" -> metrics.copy(errors = metrics.errors + 1)
            else -> metrics
        }
        metricsFlow.emit(metrics)
    }
    
    suspend fun recordTransformation(stage: String) {
        val current = metrics.transformations[stage] ?: 0
        metrics = metrics.copy(
            transformations = metrics.transformations + (stage to (current + 1))
        )
        metricsFlow.emit(metrics)
    }
    
    fun getMetricsFlow(): StateFlow<Metrics> = metricsFlow.asStateFlow()
}

// The main Fiduciary Percolator Service
object FiduciaryPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<FiduciaryPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = FiduciaryPercolatorKey
    
    private lateinit var scope: CoroutineScope
    private val percolatorPipeline = Channel<PercolatorEvent>(capacity = 1000)
    
    suspend fun start() {
        scope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
        
        println("🚀 Starting CoreTypes Fiduciary Percolator")
        
        // Initialize databases
        DatabasePercolatorKey.createDatabase("fiduciary")
        DatabasePercolatorKey.createDatabase("patrick_devine_agent")
        DatabasePercolatorKey.createDatabase("channelized_data")
        DatabasePercolatorKey.createDatabase("percolator_state")
        
        // Create channels
        ChannelPercolatorKey.createChannel("ingestion")
        ChannelPercolatorKey.createChannel("transformation")
        ChannelPercolatorKey.createChannel("storage")
        ChannelPercolatorKey.createChannel("emission")
        
        // Start the percolator pipeline
        scope.launch {
            percolatorPipeline.consumeAsFlow()
                .buffer(100)
                .collect { event ->
                    processPercolatorEvent(event)
                }
        }
        
        // Start ingestion simulator
        scope.launch {
            var count = 0
            while (true) {
                delay(1.seconds)
                val data = mapOf(
                    "source" to "fiduciary_stream",
                    "timestamp" to System.currentTimeMillis(),
                    "sequence" to count++,
                    "type" to "raw_fiduciary_data"
                )
                
                percolatorPipeline.send(PercolatorEvent.Ingest("simulator", data))
                
                if (count % 10 == 0) {
                    println("💓 Percolator heartbeat - $count events ingested")
                }
            }
        }
        
        // Start metrics reporter
        scope.launch {
            MonitorPercolatorKey.getMetricsFlow()
                .sample(5.seconds)
                .collect { metrics ->
                    println("📊 Percolator Metrics:")
                    println("   Events: ${metrics.eventsProcessed}")
                    println("   Transformations: ${metrics.transformations}")
                    println("   Errors: ${metrics.errors}")
                }
        }
        
        // Monitor percolator flow
        scope.launch {
            DatabasePercolatorKey.getPercolatorFlow().collect { event ->
                MonitorPercolatorKey.recordEvent("event")
                percolatorPipeline.send(event)
            }
        }
        
        ReactorPercolatorKey.updateState(ReactorPercolatorKey.State.Processing("startup", 0))
        println("✅ Fiduciary Percolator running")
    }
    
    private suspend fun processPercolatorEvent(event: PercolatorEvent) {
        when (event) {
            is PercolatorEvent.Ingest -> {
                ReactorPercolatorKey.updateState(
                    ReactorPercolatorKey.State.Processing("ingest", 1)
                )
                
                // Transform through pipeline stages
                var data = event.data
                
                // Stage 1: Validate
                data = TransformPercolatorKey.transform("validate", data)
                MonitorPercolatorKey.recordTransformation("validate")
                
                // Stage 2: Normalize
                data = TransformPercolatorKey.transform("normalize", data)
                MonitorPercolatorKey.recordTransformation("normalize")
                
                // Stage 3: Enrich
                data = TransformPercolatorKey.transform("enrich", data)
                MonitorPercolatorKey.recordTransformation("enrich")
                
                // Store in appropriate database
                val targetDb = when (event.source) {
                    "simulator" -> "fiduciary"
                    "patrick_devine" -> "patrick_devine_agent"
                    else -> "channelized_data"
                }
                
                val docId = "doc_${data["sequence"] ?: System.currentTimeMillis()}"
                DatabasePercolatorKey.storeWithPercolation(targetDb, docId, data, "percolated")
                
                // Emit to channels
                ChannelPercolatorKey.sendToChannel("transformation", data)
                
                ReactorPercolatorKey.updateState(ReactorPercolatorKey.State.Idle)
            }
            
            is PercolatorEvent.Transform -> {
                // Already handled in Ingest, but could add additional processing
                ChannelPercolatorKey.sendToChannel("transformation", event.data)
            }
            
            is PercolatorEvent.Store -> {
                ChannelPercolatorKey.sendToChannel("storage", event)
            }
            
            is PercolatorEvent.Emit -> {
                // Channel emission already handled
            }
        }
    }
    
    suspend fun stop() {
        ReactorPercolatorKey.updateState(ReactorPercolatorKey.State.Flushing)
        percolatorPipeline.close()
        scope.cancel()
        println("⏹️ Fiduciary Percolator stopped")
    }
}

// Network adapter for CouchDB-compatible API
object NetworkPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<NetworkPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = NetworkPercolatorKey
    
    suspend fun handleRequest(method: String, path: String, body: String? = null): Pair<Int, String> {
        return when {
            path == "/" && method == "GET" -> {
                200 to """{"couchdb":"CoreTypes Percolator","version":"1.0.0","features":["percolation","transformation","channelization"]}"""
            }
            
            path == "/_all_dbs" && method == "GET" -> {
                val dbs = listOf("fiduciary", "patrick_devine_agent", "channelized_data", "percolator_state")
                200 to dbs.joinToString(",", "[", "]") { "\"$it\"" }
            }
            
            path.startsWith("/") && path.count { it == '/' } == 2 && method == "GET" -> {
                val parts = path.trim('/').split('/')
                val db = DatabasePercolatorKey.getDatabase(parts[0])
                val doc = db?.documents?.get(parts[1])
                
                if (doc != null) {
                    200 to """{"_id":"${doc.id}","_rev":"${doc.rev}","data":${doc.data},"percolator_stage":"${doc.percolatorStage}"}"""
                } else {
                    404 to """{"error":"not_found"}"""
                }
            }
            
            path == "/_percolator/metrics" && method == "GET" -> {
                val metrics = MonitorPercolatorKey.getMetricsFlow().value
                200 to """{"events_processed":${metrics.eventsProcessed},"transformations":${metrics.transformations},"errors":${metrics.errors}}"""
            }
            
            path == "/_percolator/state" && method == "GET" -> {
                val state = ReactorPercolatorKey.getStateFlow().value
                200 to """{"state":"${state::class.simpleName}"}"""
            }
            
            else -> 404 to """{"error":"not_found"}"""
        }
    }
}

// Main entry point
suspend fun main() = coroutineScope {
    // Compose all Keys into the context
    val percolatorContext = 
        DatabasePercolatorKey +
        ChannelPercolatorKey +
        TransformPercolatorKey +
        ReactorPercolatorKey +
        MonitorPercolatorKey +
        FiduciaryPercolatorKey +
        NetworkPercolatorKey
    
    withContext(percolatorContext) {
        FiduciaryPercolatorKey.start()
        
        // Demo: Simulate some API requests after startup
        delay(3.seconds)
        
        println("\n📋 Testing Percolator API:")
        val testRequests = listOf(
            "GET" to "/",
            "GET" to "/_all_dbs",
            "GET" to "/_percolator/metrics",
            "GET" to "/_percolator/state",
            "GET" to "/fiduciary/doc_1"
        )
        
        for ((method, path) in testRequests) {
            val (status, body) = NetworkPercolatorKey.handleRequest(method, path)
            println("  $method $path -> $status: $body")
        }
        
        // Keep running
        delay(Long.MAX_VALUE)
    }
}