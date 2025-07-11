package fiduciary.fetch

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

/**
 * ZIP Range Fetcher - Rapid central directory extraction
 * Fetches only initial and final range requests from Patrick Divine archives
 */

class ZipRangeFetcher(private val httpClient: HttpClient) {
    
    companion object {
        // Patrick Divine ZIP archives
        val ARCHIVES = listOf(
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip",
            "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip"
        )
        
        const val ZIP_END_SIGNATURE = 0x06054b50L
        const val ZIP_CENTRAL_SIGNATURE = 0x02014b50L
        const val INITIAL_RANGE_SIZE = 1024L  // First 1KB
        const val FINAL_RANGE_SIZE = 256 * 1024L  // Last 256KB for large archives
    }
    
    /**
     * Fetch ZIP central directory with minimal bandwidth
     */
    suspend fun fetchZipCentralDirs(): List<ZipCentralDir> {
        println("🎯 Fetching ZIP central directories with range requests")
        
        val results = mutableListOf<ZipCentralDir>()
        
        ARCHIVES.forEachIndexed { index, url ->
            println("📦 Archive #${index + 1}: ${extractName(url)}")
            
            try {
                val centralDir = fetchSingleZipCentralDir(url)
                results.add(centralDir)
                println("   ✅ Central dir: ${centralDir.entries.size} entries, ${centralDir.totalBytes} bytes")
            } catch (e: Exception) {
                println("   ❌ Failed: ${e.message}")
            }
        }
        
        return results
    }
    
    /**
     * Fetch central directory for single ZIP
     */
    private suspend fun fetchSingleZipCentralDir(url: String): ZipCentralDir {
        val archiveName = extractName(url)
        
        // Step 1: Get file size with HEAD request
        val totalSize = getFileSize(url)
        println("   📏 Total size: $totalSize bytes")
        
        // Step 2: Initial range request (first 1KB) 
        val initialRange = fetchInitialRange(url)
        println("   📥 Initial range: ${initialRange.size} bytes")
        
        // Step 3: Final range request (last 64KB)
        val finalRange = fetchFinalRange(url, totalSize)
        println("   📥 Final range: ${finalRange.size} bytes")
        
        // Step 4: Find central directory in final range
        val centralDirOffset = findCentralDirOffset(finalRange, totalSize)
        println("   🎯 Central dir offset: $centralDirOffset")
        
        // Step 5: Parse central directory entries
        val entries = parseCentralDirEntries(finalRange, centralDirOffset, totalSize)
        
        return ZipCentralDir(
            archiveUrl = url,
            archiveName = archiveName,
            totalSize = totalSize,
            centralDirOffset = centralDirOffset,
            entries = entries,
            initialRangeData = initialRange,
            finalRangeData = finalRange,
            totalBytes = initialRange.size + finalRange.size
        )
    }
    
    /**
     * Get file size with HEAD request
     */
    private suspend fun getFileSize(url: String): Long {
        val request = HttpRequest(
            method = HttpMethod.HEAD,
            path = HttpRequestPath(url),
            headers = 2 j { i ->
                when (i) {
                    0 -> HttpHeaderName("User-Agent") j HttpHeaderValue("ZipRangeFetcher/1.0")
                    1 -> HttpHeaderName("Accept") j HttpHeaderValue("*/*")
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
     * Fetch initial range (first 1KB)
     */
    private suspend fun fetchInitialRange(url: String): ByteArray {
        return fetchRange(url, 0, INITIAL_RANGE_SIZE - 1)
    }
    
    /**
     * Fetch final range (last 256KB for large archives)
     */
    private suspend fun fetchFinalRange(url: String, totalSize: Long): ByteArray {
        val startOffset = maxOf(0, totalSize - FINAL_RANGE_SIZE)
        return fetchRange(url, startOffset, totalSize - 1)
    }
    
    /**
     * Execute range request
     */
    private suspend fun fetchRange(url: String, start: Long, end: Long): ByteArray {
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(url),
            headers = 3 j { i ->
                when (i) {
                    0 -> HttpHeaderName("Range") j HttpHeaderValue("bytes=$start-$end")
                    1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("ZipRangeFetcher/1.0")
                    2 -> HttpHeaderName("Accept") j HttpHeaderValue("*/*")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        val response = httpClient.execute(request)
        
        if (response.status.code != 206 && response.status.code != 200) {
            throw Exception("Range request failed: ${response.status.code}")
        }
        
        return response.body.toByteArray()
    }
    
    /**
     * Find central directory offset in final range
     */
    private fun findCentralDirOffset(finalRange: ByteArray, totalSize: Long): Long {
        val finalRangeStart = maxOf(0, totalSize - FINAL_RANGE_SIZE)
        
        // Search backwards for end-of-central-directory signature
        for (i in finalRange.size - 4 downTo 0) {
            val signature = readLittleEndianInt(finalRange, i).toLong() and 0xFFFFFFFFL
            
            if (signature == ZIP_END_SIGNATURE) {
                // Found end record, extract central dir offset
                val centralDirOffset = readLittleEndianInt(finalRange, i + 16).toLong() and 0xFFFFFFFFL
                return centralDirOffset
            }
        }
        
        throw Exception("Could not find ZIP end-of-central-directory signature")
    }
    
    /**
     * Parse central directory entries from final range
     */
    private fun parseCentralDirEntries(finalRange: ByteArray, centralDirOffset: Long, totalSize: Long): List<ZipEntry> {
        val entries = mutableListOf<ZipEntry>()
        val finalRangeStart = maxOf(0, totalSize - FINAL_RANGE_SIZE)
        
        // Calculate offset within final range
        val centralDirOffsetInRange = (centralDirOffset - finalRangeStart).toInt()
        
        if (centralDirOffsetInRange < 0 || centralDirOffsetInRange >= finalRange.size) {
            // Central directory not in final range, return empty for now
            return entries
        }
        
        var offset = centralDirOffsetInRange
        
        // Parse central directory file headers
        while (offset + 46 <= finalRange.size) {
            val signature = readLittleEndianInt(finalRange, offset).toLong() and 0xFFFFFFFFL
            
            if (signature != ZIP_CENTRAL_SIGNATURE) {
                break // End of central directory entries
            }
            
            // Parse central directory file header
            val method = readLittleEndianShort(finalRange, offset + 10)
            val crc32 = readLittleEndianInt(finalRange, offset + 16).toLong() and 0xFFFFFFFFL
            val compressedSize = readLittleEndianInt(finalRange, offset + 20).toLong() and 0xFFFFFFFFL
            val uncompressedSize = readLittleEndianInt(finalRange, offset + 24).toLong() and 0xFFFFFFFFL
            val nameLength = readLittleEndianShort(finalRange, offset + 28)
            val extraFieldLength = readLittleEndianShort(finalRange, offset + 30)
            val commentLength = readLittleEndianShort(finalRange, offset + 32)
            val localHeaderOffset = readLittleEndianInt(finalRange, offset + 42).toLong() and 0xFFFFFFFFL
            
            // Read filename if available
            val filenameOffset = offset + 46
            val filename = if (filenameOffset + nameLength <= finalRange.size) {
                String(finalRange, filenameOffset, nameLength)
            } else {
                "unknown_${entries.size}"
            }
            
            val entry = ZipEntry(
                filename = filename,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                crc32 = crc32,
                method = method,
                localHeaderOffset = localHeaderOffset
            )
            
            entries.add(entry)
            
            // Move to next entry
            offset += 46 + nameLength + extraFieldLength + commentLength
        }
        
        return entries
    }
    
    /**
     * Read little-endian short
     */
    private fun readLittleEndianShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
    
    /**
     * Read little-endian int
     */
    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8) or
               ((data[offset + 2].toInt() and 0xFF) shl 16) or
               ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
    
    /**
     * Extract archive name from URL
     */
    private fun extractName(url: String): String {
        return url.substringAfterLast("/").substringBefore("?")
    }
}

@Serializable
data class ZipCentralDir(
    val archiveUrl: String,
    val archiveName: String,
    val totalSize: Long,
    val centralDirOffset: Long,
    val entries: List<ZipEntry>,
    val initialRangeData: ByteArray,
    val finalRangeData: ByteArray,
    val totalBytes: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as ZipCentralDir
        
        if (archiveUrl != other.archiveUrl) return false
        if (archiveName != other.archiveName) return false
        if (totalSize != other.totalSize) return false
        if (centralDirOffset != other.centralDirOffset) return false
        if (entries != other.entries) return false
        if (!initialRangeData.contentEquals(other.initialRangeData)) return false
        if (!finalRangeData.contentEquals(other.finalRangeData)) return false
        if (totalBytes != other.totalBytes) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = archiveUrl.hashCode()
        result = 31 * result + archiveName.hashCode()
        result = 31 * result + totalSize.hashCode()
        result = 31 * result + centralDirOffset.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + initialRangeData.contentHashCode()
        result = 31 * result + finalRangeData.contentHashCode()
        result = 31 * result + totalBytes
        return result
    }
}

@Serializable
data class ZipEntry(
    val filename: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val method: Int,
    val localHeaderOffset: Long
)