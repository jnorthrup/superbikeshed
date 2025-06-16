package com.ta4k.indicators

import com.ta4k.core.model.Kline // Assuming this path is correct
import borg.trikeshed.lib.j // For creating Series instance for 'values' via infix j
import borg.trikeshed.lib.Series
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Simple Moving Average (SMA) indicator.
 * Calculates the average of a kline property (typically close price) over a specified period.
 * Operates on a Trikethed [Series] of [Kline].
 */
class SMAIndicator(
    private val klineSeries: Series<Kline>, // Changed from List<Kline>
    private val period: Int,
    private val klinePropertySelector: (Kline) -> BigDecimal = { it.closePrice }
) {
    init {
        require(period > 0) { "Period must be positive" }
    }

    // Internal cache for results remains a MutableList
    private val results = mutableListOf<BigDecimal?>()
    private var calculatedUpToIndex = -1 // Tracks the last index for which SMA has been computed

    private fun ensureCalculatedUpTo(targetIndex: Int) {
        // Do not proceed if targetIndex is invalid or already calculated
        if (targetIndex < 0 || targetIndex >= klineSeries.size || targetIndex <= calculatedUpToIndex) {
            return
        }

        // Expand results list with nulls if it's smaller than needed up to targetIndex
        // This ensures results.add() can be replaced by results[i] = value if we decide to prefill.
        // For now, results.add() is fine as we iterate sequentially.
        if (results.size < klineSeries.size) {
            for (k in results.size until klineSeries.size) {
                results.add(null) // Pre-fill with nulls up to series size
            }
        }


        // Start calculation from the next index after where we left off
        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1

        for (i in startIndex..targetIndex) {
            if (i < period - 1) {
                results[i] = null // Not enough data
                continue
            }

            var sum = BigDecimal.ZERO
            for (j in 0 until period) {
                // Use series[index] accessor
                sum += klinePropertySelector(klineSeries[i - j])
            }

            // Determine scale: use the scale of the input property, or default if it's 0 (e.g. for integers converted to BigDecimal)
            // Adding +4 for precision in average calculation, can be adjusted.
            val currentKlinePrice = klinePropertySelector(klineSeries[i])
            val calculationScale = currentKlinePrice.scale() + 4
            results[i] = sum.divide(BigDecimal(period), calculationScale, RoundingMode.HALF_UP)
        }
        calculatedUpToIndex = targetIndex
    }

    /**
     * Calculates the SMA value for the kline at the given index.
     * @param index The index of the kline in the series.
     * @return The SMA value, or null if there's not enough data for the period or index is out of bounds.
     */
    fun getValue(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) {
            return null
        }
        ensureCalculatedUpTo(index) // Ensure calculation up to the requested index
        return results[index] // Direct access after ensuring calculation
    }

    /**
     * Returns all calculated SMA values up to the latest available data in the input series,
     * as a Trikethed [Series].
     * Accessing this property will trigger calculation for all available klines if not already done.
     */
    val values: Series<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            // Wrap the internal mutable list 'results' into a Series for output
            return klineSeries.size j { index -> // Use infix j
                // getValue will ensure calculation if needed for a specific index,
                // but ensureCalculatedUpTo above should have populated most of it.
                // This direct access assumes results is padded to klineSeries.size
                if (index < 0 || index >= klineSeries.size) {
                    throw IndexOutOfBoundsException("Index $index is out of bounds for Series of size ${klineSeries.size}")
                }
                if (index < results.size) results[index] else null
            }
        }
}
