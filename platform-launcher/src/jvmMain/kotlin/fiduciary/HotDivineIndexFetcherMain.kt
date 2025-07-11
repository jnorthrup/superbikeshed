package fiduciary

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect

/**
 * Main function to run the Hot Divine Index Fetcher
 * Serves content "hot" and disposes in boneyard
 */
fun main() = runBlocking {
    println("=== Hot Divine Indexes Fetcher ===")
    println("Serving content hot and disposing in boneyard...")
    
    val boneyard = ContentBoneyard()
    val fetcher = HotDivineIndexFetcher(boneyard)
    
    // Fetch all indexes with hot content
    val indexes = fetcher.fetchAllIndexesHot()
    
    println("\n=== Hot Summary ===")
    indexes.forEach { index ->
        println("${index.archiveName}: ${index.entries.size} hot entries")
        val hotEntries = index.entries.count { it.content != null }
        println("  - Hot content: $hotEntries entries")
    }
    
    // Show boneyard stats
    val boneyardStats = boneyard.getStats()
    println("\n=== Boneyard Stats ===")
    boneyardStats.forEach { (key, value) ->
        println("$key: $value")
    }
    
    println("\nHot indexes saved to: fiduciary/divine-indexes/")
    println("Boneyard location: fiduciary/boneyard/")
    
    // Stream some hot content as demonstration
    println("\n=== Streaming Hot Content Demo ===")
    val firstArchive = HotDivineIndexFetcher.DIVINE_ARCHIVES.first()
    fetcher.streamHotContent(firstArchive)
        .take(5) // Just show first 5 entries
        .collect { entry ->
            println("Hot entry: ${entry.name} (${entry.content?.size ?: 0} bytes)")
        }
} 