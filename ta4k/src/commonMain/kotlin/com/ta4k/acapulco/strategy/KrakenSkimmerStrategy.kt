package com.ta4k.acapulco.strategy

import borg.trikeshed.lib.*
import com.ta4k.acapulco.data.*
import com.ta4k.acapulco.attention.*
import kotlinx.datetime.Instant

/**
 * Kraken Skimmer Strategy implementation using TrikeShed patterns.
 * Based on the JavaScript Kraken bot baseline tracking and profit skimming.
 * 
 * Core Logic:
 * 1. Track baseline values for each position
 * 2. Trigger harvest when price exceeds baseline by harvest percentage (3%)
 * 3. Trigger rebalance when price falls below baseline by rebalance percentage (4%)
 * 4. Adaptive dead zone mode adjusts triggers during low activity periods
 * 5. Portfolio-level crash protection when multiple assets decline
 */

// Skimmer value classes
@kotlin.jvm.JvmInline
value class BaselineValue(val value: Double) {
    operator fun times(multiplier: Double): BaselineValue = BaselineValue(value * multiplier)
    operator fun plus(other: BaselineValue): BaselineValue = BaselineValue(value + other.value)
    operator fun compareTo(other: BaselineValue): Int = value.compareTo(other.value)
}

@kotlin.jvm.JvmInline
value class HarvestTrigger(val percentage: Double) {
    fun applyToBaseline(baseline: BaselineValue): Price = Price(baseline.value * (1.0 + percentage / 100.0))
    fun reverseFromPrice(price: Price): BaselineValue = BaselineValue(price.value / (1.0 + percentage / 100.0))
}

@kotlin.jvm.JvmInline
value class RebalanceTrigger(val percentage: Double) {
    fun applyToBaseline(baseline: BaselineValue): Price = Price(baseline.value * (1.0 - percentage / 100.0))
}

@kotlin.jvm.JvmInline
value class DeviationPercent(val value: Double) {
    operator fun compareTo(other: DeviationPercent): Int = value.compareTo(other.value)
    operator fun compareTo(threshold: Double): Int = value.compareTo(threshold)
}

// Skimmer signals
enum class SkimmerAction {
    HARVEST_SELL, REBALANCE_BUY, HOLD
}

enum class MarketRegime {
    TRENDING, RANGING, VOLATILE, CRASH_PROTECTION
}

// Skimmer data structures using TrikeShed patterns
typealias BaselineSeries = Indexed<BaselineValue>
typealias DeviationSeries = Indexed<DeviationPercent>
typealias SkimmerSignalSeries = Indexed<SkimmerAction>
typealias PortfolioState = Join<MarketRegime, DeviationPercent>

// Baseline tracking for individual symbols
data class BaselineTracker(
    var baseline: BaselineValue,
    var lastUpdateTime: Instant,
    var cycleCount: Int = 0,
    var isAdaptiveMode: Boolean = false,
    var consecutiveFlags: Int = 0
) {
    fun updateBaseline(newBaseline: BaselineValue, timestamp: Instant) {
        baseline = newBaseline
        lastUpdateTime = timestamp
        cycleCount = 0
        consecutiveFlags = 0
    }
    
    fun calculateDeviation(currentPrice: Price): DeviationPercent {
        val deviation = (currentPrice.value - baseline.value) / baseline.value
        return DeviationPercent(deviation * 100.0)
    }
}

// Kraken Skimmer strategy implementation
class KrakenSkimmerStrategy(
    internal val harvestTriggerPercent: Double = 3.0,
    internal val rebalanceTriggerPercent: Double = 4.0,
    internal val adaptiveHarvestPercent: Double = 2.0,
    internal val adaptiveRebalancePercent: Double = 2.0,
    internal val confirmationCycles: Int = 3,
    internal val adaptiveConfirmationCycles: Int = 4,
    internal val adaptiveTimeoutHours: Int = 3
) {
    
    internal val baselineTrackers = mutableMapOf<Symbol, BaselineTracker>()
    
    fun analyzeSymbol(symbol: Symbol, candles: CandleSeries): SkimmerAnalysis {
        if (candles.size == 0) {
            return SkimmerAnalysis.empty(symbol)
        }
        
        // Get or create baseline tracker
        val tracker = getOrCreateTracker(symbol, candles)
        
        // Get current price
        val currentPrice = candles[candles.size - 1].component1().ohlc.close
        val currentTime = candles[candles.size - 1].component2()
        
        // Update adaptive mode status
        updateAdaptiveMode(tracker, currentTime)
        
        // Calculate deviation
        val deviation = tracker.calculateDeviation(currentPrice)
        
        // Determine triggers based on mode
        val (harvestTrigger, rebalanceTrigger, requiredCycles) = if (tracker.isAdaptiveMode) {
            Triple(
                HarvestTrigger(adaptiveHarvestPercent),
                RebalanceTrigger(adaptiveRebalancePercent),
                adaptiveConfirmationCycles
            )
        } else {
            Triple(
                HarvestTrigger(harvestTriggerPercent),
                RebalanceTrigger(rebalanceTriggerPercent),
                confirmationCycles
            )
        }
        
        // Generate skimmer signal
        val signal = generateSkimmerSignal(
            currentPrice, 
            tracker, 
            deviation, 
            harvestTrigger, 
            rebalanceTrigger, 
            requiredCycles
        )
        
        // Calculate recent volatility for market regime analysis
        val volatility = calculateRecentVolatility(candles)
        val marketRegime = determineMarketRegime(volatility, deviation)
        
        return SkimmerAnalysis(
            symbol = symbol,
            signal = signal,
            currentPrice = currentPrice,
            baseline = tracker.baseline,
            deviation = deviation,
            isAdaptiveMode = tracker.isAdaptiveMode,
            cycleCount = tracker.cycleCount,
            requiredCycles = requiredCycles,
            marketRegime = marketRegime,
            candles = candles
        )
    }
    
    internal fun getOrCreateTracker(symbol: Symbol, candles: CandleSeries): BaselineTracker {
        return baselineTrackers.getOrPut(symbol) {
            // Initialize baseline with current price
            val currentPrice = candles[candles.size - 1].component1().ohlc.close
            val currentTime = candles[candles.size - 1].component2()
            BaselineTracker(
                baseline = BaselineValue(currentPrice.value),
                lastUpdateTime = currentTime
            )
        }
    }
    
    internal fun updateAdaptiveMode(tracker: BaselineTracker, currentTime: Instant) {
        val timeSinceUpdate = currentTime.toEpochMilliseconds() - tracker.lastUpdateTime.toEpochMilliseconds()
        val adaptiveTimeoutMillis = adaptiveTimeoutHours * 60 * 60 * 1000L
        
        // Check if we should enter adaptive mode (low activity timeout)
        if (!tracker.isAdaptiveMode && timeSinceUpdate > adaptiveTimeoutMillis) {
            tracker.isAdaptiveMode = true
            tracker.cycleCount = 0
        }
    }
    
    internal fun generateSkimmerSignal(
        currentPrice: Price,
        tracker: BaselineTracker,
        deviation: DeviationPercent,
        harvestTrigger: HarvestTrigger,
        rebalanceTrigger: RebalanceTrigger,
        requiredCycles: Int
    ): SkimmerAction {
        
        val harvestThreshold = harvestTrigger.percentage
        val rebalanceThreshold = -rebalanceTrigger.percentage
        
        return when {
            // Harvest conditions
            deviation >= harvestThreshold -> {
                tracker.consecutiveFlags++
                if (tracker.consecutiveFlags >= requiredCycles) {
                    tracker.consecutiveFlags = 0
                    SkimmerAction.HARVEST_SELL
                } else {
                    SkimmerAction.HOLD
                }
            }
            
            // Rebalance conditions
            deviation <= rebalanceThreshold -> {
                tracker.consecutiveFlags++
                if (tracker.consecutiveFlags >= requiredCycles) {
                    tracker.consecutiveFlags = 0
                    SkimmerAction.REBALANCE_BUY
                } else {
                    SkimmerAction.HOLD
                }
            }
            
            // Reset counter if back in dead zone
            else -> {
                // Exit adaptive mode if price returns to normal range
                if (tracker.isAdaptiveMode && kotlin.math.abs(deviation.value) < 1.0) {
                    tracker.isAdaptiveMode = false
                }
                tracker.consecutiveFlags = 0
                SkimmerAction.HOLD
            }
        }
    }
    
    internal fun calculateRecentVolatility(candles: CandleSeries): Double {
        if (candles.size < 2) return 0.0
        
        val recentCount = kotlin.math.min(20, candles.size)
        val startIdx = candles.size - recentCount
        
        val returns = (startIdx until candles.size - 1).map { i ->
            val current = candles[i + 1].component1().ohlc.close.value
            val previous = candles[i].component1().ohlc.close.value
            kotlin.math.ln(current / previous)
        }
        
        return if (returns.isNotEmpty()) {
            val mean = returns.average()
            kotlin.math.sqrt(returns.map { (it - mean) * (it - mean) }.average())
        } else {
            0.0
        }
    }
    
    internal fun determineMarketRegime(volatility: Double, deviation: DeviationPercent): MarketRegime {
        return when {
            kotlin.math.abs(deviation.value) > 10.0 -> MarketRegime.CRASH_PROTECTION
            volatility > 0.05 -> MarketRegime.VOLATILE
            kotlin.math.abs(deviation.value) > 2.0 -> MarketRegime.TRENDING
            else -> MarketRegime.RANGING
        }
    }
    
    fun updateBaseline(symbol: Symbol, newBaseline: BaselineValue, timestamp: Instant) {
        val tracker = baselineTrackers[symbol]
        if (tracker != null) {
            tracker.updateBaseline(newBaseline, timestamp)
        }
    }
    
    fun getTrackedSymbols(): Indexed<Symbol> {
        val symbols = baselineTrackers.keys.toList()
        return Indexed.of(symbols.size) { i -> symbols[i] }
    }
}

// Skimmer analysis results
data class SkimmerAnalysis(
    val symbol: Symbol,
    val signal: SkimmerAction,
    val currentPrice: Price,
    val baseline: BaselineValue,
    val deviation: DeviationPercent,
    val isAdaptiveMode: Boolean,
    val cycleCount: Int,
    val requiredCycles: Int,
    val marketRegime: MarketRegime,
    val candles: CandleSeries
) {
    val shouldHarvest: Boolean
        get() = signal == SkimmerAction.HARVEST_SELL
    
    val shouldRebalance: Boolean
        get() = signal == SkimmerAction.REBALANCE_BUY
    
    val isInDeadZone: Boolean
        get() = signal == SkimmerAction.HOLD && kotlin.math.abs(deviation.value) < 2.0
    
    companion object {
        fun empty(symbol: Symbol): SkimmerAnalysis = SkimmerAnalysis(
            symbol = symbol,
            signal = SkimmerAction.HOLD,
            currentPrice = Price(0.0),
            baseline = BaselineValue(0.0),
            deviation = DeviationPercent(0.0),
            isAdaptiveMode = false,
            cycleCount = 0,
            requiredCycles = 3,
            marketRegime = MarketRegime.RANGING,
            candles = Indexed.of(0) { error("Empty analysis") }
        )
    }
}

// Portfolio-level skimmer for multiple symbols
class PortfolioSkimmer(
    internal val skimmerStrategy: KrakenSkimmerStrategy = KrakenSkimmerStrategy(),
    internal val crashProtectionThreshold: Double = 70.0, // % of assets declining
    internal val portfolioDeviationThreshold: Double = 5.0 // % portfolio deviation
) {
    
    fun analyzePortfolio(symbolData: Indexed<Join<Symbol, CandleSeries>>): PortfolioSkimmerAnalysis {
        val analyses = symbolData.α { (symbol, candles) ->
            skimmerStrategy.analyzeSymbol(symbol, candles)
        }
        
        // Calculate portfolio-level metrics
        val totalSymbols = analyses.size
        val decliningSymbols = analyses.play.count { it.deviation.value < -1.0 }
        val crashProtectionActive = totalSymbols > 0 && 
            (decliningSymbols.toDouble() / totalSymbols) >= (crashProtectionThreshold / 100.0)
        
        // Calculate portfolio deviation
        val avgDeviation = if (analyses.size > 0) {
            analyses.play.map { it.deviation.value }.average()
        } else 0.0
        
        val portfolioState = if (crashProtectionActive) {
            MarketRegime.CRASH_PROTECTION j DeviationPercent(avgDeviation)
        } else {
            MarketRegime.RANGING j DeviationPercent(avgDeviation)
        }
        
        return PortfolioSkimmerAnalysis(
            symbolAnalyses = analyses,
            portfolioState = portfolioState,
            crashProtectionActive = crashProtectionActive,
            totalSymbols = totalSymbols,
            decliningSymbols = decliningSymbols
        )
    }
}

// Portfolio analysis results
data class PortfolioSkimmerAnalysis(
    val symbolAnalyses: Indexed<SkimmerAnalysis>,
    val portfolioState: PortfolioState,
    val crashProtectionActive: Boolean,
    val totalSymbols: Int,
    val decliningSymbols: Int
) {
    val harvestCandidates: Indexed<SkimmerAnalysis>
        get() = symbolAnalyses.play.filter { it.shouldHarvest }.let { candidates ->
            Indexed.of(candidates.size) { i -> candidates[i] }
        }
    
    val rebalanceCandidates: Indexed<SkimmerAnalysis>
        get() = symbolAnalyses.play.filter { it.shouldRebalance }.let { candidates ->
            Indexed.of(candidates.size) { i -> candidates[i] }
        }
    
    val portfolioDeviation: DeviationPercent
        get() = portfolioState.component2()
    
    val marketRegime: MarketRegime
        get() = portfolioState.component1()
}

// Attention-driven Kraken skimmer
class AttentionKrakenSkimmer(
    internal val portfolioSkimmer: PortfolioSkimmer = PortfolioSkimmer(),
    internal val attentionActivator: AttentionStrategyActivator
) {
    
    fun analyzeWithAttention(symbolData: Indexed<Join<Symbol, CandleSeries>>): PortfolioSkimmerAnalysis {
        // Filter to only high-attention symbols for skimmer strategy
        val attentionFiltered = symbolData.play.filter { (symbol, _) ->
            attentionActivator.shouldActivateStrategy(symbol, "KrakenSkimmer")
        }.let { filtered ->
            Indexed.of(filtered.size) { i -> filtered[i] }
        }
        
        return portfolioSkimmer.analyzePortfolio(attentionFiltered)
    }
    
    fun getActiveSymbols(maxCount: Int = 10): Indexed<Symbol> {
        return attentionActivator.getActiveSymbolsForStrategy("KrakenSkimmer", maxCount)
    }
}