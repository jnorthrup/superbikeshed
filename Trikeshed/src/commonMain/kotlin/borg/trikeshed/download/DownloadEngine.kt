package borg.trikeshed.download

import borg.trikeshed.lib.ByteSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URL

@Serializable
data class DownloadConfig(
    val chunkSize: Int = 1024 * 1024, // 1MB chunks
    val maxConcurrentDownloads: Int = 4,
    val retryAttempts: Int = 3,
    val timeout: Long = 30000, // 30 seconds
    val verifyChecksum: Boolean = true
)

@Serializable
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val error: String? = null
)

@Serializable
enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

interface DownloadEngine {
    suspend fun download(url: String, destination: File, config: DownloadConfig = DownloadConfig()): Flow<DownloadProgress>
    suspend fun pause(url: String)
    suspend fun resume(url: String)
    suspend fun cancel(url: String)
    suspend fun getProgress(url: String): DownloadProgress?
}

class DownloadEngineImpl : DownloadEngine {
    private val activeDownloads = mutableMapOf<String, DownloadJob>()
    private val downloadQueue = mutableListOf<DownloadJob>()

    override suspend fun download(url: String, destination: File, config: DownloadConfig): Flow<DownloadProgress> = flow {
        val job = DownloadJob(url, destination, config)
        activeDownloads[url] = job
        downloadQueue.add(job)

        try {
            val totalSize = getContentLength(url)
            emit(DownloadProgress(0, totalSize, DownloadStatus.PENDING))

            val chunks = calculateChunks(totalSize, config.chunkSize)
            val chunkResults = chunks.map { chunk ->
                downloadChunk(url, chunk, config)
            }

            val file = destination.outputStream().buffered()
            chunkResults.forEach { chunk ->
                file.write(chunk.toByteArray())
                emit(DownloadProgress(
                    bytesDownloaded = chunk.size.toLong(),
                    totalBytes = totalSize,
                    status = DownloadStatus.DOWNLOADING
                ))
            }
            file.close()

            if (config.verifyChecksum) {
                verifyChecksum(destination, url)
            }

            emit(DownloadProgress(totalSize, totalSize, DownloadStatus.COMPLETED))
        } catch (e: Exception) {
            emit(DownloadProgress(0, 0, DownloadStatus.FAILED, e.message))
        } finally {
            activeDownloads.remove(url)
            downloadQueue.remove(job)
        }
    }

    override suspend fun pause(url: String) {
        activeDownloads[url]?.pause()
    }

    override suspend fun resume(url: String) {
        activeDownloads[url]?.resume()
    }

    override suspend fun cancel(url: String) {
        activeDownloads[url]?.cancel()
        activeDownloads.remove(url)
        downloadQueue.removeAll { it.url == url }
    }

    override suspend fun getProgress(url: String): DownloadProgress? {
        return activeDownloads[url]?.getProgress()
    }

    private suspend fun getContentLength(url: String): Long {
        val connection = URL(url).openConnection()
        return connection.contentLengthLong
    }

    private fun calculateChunks(totalSize: Long, chunkSize: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var start = 0L
        while (start < totalSize) {
            val end = minOf(start + chunkSize, totalSize)
            chunks.add(Chunk(start, end))
            start = end
        }
        return chunks
    }

    private suspend fun downloadChunk(url: String, chunk: Chunk, config: DownloadConfig): ByteSeries {
        var attempts = 0
        while (attempts < config.retryAttempts) {
            try {
                val connection = URL(url).openConnection()
                connection.setRequestProperty("Range", "bytes=${chunk.start}-${chunk.end}")
                connection.connectTimeout = config.timeout.toInt()
                connection.readTimeout = config.timeout.toInt()

                return connection.inputStream.use { input ->
                    ByteSeries(input.readBytes())
                }
            } catch (e: Exception) {
                attempts++
                if (attempts == config.retryAttempts) {
                    throw e
                }
                kotlinx.coroutines.delay(1000L * attempts)
            }
        }
        throw IllegalStateException("Failed to download chunk after ${config.retryAttempts} attempts")
    }

    private suspend fun verifyChecksum(file: File, url: String) {
        // Implement checksum verification
    }
}

data class Chunk(val start: Long, val end: Long)

class DownloadJob(
    val url: String,
    val destination: File,
    val config: DownloadConfig
) {
    private var status = DownloadStatus.PENDING
    private var bytesDownloaded = 0L

    fun pause() {
        status = DownloadStatus.PAUSED
    }

    fun resume() {
        status = DownloadStatus.DOWNLOADING
    }

    fun cancel() {
        status = DownloadStatus.FAILED
    }

    fun getProgress(): DownloadProgress {
        return DownloadProgress(bytesDownloaded, destination.length(), status)
    }

    fun updateProgress(bytes: Long) {
        bytesDownloaded = bytes
    }
} 