#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

package fiduciary.demo

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fiduciary Ingres CCEK Integration Demo
 * 
 * This demo showcases the integration of CCEK (CoroutineContext.Element.Key) network protocols
 * with the fiduciary scanning ingres system, demonstrating how the percolator pattern 
 * processes network scan results through transformation pipelines.
 */

// Simulate CCEK Protocol Keys (normally imported from trikeshed modules)
object HttpClientKey : CoroutineContext.Element, CoroutineContext.Key<HttpClientKey> {
    override val key: CoroutineContext.Key<*> get() = HttpClientKey
    
    suspend fun scan(target: String): Map<String, Any> {
        delay(100) // Simulate network call
        return mapOf(
            "protocol" to "HTTP",
            "target" to target,
            "status_code" to 200,
            "server" to "nginx/1.18.0",
            "ssl" to target.startsWith("https://"),
            "response_time_ms" to (50..200).random()
        )
    }
}

object SSHProtocolKey : CoroutineContext.Element, CoroutineContext.Key<SSHProtocolKey> {
    override val key: CoroutineContext.Key<*> get() = SSHProtocolKey
    
    suspend fun scan(target: String): Map<String, Any> {
        delay(150) // Simulate network call
        return mapOf(
            "protocol" to "SSH",
            "target" to target,
            "port" to 22,
            "version" to "OpenSSH_8.3p1",
            "auth_methods" to listOf("publickey", "password"),
            "banner" to "SSH-2.0-OpenSSH_8.3p1"
        )
    }
}

object IpfsCoreKey : CoroutineContext.Element, CoroutineContext.Key<IpfsCoreKey> {
    override val key: CoroutineContext.Key<*> get() = IpfsCoreKey
    
    suspend fun scan(target: String): Map<String, Any> {
        delay(200) // Simulate network call
        return mapOf(
            "protocol" to "IPFS",
            "target" to target,
            "api_port" to 5001,
            "gateway_port" to 8080,
            "peer_id" to "12D3KooW${(1000..9999).random()}",
            "version" to "0.13.0",
            "connected_peers" to (10..100).random()
        )
    }
}

// Fiduciary Ingres System with CCEK Integration
class FiduciaryIngressCCEK {
    
    // Ingres Event Types
    sealed class IngressEvent {
        data class ScanInitiated(val targets: List<String>, val protocols: List<String>) : IngressEvent()
        data class ScanCompleted(val target: String, val protocol: String, val data: Map<String, Any>) : IngressEvent()
        data class DataIngested(val source: String, val data: Map<String, Any>) : IngressEvent()
        data class DataTransformed(val stage: String, val data: Map<String, Any>) : IngressEvent()
        data class DataStored(val id: String, val data: Map<String, Any>) : IngressEvent()
        data class AlertTriggered(val severity: String, val message: String, val data: Map<String, Any>) : IngressEvent()
    }
    
    // Ingres Storage
    private val ingressStorage = mutableMapOf<String, Map<String, Any>>()
    private val ingressFlow = MutableSharedFlow<IngressEvent>(replay = 1000)
    
    // Transformation Pipeline
    private val transformationPipeline = Channel<Pair<String, Map<String, Any>>>(capacity = 1000)
    
    // Alert System
    private val alertThresholds = mapOf(
        "http_slow_response" to 500,
        "ssh_outdated_version" to "OpenSSH_7",
        "ipfs_low_peers" to 5
    )
    
    suspend fun startIngres(scope: CoroutineScope) {
        println("🚀 Starting Fiduciary Ingres CCEK System")
        
        // Start transformation pipeline processor
        scope.launch {
            transformationPipeline.consumeAsFlow()
                .buffer(100)
                .collect { (source, data) ->
                    processIngressData(source, data)
                }
        }
        
        // Start metrics reporter
        scope.launch {
            while (true) {
                delay(10.seconds)
                val metrics = generateMetrics()
                println("📊 Ingres Metrics: ${metrics.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
        }
        
        // Start alert monitor
        scope.launch {
            ingressFlow.collect { event ->
                when (event) {
                    is IngressEvent.ScanCompleted -> checkAlerts(event.target, event.protocol, event.data)
                    is IngressEvent.AlertTriggered -> {
                        println("🚨 ALERT [${event.severity}]: ${event.message}")
                    }
                    else -> {}
                }
            }
        }
        
        println("✅ Fiduciary Ingres CCEK System started")
    }
    
    suspend fun performNetworkScan(targets: List<String>) {
        println("🔍 Starting CCEK Network Scan")
        
        // Create context with all CCEK protocols
        val ccekContext = HttpClientKey + SSHProtocolKey + IpfsCoreKey
        
        ingressFlow.emit(IngressEvent.ScanInitiated(targets, listOf("HTTP", "SSH", "IPFS")))
        
        withContext(ccekContext) {
            targets.forEach { target ->
                // Scan with HTTP
                launch {
                    try {
                        val httpResult = HttpClientKey.scan(target)
                        ingressFlow.emit(IngressEvent.ScanCompleted(target, "HTTP", httpResult))
                        transformationPipeline.send("http_scan" to httpResult)
                    } catch (e: Exception) {
                        println("❌ HTTP scan failed for $target: ${e.message}")
                    }
                }
                
                // Scan with SSH
                launch {
                    try {
                        val sshResult = SSHProtocolKey.scan(target)
                        ingressFlow.emit(IngressEvent.ScanCompleted(target, "SSH", sshResult))
                        transformationPipeline.send("ssh_scan" to sshResult)
                    } catch (e: Exception) {
                        println("❌ SSH scan failed for $target: ${e.message}")
                    }
                }
                
                // Scan with IPFS
                launch {
                    try {
                        val ipfsResult = IpfsCoreKey.scan(target)
                        ingressFlow.emit(IngressEvent.ScanCompleted(target, "IPFS", ipfsResult))
                        transformationPipeline.send("ipfs_scan" to ipfsResult)
                    } catch (e: Exception) {
                        println("❌ IPFS scan failed for $target: ${e.message}")
                    }
                }
                
                delay(50) // Rate limiting
            }
        }
        
        println("✅ CCEK Network Scan completed")
    }
    
    private suspend fun processIngressData(source: String, data: Map<String, Any>) {
        // Stage 1: Data Ingestion
        val ingestedData = data + mapOf(
            "source" to source,
            "ingested_at" to System.currentTimeMillis(),
            "ingress_id" to "ingress_${System.currentTimeMillis()}_${(1000..9999).random()}"
        )
        
        ingressFlow.emit(IngressEvent.DataIngested(source, ingestedData))
        
        // Stage 2: Data Transformation
        val transformedData = when (source) {
            "http_scan" -> transformHttpData(ingestedData)
            "ssh_scan" -> transformSshData(ingestedData)
            "ipfs_scan" -> transformIpfsData(ingestedData)
            else -> ingestedData
        }
        
        ingressFlow.emit(IngressEvent.DataTransformed(source, transformedData))
        
        // Stage 3: Data Storage
        val storageId = transformedData["ingress_id"] as String
        ingressStorage[storageId] = transformedData
        
        ingressFlow.emit(IngressEvent.DataStored(storageId, transformedData))
        
        println("💾 Stored ${transformedData["protocol"]} scan for ${transformedData["target"]}")
    }
    
    private fun transformHttpData(data: Map<String, Any>): Map<String, Any> {
        return data + mapOf(
            "transformed_at" to System.currentTimeMillis(),
            "risk_level" to when {
                (data["response_time_ms"] as Int) > 1000 -> "high"
                (data["ssl"] as Boolean) -> "low"
                else -> "medium"
            },
            "service_type" to "web_server",
            "classification" to "external_service"
        )
    }
    
    private fun transformSshData(data: Map<String, Any>): Map<String, Any> {
        return data + mapOf(
            "transformed_at" to System.currentTimeMillis(),
            "risk_level" to when {
                (data["version"] as String).contains("OpenSSH_7") -> "high"
                (data["auth_methods"] as List<*>).contains("password") -> "medium"
                else -> "low"
            },
            "service_type" to "ssh_server",
            "classification" to "infrastructure"
        )
    }
    
    private fun transformIpfsData(data: Map<String, Any>): Map<String, Any> {
        return data + mapOf(
            "transformed_at" to System.currentTimeMillis(),
            "risk_level" to when {
                (data["connected_peers"] as Int) < 10 -> "medium"
                else -> "low"
            },
            "service_type" to "ipfs_node",
            "classification" to "distributed_storage"
        )
    }
    
    private suspend fun checkAlerts(target: String, protocol: String, data: Map<String, Any>) {
        when (protocol) {
            "HTTP" -> {
                val responseTime = data["response_time_ms"] as Int
                if (responseTime > (alertThresholds["http_slow_response"] as Int)) {
                    ingressFlow.emit(IngressEvent.AlertTriggered(
                        "WARNING",
                        "Slow HTTP response detected",
                        mapOf("target" to target, "response_time" to responseTime)
                    ))
                }
            }
            
            "SSH" -> {
                val version = data["version"] as String
                if (version.contains("OpenSSH_7")) {
                    ingressFlow.emit(IngressEvent.AlertTriggered(
                        "HIGH",
                        "Outdated SSH version detected",
                        mapOf("target" to target, "version" to version)
                    ))
                }
            }
            
            "IPFS" -> {
                val peers = data["connected_peers"] as Int
                if (peers < (alertThresholds["ipfs_low_peers"] as Int)) {
                    ingressFlow.emit(IngressEvent.AlertTriggered(
                        "INFO",
                        "Low IPFS peer count",
                        mapOf("target" to target, "peers" to peers)
                    ))
                }
            }
        }
    }
    
    private fun generateMetrics(): Map<String, Any> {
        val protocolCounts = ingressStorage.values.groupingBy { it["protocol"] }.eachCount()
        val riskLevels = ingressStorage.values.groupingBy { it["risk_level"] }.eachCount()
        
        return mapOf(
            "total_scans" to ingressStorage.size,
            "protocols" to protocolCounts,
            "risk_levels" to riskLevels,
            "storage_entries" to ingressStorage.size
        )
    }
    
    fun getIngressData(): Map<String, Map<String, Any>> = ingressStorage.toMap()
    fun getIngressFlow(): SharedFlow<IngressEvent> = ingressFlow.asSharedFlow()
}

// Main Demo
suspend fun main() = coroutineScope {
    val fiduciaryIngres = FiduciaryIngressCCEK()
    
    println("🎯 Fiduciary Ingres CCEK Integration Demo")
    println("=" .repeat(50))
    
    // Start the ingres system
    fiduciaryIngres.startIngres(this)
    
    // Wait for startup
    delay(1.seconds)
    
    // Define scan targets
    val targets = listOf(
        "example.com",
        "github.com",
        "127.0.0.1",
        "192.168.1.1",
        "10.0.0.1"
    )
    
    println("\n🎯 Scanning ${targets.size} targets with CCEK protocols...")
    
    // Perform network scan
    fiduciaryIngres.performNetworkScan(targets)
    
    // Wait for scans to complete
    delay(3.seconds)
    
    // Display results
    println("\n📊 Ingres Results Summary:")
    println("=" .repeat(30))
    
    val ingressData = fiduciaryIngres.getIngressData()
    ingressData.forEach { (id, data) ->
        println("📋 $id:")
        println("   Protocol: ${data["protocol"]}")
        println("   Target: ${data["target"]}")
        println("   Risk Level: ${data["risk_level"]}")
        println("   Classification: ${data["classification"]}")
        println()
    }
    
    println("🎉 Demo completed successfully!")
    println("Total entries processed: ${ingressData.size}")
    
    // Monitor events for a bit longer
    println("\n👀 Monitoring ingres events for 5 seconds...")
    val monitorJob = launch {
        fiduciaryIngres.getIngressFlow().collect { event ->
            when (event) {
                is FiduciaryIngressCCEK.IngressEvent.AlertTriggered -> {
                    println("🚨 ${event.severity}: ${event.message}")
                }
                else -> {}
            }
        }
    }
    
    delay(5.seconds)
    monitorJob.cancel()
    
    println("\n✅ Demo finished - CCEK protocols successfully integrated with fiduciary ingres!")
}