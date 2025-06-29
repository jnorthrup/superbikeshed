package moneyfan.indicators

import moneyfan.models.Price
// Placeholder for TrikeShed's Indexed type. This might need adjustment later.
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α
import moneyfan.trikeshed.j
import moneyfan.trikeshed.emptySeries  // Assuming this exists for empty results

/**
 * Calculates the Simple Moving Average (SMA) for a series of prices.
 *
 * @param prices An Indexed series of Price objects.
 * @param period The number of periods to consider for the SMA.
 * @return An Indexed series of Price objects representing the SMA values.
 *         If the period is invalid or there's not enough data for a period,
 *         the corresponding SMA value will be Price.UNDEFINED.
 */
fun calculateSMA(prices: Indexed<Price>, period: Int): Indexed<Price> {
    if (period <= 0) {
        // Return a series of UNDEFINED prices of the same size as input if period is invalid
        return prices.a j { Price.UNDEFINED }
    }
    if (prices.a < period) {
        // Not enough data to calculate any SMA, return all UNDEFINED
        return prices.a j { Price.UNDEFINED }
    }

    val smaValues = mutableListOf<Price>()

    // Add UNDEFINED for initial periods where SMA cannot be calculated
    for (i in 0 until period - 1) {
        smaValues.add(Price.UNDEFINED)
    }

    // Calculate SMA for the rest of the series
    for (i in period - 1 until prices.a) {
        var sum = 0.0
        var definedCount = 0
        for (j in 0 until period) {
            val price = prices.b(i - j)
            if (price.isDefined()) {
                sum += price.value
                definedCount++
            }
        }
        // Only calculate SMA if all prices in the window are defined
        if (definedCount == period) {
            smaValues.add(Price(sum / period))
        } else {
            smaValues.add(Price.UNDEFINED)
        }
    }

    // Construct Indexed<Price> from smaValues
    // This assumes Indexed can be constructed this way.
    // The exact construction might differ based on TrikeShed's API.
    return object : Indexed<Price> {
        override val a: Int = smaValues.size
        override fun b(index: Int): Price = smaValues[index]
    }
}
