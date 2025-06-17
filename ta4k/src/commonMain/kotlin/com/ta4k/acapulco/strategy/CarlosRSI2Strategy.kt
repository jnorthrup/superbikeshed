package com.ta4k.acapulco.strategy

import borg.trikeshed.lib.*
import com.ta4k.acapulco.data.*
import com.ta4k.acapulco.attention.*
import kotlinx.datetime.Instant

/**
 * Carlos RSI2 Strategy implementation using TrikeShed patterns.
 * Based on the 2-period RSI strategy with SMA trend filters.
 * 
 * Entry Rules:
 * 1. Short SMA > Long SMA (uptrend)
 * 2. RSI(2) crosses below 5 (oversold)
 * 3. Short SMA > Close Price (pullback confirmation)
 * 
 * Exit Rules:
 * 1. Short SMA < Long SMA (downtrend) OR
 * 2. RSI(2) crosses above 95 (overbought) OR
 * 3. Short SMA < Close Price (momentum shift)
 */

// Strategy value classes
@JvmInline
value class RSIValue(val value: Double) {
    fun isOversold(threshold: Double = 5.0): Boolean = value < threshold
    fun isOverbought(threshold: Double = 95.0): Boolean = value > threshold
    operator fun compareTo(other: RSIValue): Int = value.compareTo(other.value)
    operator fun compareTo(threshold: Double): Int = value.compareTo(threshold)
}

@JvmInline
value class SMAValue(val value: Double) {
    operator fun compareTo(other: SMAValue): Int = value.compareTo(other.value)
    operator fun compareTo(price: Price): Int = value.compareTo(price.value)
}

// Strategy signals
enum class TradeSignal {
    BUY, SELL, HOLD
}

enum class TrendDirection {
    UP, DOWN, SIDEWAYS
}

// Signal data structures using TrikeShed patterns
typealias RSISeries = Series<RSIValue>
typealias SMASeries = Series<SMAValue>
typealias SignalSeries = Series<TradeSignal>
typealias TrendAnalysis = Join<TrendDirection, Double> // Direction + Strength

// Carlos RSI2 strategy implementation
class CarlosRSI2Strategy(
    private val shortPeriod: Int = 2,
    private val longPeriod: Int = 15,
    private val rsiPeriod: Int = 2,
    private val rsiOversoldThreshold: Double = 5.0,
    private val rsiOverboughtThreshold: Double = 95.0
) {
    
    fun analyzeCandles(candles: CandleSeries): StrategyAnalysis {
        if (candles.size < maxOf(shortPeriod, longPeriod, rsiPeriod) + 1) {
            return StrategyAnalysis.empty()
        }
        
        // Extract price series using Series.α transformation
        val prices = candles.α { candle -> candle.a.ohlc.close }
        
        // Calculate technical indicators
        val rsiValues = calculateRSI(prices, rsiPeriod)
        val shortSMA = calculateSMA(prices, shortPeriod)
        val longSMA = calculateSMA(prices, longPeriod)
        
        // Generate trading signals
        val signals = generateSignals(prices, rsiValues, shortSMA, longSMA)
        
        // Analyze trend
        val trendAnalysis = analyzeTrend(shortSMA, longSMA)
        
        return StrategyAnalysis(
            signals = signals,
            rsiValues = rsiValues,
            shortSMA = shortSMA,
            longSMA = longSMA,
            trend = trendAnalysis,
            candles = candles
        )
    }
    
    private fun calculateRSI(prices: Series<Price>, period: Int): RSISeries {
        if (prices.size < period + 1) {
            return Series.of(0) { RSIValue(50.0) }
        }
        
        // Calculate price changes
        val priceChanges = Series.of(prices.size - 1) { i ->
            prices[i + 1].value - prices[i].value
        }
        
        // Separate gains and losses
        val gains = priceChanges.α { change -> if (change > 0) change else 0.0 }
        val losses = priceChanges.α { change -> if (change < 0) -change else 0.0 }
        
        val rsiResults = mutableListOf<RSIValue>()
        
        // Calculate initial average gain/loss
        if (gains.size >= period) {
            val initialGain = gains.▶.take(period).average()
            val initialLoss = losses.▶.take(period).average()
            
            var avgGain = initialGain
            var avgLoss = initialLoss
            
            // Calculate first RSI value
            val rs = if (avgLoss != 0.0) avgGain / avgLoss else Double.MAX_VALUE
            val rsi = 100.0 - (100.0 / (1.0 + rs))
            rsiResults.add(RSIValue(rsi))
            
            // Calculate subsequent RSI values using Wilder's smoothing
            for (i in period until gains.size) {
                avgGain = (avgGain * (period - 1) + gains[i]) / period
                avgLoss = (avgLoss * (period - 1) + losses[i]) / period
                
                val newRs = if (avgLoss != 0.0) avgGain / avgLoss else Double.MAX_VALUE
                val newRsi = 100.0 - (100.0 / (1.0 + newRs))
                rsiResults.add(RSIValue(newRsi))
            }
        }
        
        return Series.of(rsiResults.size) { i -> rsiResults[i] }
    }
    
    private fun calculateSMA(prices: Series<Price>, period: Int): SMASeries {
        if (prices.size < period) {
            return Series.of(0) { SMAValue(0.0) }
        }
        
        val smaResults = mutableListOf<SMAValue>()
        
        for (i in period - 1 until prices.size) {
            val sum = (i - period + 1..i).map { j -> prices[j].value }.sum()
            val sma = sum / period
            smaResults.add(SMAValue(sma))
        }
        
        return Series.of(smaResults.size) { i -> smaResults[i] }
    }
    
    private fun generateSignals(
        prices: Series<Price>,
        rsiValues: RSISeries,
        shortSMA: SMASeries,
        longSMA: SMASeries
    ): SignalSeries {
        val minSize = minOf(prices.size, rsiValues.size, shortSMA.size, longSMA.size)
        if (minSize == 0) {
            return Series.of(0) { TradeSignal.HOLD }
        }
        
        val signals = mutableListOf<TradeSignal>()
        
        for (i in 0 until minSize) {
            val price = prices[prices.size - minSize + i]
            val rsi = rsiValues[i]
            val shortSma = shortSMA[i]
            val longSma = longSMA[i]
            
            val signal = when {
                // Carlos RSI2 Buy Conditions
                isCarlosBuySignal(price, rsi, shortSma, longSma) -> TradeSignal.BUY
                
                // Carlos RSI2 Sell Conditions  
                isCarlosSellSignal(price, rsi, shortSma, longSma) -> TradeSignal.SELL
                
                else -> TradeSignal.HOLD
            }
            
            signals.add(signal)
        }
        
        return Series.of(signals.size) { i -> signals[i] }
    }
    
    private fun isCarlosBuySignal(
        price: Price,
        rsi: RSIValue,
        shortSma: SMAValue,
        longSma: SMAValue
    ): Boolean {
        return shortSma > longSma &&           // Trend: short SMA > long SMA (uptrend)
               rsi.isOversold(rsiOversoldThreshold) && // Signal 1: RSI < 5 (oversold)
               shortSma > price                    // Signal 2: short SMA > close (pullback)
    }
    
    private fun isCarlosSellSignal(
        price: Price,
        rsi: RSIValue,
        shortSma: SMAValue,
        longSma: SMAValue
    ): Boolean {
        return shortSma < longSma ||           // Trend: short SMA < long SMA (downtrend) OR
               rsi.isOverbought(rsiOverboughtThreshold) || // Signal 1: RSI > 95 (overbought) OR
               shortSma < price                    // Signal 2: short SMA < close (momentum shift)
    }
    
    private fun analyzeTrend(shortSMA: SMASeries, longSMA: SMASeries): TrendAnalysis {
        if (shortSMA.size == 0 || longSMA.size == 0) {
            return TrendDirection.SIDEWAYS j 0.0
        }
        
        val minSize = minOf(shortSMA.size, longSMA.size)
        val recentShort = shortSMA[minSize - 1].value
        val recentLong = longSMA[minSize - 1].value
        
        val direction = when {
            recentShort > recentLong * 1.001 -> TrendDirection.UP
            recentShort < recentLong * 0.999 -> TrendDirection.DOWN
            else -> TrendDirection.SIDEWAYS
        }
        
        val strength = kotlin.math.abs(recentShort - recentLong) / recentLong
        
        return direction j strength
    }
}

// Strategy analysis results
data class StrategyAnalysis(
    val signals: SignalSeries,
    val rsiValues: RSISeries,
    val shortSMA: SMASeries,
    val longSMA: SMASeries,
    val trend: TrendAnalysis,
    val candles: CandleSeries
) {
    val latestSignal: TradeSignal 
        get() = if (signals.size > 0) signals[signals.size - 1] else TradeSignal.HOLD
    
    val latestRSI: RSIValue
        get() = if (rsiValues.size > 0) rsiValues[rsiValues.size - 1] else RSIValue(50.0)
    
    val trendDirection: TrendDirection
        get() = trend.a
    
    val trendStrength: Double
        get() = trend.b
    
    companion object {
        fun empty(): StrategyAnalysis = StrategyAnalysis(
            signals = Series.of(0) { TradeSignal.HOLD },
            rsiValues = Series.of(0) { RSIValue(50.0) },
            shortSMA = Series.of(0) { SMAValue(0.0) },
            longSMA = Series.of(0) { SMAValue(0.0) },
            trend = TrendDirection.SIDEWAYS j 0.0,
            candles = Series.of(0) { error("Empty analysis") }
        )
    }
}

// Attention-driven Carlos strategy activator
class AttentionCarlosRSI2(
    private val strategy: CarlosRSI2Strategy = CarlosRSI2Strategy(),
    private val attentionActivator: AttentionStrategyActivator
) {
    
    fun analyzeWithAttention(symbol: Symbol, candles: CandleSeries): StrategyAnalysis {
        // Check if strategy should be activated for this symbol
        val shouldActivate = attentionActivator.shouldActivateStrategy(symbol, "CarlosRSI2")
        
        return if (shouldActivate) {
            strategy.analyzeCandles(candles)
        } else {
            StrategyAnalysis.empty()
        }
    }
    
    fun getActiveSymbols(maxCount: Int = 5): Series<Symbol> {
        return attentionActivator.getActiveSymbolsForStrategy("CarlosRSI2", maxCount)
    }
}