package com.ta4k.attention

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import java.time.Instant
import kotlin.math.abs

/**
 * Attention system for analyzing historical price patterns.
 * Identifies and tracks significant patterns in price action, volume, and indicators.
 */
class HistoricalPatternAttention(
    private val windowSize: Int = 100,  // Number of candles to analyze at once
    private val minConfidence: Double = 0.7  // Minimum confidence threshold for patterns
) {
    // Pattern categories
    enum class PatternType {
        PRICE_ACTION,    // Price movement patterns
        VOLUME,         // Volume patterns
        INDICATOR,      // Technical indicator patterns
        COMBINED        // Patterns combining multiple factors
    }

    // Pattern data class
    data class Pattern(
        val type: PatternType,
        val startIndex: Int,
        val endIndex: Int,
        val confidence: Double,
        val description: String,
        val metadata: Map<String, Any> = emptyMap()
    )

    // Internal state
    private val patterns = mutableListOf<Pattern>()
    private var lastAnalysisIndex = -1

    /**
     * Analyze a new batch of klines for patterns
     */
    fun analyze(klineSeries: Indexed<Kline>, startIndex: Int = 0): Indexed<Pattern> {
        if (startIndex < 0 || startIndex >= klineSeries.size) {
            return 0 j { _: Int -> null }
        }

        val endIndex = minOf(startIndex + windowSize, klineSeries.size)
        val window = klineSeries.slice(startIndex until endIndex)

        // Analyze different pattern types
        val pricePatterns = analyzePricePatterns(window, startIndex)
        val volumePatterns = analyzeVolumePatterns(window, startIndex)
        val indicatorPatterns = analyzeIndicatorPatterns(window, startIndex)
        val combinedPatterns = analyzeCombinedPatterns(window, startIndex)

        // Combine and filter patterns
        val allPatterns = (pricePatterns + volumePatterns + indicatorPatterns + combinedPatterns)
            .filter { it.confidence >= minConfidence }
            .sortedByDescending { it.confidence }

        // Update internal state
        patterns.addAll(allPatterns)
        lastAnalysisIndex = endIndex - 1

        return allPatterns.size j { idx: Int -> allPatterns.getOrNull(idx) }
    }

    /**
     * Analyze price action patterns
     */
    private fun analyzePricePatterns(window: Indexed<Kline>, startIndex: Int): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        
        // Detect trend patterns
        var trendStart = 0
        var trendDirection = 0
        var trendStrength = 0.0
        
        for (i in 1 until window.size) {
            val priceChange = window[i].closePrice - window[i-1].closePrice
            val currentDirection = if (priceChange > 0) 1 else if (priceChange < 0) -1 else 0
            
            if (currentDirection == trendDirection) {
                trendStrength += abs(priceChange.toDouble())
            } else {
                // End of trend
                if (trendStrength > 0) {
                    patterns.add(Pattern(
                        type = PatternType.PRICE_ACTION,
                        startIndex = startIndex + trendStart,
                        endIndex = startIndex + i - 1,
                        confidence = minOf(0.9, trendStrength / 100),
                        description = if (trendDirection > 0) "Uptrend" else "Downtrend",
                        metadata = mapOf(
                            "strength" to trendStrength,
                            "direction" to trendDirection
                        )
                    ))
                }
                trendStart = i
                trendDirection = currentDirection
                trendStrength = abs(priceChange.toDouble())
            }
        }
        
        return patterns
    }

    /**
     * Analyze volume patterns
     */
    private fun analyzeVolumePatterns(window: Indexed<Kline>, startIndex: Int): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        
        // Calculate volume moving average
        val volumeMA = calculateVolumeMA(window)
        
        // Detect volume spikes
        for (i in 0 until window.size) {
            val volume = window[i].volume
            val avgVolume = volumeMA[i]
            
            if (volume > avgVolume * 2) {  // Volume spike threshold
                patterns.add(Pattern(
                    type = PatternType.VOLUME,
                    startIndex = startIndex + i,
                    endIndex = startIndex + i,
                    confidence = minOf(0.9, volume / (avgVolume * 3)),
                    description = "Volume Spike",
                    metadata = mapOf(
                        "volume" to volume,
                        "avg_volume" to avgVolume,
                        "ratio" to (volume / avgVolume)
                    )
                ))
            }
        }
        
        return patterns
    }

    /**
     * Analyze technical indicator patterns
     */
    private fun analyzeIndicatorPatterns(window: Indexed<Kline>, startIndex: Int): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        
        // RSI patterns
        val rsiValues = calculateRSI(window)
        for (i in 1 until rsiValues.size) {
            // Oversold condition
            if (rsiValues[i] < 30 && rsiValues[i-1] < 30) {
                patterns.add(Pattern(
                    type = PatternType.INDICATOR,
                    startIndex = startIndex + i - 1,
                    endIndex = startIndex + i,
                    confidence = 0.8,
                    description = "RSI Oversold",
                    metadata = mapOf(
                        "indicator" to "RSI",
                        "value" to rsiValues[i]
                    )
                ))
            }
            // Overbought condition
            if (rsiValues[i] > 70 && rsiValues[i-1] > 70) {
                patterns.add(Pattern(
                    type = PatternType.INDICATOR,
                    startIndex = startIndex + i - 1,
                    endIndex = startIndex + i,
                    confidence = 0.8,
                    description = "RSI Overbought",
                    metadata = mapOf(
                        "indicator" to "RSI",
                        "value" to rsiValues[i]
                    )
                ))
            }
        }
        
        return patterns
    }

    /**
     * Analyze combined patterns (price + volume + indicators)
     */
    private fun analyzeCombinedPatterns(window: Indexed<Kline>, startIndex: Int): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        
        // Example: Volume confirmation of price movement
        for (i in 1 until window.size) {
            val priceChange = window[i].closePrice - window[i-1].closePrice
            val volumeChange = window[i].volume - window[i-1].volume
            
            if (abs(priceChange.toDouble()) > 0 && priceChange * volumeChange > 0) {
                patterns.add(Pattern(
                    type = PatternType.COMBINED,
                    startIndex = startIndex + i - 1,
                    endIndex = startIndex + i,
                    confidence = 0.75,
                    description = "Volume Confirmed Move",
                    metadata = mapOf(
                        "price_change" to priceChange,
                        "volume_change" to volumeChange
                    )
                ))
            }
        }
        
        return patterns
    }

    /**
     * Calculate volume moving average
     */
    private fun calculateVolumeMA(window: Indexed<Kline>): Indexed<Double> {
        val period = 20
        return window.size j { i: Int ->
            if (i < period - 1) {
                window[i].volume.toDouble()
            } else {
                var sum = 0.0
                for (j in 0 until period) {
                    sum += window[i - j].volume.toDouble()
                }
                sum / period
            }
        }
    }

    /**
     * Calculate RSI values
     */
    private fun calculateRSI(window: Indexed<Kline>): Indexed<Double> {
        val period = 14
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        
        // Calculate price changes
        for (i in 1 until window.size) {
            val change = window[i].closePrice - window[i-1].closePrice
            gains.add(if (change > 0) change.toDouble() else 0.0)
            losses.add(if (change < 0) -change.toDouble() else 0.0)
        }
        
        // Calculate RSI
        return window.size j { i: Int ->
            if (i < period) {
                50.0  // Default value for initial period
            } else {
                var avgGain = 0.0
                var avgLoss = 0.0
                
                // First average
                if (i == period) {
                    for (j in 0 until period) {
                        avgGain += gains[j]
                        avgLoss += losses[j]
                    }
                    avgGain /= period
                    avgLoss /= period
                } else {
                    // Subsequent averages using Wilder's smoothing
                    avgGain = ((avgGain * (period - 1)) + gains[i-1]) / period
                    avgLoss = ((avgLoss * (period - 1)) + losses[i-1]) / period
                }
                
                if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain/avgLoss))
            }
        }
    }

    /**
     * Get all patterns that overlap with a given range
     */
    fun getPatternsInRange(startIndex: Int, endIndex: Int): Indexed<Pattern> {
        val filteredPatterns = patterns.filter { pattern ->
            pattern.startIndex <= endIndex && pattern.endIndex >= startIndex
        }
        return filteredPatterns.size j { idx: Int -> filteredPatterns.getOrNull(idx) }
    }

    /**
     * Get the most recent patterns
     */
    fun getRecentPatterns(limit: Int = 10): Indexed<Pattern> {
        val recentPatterns = patterns.takeLast(limit)
        return recentPatterns.size j { idx: Int -> recentPatterns.getOrNull(idx) }
    }
} 