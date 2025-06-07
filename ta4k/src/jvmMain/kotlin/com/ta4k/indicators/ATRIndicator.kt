package com.ta4k.indicators

import com.ta4k.core.model.Kline // Assuming this path
import borg.trikeshed.core.Series // Import new Series
import borg.trikeshed.core.j      // Import infix j
import borg.trikeshed.core.size // Import Series extensions
import borg.trikeshed.core.get  // Import Series extensions
import java.math.BigDecimal
import java.math.RoundingMode
// kotlin.math.sqrt is not directly used by ATR, but was in the prompt. Removed for cleanliness.

/**
 * Average True Range (ATR) indicator.
 * Measures market volatility.
 * Operates on a Trikethed [Series] of [Kline].
 */
class ATRIndicator(
    private val klineSeries: Series<Kline>, // Changed
    private val period: Int
) {
    init {
        require(period > 0) { "Period must be positive" }
    }

    // Internal caches
    private val trueRangeResults = mutableListOf<BigDecimal?>() // Stores raw TR values
    private val atrResults = mutableListOf<BigDecimal?>()       // Stores smoothed ATR values
    private var calculatedUpToIndex = -1

    private val calculationScale = 8 // Internal calculation precision
    // Result scale for ATR usually matches price scale or a bit more
    private val resultScale = klineSeries.firstOrNull()?.closePrice?.scale()?.let { it + 2 } ?: 4


    private fun ensureCalculatedUpTo(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= klineSeries.size || targetIndex <= calculatedUpToIndex) {
            return
        }

        val periodBigDecimal = BigDecimal(period)

        // Pre-allocate lists if this is the first major calculation run, or if lists are too small.
        // This helps avoid repeated checks for list size during the loop and allows direct [i] access.
        if (calculatedUpToIndex == -1) { // First time calculation is triggered significantly
            for (k in 0 until klineSeries.size) { // Initialize to full size of input series
                trueRangeResults.add(null)
                atrResults.add(null)
            }
        } else { // If called again to extend calculation, ensure lists are large enough
            while (trueRangeResults.size < klineSeries.size) trueRangeResults.add(null)
            while (atrResults.size < klineSeries.size) atrResults.add(null)
        }

        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1

        for (i in startIndex..targetIndex) {
            val currentKline = klineSeries[i]
            val high = currentKline.highPrice
            val low = currentKline.lowPrice

            val currentRawTR: BigDecimal
            if (i == 0) {
                currentRawTR = high.subtract(low) // TR for day 1 is H-L
            } else {
                val prevClose = klineSeries[i - 1].closePrice
                var tr = high.subtract(low)
                tr = tr.max(high.subtract(prevClose).abs())
                tr = tr.max(low.subtract(prevClose).abs())
                currentRawTR = tr
            }
            trueRangeResults[i] = currentRawTR // Direct assignment due to pre-allocation

            // ATR calculation
            if (i < period -1 ) {
                // atrResults[i] remains null (already set during pre-allocation)
            } else if (i == period - 1) {
                // First ATR is the SMA of the first 'period' True Ranges
                var sumTR = BigDecimal.ZERO
                for (j in 0 until period) { // Sum TRs from index 0 to period-1
                    sumTR += trueRangeResults[j] ?: BigDecimal.ZERO
                }
                val firstAtr = sumTR.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                atrResults[i] = firstAtr
            } else { // i >= period
                // Subsequent ATRs use Wilder's smoothing: ATR = [(Prior ATR * (n-1)) + Current TR] / n
                val prevAtr = atrResults[i-1] ?: BigDecimal.ZERO // Should be populated from previous step
                val currentTRForSmoothing = trueRangeResults[i] ?: BigDecimal.ZERO // Current raw TR

                val atr = (prevAtr.multiply(periodBigDecimal.subtract(BigDecimal.ONE)).add(currentTRForSmoothing))
                    .divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                atrResults[i] = atr
            }
        }
        calculatedUpToIndex = targetIndex
    }

    /**
     * Calculates the True Range for the kline at the given index.
     * @param index The index of the kline in the series.
     * @return The True Range value, or null if data is insufficient or index is out of bounds.
     */
    fun getTrueRange(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) {
            return null
        }
        ensureCalculatedUpTo(index) // Ensures trueRangeResults[index] is computed
        val rawTR = if (index < trueRangeResults.size) trueRangeResults[index] else null
        return rawTR?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /**
     * Calculates the ATR value for the kline at the given index.
     * @param index The index of the kline in the series.
     * @return The ATR value, or null if there's not enough data or index is out of bounds.
     */
    fun getValue(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) {
            return null
        }
        ensureCalculatedUpTo(index) // Ensures atrResults[index] is computed
        val rawAtr = if (index < atrResults.size) atrResults[index] else null
        return rawAtr?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /**
     * Returns all calculated ATR values up to the latest available data in the input series,
     * as a Trikethed [Series].
     * Accessing this property will trigger calculation for all available klines if not already done.
     */
    val values: Series<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx -> // Use infix j
                // getValue will ensure calculation and apply scaling for the specific index
                this.getValue(idx)
            }
        }
}
