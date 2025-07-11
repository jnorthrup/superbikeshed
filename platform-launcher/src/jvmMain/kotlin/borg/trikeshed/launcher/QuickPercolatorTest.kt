@file:JvmName("QuickPercolatorTest")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import fiduciary.clean.FiduciaryPercolator
import fiduciary.clean.FiduciaryData
import fiduciary.fetch.ZipRangeFetcher
import borg.trikeshed.net.http.HttpClient

/**
 * Quick test to verify percolator is working with real archives
 */
fun main() = runBlocking {
    println("🧪 Quick Percolator Test")
    println("=" * 50)
    
    // Start percolator
    val percolator = FiduciaryPercolator
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + percolator)
    percolator.startPercolation(scope)
    
    // Monitor flow
    launch {
        percolator.getPercolationFlow().collect { data ->
            println("🍿 [${data.stage}] ${data.id}")
        }
    }
    
    // Test with real archive
    println("\n📥 Fetching real Patrick Devine archives...")
    val httpClient = HttpClient()
    val fetcher = ZipRangeFetcher(httpClient)
    
    try {
        val centralDirs = fetcher.fetchZipCentralDirs()
        println("✅ Fetched ${centralDirs.size} archives")
        
        centralDirs.forEach { dir ->
            println("\n📦 ${dir.archiveName}:")
            println("   Size: ${dir.totalSize / 1024 / 1024}MB")
            println("   Fetched: ${dir.totalBytes / 1024}KB")
            println("   Efficiency: ${(dir.totalBytes * 100) / dir.totalSize}%")
            println("   Entries: ${dir.entries.size}")
            
            val mp3s = dir.entries.filter { it.name.endsWith(".mp3") }
            println("   MP3 files: ${mp3s.size}")
        }
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
    }
    
    delay(2000)
    println("\n✅ Test complete!")
}

private operator fun String.times(count: Int) = repeat(count)