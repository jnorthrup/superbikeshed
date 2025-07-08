package moneyfan.backtesting

import moneyfan.ta4k.*
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * DOGE Backtest Runner for 2020-2022 period
 * Tests Carlos RSI2 vs Kraken Skimmer strategies on Elon pump events
 */
class DogeBacktestRunner {
    
    data class BacktestPeriod(
        val name: String,
        val startDate: String,
        val endDate: String,
        val expectedEvents: List<String>
    )
    
    data class BacktestResult(
        val period: BacktestPeriod,
        val carlosPerformance: StrategyPerformance,
        val skimmerPerformance: StrategyPerformance,
        val attentionEvents: List<AttentionEvent>,
        val tradingEvents: List<TradingEvent>
    )
    
    data class StrategyPerformance(
        val strategyName: String,
        val totalTrades: Int,
        val winningTrades: Int,
        val losingTrades: Int,
        val totalReturn: Double,
        val maxDrawdown: Double,
        val sharpeRatio: Double,
        val averageHoldTime: Double
    )
    
    data class AttentionEvent(
        val timestamp: Long,
        val symbol: String,
        val attentionScore: Double,
        val volumeSpike: Double,
        val volatility: Double,
        val eventType: String
    )
    
    data class TradingEvent(
        val timestamp: Long,
        val symbol: String,
        val strategy: String,
        val action: String,
        val price: Double,
        val quantity: Double,
        val reason: String
    )
    
    internal val backtestPeriods = listOf(
        BacktestPeriod(
            name = "Pre-Pump 2020",
            startDate = "2020-01-01",
            endDate = "2020-12-31", 
            expectedEvents = listOf("TikTok viral", "Reddit mentions", "Small influencer pumps")
        ),
        BacktestPeriod(
            name = "Elon Era 2021",
            startDate = "2021-01-01",
            endDate = "2021-12-31",
            expectedEvents = listOf("Elon tweets", "Tesla acceptance", "SNL appearance", "Major pumps")
        ),
        BacktestPeriod(
            name = "Post-Peak 2022",
            startDate = "2022-01-01", 
            endDate = "2022-12-31",
            expectedEvents = listOf("Bear market", "Twitter acquisition", "Declining influence")
        )
    )
    
    fun runFullBacktest(): List<BacktestResult> {
        println("Starting DOGE Backtest: 2020-2022")
        println("Testing Carlos RSI2 vs Kraken Skimmer strategies")
        println("Symbol: DOGEUSDT")
        println("==========================================")
        
        return backtestPeriods.map { period ->
            println("\nBacktesting period: ${period.name}")
            runPeriodBacktest(period)
        }
    }
    
    internal fun runPeriodBacktest(period: BacktestPeriod): BacktestResult {
        // Load historical DOGE data for the period
        val klines = loadHistoricalDogeData(period)
        println("Loaded ${klines.size} historical klines for ${period.name}")
        
        // Initialize both strategies
        val carlosStrategy = CarlosRSI2Strategy()
        val krakenSkimmer = KrakenSkimmerStrategy()
        val attentionSystem = createBacktestAttentionSystem()
        
        // Run attention analysis
        val attentionEvents = mutableListOf<AttentionEvent>()
        val tradingEvents = mutableListOf<TradingEvent>()
        
        // Simulate trading with both strategies
        val carlosPerformance = simulateStrategy(klines, carlosStrategy, "Carlos RSI2", tradingEvents, attentionEvents)
        val skimmerPerformance = simulateStrategy(klines, krakenSkimmer, "Kraken Skimmer", tradingEvents, attentionEvents)
        
        return BacktestResult(
            period = period,
            carlosPerformance = carlosPerformance,
            skimmerPerformance = skimmerPerformance,
            attentionEvents = attentionEvents,
            tradingEvents = tradingEvents
        )
    }
    
    internal fun loadHistoricalDogeData(period: BacktestPeriod): List<Kline> {
        TODO("Load actual historical DOGE data from Binance Data Vision for ${period.startDate} to ${period.endDate}")
    }
    
    
    internal fun simulateStrategy(
        klines: List<Kline>, 
        strategy: Any, 
        strategyName: String,
        tradingEvents: MutableList<TradingEvent>,
        attentionEvents: MutableList<AttentionEvent>
    ): StrategyPerformance {
        
        var balance = 10000.0 // Starting with $10k
        var dogeHolding = 0.0
        var tradeCount = 0
        var winningTrades = 0
        var losingTrades = 0
        val trades = mutableListOf<Double>()
        
        klines.windowed(50, 1).forEach { window ->
            if (window.size < 50) return@forEach
            
            val currentKline = window.last()
            val currentPrice = currentKline.close
            
            // Calculate attention for this window
            val attentionScore = calculateBacktestAttention(window)
            if (attentionScore > 0.7) {
                attentionEvents.add(AttentionEvent(
                    timestamp = currentKline.timestamp,
                    symbol = "DOGEUSDT",
                    attentionScore = attentionScore,
                    volumeSpike = window.takeLast(5).map { it.volume }.average() / window.take(45).map { it.volume }.average(),
                    volatility = calculateWindowVolatility(window),
                    eventType = if (attentionScore > 0.9) "Major Event" else "Notable Event"
                ))
            }
            
            // Generate trading signal based on strategy
            val signal = when (strategyName) {
                "Carlos RSI2" -> generateCarlosSignal(window)
                "Kraken Skimmer" -> generateSkimmerSignal(window, currentPrice)
                else -> "HOLD"
            }
            
            // Execute trades
            when (signal) {
                "BUY" -> {
                    if (balance > 100) { // Min trade size
                        val tradeAmount = balance * 0.1 // Risk 10% per trade
                        val quantity = tradeAmount / currentPrice
                        dogeHolding += quantity
                        balance -= tradeAmount
                        tradeCount++
                        
                        tradingEvents.add(TradingEvent(
                            timestamp = currentKline.timestamp,
                            symbol = "DOGEUSDT",
                            strategy = strategyName,
                            action = "BUY",
                            price = currentPrice,
                            quantity = quantity,
                            reason = signal
                        ))
                    }
                }
                "SELL" -> {
                    if (dogeHolding > 0) {
                        val sellValue = dogeHolding * currentPrice
                        val profit = sellValue - (trades.lastOrNull() ?: sellValue)
                        if (profit > 0) winningTrades++ else losingTrades++
                        trades.add(profit)
                        
                        balance += sellValue
                        dogeHolding = 0.0
                        
                        tradingEvents.add(TradingEvent(
                            timestamp = currentKline.timestamp,
                            symbol = "DOGEUSDT", 
                            strategy = strategyName,
                            action = "SELL",
                            price = currentPrice,
                            quantity = dogeHolding,
                            reason = signal
                        ))
                    }
                }
            }
        }
        
        // Final portfolio value
        val finalValue = balance + (dogeHolding * klines.last().close)
        val totalReturn = (finalValue - 10000.0) / 10000.0
        
        return StrategyPerformance(
            strategyName = strategyName,
            totalTrades = tradeCount,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            totalReturn = totalReturn,
            maxDrawdown = if (trades.isNotEmpty()) trades.minOrNull() ?: 0.0 else 0.0,
            sharpeRatio = if (trades.isNotEmpty()) calculateSharpeRatio(trades) else 0.0,
            averageHoldTime = 24.0 // Simplified
        )
    }
    
    internal fun generateCarlosSignal(window: List<Kline>): String {
        val rsi = calculateRSI(window.map { it.close }, 2)
        val shortSMA = window.takeLast(2).map { it.close }.average()
        val longSMA = window.takeLast(15).map { it.close }.average()
        val currentPrice = window.last().close
        
        return when {
            rsi < 5.0 && shortSMA > longSMA && shortSMA > currentPrice -> "BUY"
            rsi > 95.0 || shortSMA < longSMA || shortSMA < currentPrice -> "SELL"
            else -> "HOLD"
        }
    }
    
    internal fun generateSkimmerSignal(window: List<Kline>, currentPrice: Double): String {
        val baseline = window.takeLast(10).map { it.close }.average()
        val deviation = ((currentPrice - baseline) / baseline) * 100
        
        return when {
            deviation > 3.0 -> "SELL" // Harvest
            deviation < -4.0 -> "BUY" // Rebalance
            else -> "HOLD"
        }
    }
    
    internal fun calculateBacktestAttention(window: List<Kline>): Double {
        val recentVolumes = window.takeLast(5).map { it.volume }
        val baselineVolumes = window.take(window.size - 5).map { it.volume }
        
        val volumeSpike = recentVolumes.average() / baselineVolumes.average()
        val volatility = calculateWindowVolatility(window)
        
        return (volumeSpike * 0.6 + volatility * 100 * 0.4).coerceIn(0.0, 1.0)
    }
    
    internal fun calculateWindowVolatility(window: List<Kline>): Double {
        val prices = window.map { it.close }
        val returns = prices.zipWithNext { a, b -> kotlin.math.ln(b / a) }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
    
    internal fun calculateRSI(prices: List<Double>, period: Int): Double {
        if (prices.size < period + 1) return 50.0
        
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) -it else 0.0 }
        
        val avgGain = gains.takeLast(period).average()
        val avgLoss = losses.takeLast(period).average()
        
        if (avgLoss == 0.0) return 100.0
        
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }
    
    internal fun calculateSharpeRatio(returns: List<Double>): Double {
        val mean = returns.average()
        val std = kotlin.math.sqrt(returns.map { (it - mean) * (it - mean) }.average())
        return if (std > 0) mean / std else 0.0
    }
    
    internal fun createBacktestAttentionSystem(): AttentionSystem {
        return AttentionSystem() // Use existing attention system
    }
    
    fun printBacktestResults(results: List<BacktestResult>) {
        println("\n" + "=".repeat(60))
        println("DOGE BACKTEST RESULTS: 2020-2022")
        println("=".repeat(60))
        
        results.forEach { result ->
            println("\n${result.period.name} (${result.period.startDate} to ${result.period.endDate})")
            println("-".repeat(40))
            println("Expected Events: ${result.period.expectedEvents.joinToString(", ")}")
            println()
            
            // Carlos RSI2 Performance
            val carlos = result.carlosPerformance
            println("Carlos RSI2 Strategy:")
            println("  Total Return: ${String.format("%.2f%%", carlos.totalReturn * 100)}")
            println("  Total Trades: ${carlos.totalTrades}")
            println("  Win Rate: ${String.format("%.1f%%", (carlos.winningTrades.toDouble() / carlos.totalTrades) * 100)}")
            println("  Sharpe Ratio: ${String.format("%.2f", carlos.sharpeRatio)}")
            println()
            
            // Kraken Skimmer Performance  
            val skimmer = result.skimmerPerformance
            println("Kraken Skimmer Strategy:")
            println("  Total Return: ${String.format("%.2f%%", skimmer.totalReturn * 100)}")
            println("  Total Trades: ${skimmer.totalTrades}")
            println("  Win Rate: ${String.format("%.1f%%", (skimmer.winningTrades.toDouble() / skimmer.totalTrades) * 100)}")
            println("  Sharpe Ratio: ${String.format("%.2f", skimmer.sharpeRatio)}")
            println()
            
            // Attention Events Summary
            println("Attention Events Detected: ${result.attentionEvents.size}")
            val majorEvents = result.attentionEvents.filter { it.attentionScore > 0.9 }
            println("Major Events (>0.9 attention): ${majorEvents.size}")
            
            if (majorEvents.isNotEmpty()) {
                println("Top attention events:")
                majorEvents.sortedByDescending { it.attentionScore }.take(3).forEach { event ->
                    val date = java.time.Instant.ofEpochMilli(event.timestamp)
                    println("  ${date} - Score: ${String.format("%.3f", event.attentionScore)} (${event.eventType})")
                }
            }
            println()
        }
        
        // Overall comparison
        println("STRATEGY COMPARISON SUMMARY:")
        println("-".repeat(40))
        val carlosTotal = results.sumOf { it.carlosPerformance.totalReturn }
        val skimmerTotal = results.sumOf { it.skimmerPerformance.totalReturn }
        
        println("Carlos RSI2 3-Year Total Return: ${String.format("%.2f%%", carlosTotal * 100)}")
        println("Kraken Skimmer 3-Year Total Return: ${String.format("%.2f%%", skimmerTotal * 100)}")
        println("Winner: ${if (carlosTotal > skimmerTotal) "Carlos RSI2" else "Kraken Skimmer"}")
    }
}

// Simple strategy interfaces for backtesting
interface CarlosRSI2Strategy
interface KrakenSkimmerStrategy