package com.ta4k.stats

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

/**
 * Risk metrics implementation ported from quantstats
 */
class RiskMetrics {
    companion object {
        internal const val DEFAULT_SCALE = 8
        internal const val DEFAULT_CONFIDENCE = 0.95
        internal const val DEFAULT_SIGMA = 1.0
    }

    /**
     * Value at Risk (VaR) calculation
     */
    fun valueAtRisk(
        returns: Indexed<BigDecimal>,
        sigma: Double = DEFAULT_SIGMA,
        confidence: Double = DEFAULT_CONFIDENCE
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val sortedReturns = returns.sorted()
        val index = ((1 - confidence) * returns.size).toInt()
        return sortedReturns[index].multiply(BigDecimal(sigma))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Conditional Value at Risk (CVaR) calculation
     */
    fun conditionalValueAtRisk(
        returns: Indexed<BigDecimal>,
        sigma: Double = DEFAULT_SIGMA,
        confidence: Double = DEFAULT_CONFIDENCE
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val varValue = valueAtRisk(returns, sigma, confidence)
        val tailReturns = returns.filter { it <= varValue }
        
        return if (tailReturns.isEmpty()) BigDecimal.ZERO
        else tailReturns.average()
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Expected Shortfall calculation
     */
    fun expectedShortfall(
        returns: Indexed<BigDecimal>,
        sigma: Double = DEFAULT_SIGMA,
        confidence: Double = DEFAULT_CONFIDENCE
    ): BigDecimal {
        return conditionalValueAtRisk(returns, sigma, confidence)
    }

    /**
     * Tail Ratio calculation
     */
    fun tailRatio(
        returns: Indexed<BigDecimal>,
        cutoff: Double = DEFAULT_CONFIDENCE
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val sortedReturns = returns.sorted()
        val rightTail = sortedReturns[(cutoff * returns.size).toInt()]
        val leftTail = sortedReturns[((1 - cutoff) * returns.size).toInt()]
        
        return if (leftTail == BigDecimal.ZERO) BigDecimal.ZERO
        else rightTail.abs().divide(leftTail.abs(), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Risk of Ruin calculation
     */
    fun riskOfRuin(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val winRate = returns.count { it > BigDecimal.ZERO }.toDouble() / returns.size
        val avgWin = returns.filter { it > BigDecimal.ZERO }.average()
        val avgLoss = returns.filter { it < BigDecimal.ZERO }.average().abs()
        
        if (avgLoss == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val kelly = winRate - ((1 - winRate) / (avgWin / avgLoss))
        return if (kelly <= BigDecimal.ZERO) BigDecimal.ZERO
        else BigDecimal.ONE.subtract(kelly)
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    internal fun Indexed<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return sumOf { it }.divide(BigDecimal(size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }
}
