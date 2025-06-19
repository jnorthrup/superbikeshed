#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

import kotlinx.coroutines.runBlocking
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Simple test script for the revived fetchklines functionality
 * This demonstrates how to fetch klines from Binance Data Vision archives
 */

// Simple Kline data class for testing
data class SimpleKline(
    val openTimeMillis: Long,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val closePrice: BigDecimal,
    val volume: BigDecimal,
    val closeTimeMillis: Long,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: BigDecimal,
    val takerBuyQuoteAssetVolume: BigDecimal
)

// Simple fetchklines implementation
class SimpleFetchKlines {
    private val baseUrl = "https://data.binance.vision/data/spot"
    private val cacheDir = File(System.getProperty("user.home"), "mpdata/cache")
    
    init {
        cacheDir.mkdirs()
    }
    
    suspend fun fetchMonthKlines(
        symbol: String,
        interval: String,
        yearMonth: String
    ): List<SimpleKline> {
        val symbolDir = File(cacheDir, "klines/$interval/$symbol")
        symbolDir.mkdirs()
        
        val fileName = "${symbol}-${interval}-${yearMonth}.zip"
        val zipFile = File(symbolDir, fileName)
        val csvFile = File(symbolDir, "${symbol}-${interval}-${yearMonth}.csv")
        
        // Check if we already have the CSV file
        if (csvFile.exists()) {
            return readCsvFile(csvFile)
        }
        
        // Download zip file if not exists
        if (!zipFile.exists()) {
            val url = "$baseUrl/monthly/klines/$symbol/$interval/$fileName"
            downloadFile(url, zipFile.absolutePath)
        }
        
        // Extract and process
        if (zipFile.exists()) {
            val klines = extractZipFile(zipFile)
            saveToCsv(klines, csvFile)
            return klines
        } else {
            return emptyList()
        }
    }
    
    private fun readCsvFile(file: File): List<SimpleKline> {
        val klines = mutableListOf<SimpleKline>()
        
        file.bufferedReader().use { reader ->
            // Skip header
            reader.readLine()
            
            reader.forEachLine { line ->
                parseCsvLine(line)?.let { klines.add(it) }
            }
        }
        
        return klines
    }
    
    private fun extractZipFile(zipFile: File): List<SimpleKline> {
        val klines = mutableListOf<SimpleKline>()
        
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".csv")) {
                    zipStream.bufferedReader().use { reader ->
                        // Skip header
                        reader.readLine()
                        
                        reader.forEachLine { line ->
                            parseCsvLine(line)?.let { klines.add(it) }
                        }
                    }
                }
                entry = zipStream.nextEntry
            }
        }
        
        return klines
    }
    
    private fun parseCsvLine(line: String): SimpleKline? {
        val parts = line.split(",")
        if (parts.size < 11) return null
        
        return try {
            SimpleKline(
                openTimeMillis = parts[0].toLong(),
                openPrice = BigDecimal(parts[1]),
                highPrice = BigDecimal(parts[2]),
                lowPrice = BigDecimal(parts[3]),
                closePrice = BigDecimal(parts[4]),
                volume = BigDecimal(parts[5]),
                closeTimeMillis = parts[6].toLong(),
                quoteAssetVolume = BigDecimal(parts[7]),
                numberOfTrades = parts[8].toInt(),
                takerBuyBaseAssetVolume = BigDecimal(parts[9]),
                takerBuyQuoteAssetVolume = BigDecimal(parts[10])
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun downloadFile(url: String, filePath: String) {
        try {
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            connection.getInputStream().use { input ->
                File(filePath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("✅ Downloaded: $url")
        } catch (e: Exception) {
            println("❌ Failed to download $url: ${e.message}")
        }
    }
    
    private fun saveToCsv(klines: List<SimpleKline>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.write("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore\n")
            
            klines.forEach { kline ->
                writer.write("${kline.openTimeMillis},${kline.openPrice},${kline.highPrice},${kline.lowPrice},${kline.closePrice},${kline.volume},${kline.closeTimeMillis},${kline.quoteAssetVolume},${kline.numberOfTrades},${kline.takerBuyBaseAssetVolume},${kline.takerBuyQuoteAssetVolume},\n")
            }
        }
    }
    
    fun calculateAttentionMetrics(klines: List<SimpleKline>): AttentionMetrics {
        if (klines.size < 20) {
            return AttentionMetrics(1.0, 0.0, 0.0)
        }
        
        // Volume spike analysis
        val recentVolumes = klines.takeLast(10).map { it.volume.toDouble() }
        val baselineVolumes = klines.take(klines.size - 10).map { it.volume.toDouble() }
        
        val recentAvgVolume = recentVolumes.average()
        val baselineAvgVolume = baselineVolumes.average()
        val volumeSpike = if (baselineAvgVolume > 0) recentAvgVolume / baselineAvgVolume else 1.0
        
        // Volatility analysis
        val prices = klines.map { it.closePrice.toDouble() }
        val recentPrices = prices.takeLast(10)
        val baselinePrices = prices.take(prices.size - 10)
        
        val recentVolatility = calculateVolatility(recentPrices)
        val baselineVolatility = calculateVolatility(baselinePrices)
        val volatilitySpike = if (baselineVolatility > 0) recentVolatility / baselineVolatility else 1.0
        
        // Combined attention score
        val attentionScore = (
            volumeSpike * 0.35 +
            volatilitySpike * 0.25 +
            (recentVolatility * 100) * 0.25 +
            0.15
        ).coerceIn(0.0, 1.0)
        
        return AttentionMetrics(volumeSpike, recentVolatility, attentionScore)
    }
    
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { a, b -> kotlin.math.ln(b / a) }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        
        return kotlin.math.sqrt(variance)
    }
}

data class AttentionMetrics(
    val volumeSpike: Double,
    val volatility: Double,
    val attentionScore: Double
)

// Main test function
fun main() = runBlocking {
    println("🔥 Testing revived fetchklines functionality")
    println("=" .repeat(50))
    
    val fetcher = SimpleFetchKlines()
    
    // Test fetching BTCUSDT klines for January 2024
    println("\n📊 Fetching BTCUSDT klines for January 2024...")
    try {
        val btcKlines = fetcher.fetchMonthKlines("BTCUSDT", "1m", "2024-01")
        
        if (btcKlines.isNotEmpty()) {
            println("✅ Fetched ${btcKlines.size} BTCUSDT klines")
            
            val firstKline = btcKlines.first()
            val lastKline = btcKlines.last()
            
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val firstTime = Instant.ofEpochMilli(firstKline.openTimeMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .format(formatter)
            val lastTime = Instant.ofEpochMilli(lastKline.openTimeMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .format(formatter)
            
            println("📈 First kline: $firstTime | O:${firstKline.openPrice} H:${firstKline.highPrice} L:${firstKline.lowPrice} C:${firstKline.closePrice}")
            println("📉 Last kline: $lastTime | O:${lastKline.openPrice} H:${lastKline.highPrice} L:${lastKline.lowPrice} C:${lastKline.closePrice}")
            
            // Calculate attention metrics
            val attentionMetrics = fetcher.calculateAttentionMetrics(btcKlines)
            println("\n🎯 Attention Analysis:")
            println("   📊 Volume spike: ${String.format("%.2f", attentionMetrics.volumeSpike)}x")
            println("   📈 Volatility: ${String.format("%.4f", attentionMetrics.volatility)}")
            println("   🎯 Attention score: ${String.format("%.2f", attentionMetrics.attentionScore)}")
            
            if (attentionMetrics.attentionScore > 0.6) {
                println("   🔥 HIGH ATTENTION DETECTED!")
            } else {
                println("   😴 Normal activity level")
            }
        } else {
            println("❌ No klines fetched")
        }
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
    }
    
    println("\n✅ Test complete!")
    println("📁 Data cached in: ${fetcher.cacheDir}")
} 