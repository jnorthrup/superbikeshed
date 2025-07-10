@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlinx.coroutines.*
import kotlin.system.exitProcess

/**
 * TrikeCurl - curl-like command-line interface for HTTP/HTTPS downloads
 * 
 * Usage: trike-curl [options] <url>
 * 
 * Key features:
 * - HTTP/HTTPS downloads with resume support
 * - Custom headers and authentication
 * - Progress reporting and verbose output
 * - Multiple output formats
 * - Request/response inspection
 */
object TrikeCurl {
    
    internal val downloader = TrikeDownloader(
        maxConcurrentDownloads = 1, // curl typically downloads one at a time
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
                "-C", "--continue-at" -> {
                    if (i + 1 < args.size) {
                        val offset = args[++i]
                        resume = offset != "-"
                    }
                }
                "--connect-timeout" -> {
                    if (i + 1 < args.size) {
                        timeout = args[++i].toLongOrNull() ?: 30000
                    }
                }
                "-L", "--location" -> followRedirects = true
                "--max-redirs" -> {
                    if (i + 1 < args.size) {
                        maxRedirects = args[++i].toIntOrNull() ?: 10
                    }
                }
                "-A", "--user-agent" -> {
                    if (i + 1 < args.size) {
                        userAgent = args[++i]
                    }
                }
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
        
        // Set default output file if not specified
        if (outputFile.isEmpty()) {
            outputFile = extractFilename(url)
        }
        
        // Add default headers
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
        
        // Start the downloader
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
            
            // Monitor progress
            var lastProgress = 0L
            while (true) {
                val progress = downloader.getProgress(id)
                if (progress == null) break
                
                when (progress.status) {
                    TrikeDownloader.DownloadStatus.Completed -> {
                        if (!silent) {
                            println("✅ Download completed: $outputFile")
                        }
                        break
                    }
                    TrikeDownloader.DownloadStatus.Failed -> {
                        println("❌ Download failed")
                        break
                    }
                    TrikeDownloader.DownloadStatus.Downloading -> {
                        if (verbose && progress.downloadedBytes != lastProgress) {
                            val percentage = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes)
                            } else 0
                            
                            val speed = when {
                                progress.speed > 1024 * 1024 -> "${progress.speed / (1024 * 1024)}MB/s"
                                progress.speed > 1024 -> "${progress.speed / 1024}KB/s"
                                else -> "${progress.speed}B/s"
                            }
                            
                            println("📊 Progress: $percentage% (${progress.downloadedBytes}/${progress.totalBytes}) - $speed")
                            lastProgress = progress.downloadedBytes
                        }
                    }
                    else -> {}
                }
                
                delay(1000)
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
              -C, --continue-at <offset>   Resume download from offset (- to disable)
              --connect-timeout <seconds>  Connection timeout in seconds
              -L, --location               Follow redirects
              --max-redirs <num>           Maximum number of redirects to follow
              -A, --user-agent <agent>     Set User-Agent string
            
            Examples:
              trike-curl https://example.com/file.zip
              trike-curl -o download.zip https://example.com/file.zip
              trike-curl -H "Authorization: Bearer token" https://api.example.com/data
              trike-curl -v -L https://example.com/redirect
              trike-curl -C - https://example.com/large-file.zip
        """.trimIndent())
    }
} 