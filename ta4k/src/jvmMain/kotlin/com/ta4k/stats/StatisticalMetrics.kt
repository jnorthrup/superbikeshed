package com.ta4k.stats

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`play`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical metrics implementation ported from quantstats
 */
class StatisticalMetrics {
    companion object {
        private const val DEFAULT_SCALE = 8
    }

    /**
     * Calculate the skewness of returns
     */
    fun skew(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val mean = returns.`play`.average()
        val std = calculateStdDev(returns.`play`.toList())
        
        if (std == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val cubedDeviations = returns.`play`.map { 
            it.subtract(mean).pow(3) 
        }.toList()
        
        val skewness = cubedDeviations.average()
            .divide(std.pow(3), DEFAULT_SCALE, RoundingMode.HALF_UP)
            
        return skewness
    }

    /**
     * Calculate the kurtosis of returns
     */
    fun kurtosis(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val mean = returns.`play`.average()
        val std = calculateStdDev(returns.`play`.toList())
        
        if (std == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val fourthPowerDeviations = returns.`play`.map { 
            it.subtract(mean).pow(4) 
        }.toList()
        
        val kurtosis = fourthPowerDeviations.average()
            .divide(std.pow(4), DEFAULT_SCALE, RoundingMode.HALF_UP)
            .subtract(BigDecimal(3)) // Excess kurtosis
            
        return kurtosis
    }

    /**
     * Calculate the autocorrelation penalty
     */
    fun autocorrPenalty(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList = returns.`play`.toList()
        val n = returnsList.size
        
        if (n < 2) return BigDecimal.ZERO
        
        var sum = BigDecimal.ZERO
        for (i in 0 until n - 1) {
            sum = sum.add(returnsList[i].multiply(returnsList[i + 1]))
        }
        
        val autocorr = sum.divide(BigDecimal(n - 1), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val penalty = BigDecimal.ONE.subtract(autocorr.abs())
        
        return penalty.max(BigDecimal.ZERO)
    }

    /**
     * Calculate the information ratio
     */
    fun informationRatio(
        returns: Series<BigDecimal>,
        benchmark: Series<BigDecimal>,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val excessReturns = returns.`play`.zip(benchmark.`play`) { ret, bench ->
            ret.subtract(bench)
        }.toList()
        
        val mean = excessReturns.average()
        val std = calculateStdDev(excessReturns)
        
        if (std == BigDecimal.ZERO) return BigDecimal.ZERO
        
        return mean.multiply(BigDecimal(sqrt(periods.toDouble())))
            .divide(std, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the R-squared value
     */
    fun rSquared(returns: Series<BigDecimal>, benchmark: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val returnsList = returns.`play`.toList()
        val benchmarkList = benchmark.`play`.toList()
        
        val returnsMean = returnsList.average()
        val benchmarkMean = benchmarkList.average()
        
        var numerator = BigDecimal.ZERO
        var denominator = BigDecimal.ZERO
        
        for (i in returnsList.indices) {
            val returnsDiff = returnsList[i].subtract(returnsMean)
            val benchmarkDiff = benchmarkList[i].subtract(benchmarkMean)
            numerator = numerator.add(returnsDiff.multiply(benchmarkDiff))
            denominator = denominator.add(benchmarkDiff.pow(2))
        }
        
        if (denominator == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val r = numerator.divide(denominator, DEFAULT_SCALE, RoundingMode.HALF_UP)
        return r.pow(2)
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
}
