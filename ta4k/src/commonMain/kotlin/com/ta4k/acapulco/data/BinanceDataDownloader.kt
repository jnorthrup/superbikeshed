package com.ta4k.acapulco.data

import borg.trikeshed.lib.*
import com.ta4k.acapulco.model.Kline
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Native Kotlin implementation of fetchklines functionality
 * Replaces the bash script with pure Kotlin code
 */
class BinanceDataDownloader(
    private val cacheDir: String = System.getProperty("user.home") + "/mpdata/cache",
    private val importDir: String = System.getProperty("user.home") + "/mpdata/import"
) {
    
    companion object {
        private const val BINANCE_BASE_URL = "https://data.binance.vision/data/spot"
        private const val MAX_CONCURRENT_DOWNLOADS = 5
    }
    
    /**
     * Main entry point - equivalent to the bash script functionality
     */
    suspend fun fetchKlines(
        baseAsset: String = "BTC",
        quoteAsset: String = "USDT", 
        timeUnit: String = "1m"
    ): Result<String> = runCatching {
        val symbol = "$baseAsset$quoteAsset"
        val cachePath = "$cacheDir/klines/$timeUnit/$baseAsset/$quoteAsset"
        val targetPath = "$importDir/klines/$timeUnit/$baseAsset/$quoteAsset"
        
        // Create directories
        File(cachePath).mkdirs()
        File(targetPath).mkdirs()
        
        // Generate URLs for download
        val urls = generateDownloadUrls(symbol, timeUnit)
        
        // Download files
        downloadFiles(urls, cachePath)
        
        // Process and combine files
        val finalFile = processAndCombineFiles(cachePath, targetPath, symbol, timeUnit)
        
        // Cleanup
        cleanupCache(cachePath)
        
        finalFile
    }
    
    private fun generateDownloadUrls(symbol: String, timeUnit: String): List<String> {
        val urls = mutableListOf<String>()
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val currentMonth = "${currentDate.year}-${currentDate.monthNumber.toString().padStart(2, '0')}"
        val cleanMonth = "${currentDate.year - 1}-${currentDate.monthNumber.toString().padStart(2, '0')}"
        
        // Historical monthly data (2017-2022)
        for (year in 2017..2022) {
            for (month in 1..12) {
                val monthStr = month.toString().padStart(2, '0')
                urls.add("$BINANCE_BASE_URL/monthly/klines/$symbol/$timeUnit/$symbol-$timeUnit-$year-$monthStr.zip")
                urls.add("$BINANCE_BASE_URL/monthly/klines/$symbol/$timeUnit/$symbol-$timeUnit-$year-$monthStr.zip.CHECKSUM")
            }
        }
        
        // Current month daily data
        for (day in 1..31) {
            val dayStr = day.toString().padStart(2, '0')
            urls.add("$BINANCE_BASE_URL/daily/klines/$symbol/$timeUnit/$symbol-$timeUnit-$currentMonth-$dayStr.zip")
            urls.add("$BINANCE_BASE_URL/daily/klines/$symbol/$timeUnit/$symbol-$timeUnit-$currentMonth-$dayStr.zip.CHECKSUM")
        }
        
        return urls
    }
    
    private suspend fun downloadFiles(urls: List<String>, cachePath: String) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_DOWNLOADS)
        
        val downloadJobs = urls.map { url ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.withPermit {
                    downloadFile(url, cachePath)
                }
            }
        }
        
        downloadJobs.awaitAll()
    }
    
    private suspend fun downloadFile(url: String, cachePath: String) {
        try {
            val fileName = url.substringAfterLast('/')
            val file = File(cachePath, fileName)
            
            // Skip if file already exists and is recent
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60 * 1000) {
                return
            }
            
            println("Downloading: $fileName")
            
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            connection.getInputStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            println("Downloaded: $fileName")
        } catch (e: Exception) {
            println("Failed to download $url: ${e.message}")
        }
    }
    
    private suspend fun processAndCombineFiles(
        cachePath: String, 
        targetPath: String, 
        symbol: String, 
        timeUnit: String
    ): String {
        val tempDir = createTempDir("fetchklines")
        val finalFile = "$targetPath/final-$symbol-$timeUnit.csv"
        
        try {
            // Extract all zip files
            extractZipFiles(cachePath, tempDir)
            
            // Combine CSV files
            combineCsvFiles(tempDir, finalFile, symbol, timeUnit)
            
        } finally {
            tempDir.deleteRecursively()
        }
        
        return finalFile
    }
    
    private fun extractZipFiles(cachePath: String, tempDir: File) {
        File(cachePath).listFiles { file -> file.extension == "zip" }?.forEach { zipFile ->
            try {
                ZipInputStream(zipFile.inputStream()).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".csv")) {
                            val outputFile = File(tempDir, entry.name)
                            outputFile.outputStream().use { output ->
                                zipStream.copyTo(output)
                            }
                        }
                        entry = zipStream.nextEntry
                    }
                }
            } catch (e: Exception) {
                println("Failed to extract ${zipFile.name}: ${e.message}")
            }
        }
    }
    
    private fun combineCsvFiles(tempDir: File, finalFile: String, symbol: String, timeUnit: String) {
        val csvFiles = tempDir.listFiles { file -> 
            file.extension == "csv" && file.name.startsWith("$symbol-$timeUnit")
        } ?: emptyArray()
        
        if (csvFiles.isEmpty()) {
            throw IllegalStateException("No CSV files found to combine")
        }
        
        val header = "Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore"
        
        File(finalFile).outputStream().use { output ->
            output.write("$header\n".toByteArray())
            
            csvFiles.forEach { csvFile ->
                csvFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        
        println("Combined ${csvFiles.size} files into: $finalFile")
    }
    
    private fun cleanupCache(cachePath: String) {
        // Move old files to /tmp (equivalent to the bash script behavior)
        val oldMonth = Clock.System.now().toLocalDateTime(TimeZone.UTC).let { 
            "${it.year - 1}-${it.monthNumber.toString().padStart(2, '0')}"
        }
        
        File(cachePath).listFiles { file -> 
            file.name.contains(oldMonth) && (file.extension == "zip" || file.extension == "CHECKSUM")
        }?.forEach { file ->
            val tempFile = File("/tmp", file.name)
            file.renameTo(tempFile)
        }
    }
}

/**
 * Extension function to use semaphore with automatic release
 */
private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermit(block: suspend () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
} 