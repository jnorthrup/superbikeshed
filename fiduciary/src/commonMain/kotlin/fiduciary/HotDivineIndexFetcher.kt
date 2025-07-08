package fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Hot Divine Index Fetcher
 * 
 * Serves content "hot" (immediately available) and disposes of it in a boneyard.
 * Uses streaming and on-demand processing with automatic cleanup.
 */

data class HotDivineEntry(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val crc32: Long,
    val mimeType: String? = null,
    val content: ByteArray? = null, // Hot content loaded on demand
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HotDivineEntry
        return name == other.name && offset == other.offset
    }
    
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + offset.hashCode()
        return result
    }
}

data class HotDivineIndex(
    val archiveUrl: String,
    val archiveName: String,
    val totalSize: Long,
    val entries: List<HotDivineEntry>,
    val fetchedAt: Long,
    val version: String = "1.0"
)

/**
 * Boneyard for disposing of processed content
 */
class ContentBoneyard(
    internal val boneyardDir: String = "fiduciary/boneyard",
    internal val maxAgeHours: Long = 24
) {
    internal val processedEntries = ConcurrentHashMap<String, Long>()
    internal val cleanupJob = AtomicLong(0)
    
    init {
        val dir = File(boneyardDir)
        dir.mkdirs()
    }
    
    /**
     * Dispose of content in the boneyard
     */
    suspend fun dispose(entry: HotDivineEntry, content: ByteArray) {
        val entryId = "${entry.name}_${entry.offset}"
        val timestamp = System.currentTimeMillis()
        
        // Store in boneyard with timestamp
        val boneyardFile = File(boneyardDir, "${entryId}_${timestamp}.tmp")
        boneyardFile.writeBytes(content)
        
        processedEntries[entryId] = timestamp
        
        // Schedule cleanup
        scheduleCleanup()
    }
    
    /**
     * Get content from boneyard if still fresh
     */
    suspend fun retrieve(entry: HotDivineEntry): ByteArray? {
        val entryId = "${entry.name}_${entry.offset}"
        val timestamp = processedEntries[entryId] ?: return null
        
        val age = System.currentTimeMillis() - timestamp
        if (age > maxAgeHours * 60 * 60 * 1000) {
            // Too old, remove from boneyard
            processedEntries.remove(entryId)
            return null
        }
        
        val boneyardFile = File(boneyardDir, "${entryId}_${timestamp}.tmp")
        return if (boneyardFile.exists()) {
            boneyardFile.readBytes()
        } else {
            processedEntries.remove(entryId)
            null
        }
    }
    
    /**
     * Clean up old content
     */
    internal fun scheduleCleanup() {
        val currentJob = cleanupJob.get()
        if (currentJob == 0L) {
            val newJob = System.currentTimeMillis()
            if (cleanupJob.compareAndSet(currentJob, newJob)) {
                CoroutineScope(Dispatchers.IO).launch {
                    cleanup()
                    cleanupJob.set(0L)
                }
            }
        }
    }
    
    internal suspend fun cleanup() {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
        
        processedEntries.entries.removeIf { (entryId, timestamp) ->
            if (timestamp < cutoffTime) {
                val boneyardFile = File(boneyardDir, "${entryId}_${timestamp}.tmp")
                boneyardFile.delete()
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Get boneyard statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalEntries" to processedEntries.size,
            "boneyardDir" to boneyardDir,
            "maxAgeHours" to maxAgeHours
        )
    }
}

/**
 * Hot fetcher that serves content immediately and disposes in boneyard
 */
class HotDivineIndexFetcher(
    internal val boneyard: ContentBoneyard = ContentBoneyard(),
    internal val outputDir: String = "fiduciary/divine-indexes"
) {
    
    companion object {
        val DIVINE_ARCHIVES = listOf(
            "https://archive.org/download/patrickdevine/patrickdevine.zip",
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
        )
        
        const val ZIP_CENTRAL_DIR_SIGNATURE = 0x06054b50L
        const val HOT_CHUNK_SIZE = 1024 * 1024 // 1MB chunks for hot serving
    }
    
    /**
     * Stream hot content from archive entries
     */
    fun streamHotContent(archiveUrl: String): Flow<HotDivineEntry> = flow {
        val archiveName = extractArchiveName(archiveUrl)
        val totalSize = getFileSize(archiveUrl)
        
        println("Streaming hot content from: $archiveName")
        
        // Find central directory
        val centralDirOffset = findCentralDirectoryOffset(archiveUrl, totalSize)
        
        // Stream entries with hot content
        readCentralDirectoryStream(archiveUrl, centralDirOffset).collect { entry ->
            // Serve content hot (load on demand)
            val hotEntry = entry.copy(
                content = loadHotContent(archiveUrl, entry)
            )
            
            emit(hotEntry)
            
            // Dispose in boneyard after serving
            hotEntry.content?.let { content ->
                boneyard.dispose(entry, content)
            }
        }
    }
    
    /**
     * Load content hot (immediately available)
     */
    internal suspend fun loadHotContent(archiveUrl: String, entry: HotDivineEntry): ByteArray? {
        // Check boneyard first
        boneyard.retrieve(entry)?.let { return it }
        
        // Load fresh content
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(archiveUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=${entry.offset}-${entry.offset + entry.compressedSize - 1}")
                connection.setRequestProperty("User-Agent", "HotDivineFetcher/1.0")
                
                val responseCode = connection.responseCode
                if (responseCode == 206 || responseCode == 200) {
                    connection.inputStream.use { input ->
                        input.readAllBytes()
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                println("Failed to load hot content for ${entry.name}: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Fetch all indexes with hot content streaming
     */
    suspend fun fetchAllIndexesHot(): List<HotDivineIndex> {
        val indexes = mutableListOf<HotDivineIndex>()
        
        for (archiveUrl in DIVINE_ARCHIVES) {
            println("Fetching hot index for: $archiveUrl")
            
            val archiveName = extractArchiveName(archiveUrl)
            val totalSize = getFileSize(archiveUrl)
            
            // Stream entries with hot content
            val entries = mutableListOf<HotDivineEntry>()
            streamHotContent(archiveUrl).collect { entry ->
                entries.add(entry)
                
                // Process in chunks for hot serving
                if (entries.size % 10 == 0) {
                    println("Processed ${entries.size} hot entries from $archiveName")
                }
            }
            
            val index = HotDivineIndex(
                archiveUrl = archiveUrl,
                archiveName = archiveName,
                totalSize = totalSize,
                entries = entries,
                fetchedAt = System.currentTimeMillis()
            )
            
            indexes.add(index)
            saveHotIndex(index)
        }
        
        return indexes
    }
    
    /**
     * Stream central directory entries
     */
    internal fun readCentralDirectoryStream(archiveUrl: String, centralDirOffset: Long): Flow<HotDivineEntry> = flow {
        var currentOffset = centralDirOffset
        
        while (true) {
            val headerData = executeRangeRequest(archiveUrl, currentOffset, currentOffset + 46 - 1)
            
            val signature = parseLittleEndianInt(headerData, 0)
            if (signature != ZIP_CENTRAL_DIR_SIGNATURE.toInt()) {
                break
            }
            
            val method = parseLittleEndianShort(headerData, 10)
            val crc = parseLittleEndianInt(headerData, 16).toLong() and 0xFFFFFFFFL
            val compressedSize = parseLittleEndianInt(headerData, 20).toLong() and 0xFFFFFFFFL
            val uncompressedSize = parseLittleEndianInt(headerData, 24).toLong() and 0xFFFFFFFFL
            val nameLength = parseLittleEndianShort(headerData, 28)
            val extraFieldLength = parseLittleEndianShort(headerData, 30)
            val commentLength = parseLittleEndianShort(headerData, 32)
            val localHeaderOffset = parseLittleEndianInt(headerData, 42).toLong() and 0xFFFFFFFFL
            
            val filenameData = executeRangeRequest(archiveUrl, currentOffset + 46, currentOffset + 46 + nameLength - 1)
            val name = String(filenameData)
            
            val entry = HotDivineEntry(
                name = name,
                offset = localHeaderOffset,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                method = method,
                crc32 = crc,
                mimeType = getMimeType(name)
            )
            
            emit(entry)
            
            currentOffset += 46 + nameLength + extraFieldLength + commentLength
        }
    }
    
    /**
     * Find central directory offset
     */
    internal suspend fun findCentralDirectoryOffset(archiveUrl: String, totalSize: Long): Long {
        val searchSize = minOf(64 * 1024L, totalSize)
        val startOffset = totalSize - searchSize
        
        val searchData = executeRangeRequest(archiveUrl, startOffset, totalSize - 1)
        
        for (i in searchData.size - 4 downTo 0) {
            val signature = (searchData[i].toLong() and 0xFF) or
                           ((searchData[i + 1].toLong() and 0xFF) shl 8) or
                           ((searchData[i + 2].toLong() and 0xFF) shl 16) or
                           ((searchData[i + 3].toLong() and 0xFF) shl 24)
            
            if (signature == ZIP_CENTRAL_DIR_SIGNATURE) {
                return startOffset + i
            }
        }
        
        throw Exception("Could not find ZIP central directory signature")
    }
    
    /**
     * Execute range request
     */
    internal suspend fun executeRangeRequest(url: String, start: Long, end: Long): ByteArray {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=$start-$end")
            connection.setRequestProperty("User-Agent", "HotDivineFetcher/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode != 206 && responseCode != 200) {
                throw Exception("HTTP request failed with code: $responseCode")
            }
            
            connection.inputStream.use { input ->
                input.readAllBytes()
            }
        }
    }
    
    /**
     * Get file size
     */
    internal suspend fun getFileSize(url: String): Long {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "HotDivineFetcher/1.0")
            
            val contentLength = connection.getHeaderField("Content-Length")
            contentLength?.toLong() ?: throw Exception("Could not determine file size")
        }
    }
    
    /**
     * Extract archive name
     */
    internal fun extractArchiveName(url: String): String {
        val fileName = url.substringAfterLast("/")
        return fileName.substringBefore("?")
    }
    
    /**
     * Get MIME type
     */
    internal fun getMimeType(filename: String): String {
        val lowercaseName = filename.lowercase()
        return when {
            lowercaseName.endsWith(".pdf") -> "application/pdf"
            lowercaseName.endsWith(".doc") -> "application/msword"
            lowercaseName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lowercaseName.endsWith(".txt") -> "text/plain"
            lowercaseName.endsWith(".html") -> "text/html"
            lowercaseName.endsWith(".htm") -> "text/html"
            lowercaseName.endsWith(".mp3") -> "audio/mpeg"
            lowercaseName.endsWith(".wav") -> "audio/wav"
            lowercaseName.endsWith(".jpg") -> "image/jpeg"
            lowercaseName.endsWith(".jpeg") -> "image/jpeg"
            lowercaseName.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Save hot index
     */
    internal fun saveHotIndex(index: HotDivineIndex) {
        val outputDir = File(this.outputDir)
        outputDir.mkdirs()
        
        val filename = "${index.archiveName.replace(".zip", "")}_hot_index.json"
        val file = File(outputDir, filename)
        
        val jsonString = buildString {
            appendLine("{")
            appendLine("  \"archiveUrl\": \"${index.archiveUrl}\",")
            appendLine("  \"archiveName\": \"${index.archiveName}\",")
            appendLine("  \"totalSize\": ${index.totalSize},")
            appendLine("  \"fetchedAt\": ${index.fetchedAt},")
            appendLine("  \"version\": \"${index.version}\",")
            appendLine("  \"hotContent\": true,")
            appendLine("  \"boneyardStats\": {")
            boneyard.getStats().forEach { (key, value) ->
                appendLine("    \"$key\": \"$value\",")
            }
            appendLine("  },")
            appendLine("  \"entries\": [")
            
            index.entries.forEachIndexed { i, entry ->
                append("    {")
                append("\"name\": \"${entry.name.replace("\"", "\\\"")}\",")
                append("\"offset\": ${entry.offset},")
                append("\"compressedSize\": ${entry.compressedSize},")
                append("\"uncompressedSize\": ${entry.uncompressedSize},")
                append("\"method\": ${entry.method},")
                append("\"crc32\": ${entry.crc32}")
                if (entry.mimeType != null) {
                    append(",\"mimeType\": \"${entry.mimeType}\"")
                }
                append(",\"hotContent\": ${entry.content != null}")
                append("}")
                if (i < index.entries.size - 1) append(",")
                appendLine()
            }
            
            appendLine("  ]")
            appendLine("}")
        }
        
        file.writeText(jsonString)
        println("Saved hot index to: ${file.absolutePath}")
    }
    
    /**
     * Parse little-endian short
     */
    internal fun parseLittleEndianShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
    
    /**
     * Parse little-endian int
     */
    internal fun parseLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8) or
               ((data[offset + 2].toInt() and 0xFF) shl 16) or
               ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
} 