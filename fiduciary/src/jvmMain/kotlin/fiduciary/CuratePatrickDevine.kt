package fiduciary

import fiduciary.curator.*
import fiduciary.fetch.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

suspend fun main() {
    val ioContext = IOContext.NioContext("curator")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val fetcher = ZipRangeFetcher(httpClient)
    val curator = ZipCurator(fetcher)
    
    println("🗂️ Curating Patrick Devine archives...")
    
    // Index all files
    val index = curator.indexAllFiles()
    
    // Save index to disk
    val indexFile = File("patrick-devine-index.json")
    indexFile.writeText(Json.encodeToString(index))
    println("📝 Saved index to ${indexFile.name}")
    
    // Show statistics
    println("\n📊 Archive Statistics:")
    println("Total files: ${index.totalFiles}")
    println("Compressed size: ${formatBytes(index.totalCompressedSize)}")
    println("Uncompressed size: ${formatBytes(index.totalUncompressedSize)}")
    println("Compression ratio: ${String.format("%.1f", (1.0 - index.totalCompressedSize.toDouble() / index.totalUncompressedSize) * 100)}%")
    
    // Group by extension
    val byExtension = curator.groupByExtension(index)
    println("\n📁 Files by type:")
    byExtension.entries.sortedByDescending { it.value.size }.take(10).forEach { (ext, files) ->
        println("  .$ext: ${files.size} files")
    }
    
    // Find specific patterns
    val patterns = listOf("0720", "transcript", "analysis", "devine")
    val matches = curator.filterFiles(index, patterns)
    println("\n🔍 Pattern matches (${patterns.joinToString(", ")}):")
    matches.take(20).forEach { entry ->
        println("  ${entry.entry.filename} (${formatBytes(entry.entry.uncompressedSize)})")
    }
    
    // Create extraction plan for matches
    val plan = curator.createExtractionPlan(matches)
    println("\n📋 Extraction plan:")
    println("  ${plan.size} batches for ${matches.size} files")
    plan.take(5).forEachIndexed { i, batch ->
        println("  Batch ${i+1}: ${batch.entries.size} files, ${formatBytes(batch.totalSize)}")
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}