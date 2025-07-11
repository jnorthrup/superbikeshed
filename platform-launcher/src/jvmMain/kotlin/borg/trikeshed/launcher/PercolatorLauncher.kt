@file:JvmName("PercolatorLauncher")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import fiduciary.clean.FiduciaryPercolator
import fiduciary.clean.FiduciaryData
import kotlin.coroutines.CoroutineContext

/**
 * Simple launcher that runs just the Fiduciary Percolator
 * without all the platform complexity
 */
fun main(args: Array<String>) = runBlocking {
    println("""
    ╔═══════════════════════════════════════════════════════╗
    ║   🔥 FIDUCIARY PERCOLATOR - SIMPLE MODE              ║
    ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())
    
    val percolator = FiduciaryPercolator
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + percolator)
    
    // Start percolation
    percolator.startPercolation(scope)
    
    // Monitor flow
    launch {
        percolator.getPercolationFlow().collect { data ->
            println("🍿 ${data.id} [${data.stage}] from ${data.source}")
        }
    }
    
    // Ingest some test data
    delay(1000)
    println("\n📥 Ingesting test data...")
    
    val testData = listOf(
        FiduciaryData(
            id = "test_001",
            source = "platform_launcher",
            content = mapOf(
                "type" to "initialization",
                "status" to "active",
                "priority" to "high"
            )
        ),
        FiduciaryData(
            id = "test_002", 
            source = "couchdb_integration",
            content = mapOf(
                "database" to "fiduciary",
                "operation" to "create",
                "result" to "success"
            )
        )
    )
    
    testData.forEach { 
        percolator.ingest(it)
        delay(500)
    }
    
    // Let it process
    delay(5000)
    
    // Show results
    println("\n📊 Processing complete:")
    val storage = percolator.getStorage()
    storage.forEach { (id, data) ->
        println("  ✅ $id: ${data.stage}")
    }
    
    println("\n✨ Percolator demonstration complete!")
}