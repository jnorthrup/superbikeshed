package com.ta4k.patterns

import com.ta4k.core.model.Kline // Assuming this path
import java.math.BigDecimal

enum class SwingType {
    HIGH, LOW
}

/**
 * Represents a detected swing point in a KlineSeries.
 * @property kline The kline at which the swing point occurs.
 * @property index The index of the kline in the original series.
 * @property price The price at the swing point (high for SwingHigh, low for SwingLow).
 * @property type The type of swing point (HIGH or LOW).
 */
data class SwingPoint(
    val kline: Kline,
    val index: Int,
    val price: BigDecimal,
    val type: SwingType
)

/**
 * Detects swing highs and lows in a given Trikethed [Series] of [Kline].
 * A swing high is a kline whose high is higher than the highs of 'strength' klines to its left and right.
 * A swing low is a kline whose low is lower than the lows of 'strength' klines to its left and right.
 */
object SwingPointDetector {

    /**
     * Detects all swing points (both highs and lows) in the series.
     *
     * @param klineSeries The Trikethed [Series] of klines to analyze. // Changed from List
     * @param strength The number of klines to the left and right to compare against.
     *                 A higher strength means more significant (but fewer) swing points.
     * @return A list of [SwingPoint]s, sorted by index.
     */
    fun detectSwingPoints(klineSeries: Indexed<Kline>, strength: Int): List<SwingPoint> { // Changed parameter type
        require(strength > 0) { "Strength must be positive." }
        // Use klineSeries.size
        if (klineSeries.size < (2 * strength + 1)) {
            return emptyList() // Not enough data to identify any swing points
        }

        val swingPoints = mutableListOf<SwingPoint>()

        // Iterate through klines where a full comparison window is possible
        // Index i is the candidate swing point
        // Use klineSeries.size and klineSeries[i]
        for (i in strength until klineSeries.size - strength) {
            val candidateKline = klineSeries[i] // Access using Series operator

            // Check for Swing High
            var isSwingHigh = true
            for (j in 1..strength) {
                if (candidateKline.highPrice <= klineSeries[i - j].highPrice ||
                    candidateKline.highPrice <= klineSeries[i + j].highPrice) {
                    isSwingHigh = false
                    break
                }
            }

            if (isSwingHigh) {
                swingPoints.add(SwingPoint(candidateKline, i, candidateKline.highPrice, SwingType.HIGH))
            } else { // Exclusive check: only if not a swing high, check for swing low
                var isSwingLow = true
                for (j in 1..strength) {
                    if (candidateKline.lowPrice >= klineSeries[i - j].lowPrice ||
                        candidateKline.lowPrice >= klineSeries[i + j].lowPrice) {
                        isSwingLow = false
                        break
                    }
                }
                if (isSwingLow) {
                    swingPoints.add(SwingPoint(candidateKline, i, candidateKline.lowPrice, SwingType.LOW))
                }
            }
        }
        // The list of SwingPoint objects is already sorted by index due to iteration order.
        return swingPoints
    }
}
