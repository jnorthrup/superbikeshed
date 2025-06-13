package com.ta4k.indicators.utils

import java.math.BigDecimal
import java.math.RoundingMode

object SmoothingUtils {

    /**
     * Applies Wilder's smoothing (similar to a type of Exponential Moving Average).
     * Formula: NewValue = (OldValue * (Period - 1) + CurrentValue) / Period
     * which is equivalent to: NewValue = OldValue - OldValue/Period + CurrentValue (if OldValue is not null)
     *
     * @param T The type of elements in the input series.
     * @param series The input series of values to smooth.
     * @param period The smoothing period.
     * @param initialSumProvider A function that provides the sum of the first 'period' actual values
     *                         from the original raw series that this smoothed series is based on.
     *                         This sum is used to calculate the first smoothed value as a simple average.
     *                         The provider is called when `index == period - 1`. It should sum
     *                         the 'period' values that *end* at `series[period-1]` (or its corresponding raw value).
     * @param valueExtractor A function to extract a BigDecimal from an element of the `series` argument.
     *                       This extracted value is the "CurrentValue" in Wilder's formula for subsequent steps.
     * @param calculationScale The scale for internal calculations.
     * @return A list of smoothed values (as BigDecimal?), with initial nulls until the first smoothed value can be computed.
     *         The size of the returned list is the same as the input `series`.
     */
    fun <T> wildersSmooth(
        series: List<T>,
        period: Int,
        initialSumProvider: (index: Int, List<T>) -> BigDecimal,
        valueExtractor: (T?) -> BigDecimal, // Extracts the "current raw value" for smoothing formula
        calculationScale: Int = 8
    ): List<BigDecimal?> {
        require(period > 0) { "Period must be positive" }
        if (series.isEmpty()) return emptyList()

        val smoothedValues = MutableList<BigDecimal?>(series.size) { null }
        // Not enough data to even calculate the first SMA for Wilder's.
        // The first value is at index period-1.
        if (series.size < period) return smoothedValues

        val periodBd = BigDecimal(period)
        var previousSmoothedValue: BigDecimal? = null

        for (i in series.indices) {
            if (i < period - 1) {
                // smoothedValues[i] is already null from initialization
                continue
            }

            if (i == period - 1) {
                // First smoothed value is the average of the initial 'period' values
                // The sum is provided by initialSumProvider, covering elements that lead to series[i]
                val sumOfFirstPeriod = initialSumProvider(i, series)
                previousSmoothedValue = sumOfFirstPeriod.divide(periodBd, calculationScale, RoundingMode.HALF_UP)
                smoothedValues[i] = previousSmoothedValue
            } else {
                // Subsequent values use Wilder's smoothing formula
                // NewValue = (OldValue * (Period - 1) + CurrentValue) / Period
                // 'currentValue' here is the raw value from the series being smoothed at index 'i'.
                val currentValue = valueExtractor(series[i])

                previousSmoothedValue = previousSmoothedValue?.let {
                    // It = previousSmoothedValue
                    (it.multiply(periodBd.subtract(BigDecimal.ONE)).add(currentValue))
                        .divide(periodBd, calculationScale, RoundingMode.HALF_UP)
                }
                // If previousSmoothedValue became null due to some issue (should not happen if logic is correct
                // and period > 0), then subsequent values will also be null.
                smoothedValues[i] = previousSmoothedValue
            }
        }
        return smoothedValues
    }
}
