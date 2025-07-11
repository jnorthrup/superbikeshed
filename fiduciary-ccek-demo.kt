#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Standalone CCEK Fiduciary Demo
 * Run with: kotlin fiduciary-ccek-demo.kt
 */

// Simplified CouchDB functionality on the Key
object CouchDBKey : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<CouchDBKey>
    
    private val databases = mutableMapOf<String, MutableList<String>>()
    
    fun createDatabase(name: String) {
        databases[name] = mutableListOf()
        println("📁 Created database: $name")
    }
    
    fun addDocument(db: String, doc: String) {
        databases[db]?.add(doc)
        println("📝 Added document to $db: $doc")
    }
    
    fun getDatabases() = databases.keys.toList()
}

// Simplified Network functionality on the Key
object NetworkKey : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<NetworkKey>
    
    fun listen(port: Int) {
        println("🌐 Network listening on port $port")
    }
    
    fun handleRequest(path: String): String {
        return when (path) {
            "/" -> """{"couchdb":"CCEK Demo","version":"1.0"}"""
            "/_all_dbs" -> CouchDBKey.getDatabases().toString()
            else -> """{"error":"not_found"}"""
        }
    }
}

// Simplified Monitoring on the Key
object MonitoringKey : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<MonitoringKey>
    
    private var metrics = mutableMapOf<String, Int>()
    
    fun recordMetric(name: String) {
        metrics[name] = (metrics[name] ?: 0) + 1
    }
    
    fun report() {
        println("📊 Metrics: $metrics")
    }
}

// Main service orchestrator
object FiduciaryServiceKey : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<FiduciaryServiceKey>
    
    suspend fun start() = coroutineScope {
        println("🚀 Starting Fiduciary Service (CCEK Pattern)")
        
        // Access other keys from context
        val context = currentCoroutineContext()
        val couchDB = context[CouchDBKey] ?: error("CouchDB not in context")
        val network = context[NetworkKey] ?: error("Network not in context")
        val monitoring = context[MonitoringKey] ?: error("Monitoring not in context")
        
        // Initialize
        network.listen(5984)
        couchDB.createDatabase("fiduciary")
        couchDB.createDatabase("patrick_devine_agent")
        
        // Simulate ingestion
        launch {
            repeat(5) { i ->
                delay(2000)
                couchDB.addDocument("fiduciary", "doc_$i")
                monitoring.recordMetric("documents_ingested")
            }
            monitoring.report()
        }
        
        // Simulate requests
        launch {
            delay(1000)
            println("\n📋 Testing requests:")
            listOf("/", "/_all_dbs", "/test").forEach { path ->
                val response = network.handleRequest(path)
                println("  GET $path -> $response")
                monitoring.recordMetric("requests_handled")
            }
        }
        
        delay(12000)
        println("\n✅ Demo complete!")
    }
}

// Run the demo
runBlocking {
    // Compose all Keys into context
    val context = CouchDBKey + NetworkKey + MonitoringKey + FiduciaryServiceKey
    
    withContext(context) {
        // Access the service from context and start it
        val service = currentCoroutineContext()[FiduciaryServiceKey]
            ?: error("FiduciaryService not in context")
        service.start()
    }
}