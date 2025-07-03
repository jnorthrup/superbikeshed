package com.ta4k.stats

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`play`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Portfolio metrics implementation ported from quantstats
 */
class PortfolioMetrics {
    companion object {
        private const val DEFAULT_SCALE = 8
    }

    /**
     * Calculate the win rate of a series of returns
     */
    fun winRate(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins = returns.`play`.count { it > BigDecimal.ZERO }
        return BigDecimal(wins)
            .divide(BigDecimal(returns.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the average win size
     */
    fun avgWin(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins = returns.`play`.filter { it > BigDecimal.ZERO }.toList()
        if (wins.isEmpty()) return BigDecimal.ZERO
        
        return wins.average()
    }

    /**
     * Calculate the average loss size
     */
    fun avgLoss(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val losses = returns.`play`.filter { it < BigDecimal.ZERO }.toList()
        if (losses.isEmpty()) return BigDecimal.ZERO
        
        return losses.average()
    }

    /**
     * Calculate the profit factor (gross profit / gross loss)
     */
    fun profitFactor(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val grossProfit = returns.`play`.filter { it > BigDecimal.ZERO }
            .sumOf { it }
        val grossLoss = returns.`play`.filter { it < BigDecimal.ZERO }
            .sumOf { it.abs() }
            
        return if (grossLoss == BigDecimal.ZERO) BigDecimal.ZERO
        else grossProfit.divide(grossLoss, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the maximum drawdown
     */
    fun maxDrawdown(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        var peak = BigDecimal.ONE
        var maxDrawdown = BigDecimal.ZERO
        
        returns.`play`.fold(BigDecimal.ONE) { acc, ret ->
            val current = acc.multiply(BigDecimal.ONE.add(ret))
            peak = peak.max(current)
            val drawdown = peak.subtract(current).divide(peak, DEFAULT_SCALE, RoundingMode.HALF_UP)
            maxDrawdown = maxDrawdown.max(drawdown)
            current
        }
        
        return maxDrawdown
    }

    /**
     * Calculate the average drawdown
     */
    fun avgDrawdown(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        var peak = BigDecimal.ONE
        var drawdowns = mutableListOf<BigDecimal>()
        
        returns.`play`.fold(BigDecimal.ONE) { acc, ret ->
            val current = acc.multiply(BigDecimal.ONE.add(ret))
            peak = peak.max(current)
            val drawdown = peak.subtract(current).divide(peak, DEFAULT_SCALE, RoundingMode.HALF_UP)
            if (drawdown > BigDecimal.ZERO) drawdowns.add(drawdown)
            current
        }
        
        return if (drawdowns.isEmpty()) BigDecimal.ZERO
        else drawdowns.average()
    }

    /**
     * Calculate the exposure (percentage of time invested)
     */
    fun exposure(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val invested = returns.`play`.count { it != BigDecimal.ZERO }
        return BigDecimal(invested)
            .divide(BigDecimal(returns.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return sumOf { it }.divide(BigDecimal(size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }
}
