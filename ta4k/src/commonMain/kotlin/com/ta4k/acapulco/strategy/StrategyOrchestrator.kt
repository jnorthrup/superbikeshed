package com.ta4k.acapulco.strategy

import borg.trikeshed.lib.*
import com.ta4k.acapulco.data.*
import com.ta4k.acapulco.attention.*
import kotlinx.datetime.Instant

/**
 * Strategy Orchestrator that combines Carlos RSI2 and Kraken Skimmer strategies
 * using attention-driven symbol selection and TrikeShed patterns.
 */

// Unified strategy signals
@kotlin.jvm.JvmInline
value class StrategyConfidence(val value: Double) {
    operator fun compareTo(other: StrategyConfidence): Int = value.compareTo(other.value)
    operator fun times(multiplier: Double): StrategyConfidence = StrategyConfidence(value * multiplier)
}

@kotlin.jvm.JvmInline
value class StrategyWeight(val value: Double) {
    operator fun times(confidence: StrategyConfidence): Double = value * confidence.value
}

// Combined strategy output
enum class CombinedSignal {
    STRONG_BUY,    // Both strategies agree on buy
    BUY,           // One strategy says buy, other neutral
    WEAK_BUY,      // Conflicting signals but buy bias
    HOLD,          // No clear signal or conflicting
    WEAK_SELL,     // Conflicting signals but sell bias
    SELL,          // One strategy says sell, other neutral
    STRONG_SELL    // Both strategies agree on sell
}

// Strategy combination logic
data class StrategyVote(
    val signal: TradeSignal,
    val confidence: StrategyConfidence,
    val weight: StrategyWeight,
    val source: String
)

// Combined analysis results
data class CombinedAnalysis(
    val symbol: Symbol,
    val combinedSignal: CombinedSignal,
    val carlosAnalysis: StrategyAnalysis,
    val skimmerAnalysis: SkimmerAnalysis,
    val attentionScore: AttentionScore,
    val votes: Indexed<StrategyVote>,
    val timestamp: Instant
) {
    val isActionable: Boolean
        get() = combinedSignal != CombinedSignal.HOLD
    
    val isBuySignal: Boolean
        get() = combinedSignal in setOf(CombinedSignal.STRONG_BUY, CombinedSignal.BUY, CombinedSignal.WEAK_BUY)
    
    val isSellSignal: Boolean
        get() = combinedSignal in setOf(CombinedSignal.STRONG_SELL, CombinedSignal.SELL, CombinedSignal.WEAK_SELL)
    
    val signalStrength: Double
        get() = when (combinedSignal) {
            CombinedSignal.STRONG_BUY -> 1.0
            CombinedSignal.BUY -> 0.7
            CombinedSignal.WEAK_BUY -> 0.3
            CombinedSignal.HOLD -> 0.0
            CombinedSignal.WEAK_SELL -> -0.3
            CombinedSignal.SELL -> -0.7
            CombinedSignal.STRONG_SELL -> -1.0
        }
}

// Strategy orchestrator implementation
class StrategyOrchestrator(
    internal val historicalDataManager: HistoricalDataManager,
    internal val attentionTracker: HistoricalAttentionTracker,
    internal val carlosStrategy: AttentionCarlosRSI2,
    internal val krakenSkimmer: AttentionKrakenSkimmer
) {
    
    fun analyzeSymbol(symbol: Symbol): CombinedAnalysis {
        val candles = historicalDataManager.getHotWindow(symbol)
        val timestamp = if (candles.size > 0) {
            candles[candles.size - 1].b
        } else {
            kotlinx.datetime.Clock.System.now()
        }
        
        // Get attention score for this symbol
        val attentionWindows = attentionTracker.getSymbolAttentionWindows(symbol)
        val attentionScore = if (attentionWindows.size > 0) {
            attentionWindows.play.map { it.score }.maxOrNull() ?: AttentionScore(0.0)
        } else {
            AttentionScore(0.0)
        }
        
        // Run both strategies
        val carlosAnalysis = carlosStrategy.analyzeWithAttention(symbol, candles)
        val skimmerAnalysis = krakenSkimmer.analyzeWithAttention(
            Indexed.of(1) { symbol j candles }
        ).symbolAnalyses.let { analyses ->
            if (analyses.size > 0) analyses[0] else SkimmerAnalysis.empty(symbol)
        }
        
        // Create strategy votes
        val votes = createStrategyVotes(carlosAnalysis, skimmerAnalysis, attentionScore)
        
        // Combine signals
        val combinedSignal = combineSignals(votes)
        
        return CombinedAnalysis(
            symbol = symbol,
            combinedSignal = combinedSignal,
            carlosAnalysis = carlosAnalysis,
            skimmerAnalysis = skimmerAnalysis,
            attentionScore = attentionScore,
            votes = votes,
            timestamp = timestamp
        )
    }
    
    internal fun createStrategyVotes(
        carlosAnalysis: StrategyAnalysis,
        skimmerAnalysis: SkimmerAnalysis,
        attentionScore: AttentionScore
    ): Indexed<StrategyVote> {
        val votes = mutableListOf<StrategyVote>()
        
        // Carlos RSI2 vote
        val carlosSignal = convertCarlosSignal(carlosAnalysis.latestSignal)
        val carlosConfidence = calculateCarlosConfidence(carlosAnalysis)
        val carlosWeight = StrategyWeight(0.6) // Base weight for mean reversion
        
        votes.add(StrategyVote(
            signal = carlosSignal,
            confidence = carlosConfidence,
            weight = carlosWeight,
            source = "Carlos RSI2"
        ))
        
        // Kraken Skimmer vote
        val skimmerSignal = convertSkimmerSignal(skimmerAnalysis.signal)
        val skimmerConfidence = calculateSkimmerConfidence(skimmerAnalysis)
        val skimmerWeight = StrategyWeight(0.4) // Base weight for momentum/trend
        
        votes.add(StrategyVote(
            signal = skimmerSignal,
            confidence = skimmerConfidence,
            weight = skimmerWeight,
            source = "Kraken Skimmer"
        ))
        
        return Indexed.of(votes.size) { i -> votes[i] }
    }
    
    internal fun convertCarlosSignal(carlosSignal: TradeSignal): TradeSignal = carlosSignal
    
    internal fun convertSkimmerSignal(skimmerAction: SkimmerAction): TradeSignal {
        return when (skimmerAction) {
            SkimmerAction.HARVEST_SELL -> TradeSignal.SELL
            SkimmerAction.REBALANCE_BUY -> TradeSignal.BUY
            SkimmerAction.HOLD -> TradeSignal.HOLD
        }
    }
    
    internal fun calculateCarlosConfidence(analysis: StrategyAnalysis): StrategyConfidence {
        // Higher confidence when RSI is extreme and trend is clear
        val rsiExtremity = when {
            analysis.latestRSI.value < 10 || analysis.latestRSI.value > 90 -> 0.9
            analysis.latestRSI.value < 20 || analysis.latestRSI.value > 80 -> 0.7
            analysis.latestRSI.value < 30 || analysis.latestRSI.value > 70 -> 0.5
            else -> 0.3
        }
        
        val trendClarity = kotlin.math.min(analysis.trendStrength * 10.0, 1.0)
        val confidence = (rsiExtremity + trendClarity) / 2.0
        
        return StrategyConfidence(confidence)
    }
    
    internal fun calculateSkimmerConfidence(analysis: SkimmerAnalysis): StrategyConfidence {
        // Higher confidence when deviation is significant and cycles are complete
        val deviationSignificance = kotlin.math.min(kotlin.math.abs(analysis.deviation.value) / 10.0, 1.0)
        val cycleProgress = analysis.cycleCount.toDouble() / analysis.requiredCycles.toDouble()
        val confidence = (deviationSignificance + cycleProgress) / 2.0
        
        return StrategyConfidence(confidence)
    }
    
    internal fun combineSignals(votes: Indexed<StrategyVote>): CombinedSignal {
        if (votes.size == 0) return CombinedSignal.HOLD
        
        // Calculate weighted scores
        var buyScore = 0.0
        var sellScore = 0.0
        var totalWeight = 0.0
        
        votes.play.forEach { vote ->
            val weightedConfidence = vote.weight * vote.confidence
            totalWeight += vote.weight.value
            
            when (vote.signal) {
                TradeSignal.BUY -> buyScore += weightedConfidence
                TradeSignal.SELL -> sellScore += weightedConfidence
                TradeSignal.HOLD -> { /* No contribution */ }
            }
        }
        
        // Normalize scores
        if (totalWeight > 0) {
            buyScore /= totalWeight
            sellScore /= totalWeight
        }
        
        val netScore = buyScore - sellScore
        
        return when {
            netScore >= 0.7 -> CombinedSignal.STRONG_BUY
            netScore >= 0.4 -> CombinedSignal.BUY
            netScore >= 0.1 -> CombinedSignal.WEAK_BUY
            netScore <= -0.7 -> CombinedSignal.STRONG_SELL
            netScore <= -0.4 -> CombinedSignal.SELL
            netScore <= -0.1 -> CombinedSignal.WEAK_SELL
            else -> CombinedSignal.HOLD
        }
    }
    
    fun analyzeTopSymbols(maxSymbols: Int = 10): Indexed<CombinedAnalysis> {
        // Get top attention symbols
        val topSymbols = attentionTracker.getMostAttentionSymbols(maxSymbols)
        
        // Analyze each symbol
        val analyses = topSymbols.α { weightedSymbol ->
            analyzeSymbol(weightedSymbol.a)
        }
        
        // Sort by signal strength and attention score
        val sortedAnalyses = analyses.play.sortedByDescending { analysis ->
            kotlin.math.abs(analysis.signalStrength) * analysis.attentionScore.value
        }
        
        return Indexed.of(sortedAnalyses.size) { i -> sortedAnalyses[i] }
    }
    
    fun getActionableSignals(maxSymbols: Int = 10): Indexed<CombinedAnalysis> {
        val allAnalyses = analyzeTopSymbols(maxSymbols)
        val actionable = allAnalyses.play.filter { it.isActionable }
        
        return Indexed.of(actionable.size) { i -> actionable[i] }
    }
    
    fun generateTradingReport(): TradingReport {
        val topAnalyses = analyzeTopSymbols(20)
        val actionableSignals = getActionableSignals(10)
        val attentionSummary = attentionTracker.getAttentionSummary()
        
        val buySignals = actionableSignals.play.filter { it.isBuySignal }
        val sellSignals = actionableSignals.play.filter { it.isSellSignal }
        
        return TradingReport(
            timestamp = kotlinx.datetime.Clock.System.now(),
            totalSymbolsAnalyzed = topAnalyses.size,
            actionableSignals = actionableSignals.size,
            buySignals = buySignals.size,
            sellSignals = sellSignals.size,
            topBuyCandidate = buySignals.maxByOrNull { it.signalStrength }?.symbol,
            topSellCandidate = sellSignals.maxByOrNull { kotlin.math.abs(it.signalStrength) }?.symbol,
            attentionSummary = attentionSummary,
            analyses = topAnalyses
        )
    }
}

// Trading report data structure
data class TradingReport(
    val timestamp: Instant,
    val totalSymbolsAnalyzed: Int,
    val actionableSignals: Int,
    val buySignals: Int,
    val sellSignals: Int,
    val topBuyCandidate: Symbol?,
    val topSellCandidate: Symbol?,
    val attentionSummary: AttentionSummary,
    val analyses: Indexed<CombinedAnalysis>
) {
    val marketActivity: String
        get() = when {
            actionableSignals >= 5 -> "High"
            actionableSignals >= 2 -> "Medium"
            actionableSignals >= 1 -> "Low"
            else -> "Quiet"
        }
    
    val buyVsSellRatio: Double
        get() = if (sellSignals > 0) buySignals.toDouble() / sellSignals else {
            if (buySignals > 0) Double.POSITIVE_INFINITY else 0.0
        }
}