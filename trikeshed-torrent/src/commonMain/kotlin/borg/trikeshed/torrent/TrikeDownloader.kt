@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * TrikeDownloader - Unified downloader combining aria2c and curl functionality
 * 
 * Two personas:
 * 1. trike-aria: Torrent and multi-protocol downloads (aria2c-like)
 * 2. trike-curl: HTTP/HTTPS downloads with advanced features (curl-like)
 */
class TrikeDownloader(
    internal val maxConcurrentDownloads: Int = 5,
    internal val downloadDir: String = "./downloads",
    internal val tempDir: String = "./temp"
) {
    
    internal val activeDownloads = mutableMapOf<String, DownloadTask>()
    internal val downloadQueue = Channel<DownloadTask>(capacity = 100)
    internal var isRunning = false
    
    /**
     * Download task representing either HTTP or torrent download
     */
    sealed class DownloadTask {
        abstract val id: String
        abstract val url: String
        abstract val outputPath: String
        abstract val status: DownloadStatus
        
        data class HttpDownload(
            override val id: String,
            override val url: String,
            override val outputPath: String,
            override val status: DownloadStatus = DownloadStatus.Pending,
            val headers: Map<String, String> = emptyMap(),
            val method: String = "GET",
            val resume: Boolean = true,
            val timeout: Long = 30000
        ) : DownloadTask()
        
        data class TorrentDownload(
            override val id: String,
            override val url: String, // magnet link or torrent file URL
            override val outputPath: String,
            override val status: DownloadStatus = DownloadStatus.Pending,
            val trackers: List<String> = emptyList(),
            val maxPeers: Int = 50,
            val uploadLimit: Long = 0, // 0 = unlimited
            val downloadLimit: Long = 0 // 0 = unlimited
        ) : DownloadTask()
    }
    
    enum class DownloadStatus {
        Pending, Downloading, Paused, Completed, Failed, Cancelled
    }
    
    /**
     * Download progress information
     */
    data class DownloadProgress(
        val id: String,
        val status: DownloadStatus,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: Long, // bytes per second
        val eta: Long, // estimated time in seconds
        val connections: Int = 1
    )
    
    /**
     * Start the downloader service
     */
    suspend fun start() {
        if (isRunning) return
        
        isRunning = true
        println("🚀 Starting TrikeDownloader")
        
        // Start download worker
        launch {
            downloadWorker()
        }
        
        // Start progress reporter
        launch {
            progressReporter()
        }
    }
    
    /**
     * Stop the downloader service
     */
    suspend fun stop() {
        if (!isRunning) return
        
        isRunning = false
        println("🛑 Stopping TrikeDownloader")
        
        // Cancel all active downloads
        activeDownloads.values.forEach { task ->
            when (task) {
                is DownloadTask.HttpDownload -> cancelHttpDownload(task)
                is DownloadTask.TorrentDownload -> cancelTorrentDownload(task)
            }
        }
        activeDownloads.clear()
    }
    
    /**
     * Add HTTP download (curl-like functionality)
     */
    suspend fun addHttpDownload(
        url: String,
        outputPath: String,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        resume: Boolean = true
    ): String {
        val id = generateId()
        val task = DownloadTask.HttpDownload(
            id = id,
            url = url,
            outputPath = outputPath,
            headers = headers,
            method = method,
            resume = resume
        )
        
        downloadQueue.send(task)
        return id
    }
    
    /**
     * Add torrent download (aria2c-like functionality)
     */
    suspend fun addTorrentDownload(
        url: String, // magnet link or torrent file URL
        outputPath: String,
        trackers: List<String> = emptyList(),
        maxPeers: Int = 50
    ): String {
        val id = generateId()
        val task = DownloadTask.TorrentDownload(
            id = id,
            url = url,
            outputPath = outputPath,
            trackers = trackers,
            maxPeers = maxPeers
        )
        
        downloadQueue.send(task)
        return id
    }
    
    /**
     * Pause a download
     */
    suspend fun pauseDownload(id: String) {
        activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> pauseHttpDownload(task)
                is DownloadTask.TorrentDownload -> pauseTorrentDownload(task)
            }
        }
    }
    
    /**
     * Resume a download
     */
    suspend fun resumeDownload(id: String) {
        activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> resumeHttpDownload(task)
                is DownloadTask.TorrentDownload -> resumeTorrentDownload(task)
            }
        }
    }
    
    /**
     * Cancel a download
     */
    suspend fun cancelDownload(id: String) {
        activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> cancelHttpDownload(task)
                is DownloadTask.TorrentDownload -> cancelTorrentDownload(task)
            }
            activeDownloads.remove(id)
        }
    }
    
    /**
     * Get download progress
     */
    fun getProgress(id: String): DownloadProgress? {
        return activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> getHttpProgress(task)
                is DownloadTask.TorrentDownload -> getTorrentProgress(task)
            }
        }
    }
    
    /**
     * List all downloads
     */
    fun listDownloads(): List<DownloadProgress> {
        return activeDownloads.values.mapNotNull { task ->
            getProgress(task.id)
        }
    }
    
    /**
     * Get global statistics
     */
    fun getStats(): DownloaderStats {
        val downloads = activeDownloads.values
        return DownloaderStats(
            totalDownloads = downloads.size,
            activeDownloads = downloads.count { it.status == DownloadStatus.Downloading },
            pausedDownloads = downloads.count { it.status == DownloadStatus.Paused },
            completedDownloads = downloads.count { it.status == DownloadStatus.Completed },
            failedDownloads = downloads.count { it.status == DownloadStatus.Failed },
            totalDownloadedBytes = downloads.sumOf { 
                when (it) {
                    is DownloadTask.HttpDownload -> getHttpProgress(it)?.downloadedBytes ?: 0
                    is DownloadTask.TorrentDownload -> getTorrentProgress(it)?.downloadedBytes ?: 0
                }
            }
        )
    }
    
    data class DownloaderStats(
        val totalDownloads: Int,
        val activeDownloads: Int,
        val pausedDownloads: Int,
        val completedDownloads: Int,
        val failedDownloads: Int,
        val totalDownloadedBytes: Long
    )
    
    // Private implementation methods
    
    internal suspend fun downloadWorker() {
        for (task in downloadQueue) {
            if (!isRunning) break
            
            if (activeDownloads.size < maxConcurrentDownloads) {
                activeDownloads[task.id] = task
                launch {
                    when (task) {
                        is DownloadTask.HttpDownload -> executeHttpDownload(task)
                        is DownloadTask.TorrentDownload -> executeTorrentDownload(task)
                    }
                }
            } else {
                // Re-queue if at capacity
                downloadQueue.send(task)
                delay(1000)
            }
        }
    }
    
    internal suspend fun executeHttpDownload(task: DownloadTask.HttpDownload) {
        println("🌐 Starting HTTP download: ${task.url}")
        
        try {
            // Simulate HTTP download
            var downloaded = 0L
            val total = 1024 * 1024L // Simulate 1MB file
            
            while (downloaded < total && task.status != DownloadStatus.Cancelled) {
                delay(100)
                downloaded += 1024 * 10 // Simulate 10KB chunks
                
                // Update progress
                activeDownloads[task.id] = task.copy(
                    status = DownloadStatus.Downloading
                )
            }
            
            if (task.status != DownloadStatus.Cancelled) {
                activeDownloads[task.id] = task.copy(
                    status = DownloadStatus.Completed
                )
                println("✅ HTTP download completed: ${task.url}")
            }
        } catch (e: Exception) {
            activeDownloads[task.id] = task.copy(
                status = DownloadStatus.Failed
            )
            println("❌ HTTP download failed: ${task.url} - ${e.message}")
        }
    }
    
    internal suspend fun executeTorrentDownload(task: DownloadTask.TorrentDownload) {
        println("🔗 Starting torrent download: ${task.url}")
        
        try {
            // Simulate torrent download
            var downloaded = 0L
            val total = 1024 * 1024 * 100L // Simulate 100MB file
            
            while (downloaded < total && task.status != DownloadStatus.Cancelled) {
                delay(100)
                downloaded += 1024 * 50 // Simulate 50KB chunks
                
                // Update progress
                activeDownloads[task.id] = task.copy(
                    status = DownloadStatus.Downloading
                )
            }
            
            if (task.status != DownloadStatus.Cancelled) {
                activeDownloads[task.id] = task.copy(
                    status = DownloadStatus.Completed
                )
                println("✅ Torrent download completed: ${task.url}")
            }
        } catch (e: Exception) {
            activeDownloads[task.id] = task.copy(
                status = DownloadStatus.Failed
            )
            println("❌ Torrent download failed: ${task.url} - ${e.message}")
        }
    }
    
    internal suspend fun progressReporter() {
        while (isRunning) {
            delay(2000)
            
            val stats = getStats()
            if (stats.activeDownloads > 0) {
                println("📊 Active downloads: ${stats.activeDownloads}, " +
                       "Total downloaded: ${stats.totalDownloadedBytes / 1024}KB")
            }
        }
    }
    
    internal fun generateId(): String = "download_${System.currentTimeMillis()}"
    
    internal suspend fun pauseHttpDownload(task: DownloadTask.HttpDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Paused)
        println("⏸️ Paused HTTP download: ${task.url}")
    }
    
    internal suspend fun resumeHttpDownload(task: DownloadTask.HttpDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Downloading)
        println("▶️ Resumed HTTP download: ${task.url}")
    }
    
    internal suspend fun cancelHttpDownload(task: DownloadTask.HttpDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Cancelled)
        println("❌ Cancelled HTTP download: ${task.url}")
    }
    
    internal suspend fun pauseTorrentDownload(task: DownloadTask.TorrentDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Paused)
        println("⏸️ Paused torrent download: ${task.url}")
    }
    
    internal suspend fun resumeTorrentDownload(task: DownloadTask.TorrentDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Downloading)
        println("▶️ Resumed torrent download: ${task.url}")
    }
    
    internal suspend fun cancelTorrentDownload(task: DownloadTask.TorrentDownload) {
        activeDownloads[task.id] = task.copy(status = DownloadStatus.Cancelled)
        println("❌ Cancelled torrent download: ${task.url}")
    }
    
    internal fun getHttpProgress(task: DownloadTask.HttpDownload): DownloadProgress {
        return DownloadProgress(
            id = task.id,
            status = task.status,
            downloadedBytes = 1024 * 512L, // Simulated progress
            totalBytes = 1024 * 1024L,
            speed = 1024 * 10L,
            eta = 50L
        )
    }
    
    internal fun getTorrentProgress(task: DownloadTask.TorrentDownload): DownloadProgress {
        return DownloadProgress(
            id = task.id,
            status = task.status,
            downloadedBytes = 1024 * 1024 * 50L, // Simulated progress
            totalBytes = 1024 * 1024 * 100L,
            speed = 1024 * 50L,
            eta = 1000L,
            connections = task.maxPeers
        )
    }
} 