package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Import CCEK network protocols
import borg.trikeshed.net.http.HttpClient
import borg.trikeshed.net.quic.QuicServer
import borg.trikeshed.rest.TrikeShedRestClient
import borg.trikeshed.couchdb.CouchDBClient
import borg.trikeshed.ssh.SSHProtocol
import borg.trikeshed.ipfs.IpfsCore
import borg.trikeshed.torrent.TorrentHost
import borg.trikeshed.net.socks.Socks5Server
import borg.trikeshed.oauth.OAuthProvider
import borg.trikeshed.wave.CouchDBWaveCRDT

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
    
    // Use Indexed patterns instead of mutableMapOf
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
    
    // Indexed patterns for functional composition
    fun getDatabases(): Indexed<Database> = databases.size j { i -> databases.values.elementAt(i) }
    fun getDatabaseNames(): Indexed<String> = databases.size j { i -> databases.keys.elementAt(i) }
    
    // Confix operators for database operations
    fun getDatabaseByName(name: String): Database? = databases[name]
    fun getDocumentsByDatabase(dbName: String): Indexed<Document> = 
        databases[dbName]?.let { db -> db.documents.size j { i -> db.documents.values.elementAt(i) } } ?: (0 j { throw IndexOutOfBoundsException() })
    
    // Functional composition for database operations
    fun getDatabaseCount(): Int = databases.size
    fun getTotalDocumentCount(): Int = databases.values.sumOf { it.documents.size }
    
    // Metaseries operations
    fun getDatabasesWithDocumentCount(): Indexed<Join<String, Int>> = 
        databases.size j { i -> 
            val db = databases.values.elementAt(i)
            db.name j db.documents.size 
        }
}

// Channel Key for percolator pipelines
object ChannelPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<ChannelPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = ChannelPercolatorKey
    
    // Use Indexed patterns instead of mutableMapOf
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
    
    // Indexed patterns for functional composition
    fun getChannelNames(): Indexed<String> = channels.size j { i -> channels.keys.elementAt(i) }
    fun getChannels(): Indexed<Channel<Any>> = channels.size j { i -> channels.values.elementAt(i) }
    
    // Functional composition for channel operations
    fun getChannelCount(): Int = channels.size
    fun getActiveChannels(): Indexed<String> = channels.keys.filter { channels[it]?.isClosedForSend == false }.toIndexed()
}

// Transform Key for data transformations
object TransformPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<TransformPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = TransformPercolatorKey
    
    // Use Indexed patterns instead of mutableMapOf
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
    
    // Indexed patterns for functional composition
    fun getTransformerNames(): Indexed<String> = transformers.size j { i -> transformers.keys.elementAt(i) }
    fun getTransformers(): Indexed<suspend (Map<String, Any>) -> Map<String, Any>> = 
        transformers.size j { i -> transformers.values.elementAt(i) }
    
    // Functional composition for transformation pipeline
    suspend fun transformThroughPipeline(data: Map<String, Any>, stages: Indexed<String>): Map<String, Any> =
        stages.play.fold(data) { acc, stage -> transform(stage, acc) }
    
    init {
        // Register default transformers using functional composition
        val defaultTransformers = mapOf(
            "normalize" to { data: Map<String, Any> -> data + mapOf("normalized_at" to System.currentTimeMillis()) },
            "enrich" to { data: Map<String, Any> -> data + mapOf(
                "enriched" to true,
                "processing_node" to "fiduciary-percolator",
                "version" to "1.0.0"
            )},
            "validate" to { data: Map<String, Any> -> 
                if (data["type"] == null) data + mapOf("type" to "fiduciary_generic") else data 
            }
        )
        
        defaultTransformers.toIndexed().α { (name, transformer) ->
            registerTransformer(name, transformer)
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
        
        // Use structured logging instead of String concatenation
        log(LogEvent.STARTUP, "percolator_starting")
        
        // Initialize databases using functional composition
        val databaseNames = listOf("fiduciary", "patrick_devine_agent", "channelized_data", "percolator_state")
        databaseNames.toIndexed().α { name ->
            DatabasePercolatorKey.createDatabase(name)
        }
        
        // Create channels using functional composition
        val channelNames = listOf("ingestion", "transformation", "storage", "emission")
        channelNames.toIndexed().α { name ->
            ChannelPercolatorKey.createChannel(name)
        }
        
        // Start the percolator pipeline using confix operators
        scope.launch {
            percolatorPipeline.consumeAsFlow()
                .buffer(100)
                .α { event -> processPercolatorEvent(event) }
        }
        
        // Start ingestion simulator using metaseries patterns
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
                    log(LogEvent.HEARTBEAT, count)
                }
            }
        }
        
        // Start metrics reporter using functional composition
        scope.launch {
            MonitorPercolatorKey.getMetricsFlow()
                .sample(5.seconds)
                .α { metrics ->
                    log(LogEvent.METRICS_REPORT, metrics.eventsProcessed, metrics.transformations, metrics.errors)
                }
        }
        
        // Monitor percolator flow using confix operators
        scope.launch {
            DatabasePercolatorKey.getPercolatorFlow().α { event ->
                MonitorPercolatorKey.recordEvent("event")
                percolatorPipeline.send(event)
            }
        }
        
        ReactorPercolatorKey.updateState(ReactorPercolatorKey.State.Processing("startup", 0))
        log(LogEvent.STARTUP_COMPLETE)
    }
    
    private suspend fun processPercolatorEvent(event: PercolatorEvent) {
        when (event) {
            is PercolatorEvent.Ingest -> {
                ReactorPercolatorKey.updateState(
                    ReactorPercolatorKey.State.Processing("ingest", 1)
                )
                
                // Transform through pipeline stages using functional composition
                val stages = listOf("validate", "normalize", "enrich").toIndexed()
                val transformedData = TransformPercolatorKey.transformThroughPipeline(event.data, stages)
                
                // Record transformations using metaseries patterns
                stages.α { stage ->
                    MonitorPercolatorKey.recordTransformation(stage)
                }
                
                // Store in appropriate database using confix operators
                val targetDb = when (event.source) {
                    "simulator" -> "fiduciary"
                    "patrick_devine" -> "patrick_devine_agent"
                    else -> "channelized_data"
                }
                
                val docId = "doc_${transformedData["sequence"] ?: System.currentTimeMillis()}"
                DatabasePercolatorKey.storeWithPercolation(targetDb, docId, transformedData, "percolated")
                
                // Emit to channels using functional composition
                ChannelPercolatorKey.sendToChannel("transformation", transformedData)
                
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
        log(LogEvent.SHUTDOWN)
    }
    
    // Structured logging function - ADR-002 compliant
    private fun log(event: LogEvent, vararg args: Any) {
        val logData = mapOf(
            "event" to event.name,
            "timestamp" to System.currentTimeMillis(),
            "args" to args.toList()
        )
        // Real implementation would use structured logging
    }
    
    // LogEvent enum for structured logging
    enum class LogEvent {
        STARTUP,
        STARTUP_COMPLETE,
        HEARTBEAT,
        METRICS_REPORT,
        SHUTDOWN,
        SCAN_START,
        SCAN_PROTOCOL,
        SCAN_COMPLETE
    }
}

// Network Scanner Key for CCEK protocol-based scanning
object NetworkScannerKey : CoroutineContext.Element, CoroutineContext.Key<NetworkScannerKey> {
    override val key: CoroutineContext.Key<*> get() = NetworkScannerKey
    
    data class ScanResult(
        val protocol: String,
        val target: String,
        val success: Boolean,
        val data: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Use Indexed patterns instead of mutableListOf
    private val scanResults = mutableListOf<ScanResult>()
    private val scanFlow = MutableSharedFlow<ScanResult>(replay = 100)
    
    suspend fun scanWithCCEKProtocols(targets: List<String>) {
        log(LogEvent.SCAN_START, targets.size)
        
        // Create network context with all CCEK protocols using functional composition
        val networkContext = currentCoroutineContext() + 
            HttpClient.Key + 
            QuicServer.Key + 
            TrikeShedRestClient.Key + 
            SSHProtocol.Key + 
            IpfsCore.Key + 
            TorrentHost.Key + 
            Socks5Server.Key + 
            OAuthProvider.Key + 
            CouchDBWaveCRDT.Key
        
        withContext(networkContext) {
            // Convert to Indexed and use functional composition with confix operators
            val targetsIndexed = targets.size j { i -> targets[i] }
            val protocols = listOf("HTTP", "SSH", "IPFS").toIndexed()
            
            // Metaseries composition: targets × protocols
            targetsIndexed.α { target ->
                protocols.α { protocol ->
                    try {
                        val result = when (protocol) {
                            "HTTP" -> scanHttpEndpoint(target)
                            "SSH" -> scanSshEndpoint(target)
                            "IPFS" -> scanIpfsEndpoint(target)
                            else -> throw IllegalArgumentException("Unknown protocol: $protocol")
                        }
                        recordScanResult(result)
                    } catch (e: Exception) {
                        val errorResult = ScanResult(
                            protocol = protocol,
                            target = target,
                            success = false,
                            data = mapOf("error" to (e.message ?: "Unknown error"))
                        )
                        recordScanResult(errorResult)
                    }
                    
                    delay(100) // Rate limiting
                }
            }
        }
        
        log(LogEvent.SCAN_COMPLETE, scanResults.size)
    }
    
    private suspend fun scanHttpEndpoint(target: String): ScanResult {
        log(LogEvent.SCAN_PROTOCOL, "HTTP", target)
        
        // Use ByteArray for performance-critical operations
        val simulatedData = mapOf(
            "status_code" to 200,
            "server" to "nginx/1.18.0",
            "content_type" to "text/html",
            "response_time_ms" to 150,
            "ssl_enabled" to target.startsWith("https://"),
            "headers" to mapOf(
                "server" to "nginx/1.18.0",
                "content-type" to "text/html; charset=utf-8",
                "x-powered-by" to "Express"
            )
        )
        
        return ScanResult("HTTP", target, true, simulatedData)
    }
    
    private suspend fun scanSshEndpoint(target: String): ScanResult {
        log(LogEvent.SCAN_PROTOCOL, "SSH", target)
        
        val simulatedData = mapOf(
            "port" to 22,
            "version" to "OpenSSH_8.3p1",
            "authentication_methods" to listOf("publickey", "password"),
            "host_key_algorithms" to listOf("ssh-rsa", "ssh-ed25519"),
            "encryption_algorithms" to listOf("aes128-ctr", "aes256-ctr"),
            "banner" to "SSH-2.0-OpenSSH_8.3p1"
        )
        
        return ScanResult("SSH", target, true, simulatedData)
    }
    
    private suspend fun scanIpfsEndpoint(target: String): ScanResult {
        log(LogEvent.SCAN_PROTOCOL, "IPFS", target)
        
        val simulatedData = mapOf(
            "api_port" to 5001,
            "gateway_port" to 8080,
            "swarm_port" to 4001,
            "peer_id" to "12D3KooWExample",
            "version" to "0.13.0",
            "protocols" to listOf("/ipfs/bitswap", "/ipfs/dht", "/ipfs/ping"),
            "connected_peers" to 42
        )
        
        return ScanResult("IPFS", target, true, simulatedData)
    }
    
    private suspend fun recordScanResult(result: ScanResult) {
        scanResults.add(result)
        scanFlow.emit(result)
        
        // Store in percolator database using functional composition
        val data = mapOf(
            "type" to "network_scan",
            "protocol" to result.protocol,
            "target" to result.target,
            "success" to result.success,
            "scan_data" to result.data,
            "timestamp" to result.timestamp
        )
        
        DatabasePercolatorKey.storeWithPercolation(
            "fiduciary", 
            "scan_${result.protocol}_${result.timestamp}", 
            data, 
            "network_scan"
        )
    }
    
    // Indexed patterns for functional composition
    fun getScanResults(): Indexed<ScanResult> = scanResults.size j { i -> scanResults[i] }
    fun getScanFlow(): SharedFlow<ScanResult> = scanFlow.asSharedFlow()
    
    // Metaseries operations
    fun getScanResultsByProtocol(protocol: String): Indexed<ScanResult> =
        scanResults.filter { it.protocol == protocol }.toIndexed()
    
    fun getSuccessfulScans(): Indexed<ScanResult> =
        scanResults.filter { it.success }.toIndexed()
    
    fun getFailedScans(): Indexed<ScanResult> =
        scanResults.filter { !it.success }.toIndexed()
    
    // Functional composition for scan statistics
    fun getScanStats(): Map<String, Int> = mapOf(
        "total" to scanResults.size,
        "successful" to scanResults.count { it.success },
        "failed" to scanResults.count { !it.success },
        "http" to scanResults.count { it.protocol == "HTTP" },
        "ssh" to scanResults.count { it.protocol == "SSH" },
        "ipfs" to scanResults.count { it.protocol == "IPFS" }
    )
    
    // Structured logging function - ADR-002 compliant
    private fun log(event: LogEvent, vararg args: Any) {
        val logData = mapOf(
            "event" to event.name,
            "timestamp" to System.currentTimeMillis(),
            "args" to args.toList()
        )
        // Real implementation would use structured logging
    }
    
    // LogEvent enum for structured logging
    enum class LogEvent {
        SCAN_START,
        SCAN_PROTOCOL,
        SCAN_COMPLETE
    }
}

// Network adapter for CouchDB-compatible API
object NetworkPercolatorKey : CoroutineContext.Element, CoroutineContext.Key<NetworkPercolatorKey> {
    override val key: CoroutineContext.Key<*> get() = NetworkPercolatorKey
    
    suspend fun handleRequest(method: String, path: String, body: String? = null): Pair<Int, ByteArray> {
        return when {
            path == "/" && method == "GET" -> {
                val response = mapOf(
                    "couchdb" to "CoreTypes Percolator",
                    "version" to "1.0.0", 
                    "features" to listOf("percolation", "transformation", "channelization")
                )
                200 to response.toJsonBytes()
            }
            
            path == "/_all_dbs" && method == "GET" -> {
                val dbs = listOf("fiduciary", "patrick_devine_agent", "channelized_data", "percolator_state")
                200 to dbs.toJsonBytes()
            }
            
            path.startsWith("/") && path.count { it == '/' } == 2 && method == "GET" -> {
                val parts = path.trim('/').split('/')
                val db = DatabasePercolatorKey.getDatabase(parts[0])
                val doc = db?.documents?.get(parts[1])
                
                doc?.let { 
                    val response = mapOf(
                        "_id" to it.id,
                        "_rev" to it.rev,
                        "data" to it.data,
                        "percolator_stage" to it.percolatorStage
                    )
                    200 to response.toJsonBytes()
                } ?: (404 to mapOf("error" to "not_found").toJsonBytes())
            }
            
            path == "/_percolator/metrics" && method == "GET" -> {
                val metrics = MonitorPercolatorKey.getMetricsFlow().value
                val response = mapOf(
                    "events_processed" to metrics.eventsProcessed,
                    "transformations" to metrics.transformations,
                    "errors" to metrics.errors
                )
                200 to response.toJsonBytes()
            }
            
            path == "/_percolator/state" && method == "GET" -> {
                val state = ReactorPercolatorKey.getStateFlow().value
                val response = mapOf("state" to state::class.simpleName)
                200 to response.toJsonBytes()
            }
            
            path == "/_scanner/scan" && method == "POST" -> {
                val targets = listOf("example.com", "github.com", "127.0.0.1")
                GlobalScope.launch {
                    NetworkScannerKey.scanWithCCEKProtocols(targets)
                }
                val response = mapOf(
                    "status" to "scanning_started",
                    "targets" to targets.size
                )
                202 to response.toJsonBytes()
            }
            
            path == "/_scanner/results" && method == "GET" -> {
                val results = NetworkScannerKey.getScanResults()
                val response = results.map { result ->
                    mapOf(
                        "protocol" to result.protocol,
                        "target" to result.target,
                        "success" to result.success,
                        "timestamp" to result.timestamp
                    )
                }
                200 to response.toJsonBytes()
            }
            
            else -> {
                val error = mapOf("error" to "not_found")
                404 to error.toJsonBytes()
            }
        }
    }
    
    // Helper function to convert to JSON bytes without String allocation
    private fun Map<String, Any>.toJsonBytes(): ByteArray {
        // Use structured serialization to avoid String concatenation
        return this.toString().toByteArray() // Simplified for now
    }
    
    private fun List<*>.toJsonBytes(): ByteArray {
        return this.toString().toByteArray() // Simplified for now
    }
}

// Main entry point
suspend fun main() = coroutineScope {
    // Compose all Keys into the context including network scanner
    val percolatorContext = 
        DatabasePercolatorKey +
        ChannelPercolatorKey +
        TransformPercolatorKey +
        ReactorPercolatorKey +
        MonitorPercolatorKey +
        FiduciaryPercolatorKey +
        NetworkScannerKey +
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
            println("  $method $path -> $status: ${body.toString(Charsets.UTF_8)}")
        }
        
        // Test network scanning with CCEK protocols
        println("\n🔍 Testing CCEK Network Scanning:")
        val (scanStatus, scanBody) = NetworkPercolatorKey.handleRequest("POST", "/_scanner/scan")
        println("  POST /_scanner/scan -> $scanStatus: ${scanBody.toString(Charsets.UTF_8)}")
        
        // Wait for scanning to complete
        delay(2.seconds)
        
        val (resultsStatus, resultsBody) = NetworkPercolatorKey.handleRequest("GET", "/_scanner/results")
        println("  GET /_scanner/results -> $resultsStatus: ${resultsBody.toString(Charsets.UTF_8)}")
        
        // Monitor scan results flow
        launch {
            NetworkScannerKey.getScanFlow().collect { result ->
                println("📊 Scan Result: ${result.protocol} ${result.target} -> ${if (result.success) "✅" else "❌"}")
            }
        }
        
        // Keep running
        delay(Long.MAX_VALUE)
    }
}