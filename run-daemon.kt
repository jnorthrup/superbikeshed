#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * LIVE FIDUCIARY PERCOLATOR DAEMON
 * 
 * This daemon runs continuously, showing real-time data percolation
 * Watch as network scan data flows through transformation stages
 */

// Core Data Types
data class FiduciaryData(
    val id: String,
    val source: String,
    val content: Map<String, Any>,
    val stage: String = "raw",
    val timestamp: Long = System.currentTimeMillis()
)

// The Live Percolator Engine
object LivePercolator : CoroutineContext.Element, CoroutineContext.Key<LivePercolator> {
    override val key: CoroutineContext.Key<*> get() = LivePercolator
    
    private val ingestChannel = Channel<FiduciaryData>(capacity = 1000)
    private val processChannel = Channel<FiduciaryData>(capacity = 1000)
    private val storage = mutableMapOf<String, FiduciaryData>()
    private var totalProcessed = 0L
    private var criticalAlerts = 0
    
    suspend fun startLivePercolation(scope: CoroutineScope) {
        // Ingestion Pipeline
        scope.launch {
            ingestChannel.consumeAsFlow().collect { data ->
                println("📥 INGEST: ${data.id} from ${data.source}")
                val processed = processData(data)
                processChannel.send(processed)
            }
        }
        
        // Processing Pipeline  
        scope.launch {
            processChannel.consumeAsFlow().collect { data ->
                storage[data.id] = data
                totalProcessed++
                
                val riskScore = data.content["risk_score"] as? Int ?: 0
                val classification = data.content["security_classification"] as? String ?: "low"
                
                val indicator = when (classification) {
                    "critical" -> { criticalAlerts++; "🔴" }
                    "high" -> "🟠"
                    "medium" -> "🟡" 
                    else -> "🟢"
                }
                
                println("⚡ PROCESS: $indicator ${data.id} → Risk:$riskScore ${data.content["target"]}:${data.content["port"]} [${data.content["asset_type"]}]")
            }
        }
        
        // Continuous Data Generation
        scope.launch {
            generateLiveData()
        }
        
        // Live Status Reporting
        scope.launch {
            reportLiveStatus()
        }
    }
    
    private suspend fun generateLiveData() {
        var counter = 0
        val targets = listOf("192.168.1.100", "10.0.0.50", "172.16.1.10", "203.0.113.10", "192.168.1.200")
        val ports = listOf(22, 80, 443, 3389, 5432, 5984, 6379)
        val services = mapOf(22 to "ssh", 80 to "http", 443 to "https", 3389 to "rdp", 5432 to "postgresql", 5984 to "couchdb", 6379 to "redis")
        
        while (true) {
            val target = targets.random()
            val port = ports.random()
            val service = services[port] ?: "unknown"
            val isVulnerable = Math.random() > 0.8
            val isExposed = Math.random() > 0.7
            
            val data = FiduciaryData(
                id = "live_${++counter}",
                source = "live_scanner",
                content = mapOf(
                    "target" to target,
                    "port" to port,
                    "service" to service,
                    "exposed" to isExposed,
                    "vulnerable" to isVulnerable
                )
            )
            
            ingestChannel.send(data)
            delay((1..3).random().seconds)
        }
    }
    
    private fun processData(data: FiduciaryData): FiduciaryData {
        val content = data.content.toMutableMap()
        
        // Calculate risk score
        var riskScore = 30
        if (content["exposed"] == true) riskScore += 40
        if (content["vulnerable"] == true) riskScore += 30
        if (content["port"] == 22) riskScore += 20
        if (content["port"] == 3389) riskScore += 35
        
        content["risk_score"] = riskScore
        
        // Determine classification
        content["security_classification"] = when {
            riskScore >= 80 -> "critical"
            riskScore >= 60 -> "high" 
            riskScore >= 40 -> "medium"
            else -> "low"
        }
        
        // Determine asset type
        content["asset_type"] = when (content["port"]) {
            80, 443 -> "web_server"
            22 -> "ssh_server"
            3389 -> "rdp_server" 
            5432 -> "database"
            5984 -> "document_store"
            6379 -> "cache"
            else -> "unknown"
        }
        
        // Determine location
        val target = content["target"].toString()
        content["location"] = when {
            target.startsWith("192.168.") -> "internal"
            target.startsWith("10.") -> "internal"
            target.startsWith("172.") -> "internal"
            else -> "external"
        }
        
        return data.copy(content = content, stage = "processed")
    }
    
    private suspend fun reportLiveStatus() {
        val startTime = System.currentTimeMillis()
        
        while (true) {
            delay(10.seconds)
            
            val uptime = (System.currentTimeMillis() - startTime) / 1000 / 60
            val rate = if (uptime > 0) totalProcessed.toDouble() / uptime else 0.0
            
            println()
            println("📊 LIVE DAEMON STATUS - Uptime: ${uptime}m")
            println("   📈 Total Processed: $totalProcessed")
            println("   ⚡ Processing Rate: ${"%.1f".format(rate)}/min")
            println("   🚨 Critical Alerts: $criticalAlerts")
            println("   💾 Storage Size: ${storage.size}")
            println("   🌊 PERCOLATOR IS PERCOLATING!")
            println()
        }
    }
    
    fun getStats() = mapOf(
        "total_processed" to totalProcessed,
        "critical_alerts" to criticalAlerts,
        "storage_size" to storage.size
    )
}

// Main Daemon Entry Point
suspend fun main() = coroutineScope {
    println("🔥🔥🔥 FIDUCIARY PERCOLATOR DAEMON STARTING 🔥🔥🔥")
    println("=" .repeat(60))
    println("🌊 Live data percolation in progress...")
    println("📊 Status reports every 10 seconds")
    println("🚨 Critical alerts highlighted in red")
    println("Press Ctrl+C to stop")
    println()
    
    val percolatorContext = coroutineContext + LivePercolator
    
    withContext(percolatorContext) {
        LivePercolator.startLivePercolation(this)
        
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            println("\n🛑 Shutting down daemon...")
            val stats = LivePercolator.getStats()
            println("📊 Final Stats: ${stats}")
            println("✅ Daemon stopped")
        })
        
        // Keep running
        while (true) {
            delay(1.seconds)
        }
    }
}