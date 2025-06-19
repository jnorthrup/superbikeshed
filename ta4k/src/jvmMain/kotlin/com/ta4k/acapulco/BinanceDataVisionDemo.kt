package borg.trikeshed.acapulco

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Series
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Demo for the revived fetchklines functionality from Binance Data Vision archives
 * This demonstrates how to fetch historical kline data and integrate with the attention system
 */
object BinanceDataVisionDemo {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🔥 Reviving fetchklines from Binance Data Vision Archives")
        println("=" .repeat(60))
        
        val reader = BinanceDataVisionReader()
        
        // Demo 1: Fetch recent klines for BTCUSDT
        println("\n📊 Demo 1: Fetching recent BTCUSDT klines...")
        try {
            val btcKlines = reader.fetchKlines(
                symbol = "BTCUSDT",
                interval = "1m",
                startDate = "2024-01",
                endDate = "2024-02",
                cacheDir = "~/mpdata/cache"
            )
            
            println("✅ Fetched ${btcKlines.size} BTCUSDT klines")
            if (btcKlines.size > 0) {
                val firstKline = btcKlines[0]
                val lastKline = btcKlines[btcKlines.size - 1]
                
                println("📈 First kline: ${formatKline(firstKline)}")
                println("📉 Last kline: ${formatKline(lastKline)}")
                println("💰 Price range: ${firstKline.lowPrice} - ${lastKline.highPrice}")
            }
        } catch (e: Exception) {
            println("❌ Error fetching BTCUSDT klines: ${e.message}")
        }
        
        // Demo 2: Fetch daily klines for recent period
        println("\n📊 Demo 2: Fetching daily ETHUSDT klines...")
        try {
            val ethKlines = reader.fetchDailyKlines(
                symbol = "ETHUSDT",
                interval = "1m",
                days = 7,
                cacheDir = "~/mpdata/cache"
            )
            
            println("✅ Fetched ${ethKlines.size} ETHUSDT daily klines")
            if (ethKlines.size > 0) {
                val avgVolume = ethKlines.play.map { it.volume.toDouble() }.average()
                println("📊 Average volume: ${String.format("%.2f", avgVolume)}")
            }
        } catch (e: Exception) {
            println("❌ Error fetching ETHUSDT klines: ${e.message}")
        }
        
        // Demo 3: Fetch specific month
        println("\n📊 Demo 3: Fetching specific month (DOGEUSDT Jan 2024)...")
        try {
            val dogeKlines = reader.fetchMonthKlines(
                symbol = "DOGEUSDT",
                interval = "1m",
                yearMonth = "2024-01",
                cacheDir = "~/mpdata/cache"
            )
            
            println("✅ Fetched ${dogeKlines.size} DOGEUSDT klines for January 2024")
            if (dogeKlines.size > 0) {
                val totalTrades = dogeKlines.play.sumOf { it.numberOfTrades.toLong() }
                println("🔄 Total trades: $totalTrades")
            }
        } catch (e: Exception) {
            println("❌ Error fetching DOGEUSDT klines: ${e.message}")
        }
        
        // Demo 4: Integration with attention system
        println("\n🎯 Demo 4: Integration with attention system...")
        try {
            val attentionKlines = reader.fetchKlines(
                symbol = "ADAUSDT",
                interval = "1m",
                startDate = "2024-01",
                endDate = "2024-02",
                cacheDir = "~/mpdata/cache"
            )
            
            if (attentionKlines.size > 0) {
                // Calculate attention metrics
                val attentionMetrics = calculateAttentionMetrics(attentionKlines)
                println("🎯 Attention metrics for ADAUSDT:")
                println("   📊 Volume spike: ${String.format("%.2f", attentionMetrics.volumeSpike)}x")
                println("   📈 Volatility: ${String.format("%.4f", attentionMetrics.volatility)}")
                println("   🎯 Attention score: ${String.format("%.2f", attentionMetrics.attentionScore)}")
                
                // Filter high-attention periods
                val highAttentionKlines = filterHighAttentionPeriods(attentionKlines, 0.7)
                println("   🔥 High-attention periods: ${highAttentionKlines.size} klines")
            }
        } catch (e: Exception) {
            println("❌ Error in attention system integration: ${e.message}")
        }
        
        println("\n✅ Fetchklines revival complete!")
        println("📁 Data cached in ~/mpdata/cache/klines/")
        println("🔄 Ready for attention system integration")
    }
    
    private fun formatKline(kline: Kline): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val openTime = java.time.Instant.ofEpochMilli(kline.openTimeMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .format(formatter)
        
        return "$openTime | O:${kline.openPrice} H:${kline.highPrice} L:${kline.lowPrice} C:${kline.closePrice} V:${kline.volume}"
    }
    
    private data class AttentionMetrics(
        val volumeSpike: Double,
        val volatility: Double,
        val attentionScore: Double
    )
    
    private fun calculateAttentionMetrics(klines: Series<Kline>): AttentionMetrics {
        if (klines.size < 50) {
            return AttentionMetrics(1.0, 0.0, 0.0)
        }
        
        val klineList = klines.play
        
        // Volume spike analysis (recent 10 vs baseline 50)
        val recentVolumes = klineList.takeLast(10).map { it.volume.toDouble() }
        val baselineVolumes = klineList.take(klineList.size - 10).map { it.volume.toDouble() }
        
        val recentAvgVolume = recentVolumes.average()
        val baselineAvgVolume = baselineVolumes.average()
        val volumeSpike = if (baselineAvgVolume > 0) recentAvgVolume / baselineAvgVolume else 1.0
        
        // Volatility analysis
        val prices = klineList.map { it.closePrice.toDouble() }
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
    
    private fun filterHighAttentionPeriods(klines: Series<Kline>, threshold: Double): Series<Kline> {
        val klineList = klines.play
        val highAttentionKlines = mutableListOf<Kline>()
        
        // Use sliding window to identify high-attention periods
        for (i in 10 until klineList.size) {
            val window = klineList.subList(i - 10, i + 1)
            val metrics = calculateAttentionMetrics(Series.of(window.size) { j -> window[j] })
            
            if (metrics.attentionScore >= threshold) {
                highAttentionKlines.add(klineList[i])
            }
        }
        
        return Series.of(highAttentionKlines.size) { i -> highAttentionKlines[i] }
    }
} 