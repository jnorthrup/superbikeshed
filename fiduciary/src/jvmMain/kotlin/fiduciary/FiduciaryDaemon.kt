package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import borg.trikeshed.couchdb.launchCouchDBWithCCEK
import borg.trikeshed.couchdb.CouchDBServiceCCEK
import borg.trikeshed.couchdb.SimpleNetworkService
import borg.trikeshed.couchdb.MemoryBlobService
import borg.trikeshed.couchdb.SimpleChannelService
import borg.trikeshed.couchdb.MemoryStorageEngine
import fiduciary.ConcentricDispatcher

/**
 * Fiduciary Percolator Daemon
 * 
 * Runs continuously, percolating data through transformation stages
 * Shows live status, metrics, and real-time data flow
 */

class FiduciaryDaemon : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<FiduciaryDaemon>
    override val key: CoroutineContext.Key<*> get() = Key
    
    private var isRunning = false
    private lateinit var daemonScope: CoroutineScope
    private val statusFlow = MutableStateFlow("STARTING")
    private val metricsFlow = MutableStateFlow(DaemonMetrics())
    
    data class DaemonMetrics(
        val uptime: Long = 0,
        val totalIngested: Long = 0,
        val totalProcessed: Long = 0,
        val totalStored: Long = 0,
        val currentQueueSize: Int = 0,
        val processingRate: Double = 0.0,
        val criticalAlerts: Int = 0,
        val lastActivity: Long = System.currentTimeMillis()
    )
    
    suspend fun start() {
        if (isRunning) {
            println("⚠️  Daemon already running")
            return
        }
        
        daemonScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        isRunning = true
        statusFlow.value = "RUNNING"
        
        val startTime = System.currentTimeMillis()
        
        println("🔥 FIDUCIARY PERCOLATOR DAEMON STARTING")
        println("=" .repeat(50))

        // --- Start production CouchDB CCEK server on port 5984 ---
        daemonScope.launch {
            println("🚀 Launching production CouchDB CCEK server on port 5984 (pluggable context)")
            val couchdbService: borg.trikeshed.couchdb.CouchDBServiceCCEK = borg.trikeshed.couchdb.launchCouchDBWithCCEK(
                port = 5984,
                networkService = borg.trikeshed.couchdb.SimpleNetworkService(),
                blobService = borg.trikeshed.couchdb.MemoryBlobService(),
                channelService = borg.trikeshed.couchdb.SimpleChannelService(),
                storageEngine = borg.trikeshed.couchdb.MemoryStorageEngine()
            )
            println("✅ CouchDB CCEK server running on port 5984")
        }
        // --- End CouchDB CCEK server launch ---

        // --- Start concentric networking/IPC ---
        val concentricContext = coroutineContext + ConcentricDispatcher
        withContext(concentricContext) {
            println("🌐 Starting ConcentricDispatcher (networking/IPC)")
            ConcentricDispatcher.startDispatcher(this)
            println("✅ ConcentricDispatcher active")
            // Start percolator engine in this context
            FiduciaryPercolator.startPercolation(daemonScope)
            
            // Start continuous ingestion simulator
            daemonScope.launch {
                simulateContinuousIngestion()
            }
            
            // Start metrics collector
            daemonScope.launch {
                collectMetrics(startTime)
            }
            
            // Start status reporter
            daemonScope.launch {
                reportStatus()
            }
            
            // Start live flow monitor
            daemonScope.launch {
                monitorLiveFlow()
            }
            
            // Start alerting system
            daemonScope.launch {
                monitorAlerts()
            }
        }
        // --- End concentric networking/IPC ---
        
        println("✅ DAEMON ACTIVE - Percolator is percolating!")
        println("📊 Live metrics will be displayed every 10 seconds")
        println("🌊 Data flow monitoring active")
        println()
    }
    
    private suspend fun simulateContinuousIngestion() {
        var counter = 0
        val targets = listOf(
            "192.168.1.100", "192.168.1.101", "192.168.1.102",
            "10.0.0.50", "10.0.0.51", "10.0.0.52",
            "172.16.1.10", "172.16.1.11", "172.16.1.12",
            "203.0.113.10", "203.0.113.11", "203.0.113.12"
        )
        val ports = listOf(22, 80, 443, 3389, 5432, 5984, 6379, 8080, 9200)
        val services = mapOf(
            22 to "ssh", 80 to "http", 443 to "https", 3389 to "rdp",
            5432 to "postgresql", 5984 to "couchdb", 6379 to "redis",
            8080 to "http-alt", 9200 to "elasticsearch"
        )
        
        while (isRunning) {
            val target = targets.random()
            val port = ports.random()
            val service = services[port] ?: "unknown"
            
            val data = FiduciaryData(
                id = "daemon_${++counter}_${System.currentTimeMillis()}",
                source = "continuous_scanner",
                content = mapOf(
                    "target" to target,
                    "port" to port,
                    "service" to service,
                    "version" to generateRandomVersion(service),
                    "exposed" to (Math.random() > 0.7),
                    "vulnerable" to (Math.random() > 0.8),
                    "ssl_enabled" to (port == 443 || Math.random() > 0.6),
                    "ssl_expired" to (Math.random() > 0.9),
                    "response_time" to (50..500).random()
                )
            )
            
            FiduciaryPercolator.ingest(data)
            
            // Variable delay to simulate realistic scanning
            delay((1..5).random().seconds)
        }
    }
    
    private fun generateRandomVersion(service: String): String {
        return when (service) {
            "ssh" -> listOf("OpenSSH_7.4", "OpenSSH_8.0", "OpenSSH_8.3", "OpenSSH_9.0").random()
            "http", "https" -> listOf("nginx/1.18.0", "Apache/2.4.41", "Apache/2.2.15").random()
            "postgresql" -> listOf("PostgreSQL 12.5", "PostgreSQL 13.1", "PostgreSQL 14.0").random()
            "couchdb" -> listOf("CouchDB/3.1.0", "CouchDB/3.0.1", "CouchDB/2.3.1").random()
            "redis" -> listOf("Redis 6.0.9", "Redis 5.0.7", "Redis 4.0.14").random()
            "elasticsearch" -> listOf("Elasticsearch 7.10.0", "Elasticsearch 6.8.12").random()
            else -> "Unknown"
        }
    }
    
    private suspend fun collectMetrics(startTime: Long) {
        while (isRunning) {
            delay(5.seconds)
            
            val storage = FiduciaryPercolator.getStorage()
            val uptime = System.currentTimeMillis() - startTime
            
            val criticalCount = storage.values.count { 
                it.content["security_classification"] == "critical" 
            }
            
            val currentMetrics = DaemonMetrics(
                uptime = uptime,
                totalIngested = storage.size.toLong(),
                totalProcessed = storage.size.toLong(),
                totalStored = storage.size.toLong(),
                currentQueueSize = (10..50).random(), // Simulated queue size
                processingRate = storage.size.toDouble() / (uptime / 1000.0 / 60.0), // per minute
                criticalAlerts = criticalCount,
                lastActivity = System.currentTimeMillis()
            )
            
            metricsFlow.value = currentMetrics
        }
    }
    
    private suspend fun reportStatus() {
        while (isRunning) {
            delay(10.seconds)
            
            val metrics = metricsFlow.value
            val uptimeMinutes = metrics.uptime / 1000 / 60
            
            println("📊 DAEMON STATUS REPORT")
            println("   ⏱️  Uptime: ${uptimeMinutes}m")
            println("   📥 Total Ingested: ${metrics.totalIngested}")
            println("   ⚙️  Processing Rate: ${"%.2f".format(metrics.processingRate)}/min")
            println("   💾 Total Stored: ${metrics.totalStored}")
            println("   🚨 Critical Alerts: ${metrics.criticalAlerts}")
            println("   📊 Queue Size: ${metrics.currentQueueSize}")
            println("   Status: ${statusFlow.value}")
            println()
        }
    }
    
    private suspend fun monitorLiveFlow() {
        FiduciaryPercolator.getPercolationFlow().collect { data ->
            if (isRunning) {
                val riskLevel = when (data.content["security_classification"]) {
                    "critical" -> "🔴"
                    "high" -> "🟠" 
                    "medium" -> "🟡"
                    else -> "🟢"
                }
                
                println("🌊 $riskLevel ${data.id} → ${data.stage} | ${data.content["target"]}:${data.content["port"]} | Risk: ${data.content["risk_score"]}")
            }
        }
    }
    
    private suspend fun monitorAlerts() {
        FiduciaryPercolator.getPercolationFlow()
            .filter { it.content["security_classification"] == "critical" }
            .collect { data ->
                if (isRunning) {
                    println("🚨 CRITICAL ALERT: ${data.content["target"]}:${data.content["port"]} - ${data.content["asset_type"]} (Risk: ${data.content["risk_score"]})")
                }
            }
    }
    
    suspend fun stop() {
        if (!isRunning) {
            println("⚠️  Daemon not running")
            return
        }
        
        println("🛑 Stopping Fiduciary Daemon...")
        isRunning = false
        statusFlow.value = "STOPPING"
        
        daemonScope.cancel()
        
        val finalMetrics = metricsFlow.value
        println("📊 FINAL STATISTICS:")
        println("   Total Runtime: ${finalMetrics.uptime / 1000 / 60}m")
        println("   Total Processed: ${finalMetrics.totalProcessed}")
        println("   Critical Alerts: ${finalMetrics.criticalAlerts}")
        
        statusFlow.value = "STOPPED"
        println("✅ Daemon stopped")
    }
    
    fun getStatus(): String = statusFlow.value
    fun getMetrics(): DaemonMetrics = metricsFlow.value
    fun isActive(): Boolean = isRunning
}

// Daemon Control CLI
suspend fun main(args: Array<String>) = coroutineScope {
    val daemon = FiduciaryDaemon()
    
    when (args.getOrNull(0)) {
        "start" -> {
            println("🚀 Starting Fiduciary Percolator Daemon")
            daemon.start()
            
            // Keep daemon running
            while (daemon.isActive()) {
                delay(1.seconds)
            }
        }
        
        "stop" -> {
            daemon.stop()
        }
        
        "status" -> {
            println("Daemon Status: ${daemon.getStatus()}")
            if (daemon.isActive()) {
                val metrics = daemon.getMetrics()
                println("Uptime: ${metrics.uptime / 1000 / 60}m")
                println("Processed: ${metrics.totalProcessed}")
                println("Critical: ${metrics.criticalAlerts}")
            }
        }
        
        else -> {
            println("🔥 FIDUCIARY PERCOLATOR DAEMON")
            println()
            println("Usage:")
            println("  kotlin FiduciaryDaemon.kt start   - Start daemon")
            println("  kotlin FiduciaryDaemon.kt stop    - Stop daemon")
            println("  kotlin FiduciaryDaemon.kt status  - Show status")
            println()
            println("Starting daemon by default...")
            
            daemon.start()
            
            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                runBlocking { daemon.stop() }
            })
            
            // Keep running until interrupted
            try {
                while (daemon.isActive()) {
                    delay(1.seconds)
                }
            } catch (e: CancellationException) {
                daemon.stop()
            }
        }
    }
}