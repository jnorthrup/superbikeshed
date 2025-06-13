package borg.ipfs

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.pow
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.size
import borg.trikeshed.lib.get

/**
 * Financial operations for TrikeShed using immutable types and Series.
 */
object IpfsFinance {
    
    /**
     * Safe division with precision handling
     */
    private fun safeDivide(numerator: Double, denominator: Double, precision: Int = 8): Double {
        if (denominator == 0.0) return 0.0
        val result = numerator / denominator
        val multiplier = 10.0.pow(precision.toDouble())
        return kotlin.math.round(result * multiplier) / multiplier
    }
    
    /**
     * Calculates gross returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun grossReturn(prices: PriceSeries): ReturnSeries =
        (prices.size - 1) j { i: Int ->
            val prev = prices[i]
            val curr = prices[i + 1]
            if (curr.value == prev.value) Return.ONE
            else Return(safeDivide(curr.value, prev.value))
        }

    /**
     * Calculates log returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun logReturn(prices: PriceSeries): ReturnSeries =
        grossReturn(prices).α { g ->
            if (g.value <= 0.0) Return.ZERO else Return(ln(g.value))
        }

    /**
     * Calculates net returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun netReturn(prices: PriceSeries): ReturnSeries =
        (prices.size - 1) j { i: Int ->
            val prev = prices[i]
            val curr = prices[i + 1]
            if (curr.value == prev.value) Return.ZERO
            else Return(safeDivide(curr.value - prev.value, prev.value))
        }

    /**
     * Calculates cumulative returns from a return series.
     */
    fun cumulativeReturn(returns: ReturnSeries): ReturnSeries {
        var cumulative = 1.0
        return returns.α { ret ->
            cumulative *= (1.0 + ret.value)
            Return(cumulative - 1.0)
        }
    }

    /**
     * Calculates the Sharpe ratio for a return series.
     */
    fun sharpeRatio(returns: ReturnSeries, riskFreeRate: Double = 0.0): Double {
        val mean = returns.α { it.value }.let { series ->
            var sum = 0.0
            for (i in 0 until series.size) sum += series[i]
            sum / series.size
        }
        
        val variance = returns.α { it.value }.let { series ->
            var sumSquaredDiff = 0.0
            for (i in 0 until series.size) {
                val diff = series[i] - mean
                sumSquaredDiff += diff * diff
            }
            sumSquaredDiff / series.size
        }
        
        val stdDev = sqrt(variance)
        return if (stdDev == 0.0) 0.0 else (mean - riskFreeRate) / stdDev
    }

    /**
     * Calculates maximum drawdown from a price series.
     */
    fun maxDrawdown(prices: PriceSeries): Double {
        var peak = prices[0].value
        var maxDD = 0.0
        
        for (i in 1 until prices.size) {
            val price = prices[i].value
            if (price > peak) {
                peak = price
            } else {
                val drawdown = (peak - price) / peak
                if (drawdown > maxDD) {
                    maxDD = drawdown
                }
            }
        }
        return maxDD
    }

    /**
     * Simple moving average calculation.
     */
    fun simpleMovingAverage(prices: PriceSeries, period: Period): PriceSeries {
        if (period.value <= 0 || period.value > prices.size) {
            throw IllegalArgumentException("Invalid period: ${period.value}")
        }
        
        return (prices.size - period.value + 1) j { i ->
            var sum = 0.0
            for (j in i until i + period.value) {
                sum += prices[j].value
            }
            Price(sum / period.value)
        }
    }
}