package com.ta4k.indicators

import com.ta4k.core.model.Kline // Assuming this path
import borg.trikeshed.lib.j      // Import infix j
import borg.trikeshed.lib.Series
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Relative Strength Index (RSI) indicator.
 * Measures the speed and change of price movements.
 * Operates on a Trikethed [Series] of [Kline].
 */
class RSIIndicator(
    private val klineSeries: Series<Kline>, // Changed
    private val period: Int,
    private val klinePropertySelector: (Kline) -> BigDecimal = { it.closePrice }
) {
    init {
        require(period > 0) { "Period must be positive" }
    }

    // Internal caches - ensure they can grow as needed.
    private val results = mutableListOf<BigDecimal?>()
    private val avgGains = mutableListOf<BigDecimal?>() // Stores individual gains for first period, then smoothed avg gains
    private val avgLosses = mutableListOf<BigDecimal?>() // Stores individual losses for first period, then smoothed avg losses
    private var calculatedUpToIndex = -1

    private val calculationScale = 8
    private val resultScale = 2 // Standard RSI scale

    private fun ensureListSize(list: MutableList<BigDecimal?>, requiredSize: Int) {
        while (list.size <= requiredSize) {
            list.add(null)
        }
    }

    private fun ensureCalculatedUpTo(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= klineSeries.size || targetIndex <= calculatedUpToIndex) {
            return
        }

        // Ensure all internal lists are large enough to hold values up to targetIndex
        ensureListSize(results, targetIndex)
        ensureListSize(avgGains, targetIndex)
        ensureListSize(avgLosses, targetIndex)

        val periodBigDecimal = BigDecimal(period)
        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1

        for (i in startIndex..targetIndex) {
            if (i == 0) {
                // results[0], avgGains[0], avgLosses[0] are already null from ensureListSize or will be set so.
                // No calculation possible for the very first element.
                results[i] = null
                avgGains[i] = null
                avgLosses[i] = null
                continue
            }

            val currentPrice = klinePropertySelector(klineSeries[i])
            val prevPrice = klinePropertySelector(klineSeries[i - 1])
            val priceChange = currentPrice.subtract(prevPrice)

            val gain = if (priceChange > BigDecimal.ZERO) priceChange else BigDecimal.ZERO
            val loss = if (priceChange < BigDecimal.ZERO) priceChange.abs() else BigDecimal.ZERO

            if (i < period) {
                // Accumulate individual gains/losses for the first average calculation
                avgGains[i] = gain
                avgLosses[i] = loss
                results[i] = null // Not enough data for RSI value yet
            } else { // i >= period
                val currentSmoothedAvgGain: BigDecimal
                val currentSmoothedAvgLoss: BigDecimal

                if (i == period) {
                    // Calculate first average gain and loss using SMA of individual gains/losses
                    var sumGains = BigDecimal.ZERO
                    for (j in 1..period) { // Sum individual gains from index 1 to 'period' (inclusive)
                        sumGains += avgGains[j] ?: BigDecimal.ZERO
                    }
                    currentSmoothedAvgGain = sumGains.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)

                    var sumLosses = BigDecimal.ZERO
                    for (j in 1..period) { // Sum individual losses from index 1 to 'period'
                        sumLosses += avgLosses[j] ?: BigDecimal.ZERO
                    }
                    currentSmoothedAvgLoss = sumLosses.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                } else { // i > period
                    // Wilder's smoothing for subsequent averages
                    val prevAvgGain = avgGains[i - 1] ?: BigDecimal.ZERO
                    val prevAvgLoss = avgLosses[i - 1] ?: BigDecimal.ZERO

                    currentSmoothedAvgGain = (prevAvgGain.multiply(periodBigDecimal.subtract(BigDecimal.ONE)).add(gain))
                        .divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                    currentSmoothedAvgLoss = (prevAvgLoss.multiply(periodBigDecimal.subtract(BigDecimal.ONE)).add(loss))
                        .divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                }

                // Store the smoothed averages at index 'i'
                avgGains[i] = currentSmoothedAvgGain
                avgLosses[i] = currentSmoothedAvgLoss

                if (currentSmoothedAvgLoss.compareTo(BigDecimal.ZERO) == 0) {
                    results[i] = BigDecimal("100.00000000") // Store with calculationScale precision initially
                } else {
                    val rs = currentSmoothedAvgGain.divide(currentSmoothedAvgLoss, calculationScale, RoundingMode.HALF_UP)
                    val rsi = BigDecimal("100").subtract(
                        BigDecimal("100").divide(BigDecimal.ONE.add(rs), calculationScale, RoundingMode.HALF_UP)
                    )
                    results[i] = rsi
                }
            }
        }
        calculatedUpToIndex = targetIndex
    }

    /**
     * Calculates the RSI value for the kline at the given index.
     * @param index The index of the kline in the series.
     * @return The RSI value (0-100), or null if there's not enough data or index is out of bounds.
     */
    fun getValue(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) {
            return null
        }
        ensureCalculatedUpTo(index)
        val rawRsi = if (index < results.size) results[index] else null // results list might be shorter if index was not calculated
        return rawRsi?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /**
     * Returns all calculated RSI values up to the latest available data in the input series,
     * as a Trikethed [Series].
     * Accessing this property will trigger calculation for all available klines if not already done.
     */
    val values: Series<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx -> // Use infix j
                // getValue will ensure calculation and apply scaling
                this.getValue(idx)
            }
        }
}
