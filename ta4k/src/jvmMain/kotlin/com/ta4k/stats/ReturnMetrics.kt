package com.ta4k.stats

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`play`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

/**
 * Return metrics implementation ported from quantstats
 */
class ReturnMetrics {
    companion object {
        private const val DEFAULT_SCALE = 8
        private const val DEFAULT_PERIODS = 252 // Trading days in a year
    }

    /**
     * Geometric mean calculation
     */
    fun geometricMean(returns: Indexed<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val product = returns.`play`.fold(BigDecimal.ONE) { acc: BigDecimal, ret: BigDecimal -> 
            acc.multiply(BigDecimal.ONE.add(ret))
        }
        
        val exponent = BigDecimal.ONE.divide(BigDecimal(returns.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
        return product.pow(exponent.toInt())
            .subtract(BigDecimal.ONE)
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Compound Annual Growth Rate (CAGR) calculation
     */
    fun cagr(
        returns: Indexed<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = DEFAULT_PERIODS
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val totalReturn = returns.`play`.fold(BigDecimal.ONE) { acc: BigDecimal, ret: BigDecimal -> 
            acc.multiply(BigDecimal.ONE.add(ret))
        }
        
        val years = BigDecimal(returns.size).divide(BigDecimal(periods), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val exponent = BigDecimal.ONE.divide(years, DEFAULT_SCALE, RoundingMode.HALF_UP)
        val cagr = totalReturn.pow(exponent.toInt())
            .subtract(BigDecimal.ONE)
            .subtract(rf)
            
        return cagr.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Rolling Sharpe ratio calculation
     */
    fun rollingSharpe(
        returns: Indexed<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        window: Int = 126, // 6 months of trading days
        periods: Int = DEFAULT_PERIODS
    ): Indexed<BigDecimal> {
        if (returns.isEmpty()) return 0 j { BigDecimal.ZERO }
        
        val excessReturns = returns.`play`.map { it.subtract(rf) }.toList()
        val rollingSharpe = (returns.size - window + 1) j { i: Int ->
            val windowReturns = excessReturns.subList(i, i + window)
            val mean = windowReturns.average()
            val std = calculateStdDev(windowReturns)
            
            if (std == BigDecimal.ZERO) BigDecimal.ZERO
            else {
                val periodsSqrt = BigDecimal(kotlin.math.sqrt(periods.toDouble()))
                mean.multiply(BigDecimal(periods))
                    .divide(std.multiply(periodsSqrt), DEFAULT_SCALE, RoundingMode.HALF_UP)
            }
        }
        
        return rollingSharpe
    }

    /**
     * Rolling Sortino ratio calculation
     */
    fun rollingSortino(
        returns: Indexed<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        window: Int = 126,
        periods: Int = DEFAULT_PERIODS
    ): Indexed<BigDecimal> {
        if (returns.isEmpty()) return 0 j { BigDecimal.ZERO }
        
        val excessReturns = returns.`play`.map { it.subtract(rf) }.toList()
        val rollingSortino = (returns.size - window + 1) j { i: Int ->
            val windowReturns = excessReturns.subList(i, i + window)
            val mean = windowReturns.average()
            val downsideStd = calculateDownsideStdDev(windowReturns)
            
            if (downsideStd == BigDecimal.ZERO) BigDecimal.ZERO
            else {
                val periodsSqrt = BigDecimal(kotlin.math.sqrt(periods.toDouble()))
                mean.multiply(BigDecimal(periods))
                    .divide(downsideStd.multiply(periodsSqrt), DEFAULT_SCALE, RoundingMode.HALF_UP)
            }
        }
        
        return rollingSortino
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
            
        return BigDecimal(kotlin.math.sqrt(variance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateDownsideStdDev(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        
        val mean = values.average()
        val downsideVariance = values.filter { it < BigDecimal.ZERO }
            .map { it.subtract(mean).pow(2) }
            .average()
            
        return BigDecimal(kotlin.math.sqrt(downsideVariance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }
}
