package com.ta4k.indicators

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Relative Strength Index (RSI) indicator using Wilder's smoothing method.
 * Measures the speed and change of price movements.
 * 
 * The RSI is calculated using the following steps:
 * 1. Calculate price changes
 * 2. Separate gains and losses
 * 3. Calculate first average gain/loss using simple average
 * 4. Calculate subsequent averages using Wilder's smoothing:
 *    Smoothed Average = (Previous Average * (Period - 1) + Current Value) / Period
 * 5. Calculate RS = Average Gain / Average Loss
 * 6. Calculate RSI = 100 - (100 / (1 + RS))
 */
class RSIIndicator(
    private val klineSeries: Indexed<Kline>,
    private val period: Int,
    private val klinePropertySelector: (Kline) -> BigDecimal = { it.closePrice }
) {
    init {
        require(period > 0) { "Period must be positive" }
    }

    // Internal caches
    private val results = mutableListOf<BigDecimal?>()
    private val avgGains = mutableListOf<BigDecimal?>()
    private val avgLosses = mutableListOf<BigDecimal?>()
    private var calculatedUpToIndex = -1

    private val calculationScale = 8
    private val resultScale = 2

    private fun ensureListSize(list: MutableList<BigDecimal?>, requiredSize: Int) {
        while (list.size <= requiredSize) {
            list.add(null)
        }
    }

    private fun ensureCalculatedUpTo(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= klineSeries.size || targetIndex <= calculatedUpToIndex) {
            return
        }

        ensureListSize(results, targetIndex)
        ensureListSize(avgGains, targetIndex)
        ensureListSize(avgLosses, targetIndex)

        val periodBigDecimal = BigDecimal(period)
        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1

        for (i in startIndex..targetIndex) {
            if (i == 0) {
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
                // First period: store raw gains/losses
                avgGains[i] = gain
                avgLosses[i] = loss
                results[i] = null
            } else if (i == period) {
                // Calculate first average gain/loss using simple average
                var sumGains = BigDecimal.ZERO
                var sumLosses = BigDecimal.ZERO
                
                for (j in 1..period) {
                    sumGains = sumGains.add(avgGains.getOrNull(j) ?: BigDecimal.ZERO)
                    sumLosses = sumLosses.add(avgLosses.getOrNull(j) ?: BigDecimal.ZERO)
                }
                
                avgGains[i] = sumGains.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                avgLosses[i] = sumLosses.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                
                // Calculate first RSI
                results[i] = calculateRSI(avgGains[i]!!, avgLosses[i]!!)
            } else {
                // Subsequent periods: use Wilder's smoothing
                val prevAvgGain = avgGains[i - 1] ?: BigDecimal.ZERO
                val prevAvgLoss = avgLosses[i - 1] ?: BigDecimal.ZERO
                
                avgGains[i] = prevAvgGain.multiply(periodBigDecimal.subtract(BigDecimal.ONE))
                    .add(gain)
                    .divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                
                avgLosses[i] = prevAvgLoss.multiply(periodBigDecimal.subtract(BigDecimal.ONE))
                    .add(loss)
                    .divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                
                results[i] = calculateRSI(avgGains[i]!!, avgLosses[i]!!)
            }
        }
        calculatedUpToIndex = targetIndex
    }

    private fun calculateRSI(avgGain: BigDecimal, avgLoss: BigDecimal): BigDecimal {
        return if (avgLoss == BigDecimal.ZERO) {
            BigDecimal("100.00")
        } else {
            val rs = avgGain.divide(avgLoss, calculationScale, RoundingMode.HALF_UP)
            BigDecimal("100").subtract(
                BigDecimal("100").divide(
                    BigDecimal.ONE.add(rs),
                    calculationScale,
                    RoundingMode.HALF_UP
                )
            ).setScale(resultScale, RoundingMode.HALF_UP)
        }
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
        return results[index]
    }

    /**
     * Returns all calculated RSI values up to the latest available data in the input series,
     * as a Trikethed [Indexed].
     * Accessing this property will trigger calculation for all available klines if not already done.
     */
    val values: Indexed<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx:Int -> this.getValue(idx) }
        }
}
