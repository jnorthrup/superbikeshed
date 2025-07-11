package fiduciary

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import fiduciary.attention.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Divine Indexes Fetcher
 * 
 * Fetches Patrick Devine archive indexes into the fiduciary folder for processing.
 * Uses HTTP range requests to efficiently access ZIP central directories without
 * downloading entire archives.
 */

@Serializable
data class DivineIndexEntry(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val crc32: Long,
    val mimeType: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class DivineIndex(
    val archiveUrl: String,
    val archiveName: String,
    val totalSize: Long,
    val entries: List<DivineIndexEntry>,
    val fetchedAt: Long,
    val version: String = "1.0"
)

class DivineIndexFetcher(
    private val httpClient: HttpClient,
    private val outputDir: String = "fiduciary/divine-indexes"
) {
    
    companion object {
        val DIVINE_ARCHIVES = listOf(
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip",
            "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip"
        )
        
        const val ZIP_CENTRAL_DIR_SIGNATURE = 0x06054b50L
        const val ZIP_END_OF_CENTRAL_DIR_SIZE = 22
    }
    
    /**
     * Fetch all divine indexes
     */
    suspend fun fetchAllIndexes(): List<DivineIndex> {
        val indexes = mutableListOf<DivineIndex>()
        
        for (archiveUrl in DIVINE_ARCHIVES) {
            println("Fetching index for: $archiveUrl")
            val index = fetchArchiveIndex(archiveUrl)
            indexes.add(index)
            
            // Save to file
            saveIndex(index)
        }
        
        return indexes
    }
    
    /**
     * Fetch index for a single archive
     */
    suspend fun fetchArchiveIndex(archiveUrl: String): DivineIndex {
        val archiveName = extractArchiveName(archiveUrl)
        val totalSize = getFileSize(archiveUrl)
        
        println("Archive: $archiveName")
        println("Total size: $totalSize bytes")
        
        // Find central directory
        val centralDirOffset = findCentralDirectoryOffset(archiveUrl, totalSize)
        println("Central directory offset: $centralDirOffset")
        
        // Read central directory
        val entries = readCentralDirectory(archiveUrl, centralDirOffset)
        println("Found ${entries.size} entries")
        
        return DivineIndex(
            archiveUrl = archiveUrl,
            archiveName = archiveName,
            totalSize = totalSize,
            entries = entries,
            fetchedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Find the offset of the ZIP central directory
     */
    private suspend fun findCentralDirectoryOffset(archiveUrl: String, totalSize: Long): Long {
        // Start from end of file and search backwards for end-of-central-directory signature
        val searchSize = minOf(64 * 1024L, totalSize) // Search last 64KB
        val startOffset = totalSize - searchSize
        
        val searchData = httpClient.executeRange(
            archiveUrl,
            startOffset,
            totalSize - 1
        ).body
        
        // Search backwards for ZIP signature
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
     * Read the central directory entries
     */
    private suspend fun readCentralDirectory(archiveUrl: String, centralDirOffset: Long): List<DivineIndexEntry> {
        val entries = mutableListOf<DivineIndexEntry>()
        var currentOffset = centralDirOffset
        
        while (true) {
            // Read central directory file header (46 bytes minimum)
            val headerData = httpClient.executeRange(
                archiveUrl,
                currentOffset,
                currentOffset + 46 - 1
            ).body
            
            // Check signature
            val signature = parseLittleEndianInt(headerData, 0)
            if (signature != ZIP_CENTRAL_DIR_SIGNATURE) {
                break // End of central directory
            }
            
            // Parse header
            val method = parseLittleEndianShort(headerData, 10)
            val crc = parseLittleEndianInt(headerData, 16).toLong() and 0xFFFFFFFFL
            val compressedSize = parseLittleEndianInt(headerData, 20).toLong() and 0xFFFFFFFFL
            val uncompressedSize = parseLittleEndianInt(headerData, 24).toLong() and 0xFFFFFFFFL
            val nameLength = parseLittleEndianShort(headerData, 28)
            val extraFieldLength = parseLittleEndianShort(headerData, 30)
            val commentLength = parseLittleEndianShort(headerData, 32)
            val localHeaderOffset = parseLittleEndianInt(headerData, 42).toLong() and 0xFFFFFFFFL
            
            // Read filename
            val filenameData = httpClient.executeRange(
                archiveUrl,
                currentOffset + 46,
                currentOffset + 46 + nameLength - 1
            ).body
            
            val name = String(filenameData)
            
            // Create entry
            val entry = DivineIndexEntry(
                name = name,
                offset = localHeaderOffset,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                method = method,
                crc32 = crc,
                mimeType = getMimeType(name)
            )
            
            entries.add(entry)
            
            // Move to next entry
            currentOffset += 46 + nameLength + extraFieldLength + commentLength
        }
        
        return entries
    }
    
    /**
     * Get file size using HTTP HEAD request
     */
    private suspend fun getFileSize(url: String): Long {
        val request = HttpRequest(
            method = HttpMethod.HEAD,
            path = HttpRequestPath(url),
            headers = 2 j { i ->
                when (i) {
                    0 -> HttpHeaderName("Host") j HttpHeaderValue("archive.org")
                    1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("DivineIndexFetcher/1.0")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        val response = httpClient.execute(request)
        
        // Find Content-Length header
        for (i in 0 until response.headers.a) {
            val header = response.headers.b(i)
            if (header.a.value.equals("Content-Length", ignoreCase = true)) {
                return header.b.value.toLong()
            }
        }
        
        throw Exception("Could not determine file size")
    }
    
    /**
     * Extract archive name from URL
     */
    private fun extractArchiveName(url: String): String {
        val fileName = url.substringAfterLast("/")
        return fileName.substringBefore("?")
    }
    
    /**
     * Get MIME type from filename
     */
    private fun getMimeType(filename: String): String {
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
     * Save index to file
     */
    private fun saveIndex(index: DivineIndex) {
        val outputDir = File(this.outputDir)
        outputDir.mkdirs()
        
        val filename = "${index.archiveName.replace(".zip", "")}_index.json"
        val file = File(outputDir, filename)
        
        val json = Json { 
            prettyPrint = true 
            encodeDefaults = true
        }
        
        val jsonString = json.encodeToString(DivineIndex.serializer(), index)
        file.writeText(jsonString)
        
        println("Saved index to: ${file.absolutePath}")
    }
    
    /**
     * Parse little-endian short
     */
    private fun parseLittleEndianShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
    
    /**
     * Parse little-endian int
     */
    private fun parseLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8) or
               ((data[offset + 2].toInt() and 0xFF) shl 16) or
               ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}

/**
 * Main execution
 */
suspend fun main() {
    val ioContext = IOContext.NioContext("divine-index-fetcher")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val fetcher = DivineIndexFetcher(httpClient)
    
    println("=== Divine Indexes Fetcher ===")
    println("Fetching Patrick Devine archive indexes...")
    
    val indexes = fetcher.fetchAllIndexes()
    
    println("
=== Summary ===")
    indexes.forEach { index ->
        println("${index.archiveName}: ${index.entries.size} entries")
    }
    
    println("
Indexes saved to: fiduciary/divine-indexes/")
    
    // Return results for external processing
    mapOf(
        "indexes" to indexes,
        "timestamp" to System.currentTimeMillis(),
        "outputDir" to "fiduciary/divine-indexes"
    )
}