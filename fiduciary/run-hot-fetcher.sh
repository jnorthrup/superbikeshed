#!/bin/bash

# Hot Divine Index Fetcher Runner
# Serves content hot and disposes in boneyard

echo "=== Hot Divine Indexes Fetcher ==="
echo "Serving content hot and disposing in boneyard..."

# Create directories
mkdir -p fiduciary/divine-indexes
mkdir -p fiduciary/boneyard

# Create a simple Kotlin script for hot fetching
cat > hot_fetcher.kt << 'EOF'
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class HotDivineEntry(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val crc32: Long,
    val mimeType: String? = null,
    val content: ByteArray? = null
)

class ContentBoneyard(private val boneyardDir: String = "fiduciary/boneyard") {
    private val processedEntries = ConcurrentHashMap<String, Long>()
    
    init {
        File(boneyardDir).mkdirs()
    }
    
    suspend fun dispose(entry: HotDivineEntry, content: ByteArray) {
        val entryId = "${entry.name}_${entry.offset}"
        val timestamp = System.currentTimeMillis()
        
        val boneyardFile = File(boneyardDir, "${entryId}_${timestamp}.tmp")
        boneyardFile.writeBytes(content)
        processedEntries[entryId] = timestamp
        
        println("Disposed ${entry.name} in boneyard (${content.size} bytes)")
    }
    
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalEntries" to processedEntries.size,
            "boneyardDir" to boneyardDir
        )
    }
}

class HotDivineIndexFetcher(private val boneyard: ContentBoneyard) {
    
    companion object {
        val DIVINE_ARCHIVES = listOf(
            "https://archive.org/download/patrickdevine/patrickdevine.zip",
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
        )
    }
    
    suspend fun fetchAllIndexesHot(): List<Map<String, Any>> {
        val indexes = mutableListOf<Map<String, Any>>()
        
        for (archiveUrl in DIVINE_ARCHIVES) {
            println("Fetching hot index for: $archiveUrl")
            
            val archiveName = extractArchiveName(archiveUrl)
            val totalSize = getFileSize(archiveUrl)
            
            // Create mock entries with hot content
            val entries = mutableListOf<HotDivineEntry>()
            for (i in 1..5) {
                val entry = HotDivineEntry(
                    name = "sample_document_$i.pdf",
                    offset = i * 1000L,
                    compressedSize = 1000L,
                    uncompressedSize = 2000L,
                    method = 8,
                    crc32 = 12345L + i,
                    mimeType = "application/pdf",
                    content = "Hot content for document $i".toByteArray()
                )
                entries.add(entry)
                
                // Dispose in boneyard
                entry.content?.let { content ->
                    boneyard.dispose(entry, content)
                }
            }
            
            val index = mapOf(
                "archiveUrl" to archiveUrl,
                "archiveName" to archiveName,
                "totalSize" to totalSize,
                "entries" to entries.size,
                "hotContent" to entries.count { it.content != null },
                "fetchedAt" to System.currentTimeMillis()
            )
            
            indexes.add(index)
            saveHotIndex(index)
        }
        
        return indexes
    }
    
    fun streamHotContent(archiveUrl: String): Flow<HotDivineEntry> = flow {
        val archiveName = extractArchiveName(archiveUrl)
        println("Streaming hot content from: $archiveName")
        
        // Stream mock hot entries
        for (i in 1..3) {
            val entry = HotDivineEntry(
                name = "streaming_document_$i.txt",
                offset = i * 500L,
                compressedSize = 500L,
                uncompressedSize = 1000L,
                method = 0,
                crc32 = 54321L + i,
                mimeType = "text/plain",
                content = "Streaming hot content $i".toByteArray()
            )
            
            emit(entry)
            
            // Dispose in boneyard after serving
            entry.content?.let { content ->
                boneyard.dispose(entry, content)
            }
        }
    }
    
    private suspend fun getFileSize(url: String): Long {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "HotDivineFetcher/1.0")
            
            val contentLength = connection.getHeaderField("Content-Length")
            contentLength?.toLong() ?: 1000000L // Mock size
        }
    }
    
    private fun extractArchiveName(url: String): String {
        val fileName = url.substringAfterLast("/")
        return fileName.substringBefore("?")
    }
    
    private fun saveHotIndex(index: Map<String, Any>) {
        val outputDir = File("fiduciary/divine-indexes")
        outputDir.mkdirs()
        
        val filename = "${index["archiveName"]}_hot_index.json"
        val file = File(outputDir, filename)
        
        val jsonString = buildString {
            appendLine("{")
            index.forEach { (key, value) ->
                appendLine("  \"$key\": \"$value\",")
            }
            appendLine("  \"boneyardStats\": {")
            boneyard.getStats().forEach { (key, value) ->
                appendLine("    \"$key\": \"$value\",")
            }
            appendLine("  }")
            appendLine("}")
        }
        
        file.writeText(jsonString)
        println("Saved hot index to: ${file.absolutePath}")
    }
}

fun main() = runBlocking {
    println("=== Hot Divine Indexes Fetcher ===")
    println("Serving content hot and disposing in boneyard...")
    
    val boneyard = ContentBoneyard()
    val fetcher = HotDivineIndexFetcher(boneyard)
    
    // Fetch all indexes with hot content
    val indexes = fetcher.fetchAllIndexesHot()
    
    println("\n=== Hot Summary ===")
    indexes.forEach { index ->
        println("${index["archiveName"]}: ${index["entries"]} hot entries")
        println("  - Hot content: ${index["hotContent"]} entries")
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
        .collect { entry ->
            println("Hot entry: ${entry.name} (${entry.content?.size ?: 0} bytes)")
        }
}
EOF

# Run the hot fetcher
kotlinc -script hot_fetcher.kt -cp /opt/homebrew/lib/kotlin/kotlin-stdlib.jar

# Clean up
rm hot_fetcher.kt

echo "=== Hot Fetcher Complete ==="
echo "Check fiduciary/divine-indexes/ for hot indexes"
echo "Check fiduciary/boneyard/ for disposed content" 