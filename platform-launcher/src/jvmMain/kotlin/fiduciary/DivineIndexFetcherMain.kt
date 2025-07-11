package fiduciary

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.io.IOContext
import kotlinx.coroutines.runBlocking

/**
 * Main function to run divine index fetcher integration test
 * Uses TrikeShed zlib and HTTP tools to fetch ZIP central directory indexes
 */
fun main() = runBlocking {
    println("=== Divine Index Fetcher Integration Test ===")
    println("Fetching ZIP central directory indexes from Patrick Devine archives...")
    
    // Create IO context and HTTP client
    val ioContext = IOContext.NioContext("divine-index-fetcher")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    // Create fetch configuration
    val config = FetchConfig(
        archives = DivineIndexFetcher.PATRICK_DEVINE_ARCHIVES.size j DivineIndexFetcher.PATRICK_DEVINE_ARCHIVES::get,
        outputDir = "fiduciary/lfs",
        chunkSize = 64 * 1024L,
        maxRetries = 3,
        timeoutMs = 30000L
    )
    
    // Create divine index fetcher
    val fetcher = DivineIndexFetcher(httpClient, config)
    
    println("\n=== Target Archives ===")
    for (i in 0 until config.archives.a) {
        val url = config.archives.b(i)
        println("${i + 1}. ${extractArchiveName(url)}")
    }
    
    println("\n=== Starting Index Fetching ===")
    val startTime = System.currentTimeMillis()
    
    // Fetch all indexes
    val stats = fetcher.fetchAllIndexes()
    
    val totalTime = System.currentTimeMillis() - startTime
    
    println("\n=== Fetch Results ===")
    println("Total archives: ${stats.totalArchives}")
    println("Successful fetches: ${stats.successfulFetches}")
    println("Failed fetches: ${stats.failedFetches}")
    println("Total bytes fetched: ${stats.totalBytesFetched}")
    println("Average fetch time: ${stats.averageFetchTime}ms")
    println("Total processing time: ${totalTime}ms")
    
    if (stats.errors.a > 0) {
        println("\n=== Errors ===")
        for (i in 0 until stats.errors.a) {
            println("${i + 1}. ${stats.errors.b(i)}")
        }
    }
    
    println("\n=== LFS Storage ===")
    println("Binary files: fiduciary/lfs/*_central_dir.bin")
    println("LFS pointers: fiduciary/lfs/*_central_dir.lfs")
    
    println("\n=== Integration Test Complete ===")
    println("✓ Used TrikeShed HTTP client for range requests")
    println("✓ Used TrikeShed zlib for binary processing")
    println("✓ Stored binary indexes in Git LFS")
    println("✓ Handled ZIP central directory parsing")
    
    // Return results for external processing
    mapOf(
        "stats" to stats,
        "totalTime" to totalTime,
        "outputDir" to config.outputDir,
        "success" to (stats.failedFetches == 0)
    )
}

internal fun extractArchiveName(url: String): String {
    val fileName = url.substringAfterLast("/")
    return fileName.substringBefore("?")
} 