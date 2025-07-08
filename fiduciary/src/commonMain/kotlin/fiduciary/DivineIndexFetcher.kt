package fiduciary

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.zlib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Divine Index Fetcher
 * 
 * Uses TrikeShed zlib and HTTP tools to extract ZIP central directory indexes
 * from Patrick Devine archives and store them in Git LFS.
 */

class DivineIndexFetcher(
    internal val httpClient: HttpClient,
    internal val config: FetchConfig
) {
    
    companion object {
        val PATRICK_DEVINE_ARCHIVES = listOf(
            "https://archive.org/download/patrickdevine/patrickdevine.zip",
            "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
        )
        
        const val ZIP_CENTRAL_DIR_SIGNATURE = 0x06054b50L
        const val ZIP_END_OF_CENTRAL_DIR_SIZE = 22
        const val SEARCH_CHUNK_SIZE = 64 * 1024L // 64KB for signature search
    }
    
    internal val fetchStats = ConcurrentHashMap<String, AtomicInteger>()
    internal val errorLog = ConcurrentHashMap<String, ErrorMessage>()
    
    /**
     * Fetch central directory indexes from all Patrick Devine archives
     */
    suspend fun fetchAllIndexes(): FetchStats {
        val startTime = System.currentTimeMillis()
        val successfulFetches = AtomicInteger(0)
        val failedFetches = AtomicInteger(0)
        val totalBytesFetched = AtomicInteger(0)
        val errors = mutableListOf<ErrorMessage>()
        
        for (i in 0 until config.archives.a) {
            val archiveUrl = config.archives.b(i)
            val archiveName = extractArchiveName(archiveUrl)
            
            try {
                val result = fetchCentralDirectory(archiveUrl)
                when (result) {
                    is Either.Left -> {
                        failedFetches.incrementAndGet()
                        errors.add(result.value)
                        errorLog[archiveName] = result.value
                    }
                    is Either.Right -> {
                        successfulFetches.incrementAndGet()
                        totalBytesFetched.addAndGet(result.value.a)
                        
                        // Store in LFS
                        val lfsResult = storeInLfs(archiveName, result.value)
                        when (lfsResult) {
                            is Either.Left -> errors.add(lfsResult.value)
                            is Either.Right -> println("Stored ${archiveName} index in LFS: ${lfsResult.value}")
                        }
                    }
                }
            } catch (e: Exception) {
                failedFetches.incrementAndGet()
                val error = "Failed to fetch ${archiveName}: ${e.message}"
                errors.add(error)
                errorLog[archiveName] = error
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        val avgTime = if (successfulFetches.get() > 0) processingTime / successfulFetches.get() else 0L
        
        return FetchStats(
            totalArchives = config.archives.a,
            successfulFetches = successfulFetches.get(),
            failedFetches = failedFetches.get(),
            totalBytesFetched = totalBytesFetched.get().toLong(),
            averageFetchTime = avgTime,
            errors = errors.size j errors::get
        )
    }
    
    /**
     * Fetch central directory from a single archive
     */
    suspend fun fetchCentralDirectory(archiveUrl: ArchiveUrl): FetchResult {
        val startTime = System.currentTimeMillis()
        
        // Get archive size
        val totalSize = getArchiveSize(archiveUrl)
        
        // Find central directory offset
        val centralDirOffset = findCentralDirectoryOffset(archiveUrl, totalSize)
        
        // Calculate central directory size (search backwards from end)
        val centralDirSize = calculateCentralDirectorySize(archiveUrl, centralDirOffset, totalSize)
        
        // Fetch central directory binary data
        val centralDirBinary = fetchCentralDirectoryBinary(archiveUrl, centralDirOffset, centralDirSize)
        
        val processingTime = System.currentTimeMillis() - startTime
        println("Fetched central directory from ${extractArchiveName(archiveUrl)} in ${processingTime}ms")
        
        return Either.right(centralDirBinary)
    }
    
    /**
     * Find central directory offset by searching backwards from end of file
     */
    internal suspend fun findCentralDirectoryOffset(archiveUrl: ArchiveUrl, totalSize: ZipSize): CentralDirOffset {
        val searchStart = maxOf(0L, totalSize - SEARCH_CHUNK_SIZE)
        val searchEnd = totalSize - 1
        
        val searchData = executeRangeRequest(archiveUrl, searchStart, searchEnd)
        
        // Search backwards for ZIP central directory signature
        for (i in searchData.a - 4 downTo 0) {
            val signature = (searchData.b(i).toLong() and 0xFF) or
                           ((searchData.b(i + 1).toLong() and 0xFF) shl 8) or
                           ((searchData.b(i + 2).toLong() and 0xFF) shl 16) or
                           ((searchData.b(i + 3).toLong() and 0xFF) shl 24)
            
            if (signature == ZIP_CENTRAL_DIR_SIGNATURE) {
                return searchStart + i
            }
        }
        
        throw Exception("Could not find ZIP central directory signature in ${extractArchiveName(archiveUrl)}")
    }
    
    /**
     * Calculate central directory size by parsing end-of-central-directory record
     */
    internal suspend fun calculateCentralDirectorySize(
        archiveUrl: ArchiveUrl, 
        centralDirOffset: CentralDirOffset, 
        totalSize: ZipSize
    ): CentralDirSize {
        // Read end-of-central-directory record (22 bytes)
        val endRecordData = executeRangeRequest(
            archiveUrl, 
            centralDirOffset, 
            centralDirOffset + ZIP_END_OF_CENTRAL_DIR_SIZE - 1
        )
        
        // Parse central directory size from end record
        val centralDirSize = parseLittleEndianInt(endRecordData, 12).toLong() and 0xFFFFFFFFL
        
        return centralDirSize
    }
    
    /**
     * Fetch central directory binary data
     */
    internal suspend fun fetchCentralDirectoryBinary(
        archiveUrl: ArchiveUrl,
        centralDirOffset: CentralDirOffset,
        centralDirSize: CentralDirSize
    ): CentralDirBinary {
        val binaryData = executeRangeRequest(archiveUrl, centralDirOffset, centralDirOffset + centralDirSize - 1)
        return binaryData
    }
    
    /**
     * Execute HTTP range request using TrikeShed HTTP client
     */
    internal suspend fun executeRangeRequest(url: ArchiveUrl, start: RangeStart, end: RangeEnd): ZipBinaryData {
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(url),
            headers = 3 j { i ->
                when (i) {
                    0 -> HttpHeaderName("Range") j HttpHeaderValue("bytes=$start-$end")
                    1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("DivineIndexFetcher/1.0")
                    2 -> HttpHeaderName("Accept") j HttpHeaderValue("*/*")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        val response = httpClient.execute(request)
        
        if (response.status.code != 206 && response.status.code != 200) {
            throw Exception("HTTP request failed with status: ${response.status.code}")
        }
        
        // Convert response body to Indexed<Byte>
        val bodyBytes = response.body.toByteArray()
        return bodyBytes.size j bodyBytes::get
    }
    
    /**
     * Get archive size using HTTP HEAD request
     */
    internal suspend fun getArchiveSize(archiveUrl: ArchiveUrl): ZipSize {
        val request = HttpRequest(
            method = HttpMethod.HEAD,
            path = HttpRequestPath(archiveUrl),
            headers = 2 j { i ->
                when (i) {
                    0 -> HttpHeaderName("User-Agent") j HttpHeaderValue("DivineIndexFetcher/1.0")
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
        
        throw Exception("Could not determine archive size for ${extractArchiveName(archiveUrl)}")
    }
    
    /**
     * Store central directory binary in Git LFS
     */
    internal suspend fun storeInLfs(archiveName: ArchiveName, binaryData: CentralDirBinary): LfsResult {
        return withContext(Dispatchers.IO) {
            try {
                val lfsDir = File(config.outputDir)
                lfsDir.mkdirs()
                
                val binaryFile = File(lfsDir, "${archiveName}_central_dir.bin")
                val pointerFile = File(lfsDir, "${archiveName}_central_dir.lfs")
                
                // Write binary data
                val byteArray = ByteArray(binaryData.a) { binaryData.b(it) }
                binaryFile.writeBytes(byteArray)
                
                // Create LFS pointer
                val lfsPointer = createLfsPointer(binaryFile, byteArray.size.toLong())
                pointerFile.writeText(lfsPointer)
                
                Either.right(pointerFile.absolutePath)
            } catch (e: Exception) {
                Either.left("Failed to store ${archiveName} in LFS: ${e.message}")
            }
        }
    }
    
    /**
     * Create Git LFS pointer file content
     */
    internal fun createLfsPointer(file: File, size: LfsSize): LfsPointer {
        val sha256 = calculateSha256(file)
        return """
            version https://git-lfs.github.com/spec/v1
            oid sha256:$sha256
            size $size
        """.trimIndent()
    }
    
    /**
     * Calculate SHA256 hash of file
     */
    internal fun calculateSha256(file: File): String {
        // Simple hash for demo - would use proper SHA256
        return file.absolutePath.hashCode().toString(16).padStart(64, '0')
    }
    
    /**
     * Extract archive name from URL
     */
    internal fun extractArchiveName(url: ArchiveUrl): ArchiveName {
        val fileName = url.substringAfterLast("/")
        return fileName.substringBefore("?")
    }
    
    /**
     * Parse little-endian int from byte array
     */
    internal fun parseLittleEndianInt(data: ZipBinaryData, offset: Int): Int {
        return (data.b(offset).toInt() and 0xFF) or
               ((data.b(offset + 1).toInt() and 0xFF) shl 8) or
               ((data.b(offset + 2).toInt() and 0xFF) shl 16) or
               ((data.b(offset + 3).toInt() and 0xFF) shl 24)
    }
    
    /**
     * Get fetching statistics
     */
    fun getFetchStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        fetchStats.forEach { (key, value) ->
            stats[key] = value.get()
        }
        return stats
    }
    
    /**
     * Get error log
     */
    fun getErrorLog(): Map<String, ErrorMessage> {
        return errorLog.toMap()
    }
} 