package com.ta4k.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

object Statistics {
    fun sharpe(returns: List<BigDecimal>): BigDecimal {
        val mean = returns.average()
        val std = returns.standardDeviation()
        return if (std == BigDecimal.ZERO) BigDecimal.ZERO
        else (mean.divide(std, 8, RoundingMode.HALF_UP))
            .multiply(BigDecimal(sqrt(252.0)))
    }

    fun informationRatio(returns: List<BigDecimal>, baseline: List<BigDecimal>): BigDecimal {
        val mean = returns.average()
        val baselineMean = baseline.average()
        val diff = returns.zip(baseline) { r, b -> r.subtract(b) }
        val std = diff.standardDeviation()
        return if (std == BigDecimal.ZERO) BigDecimal.ZERO
        else (mean.subtract(baselineMean))
            .multiply(BigDecimal(sqrt(252.0)))
            .divide(std, 8, RoundingMode.HALF_UP)
    }

    fun maxDrawdown(cumulativeReturns: List<BigDecimal>): Pair<BigDecimal, Int> {
        var highWatermark = BigDecimal.ZERO
        var maxDrawdown = BigDecimal.ZERO
        var maxDrawdownDuration = 0
        var currentDrawdownDuration = 0

        cumulativeReturns.forEach { value ->
            if (value > highWatermark) {
                highWatermark = value
                currentDrawdownDuration = 0
            } else {
                val drawdown = BigDecimal.ONE.subtract(value.divide(highWatermark, 8, RoundingMode.HALF_UP))
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown
                }
                currentDrawdownDuration++
                if (currentDrawdownDuration > maxDrawdownDuration) {
                    maxDrawdownDuration = currentDrawdownDuration
                }
            }
        }

        return Pair(maxDrawdown, maxDrawdownDuration)
    }

    fun portfolioMetrics(returns: List<BigDecimal>, baseline: List<BigDecimal>? = null) {
        val mean = returns.average()
        val std = returns.standardDeviation()
        val sharpe = sharpe(returns)
        val (maxDD, maxDDD) = maxDrawdown(returns.map { BigDecimal.ONE.add(it) })
        
        println("\n=================================================")
        println("Portfolio Metrics")
        println("=================================================")
        println()
        println("Sharpe Ratio (annualised): ${sharpe.setScale(2, RoundingMode.HALF_UP)}")
        
        if (baseline != null) {
            val ir = informationRatio(returns, baseline)
            println("Information Ratio (annualised): ${ir.setScale(2, RoundingMode.HALF_UP)}")
        }
        
        println("Mean Return (annualised): ${(mean.multiply(BigDecimal(252))).setScale(4, RoundingMode.HALF_UP)}")
        println("Standard Deviation (annualised): ${(std.multiply(BigDecimal(sqrt(252.0)))).setScale(4, RoundingMode.HALF_UP)}")
        println("Maximum Drawdown: ${maxDD.setScale(4, RoundingMode.HALF_UP)}")
        println("Maximum Drawdown Duration: $maxDDD periods")
        println("Max Return: ${returns.maxOrNull()?.setScale(4, RoundingMode.HALF_UP)}")
        println("Min Return: ${returns.minOrNull()?.setScale(4, RoundingMode.HALF_UP)}")
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        return if (isEmpty()) BigDecimal.ZERO
        else reduce { acc, value -> acc.add(value) }
            .divide(BigDecimal(size), 8, RoundingMode.HALF_UP)
    }

    private fun List<BigDecimal>.standardDeviation(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        
        val mean = average()
        val variance = map { value ->
            value.subtract(mean).pow(2)
        }.average()
        
        return BigDecimal(sqrt(variance.toDouble()))
    }
} 