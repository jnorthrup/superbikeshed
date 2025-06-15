package com.ta4k.stats

import borg.trikeshed.core.Series
import borg.trikeshed.core.j
import borg.trikeshed.core.`▶`
import borg.trikeshed.core.size
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility functions for statistical calculations
 */
object StatsUtils {
    private const val DEFAULT_SCALE = 8

    /**
     * Calculate the compound annual growth rate (CAGR)
     */
    fun cagr(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val totalReturn = returns.`▶`.fold(BigDecimal.ONE) { acc, ret ->
            acc.multiply(BigDecimal.ONE.add(ret))
        }
        
        val years = BigDecimal(returns.size).divide(BigDecimal(periods), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val exponent = BigDecimal.ONE.divide(years, DEFAULT_SCALE, RoundingMode.HALF_UP)
        
        return totalReturn.pow(exponent.toInt())
            .subtract(BigDecimal.ONE)
            .subtract(rf)
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the rolling volatility
     */
    fun rollingVolatility(
        returns: Series<BigDecimal>,
        window: Int = 126,
        periods: Int = 252
    ): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { BigDecimal.ZERO }
        
        val returnsList = returns.`▶`.toList()
        val rollingVol = (returns.size - window + 1) j { i: Int ->
            val windowReturns = returnsList.subList(i, i + window)
            val std = calculateStdDev(windowReturns)
            std.multiply(BigDecimal(sqrt(periods.toDouble())))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
        }
        
        return rollingVol
    }

    /**
     * Calculate the rolling Sharpe ratio
     */
    fun rollingSharpe(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        window: Int = 126,
        periods: Int = 252
    ): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { BigDecimal.ZERO }
        
        val returnsList = returns.`▶`.toList()
        val rollingSharpe = (returns.size - window + 1) j { i: Int ->
            val windowReturns = returnsList.subList(i, i + window)
            val mean = windowReturns.average()
            val std = calculateStdDev(windowReturns)
            
            if (std == BigDecimal.ZERO) BigDecimal.ZERO
            else {
                val excessReturn = mean.subtract(rf)
                excessReturn.multiply(BigDecimal(sqrt(periods.toDouble())))
                    .divide(std, DEFAULT_SCALE, RoundingMode.HALF_UP)
            }
        }
        
        return rollingSharpe
    }

    /**
     * Calculate the rolling Sortino ratio
     */
    fun rollingSortino(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        window: Int = 126,
        periods: Int = 252
    ): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { BigDecimal.ZERO }
        
        val returnsList = returns.`▶`.toList()
        val rollingSortino = (returns.size - window + 1) j { i: Int ->
            val windowReturns = returnsList.subList(i, i + window)
            val mean = windowReturns.average()
            val downsideStd = calculateDownsideStdDev(windowReturns)
            
            if (downsideStd == BigDecimal.ZERO) BigDecimal.ZERO
            else {
                val excessReturn = mean.subtract(rf)
                excessReturn.multiply(BigDecimal(sqrt(periods.toDouble())))
                    .divide(downsideStd, DEFAULT_SCALE, RoundingMode.HALF_UP)
            }
        }
        
        return rollingSortino
    }

    /**
     * Calculate the Kelly Criterion
     */
    fun kellyCriterion(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins = returns.`▶`.filter { it > BigDecimal.ZERO }.toList()
        val losses = returns.`▶`.filter { it < BigDecimal.ZERO }.toList()
        
        if (wins.isEmpty() || losses.isEmpty()) return BigDecimal.ZERO
        
        val winRate = BigDecimal(wins.size)
            .divide(BigDecimal(returns.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val avgWin = wins.average()
        val avgLoss = losses.average().abs()
        
        if (avgLoss == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val kelly = winRate.multiply(avgWin)
            .divide(avgLoss, DEFAULT_SCALE, RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE.subtract(winRate))
            .divide(avgWin, DEFAULT_SCALE, RoundingMode.HALF_UP)
            
        return kelly.max(BigDecimal.ZERO)
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return sumOf { it }.divide(BigDecimal(size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateStdDev(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        
        val mean = values.average()
        val variance = values.map { it.subtract(mean).pow(2) }
            .average()
            
        return BigDecimal(sqrt(variance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateDownsideStdDev(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        
        val mean = values.average()
        val downsideVariance = values.filter { it < BigDecimal.ZERO }
            .map { it.subtract(mean).pow(2) }
            .average()
            
        return BigDecimal(sqrt(downsideVariance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }
}
