package moneyfan.indicators

import moneyfan.models.Price
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.emptySeries
import moneyfan.trikeshed.j // For Indexed construction (Int.j)

/**
 * Calculates the Simple Moving Average (SMA) for a series of prices.
 *
 * The SMA is the unweighted mean of the previous `period` data points.
 * For elements where the SMA cannot be calculated (i.e., the first `period - 1` elements),
 * the resulting `Indexed<Price>` will contain `Price.UNDEFINED`.
 * The output Indexed will have the same size as the input `prices` series.
 *
 * @param prices The series of prices to calculate the SMA from.
 * @param period The number of data points to include in the moving average calculation.
 *               Must be greater than 0.
 * @return A `Indexed<Price>` containing the calculated SMA values.
 *         Returns an `emptySeries()` if the input `prices` series is empty.
 * @throws IllegalArgumentException if `period` is less than or equal to 0.
 */
fun calculateSMA(prices: Indexed<Price>, period: Int): Indexed<Price> {
    if (period <= 0) {
        throw IllegalArgumentException("Period must be greater than 0, but was $period.")
    }

    if (prices.isEmpty()) {
        return emptySeries()
    }

    // The output series will have the same size as the input series.
    // For indices where SMA cannot be computed (less than `period - 1` data points available),
    // Price.UNDEFINED is used.
    return prices.component1() j { index:Int ->
        if (index < period - 1) {
            Price.UNDEFINED
        } else {
            var sum = 0.0
            // Sum the prices over the defined period window.
            // The window ends at `index` and starts at `index - period + 1`.
            for (i in (index - period + 1)..index) {
                // It's possible that earlier prices in the window are Price.UNDEFINED themselves
                // if they were the result of a previous calculation.
                // An SMA calculation should typically operate on raw, defined prices.
                // If prices[i] could be NaN, sum could become NaN. This is acceptable.
                sum += prices.component2()(i).value // Access the underlying Double value of Price
            }
            Price(sum / period)
        }
    }
}

// --- Example Usage (Conceptual, would typically be in a test or example file) ---
/*
fun main() {
    // Sample prices
    val priceList = listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0)
    val priceSeries = priceList.map { Price(it) }.toSeries()

    val period5 = 5
    val sma5 = calculateSMA(priceSeries, period5)

    println("Original Prices: ${priceSeries.toList().map { it.value }}")
    println("SMA(5) Prices: ${sma5.toList().map { it.value }}")
    // Expected SMA(5): [NaN, NaN, NaN, NaN, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0]

    val period3 = 3
    val sma3 = calculateSMA(priceSeries, period3)
    println("SMA(3) Prices: ${sma3.toList().map { it.value }}")
    // Expected SMA(3): [NaN, NaN, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0]

    // Edge case: empty series
    val emptyPriceSeries = emptySeries<Price>()
    val smaEmpty = calculateSMA(emptyPriceSeries, 5)
    println("SMA(5) for empty series: ${smaEmpty.toList()}") // Expected: []

    // Edge case: period longer than series length
    val shortPriceSeries = listOf(20.0, 21.0).map{ Price(it) }.toSeries()
    val smaShort = calculateSMA(shortPriceSeries, 3)
    println("SMA(3) for short series (2 elements): ${smaShort.toList().map { it.value }}") // Expected: [NaN, NaN]

    try {
        calculateSMA(priceSeries, 0)
    } catch (e: IllegalArgumentException) {
        println("Caught expected error for period <= 0: ${e.message}")
    }
}
*/
