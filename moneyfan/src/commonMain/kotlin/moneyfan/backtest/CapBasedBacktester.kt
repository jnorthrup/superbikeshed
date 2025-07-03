package moneyfan.backtest

import moneyfan.core.*
import moneyfan.attention.*
import borg.trikeshed.lib.*
import kotlinx.datetime.*

/**
 * Cap-based backtesting system with skimmer strategy
 * "Give me the top 10 by cap on date running skimmer at 2.5%"
 */

// Market cap tracking using TrikeShed patterns
@JvmInline
value class MarketCap(val value: Decimal) {
    operator fun compareTo(other: MarketCap): Int = value.compareTo(other.value)
    operator fun times(multiplier: Decimal): MarketCap = MarketCap(value * multiplier)
}

@JvmInline
value class SkimmerPercent(val value: Decimal) {
    fun applyToPrice(price: Price): Price = Price(price.value * (1.0 + value / 100.0))
    fun reverseFromPrice(price: Price): Price = Price(price.value / (1.0 + value / 100.0))
}

// Cap-weighted asset using Join composition
typealias CapWeightedAsset = Join<Symbol, MarketCap>
typealias SkimmerPosition = Join<CapWeightedAsset, SkimmerPercent>

// Market cap ranking on specific date
data class CapRanking(
    val date: LocalDate,
    val rankings: Indexed<CapWeightedAsset>,
    val totalMarketCap: MarketCap
)

// Skimmer strategy result
data class SkimmerResult(
    val symbol: Symbol,
    val entryPrice: Price,
    val exitPrice: Price,
    val skimPercent: SkimmerPercent,
    val quantity: Quantity,
    val profit: Price,
    val holdingPeriod: Int // days
)

typealias SkimmerResults = Indexed<SkimmerResult>

/**
 * Cap-based backtester with skimmer strategy
 */
class CapBasedBacktester {
    
    // Sample market cap data (in real system would come from data feed)
    private val marketCapData = mapOf(
        "BTC" to MarketCap(800_000_000_000.0),   // $800B
        "ETH" to MarketCap(400_000_000_000.0),   // $400B  
        "AAPL" to MarketCap(3_000_000_000_000.0), // $3T
        "MSFT" to MarketCap(2_800_000_000_000.0), // $2.8T
        "GOOGL" to MarketCap(1_800_000_000_000.0), // $1.8T
        "AMZN" to MarketCap(1_500_000_000_000.0), // $1.5T
        "TSLA" to MarketCap(800_000_000_000.0),   // $800B
        "META" to MarketCap(900_000_000_000.0),   // $900B
        "NVDA" to MarketCap(2_200_000_000_000.0), // $2.2T
        "JPM" to MarketCap(500_000_000_000.0),    // $500B
        "JNJ" to MarketCap(450_000_000_000.0),    // $450B
        "V" to MarketCap(520_000_000_000.0),      // $520B
    )
    
    /**
     * Get top N assets by market cap on specific date
     */
    fun getTopByCapOnDate(date: LocalDate, topN: Int = 10): CapRanking {
        // Sort by market cap descending using TrikeShed patterns
        val sortedAssets = marketCapData.toList()
            .sortedByDescending { it.second.value }
            .take(topN)
            .map { (symbol, cap) -> Symbol(symbol) j cap }
        
        val rankings = Indexed.of(sortedAssets.size) { sortedAssets[it] }
        val totalCap = MarketCap(sortedAssets.sumOf { (_, cap) -> cap.value })
        
        return CapRanking(date, rankings, totalCap)
    }
    
    /**
     * Run skimmer strategy on top cap assets
     * Skimmer: Buy asset, sell when it moves up by skim percentage
     */
    fun runSkimmerStrategy(
        startDate: LocalDate,
        endDate: LocalDate,
        topN: Int = 10,
        skimPercent: SkimmerPercent = SkimmerPercent(2.5),
        initialCapital: Price = Price(100_000.0)
    ): Join<SkimmerResults, Price> { // results j finalValue
        
        val capRanking = getTopByCapOnDate(startDate, topN)
        val results = mutableListOf<SkimmerResult>()
        var remainingCapital = initialCapital.value
        
        println("=== Cap-Based Skimmer Backtest ===")
        println("Date: ${startDate} to ${endDate}")
        println("Strategy: Top $topN by market cap, ${skimPercent.value}% skimmer")
        println("Initial Capital: $${initialCapital.value}")
        
        println("\nTop $topN by Market Cap on $startDate:")
        capRanking.rankings.play.forEachIndexed { index, asset ->
            val (symbol, cap) = asset
            println("${index + 1}. ${symbol.value}: $${cap.value.formatBillions()}B")
        }
        
        // Allocate capital equally across top assets
        val capitalPerAsset = remainingCapital / topN
        
        capRanking.rankings.play.forEach { asset ->
            val (symbol, cap) = asset
            
            // Simulate entry price (would be historical data in real system)
            val entryPrice = generateEntryPrice(symbol)
            val quantity = Quantity(capitalPerAsset / entryPrice.value)
            
            // Calculate exit price using skimmer percentage
            val exitPrice = skimPercent.applyToPrice(entryPrice)
            
            // Simulate holding period (random 1-30 days for demo)
            val holdingPeriod = (1..30).random()
            
            // Calculate profit
            val totalEntry = Price(quantity.value * entryPrice.value)
            val totalExit = Price(quantity.value * exitPrice.value)
            val profit = Price(totalExit.value - totalEntry.value)
            
            val result = SkimmerResult(
                symbol = symbol,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                skimPercent = skimPercent,
                quantity = quantity,
                profit = profit,
                holdingPeriod = holdingPeriod
            )
            
            results.add(result)
        }
        
        val resultsSeries = Indexed.of(results.size) { results[it] }
        val totalProfit = results.sumOf { it.profit.value }
        val finalValue = Price(initialCapital.value + totalProfit)
        
        return resultsSeries j finalValue
    }
    
    private fun generateEntryPrice(symbol: Symbol): Price {
        // Simulate realistic entry prices based on asset type
        return when (symbol.value) {
            "BTC" -> Price(45000.0 + kotlin.random.Random.nextDouble(-5000.0, 5000.0))
            "ETH" -> Price(3000.0 + kotlin.random.Random.nextDouble(-500.0, 500.0))
            "AAPL" -> Price(150.0 + kotlin.random.Random.nextDouble(-20.0, 20.0))
            "MSFT" -> Price(350.0 + kotlin.random.Random.nextDouble(-30.0, 30.0))
            "GOOGL" -> Price(2800.0 + kotlin.random.Random.nextDouble(-200.0, 200.0))
            "AMZN" -> Price(3200.0 + kotlin.random.Random.nextDouble(-300.0, 300.0))
            "TSLA" -> Price(200.0 + kotlin.random.Random.nextDouble(-50.0, 50.0))
            "META" -> Price(320.0 + kotlin.random.Random.nextDouble(-40.0, 40.0))
            "NVDA" -> Price(800.0 + kotlin.random.Random.nextDouble(-100.0, 100.0))
            "JPM" -> Price(180.0 + kotlin.random.Random.nextDouble(-20.0, 20.0))
            "JNJ" -> Price(160.0 + kotlin.random.Random.nextDouble(-15.0, 15.0))
            "V" -> Price(250.0 + kotlin.random.Random.nextDouble(-25.0, 25.0))
            else -> Price(100.0 + kotlin.random.Random.nextDouble(-10.0, 10.0))
        }
    }
    
    /**
     * Analyze skimmer results using TrikeShed patterns
     */
    fun analyzeResults(results: SkimmerResults): SkimmerAnalysis {
        val resultsList = results.play
        
        // Use Indexed α transforms for analysis
        val profits = results.α { it.profit.value }
        val holdingPeriods = results.α { it.holdingPeriod.toDouble() }
        val quantities = results.α { it.quantity.value }
        
        val totalProfit = profits.play.sum()
        val avgHoldingPeriod = holdingPeriods.play.average()
        val winRate = resultsList.count { it.profit.value > 0 }.toDouble() / resultsList.size
        val bestTrade = resultsList.maxByOrNull { it.profit.value }
        val worstTrade = resultsList.minByOrNull { it.profit.value }
        
        return SkimmerAnalysis(
            totalTrades = results.size,
            totalProfit = Price(totalProfit),
            winRate = winRate,
            avgHoldingPeriod = avgHoldingPeriod,
            bestTrade = bestTrade,
            worstTrade = worstTrade
        )
    }
    
    /**
     * Display backtest results with TrikeShed formatting
     */
    fun displayResults(results: Join<SkimmerResults, Price>) {
        val (skimmerResults, finalValue) = results
        val analysis = analyzeResults(skimmerResults)
        
        println("\n=== Skimmer Strategy Results ===")
        skimmerResults.play.forEach { result ->
            println("${result.symbol.value}: " +
                   "$${result.entryPrice.value.format(2)} → $${result.exitPrice.value.format(2)} " +
                   "(${result.holdingPeriod}d) " +
                   "Profit: $${result.profit.value.format(2)}")
        }
        
        println("\n=== Strategy Performance ===")
        println("Total Trades: ${analysis.totalTrades}")
        println("Total Profit: $${analysis.totalProfit.value.format(2)}")
        println("Final Portfolio Value: $${finalValue.value.format(2)}")
        println("Win Rate: ${(analysis.winRate * 100).format(1)}%")
        println("Average Holding Period: ${analysis.avgHoldingPeriod.format(1)} days")
        
        analysis.bestTrade?.let { trade ->
            println("Best Trade: ${trade.symbol.value} +$${trade.profit.value.format(2)}")
        }
        
        analysis.worstTrade?.let { trade ->
            println("Worst Trade: ${trade.symbol.value} $${trade.profit.value.format(2)}")
        }
    }
}

// Analysis results using TrikeShed patterns
data class SkimmerAnalysis(
    val totalTrades: Int,
    val totalProfit: Price,
    val winRate: Decimal,
    val avgHoldingPeriod: Decimal,
    val bestTrade: SkimmerResult?,
    val worstTrade: SkimmerResult?
)

// Extension functions for formatting
private fun Decimal.formatBillions(): String = (this / 1_000_000_000.0).format(1)
private fun Decimal.format(decimals: Int): String = "%.${decimals}f".format(this)

/**
 * Demo function for cap-based backtesting
 */
fun demonstrateCapBasedBacktesting() {
    val backtester = CapBasedBacktester()
    
    // Run backtest: "Give me the top 10 by cap on date running skimmer at 2.5%"
    val startDate = LocalDate(2024, 1, 1)
    val endDate = LocalDate(2024, 6, 30)
    
    val results = backtester.runSkimmerStrategy(
        startDate = startDate,
        endDate = endDate,
        topN = 10,
        skimPercent = SkimmerPercent(2.5),
        initialCapital = Price(100_000.0)
    )
    
    backtester.displayResults(results)
}