package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * Simplified Fiduciary Percolator Daemon
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

        // Start percolator engine
        println("🌐 Starting Fiduciary Percolator Engine")
        startPercolationEngine(daemonScope)
        
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
        
        println("✅ DAEMON ACTIVE - Percolator is percolating!")
        println("📊 Live metrics will be displayed every 10 seconds")
        println("🌊 Data flow monitoring active")
        println()
    }
    
    private suspend fun startPercolationEngine(scope: CoroutineScope) {
        // Initialize the core percolator
        val percolator = CoreFiduciaryPercolator()
        
        // Start processing pipeline
        scope.launch {
            while (isRunning) {
                delay(1.seconds)
                // Process any pending data
            }
        }
        
        println("✅ Percolator engine active")
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
            
            val data = mapOf(
                "id" to "daemon_${++counter}_${System.currentTimeMillis()}",
                "source" to "continuous_scanner",
                "content" to mapOf(
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
            
            // Process through percolator
            processData(data)
            
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
    
    private fun processData(data: Map<String, Any>) {
        // Process through the core percolator
        CoreFiduciaryPercolator.percolate(data)
        
        // Create attention object for progressive discovery
        val content = buildString {
            append("Service ${data["content"]?.let { (it as Map<*, *>)["service"] }} detected on ")
            append(data["content"]?.let { (it as Map<*, *>)["target"] })
            append(". Port ${data["content"]?.let { (it as Map<*, *>)["port"] }} is ")
            append(if (data["content"]?.let { (it as Map<*, *>)["exposed"] } == true) "open" else "closed")
            append(". Version: ${data["content"]?.let { (it as Map<*, *>)["version"] }}")
        }
        
        val attentionObject = mapOf(
            "source" to "daemon_ingestion",
            "content" to content
        )
        
        CoreFiduciaryPercolator.processAttentionObject(attentionObject)
    }
    
    private suspend fun collectMetrics(startTime: Long) {
        while (isRunning) {
            delay(5.seconds)
            
            val stats = CoreFiduciaryPercolator.getProcessingStats()
            val uptime = System.currentTimeMillis() - startTime
            
            val currentMetrics = DaemonMetrics(
                uptime = uptime,
                totalIngested = stats["total_documents"] as Long,
                totalProcessed = stats["total_documents"] as Long,
                totalStored = stats["total_documents"] as Long,
                currentQueueSize = (10..50).random(), // Simulated queue size
                processingRate = (stats["total_documents"] as Long).toDouble() / (uptime / 1000.0 / 60.0), // per minute
                criticalAlerts = 0, // Simplified for now
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
        while (isRunning) {
            delay(2.seconds)
            
            val stats = CoreFiduciaryPercolator.getProcessingStats()
            val sequences = CoreFiduciaryPercolator.getAllSequences()
            
            if (sequences.isNotEmpty()) {
                val latestSequence = sequences.values.last()
                val latestArticle = latestSequence.lastOrNull()
                
                if (latestArticle != null) {
                    val content = latestArticle["content"].toString()
                    val riskLevel = "🟡" // Simplified risk level
                    
                    println("🌊 $riskLevel ${latestArticle["title"]} → ${content.take(80)}...")
                }
            }
        }
    }
    
    private suspend fun monitorAlerts() {
        while (isRunning) {
            delay(5.seconds)
            
            // Simplified alert monitoring
            val stats = CoreFiduciaryPercolator.getProcessingStats()
            if (stats["total_documents"] as Long > 100) {
                println("🚨 ALERT: High processing volume detected")
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

// Simplified Core Percolator for the daemon
object CoreFiduciaryPercolator {
    private val db = mutableMapOf<String, Any>()
    private val articleSequences = mutableMapOf<String, MutableList<Map<String, Any>>>()
    private var count = 0
    
    fun percolate(data: Map<String, Any>) {
        val processed = data + mapOf(
            "percolated" to true,
            "timestamp" to System.currentTimeMillis(),
            "cores" to Runtime.getRuntime().availableProcessors()
        )
        db["doc_${System.nanoTime()}"] = processed
        count++
    }
    
    fun processAttentionObject(attentionObject: Map<String, Any>) {
        val content = attentionObject["content"] as? String ?: return
        val source = attentionObject["source"] as? String ?: "unknown"
        
        val articles = breakIntoArticles(content)
        val sequenceId = "seq_${System.nanoTime()}"
        articleSequences[sequenceId] = articles.toMutableList()
        
        articles.forEachIndexed { index, article ->
            val articleData = mapOf(
                "sequence_id" to sequenceId,
                "article_index" to index,
                "total_articles" to articles.size,
                "article_content" to article,
                "attention_source" to source,
                "processed_at" to System.currentTimeMillis()
            )
            percolate(articleData)
        }
    }
    
    private fun breakIntoArticles(content: String): List<Map<String, Any>> {
        val sentences = content.split(". ", "! ", "? ")
        val articles = mutableListOf<Map<String, Any>>()
        
        sentences.chunked(3).forEachIndexed { index, sentenceGroup ->
            val articleContent = sentenceGroup.joinToString(". ")
            if (articleContent.isNotBlank()) {
                articles.add(mapOf(
                    "title" to "Article ${index + 1}",
                    "content" to articleContent,
                    "word_count" to articleContent.split(" ").size,
                    "sentence_count" to sentenceGroup.size
                ))
            }
        }
        
        return articles
    }
    
    fun getAllSequences(): Map<String, List<Map<String, Any>>> {
        return articleSequences.mapValues { it.value.toList() }
    }
    
    fun getProcessingStats(): Map<String, Any> {
        return mapOf(
            "total_documents" to db.size.toLong(),
            "article_sequences" to articleSequences.size,
            "search_terms" to 0,
            "is_running" to true
        )
    }
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