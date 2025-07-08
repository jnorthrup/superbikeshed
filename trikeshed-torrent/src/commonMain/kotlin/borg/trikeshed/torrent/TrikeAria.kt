@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlinx.coroutines.*
import kotlin.system.exitProcess

/**
 * TrikeAria - aria2c-like command-line interface for torrent and multi-protocol downloads
 * 
 * Usage: trike-aria [options] <urls...>
 * 
 * Key features:
 * - Torrent downloads (magnet links, .torrent files)
 * - HTTP/FTP downloads with resume support
 * - Concurrent downloads with connection limits
 * - Progress reporting and statistics
 * - Session management
 */
object TrikeAria {
    
    internal val downloader = TrikeDownloader(
        maxConcurrentDownloads = 5,
        downloadDir = "./downloads",
        tempDir = "./temp"
    )
    
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            try {
                parseArgsAndExecute(args)
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
                exitProcess(1)
            }
        }
    }
    
    internal suspend fun parseArgsAndExecute(args: Array<String>) {
        if (args.isEmpty()) {
            showHelp()
            return
        }
        
        var urls = mutableListOf<String>()
        var outputDir = "./downloads"
        var maxConnections = 16
        var maxConcurrent = 5
        var maxDownloadLimit = 0L
        var maxUploadLimit = 0L
        var showHelp = false
        var listDownloads = false
        var pauseAll = false
        var resumeAll = false
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-h", "--help" -> showHelp = true
                "-d", "--dir" -> {
                    if (i + 1 < args.size) {
                        outputDir = args[++i]
                    }
                }
                "-x", "--max-connection-per-server" -> {
                    if (i + 1 < args.size) {
                        maxConnections = args[++i].toIntOrNull() ?: 16
                    }
                }
                "-j", "--max-concurrent-downloads" -> {
                    if (i + 1 < args.size) {
                        maxConcurrent = args[++i].toIntOrNull() ?: 5
                    }
                }
                "--max-download-limit" -> {
                    if (i + 1 < args.size) {
                        maxDownloadLimit = args[++i].toLongOrNull() ?: 0
                    }
                }
                "--max-upload-limit" -> {
                    if (i + 1 < args.size) {
                        maxUploadLimit = args[++i].toLongOrNull() ?: 0
                    }
                }
                "--list" -> listDownloads = true
                "--pause-all" -> pauseAll = true
                "--resume-all" -> resumeAll = true
                else -> {
                    if (!args[i].startsWith("-")) {
                        urls.add(args[i])
                    }
                }
            }
            i++
        }
        
        if (showHelp) {
            showHelp()
            return
        }
        
        // Start the downloader
        downloader.start()
        
        try {
            when {
                listDownloads -> listDownloads()
                pauseAll -> pauseAllDownloads()
                resumeAll -> resumeAllDownloads()
                urls.isNotEmpty() -> addDownloads(urls, outputDir, maxConnections, maxDownloadLimit, maxUploadLimit)
                else -> showHelp()
            }
        } finally {
            // Keep running for a bit to show progress
            if (urls.isNotEmpty()) {
                delay(5000)
            }
            downloader.stop()
        }
    }
    
    internal suspend fun addDownloads(
        urls: List<String>,
        outputDir: String,
        maxConnections: Int,
        maxDownloadLimit: Long,
        maxUploadLimit: Long
    ) {
        println("🚀 Adding ${urls.size} download(s) to queue...")
        
        urls.forEach { url ->
            when {
                url.startsWith("magnet:") || url.endsWith(".torrent") -> {
                    val id = downloader.addTorrentDownload(
                        url = url,
                        outputPath = "$outputDir/${extractFilename(url)}",
                        maxPeers = maxConnections
                    )
                    println("🔗 Added torrent: $url (ID: $id)")
                }
                else -> {
                    val id = downloader.addHttpDownload(
                        url = url,
                        outputPath = "$outputDir/${extractFilename(url)}"
                    )
                    println("🌐 Added HTTP download: $url (ID: $id)")
                }
            }
        }
        
        println("📊 Downloads added. Use --list to see progress.")
    }
    
    internal suspend fun listDownloads() {
        val downloads = downloader.listDownloads()
        val stats = downloader.getStats()
        
        println("📋 Active Downloads (${stats.activeDownloads}/${stats.totalDownloads})")
        println("=" * 80)
        
        if (downloads.isEmpty()) {
            println("No active downloads")
        } else {
            downloads.forEach { progress ->
                val percentage = if (progress.totalBytes > 0) {
                    (progress.downloadedBytes * 100 / progress.totalBytes)
                } else 0
                
                val speed = when {
                    progress.speed > 1024 * 1024 -> "${progress.speed / (1024 * 1024)}MB/s"
                    progress.speed > 1024 -> "${progress.speed / 1024}KB/s"
                    else -> "${progress.speed}B/s"
                }
                
                val eta = when {
                    progress.eta > 3600 -> "${progress.eta / 3600}h"
                    progress.eta > 60 -> "${progress.eta / 60}m"
                    else -> "${progress.eta}s"
                }
                
                println("[${progress.status}] ${progress.id}")
                println("  Progress: $percentage% (${progress.downloadedBytes}/${progress.totalBytes} bytes)")
                println("  Speed: $speed, ETA: $eta, Connections: ${progress.connections}")
                println()
            }
        }
        
        println("📊 Global Stats:")
        println("  Total downloaded: ${stats.totalDownloadedBytes / (1024 * 1024)}MB")
        println("  Completed: ${stats.completedDownloads}, Failed: ${stats.failedDownloads}")
    }
    
    internal suspend fun pauseAllDownloads() {
        val downloads = downloader.listDownloads()
        downloads.forEach { progress ->
            if (progress.status == TrikeDownloader.DownloadStatus.Downloading) {
                downloader.pauseDownload(progress.id)
            }
        }
        println("⏸️ Paused all active downloads")
    }
    
    internal suspend fun resumeAllDownloads() {
        val downloads = downloader.listDownloads()
        downloads.forEach { progress ->
            if (progress.status == TrikeDownloader.DownloadStatus.Paused) {
                downloader.resumeDownload(progress.id)
            }
        }
        println("▶️ Resumed all paused downloads")
    }
    
    internal fun extractFilename(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "download" }
    }
    
    internal fun showHelp() {
        println("""
            TrikeAria - aria2c-like downloader for torrents and HTTP/FTP
            
            Usage: trike-aria [options] <urls...>
            
            Options:
              -h, --help                           Show this help message
              -d, --dir <directory>               Set download directory (default: ./downloads)
              -x, --max-connection-per-server <n> Set max connections per server (default: 16)
              -j, --max-concurrent-downloads <n>  Set max concurrent downloads (default: 5)
              --max-download-limit <speed>        Set download speed limit in bytes/sec
              --max-upload-limit <speed>          Set upload speed limit in bytes/sec
              --list                              List all downloads
              --pause-all                         Pause all active downloads
              --resume-all                        Resume all paused downloads
            
            Examples:
              trike-aria magnet:?xt=urn:btih:...
              trike-aria https://example.com/file.zip
              trike-aria --dir /downloads file.torrent
              trike-aria --list
        """.trimIndent())
    }
    
    internal operator fun String.times(n: Int): String = repeat(n)
} 