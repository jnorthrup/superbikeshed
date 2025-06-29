package moneyfan.indicators

import moneyfan.models.Price
// Placeholder for TrikeShed's Indexed type. This might need adjustment later.
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α
import moneyfan.trikeshed.j
import moneyfan.trikeshed.emptySeries // Assuming this exists for empty results

/**
 * Calculates the Relative Strength Index (RSI) for a series of prices.
 *
 * @param prices An Indexed series of Price objects.
 * @param period The number of periods to consider for the RSI.
 * @return An Indexed series of Double values representing the RSI.
 *         If RSI cannot be calculated (e.g., insufficient data, undefined prices),
 *         Double.NaN is used.
 */
fun calculateRSI(prices: Indexed<Price>, period: Int): Indexed<Double> {
    if (period <= 0) {
        return prices.a j { Double.NaN } // Return NaN for all if period is invalid
    }
    if (prices.a < period + 1) { // Need at least period + 1 prices for one change value
        return prices.a j { Double.NaN } // Not enough data
    }

    val rsiValues = mutableListOf<Double>()

    // Calculate price changes
    val priceChanges = mutableListOf<Double>()
    for (i in 0 until prices.a - 1) {
        val current = prices.b(i + 1)
        val previous = prices.b(i)
        if (current.isDefined() && previous.isDefined()) {
            priceChanges.add(current.value - previous.value)
        } else {
            // If any price in a change calculation is undefined, the change is undefined.
            // This will propagate NaN in RSI. Add a placeholder that will lead to NaN.
            priceChanges.add(Double.NaN)
        }
    }

    if (priceChanges.size < period) {
         // Fill rsiValues with NaN up to prices.a length
        for (i in 0 until prices.a) {
            rsiValues.add(Double.NaN)
        }
        return object : Indexed<Double> {
            override val a: Int = rsiValues.size
            override fun b(index: Int): Double = rsiValues[index]
        }
    }

    // Add NaN for initial periods where RSI cannot be calculated
    // RSI needs 'period' changes, so first 'period' price points + 1 for first change
    // = 'period+1' prices result in first RSI.
    // So, for indices 0 to period-1 of priceChanges (which means indices 0 to period of prices), RSI is NaN.
    // The chronicle seems to align RSI output with the price input index.
    for (i in 0 until period) {
        rsiValues.add(Double.NaN)
    }

    var avgGain = 0.0
    var avgLoss = 0.0

    // Calculate initial average gain/loss for the first RSI value
    var initialGainsSum = 0.0
    var initialLossesSum = 0.0
    var validInitialChanges = 0
    for (i in 0 until period) {
        val change = priceChanges[i]
        if (change.isNaN()) { // If any change in the initial window is NaN, first RSI is NaN
            avgGain = Double.NaN // Mark as NaN to propagate
            break
        }
        if (change > 0) {
            initialGainsSum += change
        } else {
            initialLossesSum += -change // Losses are positive values
        }
        validInitialChanges++
    }

    if (validInitialChanges == period && !avgGain.isNaN()) {
        avgGain = initialGainsSum / period
        avgLoss = initialLossesSum / period
    } else { // Not enough valid changes for the first RSI calculation
        avgGain = Double.NaN // Propagate NaN
        avgLoss = Double.NaN
    }

    if (avgGain.isNaN()) { // If first avgGain/Loss is NaN, all subsequent RSIs depending on it are NaN
        for (i in period until prices.a) {
            rsiValues.add(Double.NaN)
        }
    } else {
        val firstRs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
        val firstRsi = 100.0 - (100.0 / (1.0 + firstRs))
        rsiValues.add(if(firstRsi.isInfinite()) 100.0 else firstRsi) // Handle infinite RS from zero avgLoss

        // Calculate subsequent RSI values using Wilder's smoothing
        for (i in period until priceChanges.size) {
            val change = priceChanges[i]
            if (change.isNaN()) { // If current change is NaN, this and subsequent RSIs become NaN
                avgGain = Double.NaN
                rsiValues.add(Double.NaN)
                continue
            }
            if (avgGain.isNaN()) { // If previous avgGain was NaN, current and subsequent are NaN
                 rsiValues.add(Double.NaN)
                 continue
            }

            val currentGain = if (change > 0) change else 0.0
            val currentLoss = if (change < 0) -change else 0.0

            avgGain = (avgGain * (period - 1) + currentGain) / period
            avgLoss = (avgLoss * (period - 1) + currentLoss) / period

            val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
            var rsi = 100.0 - (100.0 / (1.0 + rs))
            if (rsi.isInfinite()) rsi = 100.0 // Cap at 100 if avgLoss is 0
            rsiValues.add(rsi)
        }
    }

    // Ensure rsiValues list has the same size as prices input, padding with NaN if necessary
    // This can happen if priceChanges had NaNs shortening the loop for subsequent RSIs.
    while (rsiValues.size < prices.a) {
        rsiValues.add(Double.NaN)
    }

    return object : Indexed<Double> {
        override val a: Int = rsiValues.size
        override fun b(index: Int): Double = rsiValues[index]
    }
}
