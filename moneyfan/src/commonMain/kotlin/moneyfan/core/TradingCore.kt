package moneyfan.core

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import moneyfan.trikeshed.Series
import moneyfan.trikeshed.Join
import moneyfan.trikeshed.j
import moneyfan.models.TradingSignal

/**
 * TrikeShed-based trading core using Series<T> and Join<A,B> patterns
 * Unified decimal system using Double for performance with precision tracking
 */

// Precision-aware decimal system
typealias Decimal = Double
typealias Precision = Int

// Core value classes for type safety
@JvmInline
value class Price(val value: Decimal) {
    operator fun plus(other: Price): Price = Price(value + other.value)
    operator fun minus(other: Price): Price = Price(value - other.value)
    operator fun times(multiplier: Decimal): Price = Price(value * multiplier)
    operator fun times(quantity: Quantity): Price = Price(value * quantity.value)
    operator fun div(divisor: Decimal): Price = Price(value / divisor)
    operator fun compareTo(other: Price): Int = value.compareTo(other.value)
}

@JvmInline
value class Volume(val value: Decimal) {
    operator fun plus(other: Volume): Volume = Volume(value + other.value)
    operator fun times(multiplier: Decimal): Volume = Volume(value * multiplier)
}

@JvmInline
value class Symbol(val value: String)

@JvmInline
value class TradeId(val value: String)

@JvmInline
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

// High-Frequency Trading Primitive Array Optimizations
@JvmInline
value class TickPriceArray(val data: DoubleArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Price = Price(data[index])
    fun update(index: Int, price: Price) { data[index] = price.value }
    
    companion object {
        fun create(capacity: Int): TickPriceArray = TickPriceArray(DoubleArray(capacity))
        val UNDEFINED = Price(Double.NaN)
    }
}

@JvmInline  
value class TickVolumeArray(val data: DoubleArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Volume = Volume(data[index])
    fun update(index: Int, volume: Volume) { data[index] = volume.value }
    
    companion object {
        fun create(capacity: Int): TickVolumeArray = TickVolumeArray(DoubleArray(capacity))
    }
}

@JvmInline
value class TickTimestampArray(val data: LongArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Instant = Instant.fromEpochMilliseconds(data[index])
    fun update(index: Int, timestamp: Instant) { data[index] = timestamp.toEpochMilliseconds() }
    
    companion object {
        fun create(capacity: Int): TickTimestampArray = TickTimestampArray(LongArray(capacity))
    }
}

/**
 * High-frequency tick buffer using primitive arrays for optimal performance
 * Uses circular buffer pattern to minimize memory allocations
 */
data class HFTTickBuffer(
    val symbol: Symbol,
    val prices: TickPriceArray,
    val volumes: TickVolumeArray,
    val timestamps: TickTimestampArray,
    var head: Int = 0,
    var size: Int = 0
) {
    val capacity: Int get() = prices.size
    val isFull: Boolean get() = size >= capacity
    
    init {
        require(prices.size == volumes.size && volumes.size == timestamps.size) {
            "All arrays must have the same capacity"
        }
    }
    
    fun addTick(price: Price, volume: Volume, timestamp: Instant) {
        prices.update(head, price)
        volumes.update(head, volume)
        timestamps.update(head, timestamp)
        head = (head + 1) % capacity
        if (size < capacity) size++
    }
    
    fun getLatestTick(): MarketTick? {
        if (size == 0) return null
        val latestIndex = if (head == 0) size - 1 else head - 1
        return MarketTick(
            symbol = symbol,
            data = (prices[latestIndex] j volumes[latestIndex]) j timestamps[latestIndex],
            tradeId = TradeId("tick_${System.currentTimeMillis()}")
        )
    }
    
    fun toSeries(): Series<MarketTick> = size j { i ->
        val index = if (size < capacity) i else (head + i) % capacity
        MarketTick(
            symbol = symbol,
            data = (prices[index] j volumes[index]) j timestamps[index],
            tradeId = TradeId("tick_$index")
        )
    }
    
    companion object {
        fun create(symbol: Symbol, capacity: Int = 10000): HFTTickBuffer = HFTTickBuffer(
            symbol = symbol,
            prices = TickPriceArray.create(capacity),
            volumes = TickVolumeArray.create(capacity),
            timestamps = TickTimestampArray.create(capacity)
        )
    }
}

// Enhanced Time-Series Portfolio Management Types
typealias TimestampSeries = Series<Instant>
typealias PortfolioTimeSeries<T> = Join<TimestampSeries, Series<T>>
typealias PositionHistory = PortfolioTimeSeries<Position>
typealias ValueHistory = PortfolioTimeSeries<Price>
typealias RiskMetricsHistory = PortfolioTimeSeries<Join<Decimal, Decimal>> // VaR j Beta

// Advanced Asset Relationship Mappings
typealias AssetCorrelationMap = Join<Symbol, Series<Join<Symbol, Decimal>>>
typealias PortfolioWeights = Join<Symbol, Decimal>
typealias AssetAllocation = Join<PortfolioWeights, Join<Price, Volume>>
typealias RiskFactors = Join<Symbol, Join<Decimal, Join<Decimal, Decimal>>> // beta j (var j covar)
typealias PerformanceAttribution = Join<Symbol, Join<Decimal, Decimal>> // contribution j alpha
typealias StrategySignals = Join<Symbol, Join<TradingSignal, Decimal>> // signal j confidence

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

typealias TickSeries = Series<MarketTick>
typealias CandleSeries = Series<Candlestick>
typealias PriceSeries = Series<Price>

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
        
        if (ticks.a == 0) {
            return 0 j { _: Int -> candleData[0] } // Will never be called since size is 0
        }
        
        // Group ticks by symbol manually using for loop for performance
        val symbolGroups = mutableMapOf<Symbol, MutableList<MarketTick>>()
        for (i in 0 until ticks.a) {
            val tick = ticks[i]
            val symbolTicks = symbolGroups.getOrPut(tick.symbol) { mutableListOf() }
            symbolTicks.add(tick)
        }
        
        symbolGroups.forEach { (symbol, symbolTicks) ->
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
        
        return candleData.size j { i -> candleData[i] }
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
        
        return ticks.size j { i -> ticks[i] }
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
 * Technical analysis indicators using Series transformations
 */
class TechnicalAnalysis {
    
    fun simpleMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
        if (prices.a < period) {
            return 0 j { Price(0.0) }
        }
        
        // Use windowed operation with List conversion for compatibility
        val priceList = prices.play.toList()
        val windows = priceList.windowed(period) { window ->
            val sum = window.map { it.value }.sum()
            Price(sum / period)
        }
        
        return windows.size j { i -> windows[i] }
    }
    
    fun exponentialMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
        if (prices.a < period) {
            return 0 j { Price(0.0) }
        }
        
        val emaData = mutableListOf<Price>()
        val priceList = prices.play.toList()
        val multiplier = 2.0 / (period + 1)
        
        // Start with simple average for first EMA
        val initialSum = priceList.take(period).map { it.value }.sum()
        var ema = initialSum / period
        emaData.add(Price(ema))
        
        // Calculate remaining EMAs using for loop
        for (i in period until priceList.size) {
            ema = (priceList[i].value - ema) * multiplier + ema
            emaData.add(Price(ema))
        }
        
        return emaData.size j { i -> emaData[i] }
    }
    
    fun bollingerBands(prices: PriceSeries, period: Int, stdDev: Decimal = 2.0): Join<PriceSeries, Join<PriceSeries, PriceSeries>> {
        val sma = simpleMovingAverage(prices, period)
        val smaList = sma.play.toList()
        val priceList = prices.play.toList()
        
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
        
        val upperBandSeries: PriceSeries = upperBand.size j { i -> upperBand[i] }
        val lowerBandSeries: PriceSeries = lowerBand.size j { i -> lowerBand[i] }
        
        return sma j (upperBandSeries j lowerBandSeries)
    }
    
    fun rsi(prices: PriceSeries, period: Int = 14): PriceSeries {
        if (prices.a < period + 1) {
            return 0 j { _: Int -> Price(50.0) } // Neutral RSI
        }
        
        val priceList = prices.play.toList()
        val priceChanges = priceList.zipWithNext { prev, curr -> curr.value - prev.value }
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
        
        return rsiValues.size j { i -> rsiValues[i] }
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
    val positions: Series<Position>,
    val cashBalance: Price,
    val totalValue: Price,
    val dayPnL: Price,
    val totalReturn: Decimal
)

class PortfolioManager {
    private val positions = mutableMapOf<Symbol, Position>()
    private var cashBalance = 10000.0 // Start with $10,000
    private var initialValue = 10000.0
    
    // Time-series history tracking
    private val positionHistoryBuffer = mutableListOf<Pair<Instant, Map<Symbol, Position>>>()
    private val valueHistoryBuffer = mutableListOf<Pair<Instant, Price>>()
    private val riskHistoryBuffer = mutableListOf<Pair<Instant, Join<Decimal, Decimal>>>()
    private val maxHistorySize = 10000 // Configurable history buffer size
    
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
        
        // Update history tracking
        updateHistoryBuffers()
        
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
        
        // Update history tracking
        updateHistoryBuffers()
        
        return true
    }
    
    fun getPortfolioState(currentPrices: Map<Symbol, Price>): PortfolioState {
        val positionList = positions.values.map { position ->
            val currentPrice = currentPrices[position.symbol] ?: position.averagePrice
            val unrealizedPnL = (currentPrice - position.averagePrice) * position.quantity
            
            position.copy(unrealizedPnL = unrealizedPnL)
        }
        
        val positionSeries = positionList.size j { i -> positionList[i] }
        
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
    
    /**
     * Update time-series history buffers with current state
     */
    private fun updateHistoryBuffers() {
        val timestamp = kotlinx.datetime.Clock.System.now()
        
        // Track position history
        val currentPositions = positions.toMap()
        positionHistoryBuffer.add(timestamp to currentPositions)
        if (positionHistoryBuffer.size > maxHistorySize) {
            positionHistoryBuffer.removeAt(0)
        }
        
        // Track value history
        val totalValue = Price(cashBalance + positions.values.sumOf { position ->
            // Use last known price or average price
            position.averagePrice.value * position.quantity.value
        })
        valueHistoryBuffer.add(timestamp to totalValue)
        if (valueHistoryBuffer.size > maxHistorySize) {
            valueHistoryBuffer.removeAt(0)
        }
        
        // Track risk metrics history
        val riskMetrics = getRiskMetrics()
        riskHistoryBuffer.add(timestamp to riskMetrics)
        if (riskHistoryBuffer.size > maxHistorySize) {
            riskHistoryBuffer.removeAt(0)
        }
    }
    
    /**
     * Get enhanced portfolio state with time-series tracking
     */
    fun getEnhancedPortfolioState(currentPrices: Map<Symbol, Price>): EnhancedPortfolioState {
        val basicState = getPortfolioState(currentPrices)
        
        // Create time-series from buffers
        val timestampSeries = positionHistoryBuffer.size j { i -> positionHistoryBuffer[i].first }
        val positionHistory = timestampSeries j (positionHistoryBuffer.size j { i ->
            val positionMap = positionHistoryBuffer[i].second
            positionMap.values.toList().size j { j -> positionMap.values.toList()[j] }
        })
        
        val valueHistory = timestampSeries j (valueHistoryBuffer.size j { i -> valueHistoryBuffer[i].second })
        val riskHistory = timestampSeries j (riskHistoryBuffer.size j { i -> riskHistoryBuffer[i].second })
        
        // Calculate performance metrics
        val performanceMetrics = calculatePerformanceMetrics()
        
        return EnhancedPortfolioState(
            positions = basicState.positions,
            positionHistory = positionHistory,
            valueHistory = valueHistory,
            riskHistory = riskHistory,
            performanceMetrics = performanceMetrics
        )
    }
    
    /**
     * Calculate comprehensive performance metrics
     */
    private fun calculatePerformanceMetrics(): PerformanceMetrics {
        if (valueHistoryBuffer.size < 2) {
            return PerformanceMetrics(
                sharpeRatio = 0.0,
                maxDrawdown = 0.0,
                totalReturn = 0.0,
                volatility = 0.0,
                winRate = 0.0
            )
        }
        
        // Calculate returns
        val returns = mutableListOf<Double>()
        for (i in 1 until valueHistoryBuffer.size) {
            val prevValue = valueHistoryBuffer[i-1].second.value
            val currValue = valueHistoryBuffer[i].second.value
            val returnValue = (currValue - prevValue) / prevValue
            returns.add(returnValue)
        }
        
        // Performance calculations using for loops for optimal performance
        var totalReturn = 0.0
        var sumSquaredDeviations = 0.0
        val mean = returns.average()
        
        for (ret in returns) {
            totalReturn += ret
            val deviation = ret - mean
            sumSquaredDeviations += deviation * deviation
        }
        
        val volatility = kotlin.math.sqrt(sumSquaredDeviations / returns.size)
        val sharpeRatio = if (volatility > 0) mean / volatility else 0.0
        
        // Calculate max drawdown
        var maxDrawdown = 0.0
        var peak = valueHistoryBuffer[0].second.value
        for (i in 1 until valueHistoryBuffer.size) {
            val value = valueHistoryBuffer[i].second.value
            if (value > peak) {
                peak = value
            } else {
                val drawdown = (peak - value) / peak
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown
                }
            }
        }
        
        // Calculate win rate (simplified)
        var wins = 0
        for (ret in returns) {
            if (ret > 0) wins++
        }
        val winRate = wins.toDouble() / returns.size
        
        return PerformanceMetrics(
            sharpeRatio = sharpeRatio,
            maxDrawdown = maxDrawdown,
            totalReturn = totalReturn,
            volatility = volatility,
            winRate = winRate
        )
    }
}

/**
 * Performance-optimized trading calculations using for loops (gold standard for performance)
 */
object OptimizedTradingCalculations {
    
    /**
     * Fast moving average calculation using for loops for maximum performance
     */
    fun fastMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
        return prices.a j { index ->
            if (index < period - 1) {
                TickPriceArray.UNDEFINED
            } else {
                var sum = 0.0
                // Performance-critical: use for loop, not forEach
                for (i in (index - period + 1)..index) {
                    sum += prices[i].value
                }
                Price(sum / period)
            }
        }
    }
    
    /**
     * Cache-friendly RSI calculation using for loops
     */
    fun fastRSI(prices: PriceSeries, period: Int): PriceSeries {
        val changes = (prices.a - 1) j { i -> prices[i + 1].value - prices[i].value }
        
        return changes.a j { index ->
            if (index < period - 1) {
                Price(50.0) // Neutral RSI
            } else {
                var gainSum = 0.0
                var lossSum = 0.0
                
                // Optimized for loop pattern
                for (i in (index - period + 1)..index) {
                    val change = changes[i]
                    if (change > 0) gainSum += change else lossSum -= change
                }
                
                val rs = if (lossSum > 0) gainSum / lossSum else Double.MAX_VALUE
                Price(100.0 - (100.0 / (1.0 + rs)))
            }
        }
    }
    
    /**
     * High-frequency Bollinger Bands calculation optimized for primitive arrays
     */
    fun fastBollingerBands(buffer: HFTTickBuffer, period: Int, stdDev: Decimal = 2.0): Join<PriceSeries, Join<PriceSeries, PriceSeries>> {
        val sma = buffer.size j { index ->
            if (index < period - 1) {
                TickPriceArray.UNDEFINED
            } else {
                var sum = 0.0
                for (i in (index - period + 1)..index) {
                    val bufferIndex = if (buffer.size < buffer.capacity) i else (buffer.head + i) % buffer.capacity
                    sum += buffer.prices[bufferIndex].value
                }
                Price(sum / period)
            }
        }
        
        val upperBand = buffer.size j { index ->
            if (index < period - 1) {
                TickPriceArray.UNDEFINED
            } else {
                val mean = sma[index].value
                var variance = 0.0
                for (i in (index - period + 1)..index) {
                    val bufferIndex = if (buffer.size < buffer.capacity) i else (buffer.head + i) % buffer.capacity
                    val diff = buffer.prices[bufferIndex].value - mean
                    variance += diff * diff
                }
                val standardDeviation = kotlin.math.sqrt(variance / period)
                Price(mean + stdDev * standardDeviation)
            }
        }
        
        val lowerBand = buffer.size j { index ->
            if (index < period - 1) {
                TickPriceArray.UNDEFINED
            } else {
                val mean = sma[index].value
                var variance = 0.0
                for (i in (index - period + 1)..index) {
                    val bufferIndex = if (buffer.size < buffer.capacity) i else (buffer.head + i) % buffer.capacity
                    val diff = buffer.prices[bufferIndex].value - mean
                    variance += diff * diff
                }
                val standardDeviation = kotlin.math.sqrt(variance / period)
                Price(mean - stdDev * standardDeviation)
            }
        }
        
        return sma j (upperBand j lowerBand)
    }
}

/**
 * Enhanced portfolio management with time-series tracking
 */
data class EnhancedPortfolioState(
    val positions: Series<Position>,
    val positionHistory: PositionHistory,
    val valueHistory: ValueHistory,
    val riskHistory: RiskMetricsHistory,
    val performanceMetrics: PerformanceMetrics
)

data class PerformanceMetrics(
    val sharpeRatio: Decimal,
    val maxDrawdown: Decimal,
    val totalReturn: Decimal,
    val volatility: Decimal,
    val winRate: Decimal
)

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