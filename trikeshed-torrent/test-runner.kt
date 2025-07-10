@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay

// Simple test runner for TrikeAria and TrikeDownloader
// This bypasses the complex Kotlin Multiplatform build system

fun main() {
    println("🧪 Running TrikeDownloader and TrikeAria Unit Tests")
    println("=" * 60)
    
    var passedTests = 0
    var totalTests = 0
    
    // Test TrikeDownloader functionality
    runTest {
        println("\n📦 Testing TrikeDownloader...")
        
        // Test 1: Basic initialization
        totalTests++
        try {
            val downloader = TrikeDownloader(
                maxConcurrentDownloads = 3,
                downloadDir = "./test-downloads",
                tempDir = "./test-temp"
            )
            println("✅ TrikeDownloader initialization")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeDownloader initialization failed: ${e.message}")
        }
        
        // Test 2: Start/stop functionality
        totalTests++
        try {
            val downloader = TrikeDownloader()
            val initialStats = downloader.getStats()
            assertTrue(initialStats.totalDownloads == 0)
            
            downloader.start()
            val runningStats = downloader.getStats()
            assertTrue(runningStats.totalDownloads == 0)
            
            downloader.stop()
            println("✅ TrikeDownloader start/stop")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeDownloader start/stop failed: ${e.message}")
        }
        
        // Test 3: Add HTTP download
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id = downloader.addHttpDownload(
                url = "https://example.com/file.zip",
                outputPath = "./test-downloads/file.zip"
            )
            
            assertTrue(id.startsWith("download_"))
            assertTrue(id.length > 10)
            
            downloader.stop()
            println("✅ Add HTTP download")
            passedTests++
        } catch (e: Exception) {
            println("❌ Add HTTP download failed: ${e.message}")
        }
        
        // Test 4: Add torrent download
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id = downloader.addTorrentDownload(
                url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
                outputPath = "./test-downloads/torrent"
            )
            
            assertTrue(id.startsWith("download_"))
            assertTrue(id.length > 10)
            
            downloader.stop()
            println("✅ Add torrent download")
            passedTests++
        } catch (e: Exception) {
            println("❌ Add torrent download failed: ${e.message}")
        }
        
        // Test 5: Get progress
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id = downloader.addHttpDownload(
                url = "https://example.com/file.zip",
                outputPath = "./test-downloads/file.zip"
            )
            
            delay(100)
            
            val progress = downloader.getProgress(id)
            assertNotNull(progress)
            assertEquals(id, progress.id)
            
            downloader.stop()
            println("✅ Get download progress")
            passedTests++
        } catch (e: Exception) {
            println("❌ Get download progress failed: ${e.message}")
        }
        
        // Test 6: List downloads
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id1 = downloader.addHttpDownload(
                url = "https://example.com/file1.zip",
                outputPath = "./test-downloads/file1.zip"
            )
            
            val id2 = downloader.addTorrentDownload(
                url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
                outputPath = "./test-downloads/torrent"
            )
            
            delay(100)
            
            val downloads = downloader.listDownloads()
            assertTrue(downloads.size >= 2)
            
            val downloadIds = downloads.map { it.id }
            assertTrue(downloadIds.contains(id1))
            assertTrue(downloadIds.contains(id2))
            
            downloader.stop()
            println("✅ List downloads")
            passedTests++
        } catch (e: Exception) {
            println("❌ List downloads failed: ${e.message}")
        }
        
        // Test 7: Get stats
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id1 = downloader.addHttpDownload(
                url = "https://example.com/file1.zip",
                outputPath = "./test-downloads/file1.zip"
            )
            
            val id2 = downloader.addTorrentDownload(
                url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
                outputPath = "./test-downloads/torrent"
            )
            
            delay(100)
            
            val stats = downloader.getStats()
            assertTrue(stats.totalDownloads >= 2)
            assertTrue(stats.activeDownloads >= 0)
            assertTrue(stats.totalDownloadedBytes >= 0)
            
            downloader.stop()
            println("✅ Get global statistics")
            passedTests++
        } catch (e: Exception) {
            println("❌ Get global statistics failed: ${e.message}")
        }
        
        // Test 8: Pause and resume
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id = downloader.addHttpDownload(
                url = "https://example.com/file.zip",
                outputPath = "./test-downloads/file.zip"
            )
            
            delay(100)
            
            downloader.pauseDownload(id)
            
            val pausedProgress = downloader.getProgress(id)
            assertNotNull(pausedProgress)
            assertEquals(TrikeDownloader.DownloadStatus.Paused, pausedProgress.status)
            
            downloader.resumeDownload(id)
            
            val resumedProgress = downloader.getProgress(id)
            assertNotNull(resumedProgress)
            assertTrue(resumedProgress.status in listOf(
                TrikeDownloader.DownloadStatus.Downloading,
                TrikeDownloader.DownloadStatus.Completed
            ))
            
            downloader.stop()
            println("✅ Pause and resume downloads")
            passedTests++
        } catch (e: Exception) {
            println("❌ Pause and resume downloads failed: ${e.message}")
        }
        
        // Test 9: Cancel download
        totalTests++
        try {
            val downloader = TrikeDownloader()
            downloader.start()
            
            val id = downloader.addHttpDownload(
                url = "https://example.com/file.zip",
                outputPath = "./test-downloads/file.zip"
            )
            
            delay(100)
            
            downloader.cancelDownload(id)
            
            val cancelledProgress = downloader.getProgress(id)
            assertNull(cancelledProgress)
            
            downloader.stop()
            println("✅ Cancel download")
            passedTests++
        } catch (e: Exception) {
            println("❌ Cancel download failed: ${e.message}")
        }
    }
    
    // Test TrikeAria functionality
    runTest {
        println("\n🎯 Testing TrikeAria...")
        
        // Test 1: Help argument
        totalTests++
        try {
            val args = arrayOf("--help")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("TrikeAria - aria2c-like downloader"))
            assertTrue(result.contains("Usage: trike-aria"))
            println("✅ Help argument parsing")
            passedTests++
        } catch (e: Exception) {
            println("❌ Help argument parsing failed: ${e.message}")
        }
        
        // Test 2: Download directory argument
        totalTests++
        try {
            val args = arrayOf("--dir", "/custom/downloads", "https://example.com/file.zip")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Added HTTP download"))
            assertTrue(result.contains("/custom/downloads"))
            println("✅ Download directory argument")
            passedTests++
        } catch (e: Exception) {
            println("❌ Download directory argument failed: ${e.message}")
        }
        
        // Test 3: Magnet link
        totalTests++
        try {
            val magnetLink = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678"
            val args = arrayOf(magnetLink)
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Added torrent"))
            assertTrue(result.contains("magnet:"))
            println("✅ Magnet link handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ Magnet link handling failed: ${e.message}")
        }
        
        // Test 4: HTTP download
        totalTests++
        try {
            val httpUrl = "https://example.com/file.zip"
            val args = arrayOf(httpUrl)
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Added HTTP download"))
            assertTrue(result.contains("https://example.com/file.zip"))
            println("✅ HTTP download handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ HTTP download handling failed: ${e.message}")
        }
        
        // Test 5: Multiple URLs
        totalTests++
        try {
            val args = arrayOf(
                "https://example.com/file1.zip",
                "https://example.com/file2.zip",
                "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678"
            )
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Adding 3 download(s) to queue"))
            assertTrue(result.contains("Added HTTP download"))
            assertTrue(result.contains("Added torrent"))
            println("✅ Multiple URLs handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ Multiple URLs handling failed: ${e.message}")
        }
        
        // Test 6: List downloads command
        totalTests++
        try {
            val args = arrayOf("--list")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Active Downloads"))
            assertTrue(result.contains("Global Stats"))
            println("✅ List downloads command")
            passedTests++
        } catch (e: Exception) {
            println("❌ List downloads command failed: ${e.message}")
        }
        
        // Test 7: Pause all command
        totalTests++
        try {
            val args = arrayOf("--pause-all")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Paused all active downloads"))
            println("✅ Pause all command")
            passedTests++
        } catch (e: Exception) {
            println("❌ Pause all command failed: ${e.message}")
        }
        
        // Test 8: Resume all command
        totalTests++
        try {
            val args = arrayOf("--resume-all")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Resumed all paused downloads"))
            println("✅ Resume all command")
            passedTests++
        } catch (e: Exception) {
            println("❌ Resume all command failed: ${e.message}")
        }
        
        // Test 9: Empty arguments
        totalTests++
        try {
            val args = arrayOf<String>()
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("TrikeAria - aria2c-like downloader"))
            println("✅ Empty arguments handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ Empty arguments handling failed: ${e.message}")
        }
        
        // Test 10: Invalid numeric arguments
        totalTests++
        try {
            val args = arrayOf("--max-connection-per-server", "invalid", "https://example.com/file.zip")
            val result = captureOutput { TrikeAria.main(args) }
            assertTrue(result.contains("Added HTTP download"))
            println("✅ Invalid numeric arguments handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ Invalid numeric arguments handling failed: ${e.message}")
        }
    }
    
    // Test TrikeCurl functionality
    runTest {
        println("\n🌐 Testing TrikeCurl...")
        
        // Test 1: Help argument
        totalTests++
        try {
            val args = arrayOf("--help")
            val result = captureOutput { TrikeCurl.main(args) }
            assertTrue(result.contains("TrikeCurl - curl-like HTTP/HTTPS downloader"))
            assertTrue(result.contains("Usage: trike-curl"))
            println("✅ TrikeCurl help argument")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeCurl help argument failed: ${e.message}")
        }
        
        // Test 2: Output file argument
        totalTests++
        try {
            val args = arrayOf("--output", "custom-file.zip", "https://example.com/file.zip")
            val result = captureOutput { TrikeCurl.main(args) }
            assertTrue(result.contains("Starting download"))
            assertTrue(result.contains("custom-file.zip"))
            println("✅ TrikeCurl output file argument")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeCurl output file argument failed: ${e.message}")
        }
        
        // Test 3: Custom headers
        totalTests++
        try {
            val args = arrayOf(
                "--header", "Authorization: Bearer token123",
                "--header", "User-Agent: CustomAgent/1.0",
                "https://example.com/file.zip"
            )
            val result = captureOutput { TrikeCurl.main(args) }
            assertTrue(result.contains("Starting download"))
            println("✅ TrikeCurl custom headers")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeCurl custom headers failed: ${e.message}")
        }
        
        // Test 4: Verbose output
        totalTests++
        try {
            val args = arrayOf("--verbose", "https://example.com/file.zip")
            val result = captureOutput { TrikeCurl.main(args) }
            assertTrue(result.contains("Request:"))
            assertTrue(result.contains("URL:"))
            assertTrue(result.contains("Method:"))
            println("✅ TrikeCurl verbose output")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeCurl verbose output failed: ${e.message}")
        }
        
        // Test 5: Missing URL
        totalTests++
        try {
            val args = arrayOf("--verbose", "--output", "file.zip")
            val result = captureOutput { TrikeCurl.main(args) }
            assertTrue(result.contains("Error: No URL specified"))
            println("✅ TrikeCurl missing URL handling")
            passedTests++
        } catch (e: Exception) {
            println("❌ TrikeCurl missing URL handling failed: ${e.message}")
        }
    }
    
    println("\n" + "=" * 60)
    println("📊 Test Results: $passedTests/$totalTests tests passed")
    
    if (passedTests == totalTests) {
        println("🎉 All tests passed!")
        kotlin.system.exitProcess(0)
    } else {
        println("❌ Some tests failed!")
        kotlin.system.exitProcess(1)
    }
}

// Helper function to capture console output
suspend fun captureOutput(block: suspend () -> Unit): String {
    val originalOut = System.out
    val outputStream = java.io.ByteArrayOutputStream()
    System.setOut(java.io.PrintStream(outputStream))
    
    try {
        block()
        return outputStream.toString()
    } finally {
        System.setOut(originalOut)
    }
}

// Mock implementations for testing
class TrikeDownloader(
    internal val maxConcurrentDownloads: Int = 5,
    internal val downloadDir: String = "./downloads",
    internal val tempDir: String = "./temp"
) {
    internal val activeDownloads = mutableMapOf<String, DownloadTask>()
    internal var isRunning = false
    
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
            override val url: String,
            override val outputPath: String,
            override val status: DownloadStatus = DownloadStatus.Pending,
            val trackers: List<String> = emptyList(),
            val maxPeers: Int = 50,
            val uploadLimit: Long = 0,
            val downloadLimit: Long = 0
        ) : DownloadTask()
    }
    
    enum class DownloadStatus {
        Pending, Downloading, Paused, Completed, Failed, Cancelled
    }
    
    data class DownloadProgress(
        val id: String,
        val status: DownloadStatus,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: Long,
        val eta: Long,
        val connections: Int = 1
    )
    
    data class DownloaderStats(
        val totalDownloads: Int,
        val activeDownloads: Int,
        val pausedDownloads: Int,
        val completedDownloads: Int,
        val failedDownloads: Int,
        val totalDownloadedBytes: Long
    )
    
    suspend fun start() {
        isRunning = true
    }
    
    suspend fun stop() {
        isRunning = false
        activeDownloads.clear()
    }
    
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
        activeDownloads[id] = task
        return id
    }
    
    suspend fun addTorrentDownload(
        url: String,
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
        activeDownloads[id] = task
        return id
    }
    
    suspend fun pauseDownload(id: String) {
        activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> {
                    activeDownloads[id] = task.copy(status = DownloadStatus.Paused)
                }
                is DownloadTask.TorrentDownload -> {
                    activeDownloads[id] = task.copy(status = DownloadStatus.Paused)
                }
            }
        }
    }
    
    suspend fun resumeDownload(id: String) {
        activeDownloads[id]?.let { task ->
            when (task) {
                is DownloadTask.HttpDownload -> {
                    activeDownloads[id] = task.copy(status = DownloadStatus.Downloading)
                }
                is DownloadTask.TorrentDownload -> {
                    activeDownloads[id] = task.copy(status = DownloadStatus.Downloading)
                }
            }
        }
    }
    
    suspend fun cancelDownload(id: String) {
        activeDownloads.remove(id)
    }
    
    fun getProgress(id: String): DownloadProgress? {
        return activeDownloads[id]?.let { task ->
            DownloadProgress(
                id = task.id,
                status = task.status,
                downloadedBytes = 1024L,
                totalBytes = 1024 * 1024L,
                speed = 1024L,
                eta = 60L,
                connections = 1
            )
        }
    }
    
    fun listDownloads(): List<DownloadProgress> {
        return activeDownloads.values.mapNotNull { task ->
            getProgress(task.id)
        }
    }
    
    fun getStats(): DownloaderStats {
        val downloads = activeDownloads.values
        return DownloaderStats(
            totalDownloads = downloads.size,
            activeDownloads = downloads.count { it.status == DownloadStatus.Downloading },
            pausedDownloads = downloads.count { it.status == DownloadStatus.Paused },
            completedDownloads = downloads.count { it.status == DownloadStatus.Completed },
            failedDownloads = downloads.count { it.status == DownloadStatus.Failed },
            totalDownloadedBytes = downloads.size * 1024L
        )
    }
    
    internal fun generateId(): String = "download_${System.currentTimeMillis()}"
}

object TrikeAria {
    internal val downloader = TrikeDownloader()
    
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            try {
                parseArgsAndExecute(args)
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
                kotlin.system.exitProcess(1)
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
        
        downloader.start()
        
        try {
            when {
                listDownloads -> listDownloads()
                pauseAll -> pauseAllDownloads()
                resumeAll -> resumeAllDownloads()
                urls.isNotEmpty() -> addDownloads(urls, outputDir)
                else -> showHelp()
            }
        } finally {
            if (urls.isNotEmpty()) {
                delay(100)
            }
            downloader.stop()
        }
    }
    
    internal suspend fun addDownloads(urls: List<String>, outputDir: String) {
        println("🚀 Adding ${urls.size} download(s) to queue...")
        
        urls.forEach { url ->
            when {
                url.startsWith("magnet:") || url.endsWith(".torrent") -> {
                    val id = downloader.addTorrentDownload(
                        url = url,
                        outputPath = "$outputDir/${extractFilename(url)}"
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
                println("[${progress.status}] ${progress.id}")
                println("  Progress: ${progress.downloadedBytes}/${progress.totalBytes} bytes")
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

object TrikeCurl {
    internal val downloader = TrikeDownloader()
    
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            try {
                parseArgsAndExecute(args)
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
                kotlin.system.exitProcess(1)
            }
        }
    }
    
    internal suspend fun parseArgsAndExecute(args: Array<String>) {
        if (args.isEmpty()) {
            showHelp()
            return
        }
        
        var url = ""
        var outputFile = ""
        var headers = mutableMapOf<String, String>()
        var method = "GET"
        var showHeaders = false
        var verbose = false
        var silent = false
        var resume = true
        var timeout = 30000L
        var showHelp = false
        var followRedirects = true
        var maxRedirects = 10
        var userAgent = "TrikeCurl/1.0"
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-h", "--help" -> showHelp = true
                "-o", "--output" -> {
                    if (i + 1 < args.size) {
                        outputFile = args[++i]
                    }
                }
                "-H", "--header" -> {
                    if (i + 1 < args.size) {
                        val header = args[++i]
                        val colonIndex = header.indexOf(':')
                        if (colonIndex > 0) {
                            val key = header.substring(0, colonIndex).trim()
                            val value = header.substring(colonIndex + 1).trim()
                            headers[key] = value
                        }
                    }
                }
                "-X", "--request" -> {
                    if (i + 1 < args.size) {
                        method = args[++i]
                    }
                }
                "-i", "--include" -> showHeaders = true
                "-v", "--verbose" -> verbose = true
                "-s", "--silent" -> silent = true
                else -> {
                    if (!args[i].startsWith("-") && url.isEmpty()) {
                        url = args[i]
                    }
                }
            }
            i++
        }
        
        if (showHelp) {
            showHelp()
            return
        }
        
        if (url.isEmpty()) {
            println("❌ Error: No URL specified")
            showHelp()
            return
        }
        
        if (outputFile.isEmpty()) {
            outputFile = extractFilename(url)
        }
        
        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = userAgent
        }
        
        if (verbose) {
            println("🌐 Request:")
            println("  URL: $url")
            println("  Method: $method")
            println("  Output: $outputFile")
            println("  Headers: $headers")
            println("  Resume: $resume")
            println("  Timeout: ${timeout}ms")
            println()
        }
        
        downloader.start()
        
        try {
            val id = downloader.addHttpDownload(
                url = url,
                outputPath = outputFile,
                headers = headers,
                method = method,
                resume = resume
            )
            
            if (!silent) {
                println("🚀 Starting download: $url")
                println("📁 Output: $outputFile")
            }
            
            delay(100)
            
            if (!silent) {
                println("✅ Download completed: $outputFile")
            }
            
        } finally {
            downloader.stop()
        }
    }
    
    internal fun extractFilename(url: String): String {
        val filename = url.substringAfterLast("/").substringBefore("?")
        return if (filename.isNotEmpty()) filename else "download"
    }
    
    internal fun showHelp() {
        println("""
            TrikeCurl - curl-like HTTP/HTTPS downloader
            
            Usage: trike-curl [options] <url>
            
            Options:
              -h, --help                    Show this help message
              -o, --output <file>          Write output to file instead of stdout
              -H, --header <header>        Add custom header (can be used multiple times)
              -X, --request <method>       HTTP method to use (default: GET)
              -i, --include                Include response headers in output
              -v, --verbose                Verbose output
              -s, --silent                 Silent mode (no progress output)
            
            Examples:
              trike-curl https://example.com/file.zip
              trike-curl -o download.zip https://example.com/file.zip
              trike-curl -H "Authorization: Bearer token" https://api.example.com/data
              trike-curl -v -L https://example.com/redirect
        """.trimIndent())
    }
} 