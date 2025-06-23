package com.ta4k.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

object TimeIndexed {
    fun rollingWindow(values: List<BigDecimal>, window: Int): List<List<BigDecimal>> {
        return values.windowed(window)
    }

    fun movingAverage(values: List<BigDecimal>, window: Int): List<BigDecimal> {
        return values.windowed(window) { windowValues ->
            windowValues.average()
        }
    }

    fun exponentialMovingAverage(values: List<BigDecimal>, window: Int): List<BigDecimal> {
        val alpha = BigDecimal(2.0 / (window + 1))
        val result = mutableListOf<BigDecimal>()
        var ema = values.firstOrNull() ?: BigDecimal.ZERO

        values.forEach { value ->
            ema = ema.multiply(BigDecimal.ONE.subtract(alpha))
                .add(value.multiply(alpha))
                .setScale(8, RoundingMode.HALF_UP)
            result.add(ema)
        }

        return result
    }

    fun movingStandardDeviation(values: List<BigDecimal>, window: Int): List<BigDecimal> {
        return values.windowed(window) { windowValues ->
            val mean = windowValues.average()
            val variance = windowValues.map { value ->
                value.subtract(mean).pow(2)
            }.average()
            BigDecimal(sqrt(variance.toDouble()))
        }
    }

    fun movingVariance(values: List<BigDecimal>, window: Int): List<BigDecimal> {
        return values.windowed(window) { windowValues ->
            val mean = windowValues.average()
            windowValues.map { value ->
                value.subtract(mean).pow(2)
            }.average()
        }
    }

    fun stochasticOscillator(
        high: List<BigDecimal>,
        low: List<BigDecimal>,
        close: List<BigDecimal>,
        period: Int = 14
    ): Pair<List<BigDecimal>, List<BigDecimal>> {
        val kValues = mutableListOf<BigDecimal>()
        val dValues = mutableListOf<BigDecimal>()

        for (i in period - 1 until close.size) {
            val periodHigh = high.subList(i - period + 1, i + 1).maxOrNull() ?: BigDecimal.ZERO
            val periodLow = low.subList(i - period + 1, i + 1).minOrNull() ?: BigDecimal.ZERO
            val currentClose = close[i]

            val k = if (periodHigh == periodLow) BigDecimal("50")
            else currentClose.subtract(periodLow)
                .multiply(BigDecimal(100))
                .divide(periodHigh.subtract(periodLow), 8, RoundingMode.HALF_UP)

            kValues.add(k)
        }

        // Calculate %D (3-period SMA of %K)
        dValues.addAll(movingAverage(kValues, 3))

        return Pair(kValues, dValues)
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        return if (isEmpty()) BigDecimal.ZERO
        else reduce { acc, value -> acc.add(value) }
            .divide(BigDecimal(size), 8, RoundingMode.HALF_UP)
    }
}
