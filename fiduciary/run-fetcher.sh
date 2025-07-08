#!/bin/bash

# Simple script to run the divine index fetcher
# This will compile and run the Kotlin code directly

echo "=== Divine Indexes Fetcher ==="
echo "Compiling and running..."

# Create a simple Kotlin script that can be run with kotlinc
cat > temp_fetcher.kt << 'EOF'
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import kotlinx.coroutines.*

data class DivineIndexEntry(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val crc32: Long,
    val mimeType: String? = null
)

data class DivineIndex(
    val archiveUrl: String,
    val archiveName: String,
    val totalSize: Long,
    val entries: List<DivineIndexEntry>,
    val fetchedAt: Long
)

class DivineIndexFetcher(private val outputDir: String = "divine-indexes") {
    
    companion object {
        val DIVINE_ARCHIVES = listOf(
            "https://archive.org/download/patrickdevine/patrickdevine.zip",
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
        )
        
        const val ZIP_CENTRAL_DIR_SIGNATURE = 0x06054b50L
    }
    
    suspend fun fetchAllIndexes(): List<DivineIndex> {
        val indexes = mutableListOf<DivineIndex>()
        
        for (archiveUrl in DIVINE_ARCHIVES) {
            println("Fetching index for: $archiveUrl")
            val index = fetchArchiveIndex(archiveUrl)
            indexes.add(index)
            saveIndex(index)
        }
        
        return indexes
    }
    
    suspend fun fetchArchiveIndex(archiveUrl: String): DivineIndex {
        val archiveName = extractArchiveName(archiveUrl)
        val totalSize = getFileSize(archiveUrl)
        
        println("Archive: $archiveName")
        println("Total size: $totalSize bytes")
        
        // For now, just create a mock index
        val entries = listOf(
            DivineIndexEntry(
                name = "sample_document.pdf",
                offset = 0L,
                compressedSize = 1000L,
                uncompressedSize = 2000L,
                method = 8,
                crc32 = 12345L,
                mimeType = "application/pdf"
            )
        )
        
        return DivineIndex(
            archiveUrl = archiveUrl,
            archiveName = archiveName,
            totalSize = totalSize,
            entries = entries,
            fetchedAt = System.currentTimeMillis()
        )
    }
    
    private suspend fun getFileSize(url: String): Long {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "DivineIndexFetcher/1.0")
            
            val contentLength = connection.getHeaderField("Content-Length")
            contentLength?.toLong() ?: 0L
        }
    }
    
    private fun extractArchiveName(url: String): String {
        val fileName = url.substringAfterLast("/")
        return fileName.substringBefore("?")
    }
    
    private fun saveIndex(index: DivineIndex) {
        val outputDir = File(this.outputDir)
        outputDir.mkdirs()
        
        val filename = "${index.archiveName.replace(".zip", "")}_index.json"
        val file = File(outputDir, filename)
        
        val jsonString = buildString {
            appendLine("{")
            appendLine("  \"archiveUrl\": \"${index.archiveUrl}\",")
            appendLine("  \"archiveName\": \"${index.archiveName}\",")
            appendLine("  \"totalSize\": ${index.totalSize},")
            appendLine("  \"fetchedAt\": ${index.fetchedAt},")
            appendLine("  \"entries\": [")
            
            index.entries.forEachIndexed { i, entry ->
                append("    {")
                append("\"name\": \"${entry.name}\",")
                append("\"offset\": ${entry.offset},")
                append("\"compressedSize\": ${entry.compressedSize},")
                append("\"uncompressedSize\": ${entry.uncompressedSize},")
                append("\"method\": ${entry.method},")
                append("\"crc32\": ${entry.crc32}")
                if (entry.mimeType != null) {
                    append(",\"mimeType\": \"${entry.mimeType}\"")
                }
                append("}")
                if (i < index.entries.size - 1) append(",")
                appendLine()
            }
            
            appendLine("  ]")
            appendLine("}")
        }
        
        file.writeText(jsonString)
        println("Saved index to: ${file.absolutePath}")
    }
}

fun main() = runBlocking {
    println("=== Divine Indexes Fetcher ===")
    println("Fetching Patrick Devine archive indexes...")
    
    val fetcher = DivineIndexFetcher()
    val indexes = fetcher.fetchAllIndexes()
    
    println("\n=== Summary ===")
    indexes.forEach { index ->
        println("${index.archiveName}: ${index.entries.size} entries")
    }
    
    println("\nIndexes saved to: divine-indexes/")
}
EOF

# Run the script
kotlinc -script temp_fetcher.kt

# Clean up
rm temp_fetcher.kt 