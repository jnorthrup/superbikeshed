#!/usr/bin/env kotlin

/**
 * JDBC2JSON Attention Bridge Example
 * 
 * Demonstrates how to integrate jdbc2json functionality with the fiduciary attention system.
 * Based on: https://github.com/jnorthrup/jdbc2json
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

import fiduciary.attention.*
import fiduciary.metaverse.CouchDBService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

// === Example Configuration ===

/**
 * Example JDBC configuration for a fiduciary database
 */
val fiduciaryDbConfig = JDBCConnectionConfig(
    url = "jdbc:postgresql://localhost:5432/fiduciary_production",
    username = "fiduciary_app",
    password = System.getenv("FIDUCIARY_DB_PASSWORD") ?: "default_password",
    schemaPattern = "fiduciary",
    catalog = "fiduciary_production",
    tableNamePattern = "%",
    types = listOf("TABLE", "VIEW"),
    fetchSize = 1000,
    bulkSize = 500
)

/**
 * Example JDBC configuration for a compliance database
 */
val complianceDbConfig = JDBCConnectionConfig(
    url = "jdbc:mysql://localhost:3306/compliance_system",
    username = "compliance_user",
    password = System.getenv("COMPLIANCE_DB_PASSWORD") ?: "default_password",
    schemaPattern = "compliance",
    tableNamePattern = "%",
    types = listOf("TABLE"),
    fetchSize = 500,
    bulkSize = 250
)

// === Example Usage Functions ===

/**
 * Example 1: Basic database synchronization with attention filtering
 */
suspend fun exampleBasicSync() {
    println("=== Example 1: Basic Database Sync ===")
    
    val couchDbService = createMockCouchDBService()
    val memvidBridge = createMockMemvidBridge()
    
    val bridge = createJDBC2JSONAttentionBridge(
        couchDbService = couchDbService,
        memvidBridge = memvidBridge,
        attentionThreshold = 0.5
    )
    
    // Process database with attention filtering
    bridge.processDatabaseWithAttention(fiduciaryDbConfig, "fiduciary_").collect { event ->
        when (event) {
            is JDBCAttentionEvent.TableDiscovery -> {
                println("📋 Discovered: ${event.tableName} (${event.relevance}, attention: ${event.attentionScore})")
            }
            is JDBCAttentionEvent.RowProcessing -> {
                if (event.attentionScore > 0.8) {
                    println("  ⭐ High attention row: ${event.tableName}.${event.rowId}")
                }
            }
            is JDBCAttentionEvent.BulkInsert -> {
                println("  📦 Bulk insert: ${event.totalRows} rows to ${event.tableName} (${event.processingTimeMs}ms)")
            }
        }
    }
    
    println("✅ Basic sync completed")
}

/**
 * Example 2: Incremental synchronization for compliance monitoring
 */
suspend fun exampleIncrementalSync() {
    println("\n=== Example 2: Incremental Compliance Sync ===")
    
    val couchDbService = createMockCouchDBService()
    val memvidBridge = createMockMemvidBridge()
    
    val bridge = createJDBC2JSONAttentionBridge(
        couchDbService = couchDbService,
        memvidBridge = memvidBridge,
        attentionThreshold = 0.7 // Higher threshold for compliance
    )
    
    // Perform incremental sync
    bridge.syncChangesWithAttention(
        config = complianceDbConfig,
        couchDbPrefix = "compliance_",
        syncMode = SyncMode.INCREMENTAL
    ).collect { event ->
        when (event) {
            is JDBCAttentionEvent.SyncOperation -> {
                val emoji = when (event.operation) {
                    "UPDATE" -> "🔄"
                    "ADD" -> "➕"
                    "DELETE" -> "➖"
                    else -> "📝"
                }
                println("$emoji ${event.operation}: ${event.affectedRows} rows in ${event.tableName}")
            }
        }
    }
    
    println("✅ Incremental sync completed")
}

/**
 * Example 3: Attention-only processing for high-priority data
 */
suspend fun exampleAttentionOnlySync() {
    println("\n=== Example 3: Attention-Only Processing ===")
    
    val couchDbService = createMockCouchDBService()
    val memvidBridge = createMockMemvidBridge()
    
    val bridge = createJDBC2JSONAttentionBridge(
        couchDbService = couchDbService,
        memvidBridge = memvidBridge,
        attentionThreshold = 0.9 // Very high threshold
    )
    
    // Only process high-attention data
    bridge.syncChangesWithAttention(
        config = fiduciaryDbConfig,
        couchDbPrefix = "critical_",
        syncMode = SyncMode.ATTENTION_ONLY
    ).collect { event ->
        when (event) {
            is JDBCAttentionEvent.SyncOperation -> {
                println("🔥 CRITICAL: ${event.operation} in ${event.tableName} (attention: ${event.attentionScore})")
            }
        }
    }
    
    println("✅ Attention-only sync completed")
}

/**
 * Example 4: Real-time monitoring with attention events
 */
suspend fun exampleRealTimeMonitoring() {
    println("\n=== Example 4: Real-Time Monitoring ===")
    
    val couchDbService = createMockCouchDBService()
    val memvidBridge = createMockMemvidBridge()
    
    val bridge = createJDBC2JSONAttentionBridge(
        couchDbService = couchDbService,
        memvidBridge = memvidBridge,
        attentionThreshold = 0.6
    )
    
    // Simulate real-time monitoring
    repeat(5) { cycle ->
        println("\n🔄 Monitoring cycle ${cycle + 1}:")
        
        bridge.syncChangesWithAttention(
            config = fiduciaryDbConfig,
            couchDbPrefix = "realtime_",
            syncMode = SyncMode.INCREMENTAL
        ).collect { event ->
            when (event) {
                is JDBCAttentionEvent.SyncOperation -> {
                    val priority = when {
                        event.attentionScore > 0.9 -> "🔴 HIGH"
                        event.attentionScore > 0.7 -> "🟡 MEDIUM"
                        else -> "🟢 LOW"
                    }
                    println("  $priority ${event.operation}: ${event.affectedRows} rows in ${event.tableName}")
                }
            }
        }
        
        delay(1000) // Simulate 1-second monitoring interval
    }
    
    println("✅ Real-time monitoring completed")
}

/**
 * Example 5: Performance optimization with batch processing
 */
suspend fun exampleBatchOptimization() {
    println("\n=== Example 5: Batch Processing Optimization ===")
    
    val couchDbService = createMockCouchDBService()
    val memvidBridge = createMockMemvidBridge()
    
    // Test different batch sizes
    val batchSizes = listOf(100, 500, 1000, 2000)
    
    batchSizes.forEach { batchSize ->
        val config = fiduciaryDbConfig.copy(bulkSize = batchSize)
        
        val bridge = createJDBC2JSONAttentionBridge(
            couchDbService = couchDbService,
            memvidBridge = memvidBridge,
            attentionThreshold = 0.5
        )
        
        val startTime = Clock.System.now().toEpochMilliseconds()
        var totalRows = 0L
        var totalTime = 0L
        
        bridge.processDatabaseWithAttention(config, "batch_test_").collect { event ->
            when (event) {
                is JDBCAttentionEvent.BulkInsert -> {
                    totalRows += event.totalRows
                    totalTime += event.processingTimeMs
                }
            }
        }
        
        val endTime = Clock.System.now().toEpochMilliseconds()
        val overallTime = endTime - startTime
        val throughput = if (overallTime > 0) totalRows * 1000 / overallTime else 0
        
        println("📊 Batch size $batchSize: $totalRows rows in ${overallTime}ms (${throughput} rows/sec)")
    }
    
    println("✅ Batch optimization test completed")
}

// === Mock Service Implementations ===

fun createMockCouchDBService(): CouchDBService {
    return object : CouchDBService {
        override suspend fun saveDocument(database: String, document: Map<String, Any>): Boolean {
            // Simulate CouchDB save
            return true
        }
        
        override suspend fun getDocument(database: String, documentId: String): Map<String, Any>? {
            // Simulate CouchDB retrieval
            return null
        }
        
        override suspend fun deleteDocument(database: String, documentId: String): Boolean {
            // Simulate CouchDB deletion
            return true
        }
    }
}

fun createMockMemvidBridge(): FiduciaryMemvidBridge {
    return object : FiduciaryMemvidBridge {
        override suspend fun registerEvent(event: AttentionEvent): Boolean {
            // Simulate attention event registration
            return true
        }
        
        override suspend fun registerJDBCEvent(event: JDBCAttentionEvent): Boolean {
            // Simulate JDBC event registration
            return true
        }
    }
}

// === Main Execution ===

suspend fun main() {
    println("🚀 JDBC2JSON Attention Bridge Examples")
    println("Based on: https://github.com/jnorthrup/jdbc2json")
    println("=" * 60)
    
    try {
        exampleBasicSync()
        exampleIncrementalSync()
        exampleAttentionOnlySync()
        exampleRealTimeMonitoring()
        exampleBatchOptimization()
        
        println("\n🎉 All examples completed successfully!")
        
    } catch (e: Exception) {
        println("❌ Error running examples: ${e.message}")
        e.printStackTrace()
    }
}

// Run the examples
runBlocking {
    main()
} 