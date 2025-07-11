package fiduciary

import kotlinx.coroutines.*

/**
 * Simple runner for the CoreTypes Fiduciary Percolator
 * 
 * This demonstrates the percolator pattern in action with:
 * - Data ingestion simulation
 * - Multi-stage transformation pipeline
 * - Channel-based event distribution
 * - CouchDB-compatible storage
 */

fun main() = runBlocking {
    println("🎯 CoreTypes Fiduciary Percolator Demo")
    println("=" * 50)
    
    
    // Run the percolator
    try {
        CoreTypesFiduciaryPercolator.main()
    } catch (e: Exception) {
        println("❌ Error running percolator: ${e.message}")
        e.printStackTrace()
    }
}

operator fun String.times(count: Int): String = repeat(count)