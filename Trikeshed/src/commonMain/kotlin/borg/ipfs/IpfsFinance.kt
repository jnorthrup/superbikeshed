package borg.ipfs

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ln
import kotlin.math.sqrt
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.combine

/**
 * Financial operations for TrikeShed using immutable types and Series.
 */
object IpfsFinance {
    /**
     * Calculates gross returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun grossReturn(prices: PriceSeries): ReturnSeries =
        (prices.a - 1) j { i: Int ->
            val prev = prices.b(i)
            val curr = prices.b(i + 1)
            if (curr.value == prev.value) Return.ONE
            else Return(curr.value.divide(prev.value, 8, RoundingMode.HALF_UP))
        }

    /**
     * Calculates log returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun logReturn(prices: PriceSeries): ReturnSeries =
        grossReturn(prices).let { gross ->
            gross.a j { i: Int ->
                val g = gross.b(i)
                if (g.value <= BigDecimal.ZERO) Return.ZERO
                else Return(BigDecimal(ln(g.value.toDouble())))
            }
        }

    /**
     * Calculates net returns from a series of prices.
     * Returns a Series of size (prices.size - 1), or (prices.size) if strict = false.
     */
    fun netReturn(prices: PriceSeries, strict: Boolean = true): ReturnSeries =
        (prices.a - 1) j { i: Int ->
            val prev = prices.b(i)
            val curr = prices.b(i + 1)
            if (curr.value == prev.value) Return.ZERO
            else Return(curr.value.subtract(prev.value).divide(prev.value, 8, RoundingMode.HALF_UP))
        }.let { returns ->
            if (strict) returns else {
                val s1 = 1 j { _: Int -> Return.ZERO }
                val s2 = returns
                val s1Pair = s1 as Pair<Int, (Int) -> Return>
                val s2Pair = s2 as Pair<Int, (Int) -> Return>
                val n1 = s1Pair.first
                val f1 = s1Pair.second
                val n2 = s2Pair.first
                val f2 = s2Pair.second
                val total = n1 + n2
                total j { i: Int -> if (i < n1) f1(i) else f2(i - n1) }
            }
        }

    /**
     * Calculates compound returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun compoundReturn(prices: PriceSeries): ReturnSeries =
        grossReturn(prices).let { gross ->
            val arr = Array(gross.a + 1) { Return.ONE }
            for (i in 1 until arr.size) {
                arr[i] = Return(arr[i - 1].value.multiply(gross.b(i - 1).value))
            }
            (arr.size - 1) j { i: Int -> arr[i + 1] }
        }

    /**
     * Calculates percentage returns from a series of prices.
     * Returns a Series of size (prices.size - 1).
     */
    fun percentReturn(prices: PriceSeries): PercentageSeries =
        compoundReturn(prices).let { compound ->
            compound.a j { i: Int ->
                Percentage(compound.b(i).value.subtract(BigDecimal.ONE)
                    .multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP))
            }
        }

    /**
     * Calculates returns based on the specified type.
     */
    fun calculateReturns(prices: PriceSeries, returnType: ReturnType): Series<*> = when (returnType) {
        ReturnType.GROSS -> grossReturn(prices) as Series<*>
        ReturnType.LOG -> logReturn(prices) as Series<*>
        ReturnType.NET -> netReturn(prices) as Series<*>
        ReturnType.COMPOUND -> compoundReturn(prices) as Series<*>
        ReturnType.PERCENT -> percentReturn(prices) as Series<*>
    }

    /**
     * Calculates the Sharpe ratio for a series of returns.
     */
    fun sharpe(returns: ReturnSeries): Return {
        val mean = returns.average()
        val std = returns.standardDeviation()
        return if (std.value == BigDecimal.ZERO) Return.ZERO
        else Return(mean.value.divide(std.value, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal(sqrt(252.0))))
    }

    /**
     * Calculates the information ratio comparing returns to a baseline.
     */
    fun informationRatio(returns: ReturnSeries, baseline: ReturnSeries): Return {
        val mean = returns.average()
        val baselineMean = baseline.average()
        val diff = returns.a j { i: Int -> Return(returns.b(i).value.subtract(baseline.b(i).value)) }
        val std = diff.standardDeviation()
        return if (std.value == BigDecimal.ZERO) Return.ZERO
        else Return(mean.value.subtract(baselineMean.value)
            .multiply(BigDecimal(sqrt(252.0)))
            .divide(std.value, 8, RoundingMode.HALF_UP))
    }

    /**
     * Calculates maximum drawdown and its duration.
     */
    fun maxDrawdown(cumulativeReturns: ReturnSeries): DrawdownWithDuration {
        data class DrawdownState(
            val highWatermark: Return = Return.ONE,
            val maxDrawdown: Drawdown = Drawdown.ZERO,
            val maxDrawdownDuration: Period = Period.ZERO,
            val currentDrawdownDuration: Period = Period.ZERO
        )
        var state = DrawdownState()
        for (i in 0 until cumulativeReturns.a) {
            val value = cumulativeReturns.b(i)
            state = if (value.value > state.highWatermark.value) {
                state.copy(
                    highWatermark = value,
                    currentDrawdownDuration = Period.ZERO
                )
            } else {
                val drawdown = Drawdown(BigDecimal.ONE.subtract(
                    value.value.divide(state.highWatermark.value, 8, RoundingMode.HALF_UP)))
                val newMaxDrawdown = if (drawdown.value > state.maxDrawdown.value) drawdown else state.maxDrawdown
                val newDuration = Period(state.currentDrawdownDuration.value + 1)
                val newMaxDuration = if (newDuration.value > state.maxDrawdownDuration.value)
                    newDuration else state.maxDrawdownDuration
                state.copy(
                    maxDrawdown = newMaxDrawdown,
                    maxDrawdownDuration = newMaxDuration,
                    currentDrawdownDuration = newDuration
                )
            }
        }
        return state.maxDrawdown j state.maxDrawdownDuration
    }

    /**
     * Calculates portfolio metrics.
     */
    fun portfolioMetrics(returns: ReturnSeries, baseline: ReturnSeries? = null): Map<String, Any> {
        val mean = returns.average()
        val std = returns.standardDeviation()
        val sharpe = sharpe(returns)
        val (maxDD, maxDDD) = maxDrawdown(returns.a j { i: Int -> Return(BigDecimal.ONE.add(returns.b(i).value)) })
        return buildMap {
            put("sharpe", sharpe)
            put("meanReturn", mean)
            put("stdDev", std)
            put("maxDrawdown", maxDD)
            put("maxDrawdownDuration", maxDDD)
            put("maxReturn", returns.maxOrNull() ?: Return.ZERO)
            put("minReturn", returns.minOrNull() ?: Return.ZERO)
            if (baseline != null) put("informationRatio", informationRatio(returns, baseline))
        }
    }

    // Helper: average for Series<Return>
    private fun Series<Return>.average(): Return {
        if (a == 0) return Return.ZERO
        var sum = BigDecimal.ZERO
        for (i in 0 until a) sum = sum.add(b(i).value)
        return Return(sum.divide(BigDecimal(a), 8, RoundingMode.HALF_UP))
    }

    // Helper: standard deviation for Series<Return>
    private fun Series<Return>.standardDeviation(): StandardDeviation {
        if (a == 0) return StandardDeviation.ZERO
        val mean = average().value
        var sumSq = BigDecimal.ZERO
        for (i in 0 until a) {
            val diff = b(i).value.subtract(mean)
            sumSq = sumSq.add(diff.multiply(diff))
        }
        val variance = sumSq.divide(BigDecimal(a), 8, RoundingMode.HALF_UP)
        return StandardDeviation(BigDecimal(sqrt(variance.toDouble())))
    }

    // Helper: maxOrNull for Series<Return>
    private fun Series<Return>.maxOrNull(): Return? {
        if (a == 0) return null
        var max = b(0)
        for (i in 1 until a) if (b(i).value > max.value) max = b(i)
        return max
    }

    // Helper: minOrNull for Series<Return>
    private fun Series<Return>.minOrNull(): Return? {
        if (a == 0) return null
        var min = b(0)
        for (i in 1 until a) if (b(i).value < min.value) min = b(i)
        return min
    }
} 