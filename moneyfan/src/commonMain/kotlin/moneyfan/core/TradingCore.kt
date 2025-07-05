package moneyfan.core

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant

/**
 * TrikeShed-based trading core using Indexed<T> and Join<A,B> patterns
 * Unified decimal system using Double for performance with precision tracking
 */

// Precision-aware decimal system
typealias Decimal = Double
typealias Precision = Int

// Core value classes for type safety
value class Price(val value: Decimal) {
    operator fun plus(other: Price): Price = Price(value + other.value)
    operator fun minus(other: Price): Price = Price(value - other.value)
    operator fun times(multiplier: Decimal): Price = Price(value * multiplier)
    operator fun times(quantity: Quantity): Price = Price(value * quantity.value)
    operator fun div(divisor: Decimal): Price = Price(value / divisor)
    operator fun compareTo(other: Price): Int = value.compareTo(other.value)
}

value class Volume(val value: Decimal) {
    operator fun plus(other: Volume): Volume = Volume(value + other.value)
    operator fun times(multiplier: Decimal): Volume = Volume(value * multiplier)
}

value class Symbol(val value: String)

value class TradeId(val value: String)

value class Quantity(val value: Decimal) {
    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)
    operator fun minus(other: Quantity): Quantity = Quantity(value - other.value)
    operator fun times(price: Price): Price = Price(value * price.value)
    operator fun compareTo(other: Quantity): Int = value.compareTo(other.value)
}

// Trading data structures using TrikeShed patterns
typealias PriceVolume = Join<Price, Volume>
typealias TickData = Join<PriceVolume, Instant>
typealias OHLCV = Join<Join<Price, Price>, Join<Join<Price, Price>, Volume>> // Open-High-Low-Close-Volume

// Helper functions for OHLCV access
val OHLCV.open: Price get() = this.a.a
val OHLCV.high: Price get() = this.a.b  
val OHLCV.low: Price get() = this.b.a.a
val OHLCV.close: Price get() = this.b.a.b
val OHLCV.volume: Volume get() = this.b.b

fun OHLCV(open: Price, high: Price, low: Price, close: Price, volume: Volume): OHLCV =
    (open j high) j ((low j close) j volume)

/**
 * Market tick representing a single trade event
 */
data class MarketTick(
    val symbol: Symbol,
    val data: TickData,
    val tradeId: TradeId
) {
    val price: Price get() = data.a.a
    val volume: Volume get() = data.a.b
    val timestamp: Instant get() = data.b
}

/**
 * Enhanced candlestick data for comprehensive analysis
 */
data class Candlestick(
    val symbol: Symbol,
    val ohlcv: OHLCV,
    val startTime: Instant,
    val endTime: Instant,
    val tickCount: Int,
    val volatility: Decimal = 0.0,
    val vwap: Price = Price(0.0)
)

typealias TickSeries = Indexed<MarketTick>
typealias CandleSeries = Indexed<Candlestick>
typealias PriceSeries = Indexed<Price>

/**
 * Trading engine for processing market data with advanced algorithms
 */
class TradingEngine {
    private var tickCount = 0L
    private val volatilityTracker = VolatilityTracker()
    
    fun createTick(symbol: String, price: Decimal, volume: Decimal): MarketTick {
        return MarketTick(
            symbol = Symbol(symbol),
            data = (Price(price) j Volume(volume)) j kotlinx.datetime.Clock.System.now(),
            tradeId = TradeId("trade_${++tickCount}")
        )
    }
    
    fun processTickSeries(ticks: TickSeries): CandleSeries {
        val candleData = mutableListOf<Candlestick>()
        
        if (ticks.play.isEmpty()) {
            return Indexed.of(0) { candleData[it] }
        }
        
        // Group ticks by symbol and time window using Indexed.α transformation
        val groupedTicks = ticks.α { tick -> tick.symbol to tick }.play.groupBy { it.first }
        
        groupedTicks.forEach { (symbol, symbolPairs) ->
            val symbolTicks = symbolPairs.map { it.second }
            
            if (symbolTicks.isNotEmpty()) {
                val prices = symbolTicks.map { it.price }
                val volumes = symbolTicks.map { it.volume }
                
                val open = prices.first()
                val close = prices.last()
                val high = prices.maxBy { it.value }
                val low = prices.minBy { it.value }
                val totalVolume = volumes.fold(Volume(0.0)) { acc, vol -> acc + vol }
                
                // Calculate additional metrics
                val volatility = volatilityTracker.calculateVolatility(prices)
                val vwap = calculateVWAP(prices, volumes)
                
                val candle = Candlestick(
                    symbol = symbol,
                    ohlcv = OHLCV(open, high, low, close, totalVolume),
                    startTime = symbolTicks.first().timestamp,
                    endTime = symbolTicks.last().timestamp,
                    tickCount = symbolTicks.size,
                    volatility = volatility,
                    vwap = vwap
                )
                
                candleData.add(candle)
            }
        }
        
        return Indexed.of(candleData.size) { i -> candleData[i] }
    }
    
    private fun calculateVWAP(prices: List<Price>, volumes: List<Volume>): Price {
        val priceVolumePairs = prices.zip(volumes)
        val totalValue = priceVolumePairs.fold(0.0) { acc, (price, volume) -> 
            acc + (price.value * volume.value) 
        }
        val totalVolume = volumes.fold(0.0) { acc, volume -> acc + volume.value }
        
        return if (totalVolume > 0.0) Price(totalValue / totalVolume) else prices.first()
    }
    
    fun generateSampleTicks(symbol: String, count: Int, basePrice: Decimal = 100.0): TickSeries {
        val ticks = mutableListOf<MarketTick>()
        var currentPrice = basePrice
        var volatility = 0.02 // 2% volatility
        
        repeat(count) { i ->
            // Advanced price simulation with volatility clustering
            val randomShock = generateGaussian() * volatility
            val momentum = if (i > 0) (currentPrice - basePrice) * 0.001 else 0.0
            
            currentPrice += randomShock + momentum
            currentPrice = kotlin.math.max(0.01, currentPrice) // Prevent negative prices
            
            // Volume correlates inversely with price stability
            val volumeBase = 1000.0
            val volumeVolatility = kotlin.math.abs(randomShock) * 10000.0
            val volume = volumeBase + volumeVolatility + kotlin.random.Random.nextDouble() * 500.0
            
            ticks.add(createTick(symbol, currentPrice, volume))
            
            // Update volatility (GARCH-like behavior)
            volatility = 0.95 * volatility + 0.05 * kotlin.math.abs(randomShock)
        }
        
        return Indexed.of(ticks.size) { i -> ticks[i] }
    }
}

/**
 * Volatility tracking for market risk assessment
 */
class VolatilityTracker {
    fun calculateVolatility(prices: List<Price>): Decimal {
        if (prices.size < 2) return 0.0
        
        // Calculate returns
        val returns = prices.zipWithNext { prev, curr ->
            kotlin.math.ln(curr.value / prev.value)
        }
        
        // Calculate standard deviation of returns
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        
        return kotlin.math.sqrt(variance)
    }
}

/**
 * Technical analysis indicators using Indexed transformations
 */
class TechnicalAnalysis {
    
    fun simpleMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
        if (prices.size < period) {
            return Indexed.of(0) { Price(0.0) }
        }
        
        // Use Indexed.α for functional transformation
        val windows = prices.play.windowed(period) { window ->
            val sum = window.map { it.value }.sum()
            Price(sum / period)
        }
        
        return Indexed.of(windows.size) { i -> windows[i] }
    }
    
    fun exponentialMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
        if (prices.size < period) {
            return Indexed.of(0) { Price(0.0) }
        }
        
        val emaData = mutableListOf<Price>()
        val priceList = prices.play
        val multiplier = 2.0 / (period + 1)
        
        // Start with simple average for first EMA
        val initialSum = priceList.take(period).map { it.value }.sum()
        var ema = initialSum / period
        emaData.add(Price(ema))
        
        // Calculate remaining EMAs using Indexed transformation
        for (i in period until priceList.size) {
            ema = (priceList[i].value - ema) * multiplier + ema
            emaData.add(Price(ema))
        }
        
        return Indexed.of(emaData.size) { i -> emaData[i] }
    }
    
    fun bollingerBands(prices: PriceSeries, period: Int, stdDev: Decimal = 2.0): Join<PriceSeries, Join<PriceSeries, PriceSeries>> {
        val sma = simpleMovingAverage(prices, period)
        val smaList = sma.play
        val priceList = prices.play
        
        val upperBand = mutableListOf<Price>()
        val lowerBand = mutableListOf<Price>()
        
        for (i in 0 until smaList.size) {
            val windowStart = i
            val windowEnd = i + period
            if (windowEnd <= priceList.size) {
                val window = priceList.subList(windowStart, windowEnd)
                val mean = smaList[i].value
                val variance = window.map { (it.value - mean) * (it.value - mean) }.average()
                val standardDeviation = kotlin.math.sqrt(variance)
                
                upperBand.add(Price(mean + stdDev * standardDeviation))
                lowerBand.add(Price(mean - stdDev * standardDeviation))
            }
        }
        
        return sma j (Indexed.of(upperBand.size) { i -> upperBand[i] } j Indexed.of(lowerBand.size) { i -> lowerBand[i] })
    }
    
    fun rsi(prices: PriceSeries, period: Int = 14): PriceSeries {
        if (prices.size < period + 1) {
            return Indexed.of(0) { Price(50.0) } // Neutral RSI
        }
        
        val priceChanges = prices.play.zipWithNext { prev, curr -> curr.value - prev.value }
        val gains = priceChanges.map { kotlin.math.max(0.0, it) }
        val losses = priceChanges.map { kotlin.math.abs(kotlin.math.min(0.0, it)) }
        
        val rsiValues = mutableListOf<Price>()
        
        // Calculate initial averages
        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()
        
        val rs = if (avgLoss != 0.0) avgGain / avgLoss else Double.MAX_VALUE
        val rsi = 100.0 - (100.0 / (1.0 + rs))
        rsiValues.add(Price(rsi))
        
        // Calculate remaining RSI values using Wilder's smoothing
        for (i in period until gains.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            
            val newRs = if (avgLoss != 0.0) avgGain / avgLoss else Double.MAX_VALUE
            val newRsi = 100.0 - (100.0 / (1.0 + newRs))
            rsiValues.add(Price(newRsi))
        }
        
        return Indexed.of(rsiValues.size) { i -> rsiValues[i] }
    }
}

/**
 * Portfolio management and position tracking
 */
data class Position(
    val symbol: Symbol,
    val quantity: Quantity,
    val averagePrice: Price,
    val totalCost: Price,
    val unrealizedPnL: Price = Price(0.0),
    val realizedPnL: Price = Price(0.0)
)

data class PortfolioState(
    val positions: Indexed<Position>,
    val cashBalance: Price,
    val totalValue: Price,
    val dayPnL: Price,
    val totalReturn: Decimal
)

class PortfolioManager {
    private val positions = mutableMapOf<Symbol, Position>()
    private var cashBalance = 10000.0 // Start with $10,000
    private var initialValue = 10000.0
    
    fun buyPosition(symbol: Symbol, quantity: Quantity, price: Price): Boolean {
        val totalCost = quantity * price
        
        if (totalCost.value > cashBalance) {
            return false // Insufficient funds
        }
        
        cashBalance -= totalCost.value
        
        val existingPosition = positions[symbol]
        if (existingPosition != null) {
            // Average down/up existing position using weighted average
            val newQuantity = existingPosition.quantity + quantity
            val newTotalCost = existingPosition.totalCost + totalCost
            val newAvgPrice = Price(newTotalCost.value / newQuantity.value)
            
            positions[symbol] = Position(symbol, newQuantity, newAvgPrice, newTotalCost)
        } else {
            positions[symbol] = Position(symbol, quantity, price, totalCost)
        }
        
        return true
    }
    
    fun sellPosition(symbol: Symbol, quantity: Quantity, price: Price): Boolean {
        val position = positions[symbol] ?: return false
        
        if (quantity > position.quantity) {
            return false // Not enough shares
        }
        
        val saleValue = quantity * price
        cashBalance += saleValue.value
        
        // Calculate realized P&L
        val avgCostBasis = position.averagePrice
        val realizedPnL = (price - avgCostBasis) * quantity
        
        val remainingQuantity = position.quantity - quantity
        if (remainingQuantity.value <= 0.0) {
            positions.remove(symbol)
        } else {
            val remainingCost = Price(position.totalCost.value - saleValue.value)
            positions[symbol] = position.copy(
                quantity = remainingQuantity,
                totalCost = remainingCost,
                realizedPnL = position.realizedPnL + realizedPnL
            )
        }
        
        return true
    }
    
    fun getPortfolioState(currentPrices: Map<Symbol, Price>): PortfolioState {
        val positionList = positions.values.map { position ->
            val currentPrice = currentPrices[position.symbol] ?: position.averagePrice
            val unrealizedPnL = (currentPrice - position.averagePrice) * position.quantity
            
            position.copy(unrealizedPnL = unrealizedPnL)
        }
        
        val positionSeries = Indexed.of(positionList.size) { i -> positionList[i] }
        
        val totalPositionValue = positionList.fold(0.0) { acc, position ->
            val currentPrice = currentPrices[position.symbol] ?: position.averagePrice
            acc + (position.quantity * currentPrice).value
        }
        
        val totalValue = Price(cashBalance + totalPositionValue)
        val totalReturn = (totalValue.value - initialValue) / initialValue
        
        val dayPnL = positionList.fold(Price(0.0)) { acc, position ->
            acc + position.unrealizedPnL + position.realizedPnL
        }
        
        return PortfolioState(
            positions = positionSeries,
            cashBalance = Price(cashBalance),
            totalValue = totalValue,
            dayPnL = dayPnL,
            totalReturn = totalReturn
        )
    }
    
    fun getPositionValue(symbol: Symbol, currentPrice: Price): Price {
        val position = positions[symbol] ?: return Price(0.0)
        return position.quantity * currentPrice
    }
    
    fun getRiskMetrics(): Join<Decimal, Decimal> { // VaR j Beta
        // Simplified risk calculation
        val totalPositions = positions.values.size
        val diversificationRatio = if (totalPositions > 0) 1.0 / totalPositions else 1.0
        val estimatedVaR = 0.05 * diversificationRatio // 5% base VaR adjusted for diversification
        val estimatedBeta = 1.0 // Market beta
        
        return estimatedVaR j estimatedBeta
    }
}

/**
 * Generate pseudo-Gaussian random number using Box-Muller transform
 * Kotlin/Common compatible implementation
 */
private var gaussianReady = false
private var gaussianValue = 0.0

fun generateGaussian(): Double {
    if (gaussianReady) {
        gaussianReady = false
        return gaussianValue
    }
    
    var u = 0.0
    var v = 0.0
    var s = 0.0
    
    do {
        u = kotlin.random.Random.nextDouble() * 2.0 - 1.0
        v = kotlin.random.Random.nextDouble() * 2.0 - 1.0
        s = u * u + v * v
    } while (s >= 1.0 || s == 0.0)
    
    val multiplier = kotlin.math.sqrt(-2.0 * kotlin.math.ln(s) / s)
    gaussianValue = v * multiplier
    gaussianReady = true
    
    return u * multiplier
}