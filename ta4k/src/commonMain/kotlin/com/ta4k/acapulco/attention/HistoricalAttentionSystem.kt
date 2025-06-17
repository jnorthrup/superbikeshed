package com.ta4k.acapulco.attention

import borg.trikeshed.lib.*
import com.ta4k.acapulco.data.*
import kotlinx.datetime.Instant
import kotlin.math.*

/**
 * Historical attention system for identifying interesting patterns in Binance library data.
 * Uses TrikeShed patterns to focus on high-volatility and high-volume periods.
 */

// Attention value classes
@JvmInline
value class AttentionScore(val value: Double) {
    operator fun compareTo(other: AttentionScore): Int = value.compareTo(other.value)
    operator fun plus(other: AttentionScore): AttentionScore = AttentionScore(value + other.value)
    operator fun times(multiplier: Double): AttentionScore = AttentionScore(value * multiplier)
}

@JvmInline
value class VolatilityGauge(val value: Double) {
    fun toAttentionScore(): AttentionScore = AttentionScore(value * 100.0) // Scale volatility to attention
}

@JvmInline
value class VolumeGauge(val value: Double) {
    fun toAttentionScore(): AttentionScore = AttentionScore(ln(value + 1.0)) // Log scale for volume
}

@JvmInline
value class AttentionWindow(val startIndex: Int, val endIndex: Int, val score: AttentionScore)

// Core attention data structures using TrikeShed Join patterns
typealias GaugeReading = Join<VolatilityGauge, VolumeGauge>
typealias AttentionFocus = Join<Symbol, AttentionScore>
typealias AttentionSeries = Series<AttentionWindow>
typealias WeightedSymbol = Join<Symbol, AttentionScore>

// Historical attention analyzer
class HistoricalAttentionAnalyzer {
    
    fun analyzeCandles(candles: CandleSeries): AttentionSeries {
        if (candles.size < 2) {
            return Series.of(0) { error("Insufficient data") }
        }
        
        // Calculate attention windows using Series.α transformations
        val volatilityScores = calculateVolatilityScores(candles)
        val volumeScores = calculateVolumeScores(candles)
        
        // Combine volatility and volume into attention scores
        val attentionScores = combineGauges(volatilityScores, volumeScores)
        
        // Find high-attention windows
        return findAttentionWindows(attentionScores)
    }
    
    private fun calculateVolatilityScores(candles: CandleSeries): Series<VolatilityGauge> {
        // Calculate price volatility using Series transformations
        val priceChanges = Series.of(candles.size - 1) { i ->
            val current = candles[i + 1].a.ohlc.close.value
            val previous = candles[i].a.ohlc.close.value
            abs(current - previous) / previous
        }
        
        // Convert to volatility gauges
        return priceChanges.α { change -> VolatilityGauge(change) }
    }
    
    private fun calculateVolumeScores(candles: CandleSeries): Series<VolumeGauge> {
        // Calculate volume normalized by moving average
        val windowSize = minOf(20, candles.size)
        
        return Series.of(candles.size) { i ->
            val currentVolume = candles[i].a.volume.value
            
            // Calculate average volume in window
            val startIdx = maxOf(0, i - windowSize + 1)
            val endIdx = i + 1
            val avgVolume = (startIdx until endIdx).map { j -> 
                candles[j].a.volume.value 
            }.average()
            
            // Normalize current volume by average
            val normalizedVolume = if (avgVolume > 0) currentVolume / avgVolume else 1.0
            VolumeGauge(normalizedVolume)
        }
    }
    
    private fun combineGauges(volatilityScores: Series<VolatilityGauge>, volumeScores: Series<VolumeGauge>): Series<AttentionScore> {
        val minSize = minOf(volatilityScores.size, volumeScores.size)
        
        return Series.of(minSize) { i ->
            val volatilityAttention = volatilityScores[i].toAttentionScore()
            val volumeAttention = volumeScores[i].toAttentionScore()
            
            // Combine attention scores (weighted average)
            val combined = (volatilityAttention * 0.7) + (volumeAttention * 0.3)
            combined
        }
    }
    
    private fun findAttentionWindows(attentionScores: Series<AttentionScore>): AttentionSeries {
        val threshold = AttentionScore(2.0) // Configurable attention threshold
        val minWindowSize = 5
        val windows = mutableListOf<AttentionWindow>()
        
        var windowStart = -1
        var windowScore = AttentionScore(0.0)
        
        for (i in 0 until attentionScores.size) {
            val score = attentionScores[i]
            
            if (score >= threshold) {
                if (windowStart == -1) {
                    windowStart = i
                    windowScore = score
                } else {
                    windowScore = windowScore + score
                }
            } else {
                if (windowStart != -1 && i - windowStart >= minWindowSize) {
                    windows.add(AttentionWindow(windowStart, i - 1, windowScore))
                }
                windowStart = -1
                windowScore = AttentionScore(0.0)
            }
        }
        
        // Handle window at end of data
        if (windowStart != -1 && attentionScores.size - windowStart >= minWindowSize) {
            windows.add(AttentionWindow(windowStart, attentionScores.size - 1, windowScore))
        }
        
        return Series.of(windows.size) { i -> windows[i] }
    }
}

// Multi-symbol attention tracker
class HistoricalAttentionTracker(
    private val dataManager: HistoricalDataManager,
    private val analyzer: HistoricalAttentionAnalyzer = HistoricalAttentionAnalyzer()
) {
    
    fun analyzeAllSymbols(): Series<WeightedSymbol> {
        val symbols = dataManager.getAvailableSymbols()
        val weightedSymbols = mutableListOf<WeightedSymbol>()
        
        // Analyze each symbol using Series operations
        symbols.▶.forEach { symbol ->
            val candles = dataManager.getHotWindow(symbol)
            if (candles.size > 0) {
                val attentionWindows = analyzer.analyzeCandles(candles)
                
                // Calculate total attention score for this symbol
                val totalScore = if (attentionWindows.size > 0) {
                    attentionWindows.▶.fold(AttentionScore(0.0)) { acc, window -> 
                        acc + window.score 
                    }
                } else {
                    AttentionScore(0.0)
                }
                
                weightedSymbols.add(symbol j totalScore)
            }
        }
        
        // Sort by attention score (descending)
        val sortedSymbols = weightedSymbols.sortedByDescending { it.b.value }
        
        return Series.of(sortedSymbols.size) { i -> sortedSymbols[i] }
    }
    
    fun getMostAttentionSymbols(count: Int): Series<WeightedSymbol> {
        val allWeighted = analyzeAllSymbols()
        val actualCount = minOf(count, allWeighted.size)
        
        return Series.of(actualCount) { i -> allWeighted[i] }
    }
    
    fun getSymbolAttentionWindows(symbol: Symbol): AttentionSeries {
        val candles = dataManager.getHotWindow(symbol)
        return if (candles.size > 0) {
            analyzer.analyzeCandles(candles)
        } else {
            Series.of(0) { error("No data for symbol: ${symbol.value}") }
        }
    }
    
    fun getAttentionSummary(): AttentionSummary {
        val allWeighted = analyzeAllSymbols()
        val totalSymbols = allWeighted.size
        val highAttentionCount = allWeighted.▶.count { it.b.value > 5.0 }
        
        val avgAttention = if (totalSymbols > 0) {
            allWeighted.▶.map { it.b.value }.average()
        } else 0.0
        
        return AttentionSummary(
            totalSymbols = totalSymbols,
            highAttentionSymbols = highAttentionCount,
            averageAttentionScore = AttentionScore(avgAttention),
            topSymbol = if (allWeighted.size > 0) allWeighted[0].a else Symbol("NONE")
        )
    }
}

// Attention summary data class
data class AttentionSummary(
    val totalSymbols: Int,
    val highAttentionSymbols: Int,
    val averageAttentionScore: AttentionScore,
    val topSymbol: Symbol
)

// Attention-driven strategy activation
class AttentionStrategyActivator(
    private val attentionTracker: HistoricalAttentionTracker
) {
    
    fun getActiveSymbolsForStrategy(strategy: String, maxSymbols: Int = 5): Series<Symbol> {
        val topSymbols = attentionTracker.getMostAttentionSymbols(maxSymbols)
        
        // Extract just the symbols using Series.α transformation
        return topSymbols.α { weightedSymbol -> weightedSymbol.a }
    }
    
    fun shouldActivateStrategy(symbol: Symbol, strategy: String): Boolean {
        val attentionWindows = attentionTracker.getSymbolAttentionWindows(symbol)
        
        // Strategy-specific activation logic
        return when (strategy) {
            "CarlosRSI2" -> {
                // Activate Carlos RSI2 on moderate attention (good for mean reversion)
                attentionWindows.▶.any { it.score.value > 3.0 && it.score.value < 10.0 }
            }
            "KrakenSkimmer" -> {
                // Activate Kraken Skimmer on high attention (good for trending)
                attentionWindows.▶.any { it.score.value > 8.0 }
            }
            else -> false
        }
    }
}