package moneyfan.binance

import kotlinx.coroutines.*
import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * Simple demo for loading Binance archive data and running backtesting
 * This bypasses the broken TrikeShed dependencies and focuses on core functionality
 */
object BinanceBacktestDemo {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🔥 Binance Archive Backtesting Demo")
        println("=" .repeat(60))
        
        // Demo 1: Download and parse a sample Binance archive
        println("\n📊 Demo 1: Downloading sample Binance archive...")
        val sampleData = downloadSampleBinanceData()
        
        if (sampleData.isNotEmpty()) {
            println("✅ Downloaded ${sampleData.size} klines")
            println("📈 First kline: ${formatKline(sampleData.first())}")
            println("📉 Last kline: ${formatKline(sampleData.last())}")
            
            // Demo 2: Save as CSV for inspection
            println("\n📊 Demo 2: Saving as CSV...")
            saveToCsv(sampleData, "sample_binance_data.csv")
            println("✅ Saved to sample_binance_data.csv")
            
            // Demo 3: Parse CSV and filter by date range
            println("\n📊 Demo 3: Parsing CSV with date filtering...")
            val filteredData = parseCsvWithDateRange("sample_binance_data.csv", 
                startTime = 1640995200000L, // 2022-01-01
                endTime = 1643673600000L    // 2022-02-01
            )
            println("✅ Filtered to ${filteredData.size} klines in date range")
            
            // Demo 4: Run simple backtesting
            println("\n📊 Demo 4: Running backtesting...")
            val backtestResult = runSimpleBacktest(filteredData, 10000.0)
            println("💰 Initial capital: $10,000")
            println("💰 Final capital: $${String.format("%.2f", backtestResult.finalCapital)}")
            println("📊 Total return: ${String.format("%.2f", backtestResult.totalReturn * 100)}%")
            println("🔄 Number of trades: ${backtestResult.trades.size}")
            
            // Demo 5: Calculate basic technical indicators
            println("\n📊 Demo 5: Technical indicators...")
            val indicators = calculateTechnicalIndicators(filteredData)
            println("📈 SMA(20): ${String.format("%.2f", indicators.sma20)}")
            println("📉 RSI(14): ${String.format("%.2f", indicators.rsi14)}")
            println("📊 Volatility: ${String.format("%.4f", indicators.volatility)}")
            
        } else {
            println("❌ Failed to download sample data")
        }
        
        println("\n✅ Demo complete!")
    }
    
    data class Kline(
        val openTime: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
        val closeTime: Long,
        val quoteAssetVolume: Double,
        val numberOfTrades: Long,
        val takerBuyBaseAssetVolume: Double,
        val takerBuyQuoteAssetVolume: Double
    )
    
    data class BacktestResult(
        val finalCapital: Double,
        val totalReturn: Double,
        val trades: List<Trade>
    )
    
    data class Trade(
        val timestamp: Long,
        val action: String,
        val price: Double,
        val quantity: Double
    )
    
    data class TechnicalIndicators(
        val sma20: Double,
        val rsi14: Double,
        val volatility: Double
    )
    
    private suspend fun downloadSampleBinanceData(): List<Kline> = withContext(Dispatchers.IO) {
        try {
            // Download a sample DOGEUSDT 1h kline file from Binance Data Vision
            val url = "https://data.binance.vision/data/spot/monthly/klines/DOGEUSDT/1h/DOGEUSDT-1h-2022-01.zip"
            val tempFile = File.createTempFile("binance_sample", ".zip")
            
            println("Downloading from: $url")
            URL(url).openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Extract and parse the ZIP file
            val klines = mutableListOf<Kline>()
            ZipInputStream(FileInputStream(tempFile)).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".csv")) {
                        zipStream.bufferedReader().use { reader ->
                            // Skip header
                            reader.readLine()
                            
                            reader.forEachLine { line ->
                                parseKlineCsvLine(line)?.let { klines.add(it) }
                            }
                        }
                    }
                    entry = zipStream.nextEntry
                }
            }
            
            tempFile.delete()
            klines
            
        } catch (e: Exception) {
            println("Error downloading data: ${e.message}")
            emptyList()
        }
    }
    
    private fun parseKlineCsvLine(line: String): Kline? {
        val parts = line.split(",")
        if (parts.size < 11) return null
        
        return try {
            Kline(
                openTime = parts[0].toLong(),
                open = parts[1].toDouble(),
                high = parts[2].toDouble(),
                low = parts[3].toDouble(),
                close = parts[4].toDouble(),
                volume = parts[5].toDouble(),
                closeTime = parts[6].toLong(),
                quoteAssetVolume = parts[7].toDouble(),
                numberOfTrades = parts[8].toLong(),
                takerBuyBaseAssetVolume = parts[9].toDouble(),
                takerBuyQuoteAssetVolume = parts[10].toDouble()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun saveToCsv(klines: List<Kline>, filename: String) {
        File(filename).bufferedWriter().use { writer ->
            // Write header
            writer.write("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume\n")
            
            // Write data
            klines.forEach { kline ->
                writer.write("${kline.openTime},${kline.open},${kline.high},${kline.low},${kline.close},${kline.volume},${kline.closeTime},${kline.quoteAssetVolume},${kline.numberOfTrades},${kline.takerBuyBaseAssetVolume},${kline.takerBuyQuoteAssetVolume}\n")
            }
        }
    }
    
    private fun parseCsvWithDateRange(filename: String, startTime: Long, endTime: Long): List<Kline> {
        val klines = mutableListOf<Kline>()
        
        File(filename).bufferedReader().use { reader ->
            // Skip header
            reader.readLine()
            
            reader.forEachLine { line ->
                parseKlineCsvLine(line)?.let { kline ->
                    if (kline.openTime >= startTime && kline.openTime <= endTime) {
                        klines.add(kline)
                    }
                }
            }
        }
        
        return klines
    }
    
    private fun runSimpleBacktest(klines: List<Kline>, initialCapital: Double): BacktestResult {
        var capital = initialCapital
        var position = 0.0
        val trades = mutableListOf<Trade>()
        
        // Simple moving average crossover strategy
        val shortPeriod = 5
        val longPeriod = 20
        
        for (i in longPeriod until klines.size) {
            val currentPrice = klines[i].close
            val currentTime = klines[i].openTime
            
            // Calculate moving averages
            val shortSma = klines.subList(i - shortPeriod + 1, i + 1).map { it.close }.average()
            val longSma = klines.subList(i - longPeriod + 1, i + 1).map { it.close }.average()
            
            // Trading logic: buy when short SMA crosses above long SMA, sell when it crosses below
            val prevShortSma = klines.subList(i - shortPeriod, i).map { it.close }.average()
            val prevLongSma = klines.subList(i - longPeriod, i).map { it.close }.average()
            
            val shortAboveLong = shortSma > longSma
            val prevShortAboveLong = prevShortSma > prevLongSma
            
            when {
                // Buy signal: short SMA crosses above long SMA
                shortAboveLong && !prevShortAboveLong && position == 0.0 -> {
                    position = capital / currentPrice
                    capital = 0.0
                    trades.add(Trade(currentTime, "BUY", currentPrice, position))
                }
                // Sell signal: short SMA crosses below long SMA
                !shortAboveLong && prevShortAboveLong && position > 0.0 -> {
                    capital = position * currentPrice
                    trades.add(Trade(currentTime, "SELL", currentPrice, position))
                    position = 0.0
                }
            }
        }
        
        // Close any remaining position
        if (position > 0.0) {
            val finalPrice = klines.last().close
            capital = position * finalPrice
            trades.add(Trade(klines.last().openTime, "SELL", finalPrice, position))
        }
        
        val totalReturn = (capital - initialCapital) / initialCapital
        
        return BacktestResult(capital, totalReturn, trades)
    }
    
    private fun calculateTechnicalIndicators(klines: List<Kline>): TechnicalIndicators {
        if (klines.size < 20) {
            return TechnicalIndicators(0.0, 50.0, 0.0)
        }
        
        val prices = klines.map { it.close }
        
        // Calculate SMA(20)
        val sma20 = prices.takeLast(20).average()
        
        // Calculate RSI(14)
        val rsi14 = calculateRSI(prices, 14)
        
        // Calculate volatility (standard deviation of returns)
        val returns = prices.zipWithNext { a, b -> ln(b / a) }
        val volatility = sqrt(returns.map { (it - returns.average()) * (it - returns.average()) }.average())
        
        return TechnicalIndicators(sma20, rsi14, volatility)
    }
    
    private fun calculateRSI(prices: List<Double>, period: Int): Double {
        if (prices.size < period + 1) return 50.0
        
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        
        for (i in 1 until prices.size) {
            val change = prices[i] - prices[i - 1]
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)
        }
        
        val avgGain = gains.takeLast(period).average()
        val avgLoss = losses.takeLast(period).average()
        
        return if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }
    
    private fun formatKline(kline: Kline): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val openTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.openTime), ZoneOffset.UTC)
            .format(formatter)
        
        return "$openTime | O:${String.format("%.4f", kline.open)} H:${String.format("%.4f", kline.high)} L:${String.format("%.4f", kline.low)} C:${String.format("%.4f", kline.close)} V:${String.format("%.0f", kline.volume)}"
    }
} 