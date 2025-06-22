package borg.trikeshed.brokeshed

import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Technical Analysis using TrikeShed's functional programming approach
 */
object TechnicalAnalysis {
    
    /**
     * Simple Moving Average using TrikeShed α transforms
     */
    fun sma(prices: Indexed<Price>, period: Int): Indexed<Price> {
        require(period > 0) { "Period must be positive" }
        
        return prices.size j { i ->
            if (i < period - 1) {
                prices[0] // Not enough data, return first price
            } else {
                val sum = (i - period + 1..i).sumOf { prices[it] }
                sum / period
            }
        }
    }
    
    /**
     * Exponential Moving Average
     */
    fun ema(prices: Indexed<Price>, period: Int): Indexed<Price> {
        require(period > 0) { "Period must be positive" }
        val alpha = 2.0 / (period + 1)
        
        return prices.size j { i ->
            if (i == 0) {
                prices[0]
            } else {
                val previousEma = if (i == 1) prices[0] else {
                    // Recursive calculation - in production would use memoization
                    var ema = prices[0]
                    for (j in 1..i-1) {
                        ema = alpha * prices[j] + (1 - alpha) * ema
                    }
                    ema
                }
                alpha * prices[i] + (1 - alpha) * previousEma
            }
        }
    }
    
    /**
     * Relative Strength Index (RSI)
     */
    fun rsi(prices: Indexed<Price>, period: Int = 14): Indexed<Double> {
        require(period > 0) { "Period must be positive" }
        require(prices.size > period) { "Need more data points than period" }
        
        return prices.size j { i ->
            if (i < period) {
                50.0 // Neutral RSI when not enough data
            } else {
                var gains = 0.0
                var losses = 0.0
                
                for (j in i - period + 1..i) {
                    val change = prices[j] - prices[j - 1]
                    if (change > 0) gains += change
                    else losses += abs(change)
                }
                
                val avgGain = gains / period
                val avgLoss = losses / period
                
                if (avgLoss == 0.0) 100.0
                else {
                    val rs = avgGain / avgLoss
                    100.0 - (100.0 / (1.0 + rs))
                }
            }
        }
    }
    
    /**
     * Bollinger Bands
     */
    data class BollingerBands(
        val upper: Indexed<Price>,
        val middle: Indexed<Price>,
        val lower: Indexed<Price>
    )
    
    fun bollingerBands(prices: Indexed<Price>, period: Int = 20, multiplier: Double = 2.0): BollingerBands {
        val sma = sma(prices, period)
        
        val standardDeviation = prices.size j { i ->
            if (i < period - 1) {
                0.0
            } else {
                val mean = sma[i]
                val variance = (i - period + 1..i).sumOf { j ->
                    val diff = prices[j] - mean
                    diff * diff
                } / period
                sqrt(variance)
            }
        }
        
        val upper = prices.size j { i -> sma[i] + multiplier * standardDeviation[i] }
        val lower = prices.size j { i -> sma[i] - multiplier * standardDeviation[i] }
        
        return BollingerBands(upper, sma, lower)
    }
    
    /**
     * MACD (Moving Average Convergence Divergence)
     */
    data class MACD(
        val macdLine: Indexed<Price>,
        val signalLine: Indexed<Price>,
        val histogram: Indexed<Price>
    )
    
    fun macd(prices: Indexed<Price>, fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9): MACD {
        val fastEma = ema(prices, fastPeriod)
        val slowEma = ema(prices, slowPeriod)
        
        val macdLine = prices.size j { i -> fastEma[i] - slowEma[i] }
        val signalLine = ema(macdLine, signalPeriod)
        val histogram = prices.size j { i -> macdLine[i] - signalLine[i] }
        
        return MACD(macdLine, signalLine, histogram)
    }
    
    /**
     * Stochastic Oscillator
     */
    data class Stochastic(
        val percentK: Indexed<Double>,
        val percentD: Indexed<Double>
    )
    
    fun stochastic(highs: Indexed<Price>, lows: Indexed<Price>, closes: Indexed<Price>, period: Int = 14): Stochastic {
        val percentK = closes.size j { i ->
            if (i < period - 1) {
                50.0
            } else {
                val highestHigh = (i - period + 1..i).maxOf { highs[it] }
                val lowestLow = (i - period + 1..i).minOf { lows[it] }
                
                if (highestHigh == lowestLow) 50.0
                else ((closes[i] - lowestLow) / (highestHigh - lowestLow)) * 100.0
            }
        }
        
        val percentD = sma(percentK.size j { i -> percentK[i] }, 3)
        
        return Stochastic(percentK, percentD)
    }
    
    /**
     * Volume Weighted Average Price (VWAP)
     */
    fun vwap(prices: Indexed<Price>, volumes: Indexed<Volume>): Indexed<Price> {
        require(prices.size == volumes.size) { "Prices and volumes must have same size" }
        
        return prices.size j { i ->
            var totalPriceVolume = 0.0
            var totalVolume = 0L
            
            for (j in 0..i) {
                totalPriceVolume += prices[j] * volumes[j]
                totalVolume += volumes[j]
            }
            
            if (totalVolume == 0L) prices[i]
            else totalPriceVolume / totalVolume
        }
    }
    
    /**
     * Average True Range (ATR)
     */
    fun atr(highs: Indexed<Price>, lows: Indexed<Price>, closes: Indexed<Price>, period: Int = 14): Indexed<Price> {
        val trueRanges = closes.size j { i ->
            if (i == 0) {
                highs[0] - lows[0]
            } else {
                val tr1 = highs[i] - lows[i]
                val tr2 = abs(highs[i] - closes[i - 1])
                val tr3 = abs(lows[i] - closes[i - 1])
                maxOf(tr1, tr2, tr3)
            }
        }
        
        return sma(trueRanges, period)
    }
}

/**
 * Technical indicator extensions for OHLCV data
 */
fun OHLCVSeries.extractPrices(field: String): Indexed<Price> = this.size j { i ->
    val ohlcv = this[i]
    when (field.lowercase()) {
        "open" -> ohlcv.open
        "high" -> ohlcv.high
        "low" -> ohlcv.low
        "close" -> ohlcv.close
        else -> ohlcv.close
    }
}

fun OHLCVSeries.extractVolumes(): Indexed<Volume> = this.size j { i -> this[i].volume }

/**
 * Convenience methods for common technical analysis
 */
fun OHLCVSeries.sma(period: Int, field: String = "close"): Indexed<Price> = 
    TechnicalAnalysis.sma(extractPrices(field), period)

fun OHLCVSeries.ema(period: Int, field: String = "close"): Indexed<Price> = 
    TechnicalAnalysis.ema(extractPrices(field), period)

fun OHLCVSeries.rsi(period: Int = 14, field: String = "close"): Indexed<Double> = 
    TechnicalAnalysis.rsi(extractPrices(field), period)

fun OHLCVSeries.bollingerBands(period: Int = 20, multiplier: Double = 2.0, field: String = "close"): TechnicalAnalysis.BollingerBands = 
    TechnicalAnalysis.bollingerBands(extractPrices(field), period, multiplier)

fun OHLCVSeries.macd(fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9, field: String = "close"): TechnicalAnalysis.MACD = 
    TechnicalAnalysis.macd(extractPrices(field), fastPeriod, slowPeriod, signalPeriod)

fun OHLCVSeries.stochastic(period: Int = 14): TechnicalAnalysis.Stochastic = 
    TechnicalAnalysis.stochastic(extractPrices("high"), extractPrices("low"), extractPrices("close"), period)

fun OHLCVSeries.vwap(): Indexed<Price> = 
    TechnicalAnalysis.vwap(extractPrices("close"), extractVolumes())

fun OHLCVSeries.atr(period: Int = 14): Indexed<Price> = 
    TechnicalAnalysis.atr(extractPrices("high"), extractPrices("low"), extractPrices("close"), period)