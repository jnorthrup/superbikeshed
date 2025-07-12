package moneyfan.indicators

import moneyfan.models.Price
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.j // For Indexed construction
import moneyfan.trikeshed.emptySeries
import kotlin.math.abs // For absolute value in losses

/**
 * Calculates the Relative Strength Index (RSI) for a series of prices.
 *
 * RSI is a momentum oscillator that measures the speed and change of price movements.
 * It is calculated using average gains and average losses over a specified period,
 * typically using a Simple Moving Average (SMA) for these averages in this implementation.
 *
 * The output Indexed will have the same size as the input `prices` series.
 * Values where RSI cannot be calculated (typically at the beginning of the series)
 * will be `Double.NaN`.
 *
 * @param prices The series of prices to calculate RSI from.
 * @param period The look-back period for calculating average gains and losses (e.g., 14).
 *               Must be greater than 0.
 * @return A `Indexed<Double>` containing the calculated RSI values (0-100), or `Double.NaN` for undefined values.
 *         Returns an `emptySeries()` if the input `prices` series does not have enough data
 *         (i.e., `prices.component1() < period + 1`, as at least `period` deltas are needed).
 * @throws IllegalArgumentException if `period` is less than or equal to 0.
 */
fun calculateRSI(prices: Indexed<Price>, period: Int): Indexed<Double> {
    if (period <= 0) {
        throw IllegalArgumentException("Period must be greater than 0, but was $period.")
    }

    // Need at least 'period' number of changes, which means 'period + 1' prices.
    // prices.component1() gives the number of elements.
    // prices.component2()(i) is getter for index i.
    // deltas will have prices.component1() elements, first is Price(0.0)
    // gains/losses will have prices.component1() elements.
    // avgGains/avgLosses (SMA on gains/losses) will have prices.component1() elements.
    // First (period-1) elements of avgGains/avgLosses will be Price.UNDEFINED.
    // So, the first calculable RSI will be at index `period` of the original `prices` series.
    if (prices.isEmpty() || prices.component1() < period +1 ) { // prices.component1() < period + 1 means not enough data for any RSI calculation
         // Return a series of NaNs of the same size as prices if prices is not empty
        if (prices.isEmpty()) return emptySeries()
        return prices.component1() j { Double.NaN }
    }

    // a. Calculate Price Deltas
    // The first delta is undefined (or 0). For simplicity, using Price(0.0).
    // Size of deltas series is prices.component1()
    val deltas = prices.component1() j { i:Int ->
        if (i == 0) {
            Price(0.0) // Or Price.UNDEFINED, but 0.0 simplifies gain/loss separation
        } else {
            Price(prices.component2()(i).value - prices.component2()(i - 1).value)
        }
    }

    // b. Separate Gains and Losses
    // Size of gains/losses series is prices.component1()
    val gains = deltas.component1() j { i:Int ->
        val deltaVal = deltas.component2()(i).value
        if (deltaVal > 0) Price(deltaVal) else Price(0.0)
    }

    val losses = deltas.component1() j { i:Int ->
        val deltaVal = deltas.component2()(i).value
        if (deltaVal < 0) Price(abs(deltaVal)) else Price(0.0) // Losses are positive values
    }

    // c. Calculate Average Gains and Average Losses using SMA
    // Size of avgGains/avgLosses series is prices.component1()
    // First `period-1` elements of these will be Price.UNDEFINED
    val avgGains = calculateSMA(gains, period)
    val avgLosses = calculateSMA(losses, period)

    // d. Calculate RS and RSI
    // Size of rsiSeries is prices.component1()
    return prices.component1() j { index:Int ->
        // avgGains/avgLosses have Price.UNDEFINED (which is Price(Double.NaN)) for the first `period-1` elements.
        // The delta calculation also means that the first meaningful gain/loss value is effectively at index 1 of the delta series.
        // So, the first `period-1` values of avgGains/avgLosses (corresponding to original price indices 0 to period-2) are UNDEFINED.
        // Additionally, the delta at index 0 is 0. So gain/loss at index 0 is 0. SMA over these initial 0s might also be 0.
        // The first index where avgGains/avgLosses is NOT Price.UNDEFINED is `period - 1`.
        // However, this corresponds to the SMA over deltas from index 0 to `period-1`.
        // The first meaningful delta is at index 1. So, the first meaningful SMA window ends at delta index `period`.
        // This corresponds to `prices` index `period`.
        if (index < period) { // RSI is typically undefined until the `period`-th index of prices
            Double.NaN
        } else {
            val avgGain = avgGains.component2()(index).value
            val avgLoss = avgLosses.component2()(index).value

            if (avgGain.isNaN() || avgLoss.isNaN()) { // Handles Price.UNDEFINED from SMA
                Double.NaN
            } else if (avgLoss == 0.0) {
                if (avgGain == 0.0) 50.0 else 100.0 // Both 0 => neutral; only gains => max RSI
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
        }
    }
}

// --- Example Usage (Conceptual) ---
/*
fun main() {
    val priceList = listOf(
        44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08,
        45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64
    ).map { Price(it) }
    val prices = priceList.toSeries()

    val rsiPeriod = 14 // Common period for RSI

    // Test with enough data
    if (prices.component1() >= rsiPeriod + 1) {
        val rsi14 = calculateRSI(prices, rsiPeriod)
        println("Prices (${prices.component1()}): ${prices.toList().map { "%.2f".format(it.value) }}")
        println("RSI($rsiPeriod) (${rsi14.component1()}): ${rsi14.toList().map { if (it.isNaN()) "NaN" else "%.2f".format(it) }}")
        // Expected: First `rsiPeriod` values of RSI are NaN.
        // For RSI(14), first 14 values (index 0 to 13) will be NaN. First calculated RSI at index 14.
    } else {
        println("Not enough data to calculate RSI($rsiPeriod). Price points: ${prices.component1()}, Needed: ${rsiPeriod + 1}")
    }


    val simplePrices = listOf(10.0, 11.0, 10.0, 12.0, 9.0, 13.0).map { Price(it) }.toSeries()
    val rsi2 = calculateRSI(simplePrices, 2)
    // Deltas: [0, +1, -1, +2, -3, +4]
    // Gains:  [0,  1,  0,  2,  0,  4]
    // Losses: [0,  0,  1,  0,  3,  0]

    // SMA(2) Gains:  [0, 0.5, 0.5, 1.0, 1.0, 2.0] (indices 0,1,2,3,4,5)
    //                (NaN,0.5, 0.5, 1.0, 1.0, 2.0) if Price.UNDEFINED was used for first SMA
    // calculateSMA output for gains (period 2):
    // gains: [Price(0.0), Price(1.0), Price(0.0), Price(2.0), Price(0.0), Price(4.0)]
    // smaGains: [Price(NaN), Price(0.5), Price(0.5), Price(1.0), Price(1.0), Price(2.0)] (Indices 0,1,2,3,4,5)

    // SMA(2) Losses: [0, 0,   0.5, 0.5, 1.5, 1.5]
    // calculateSMA output for losses (period 2):
    // losses: [Price(0.0), Price(0.0), Price(1.0), Price(0.0), Price(3.0), Price(0.0)]
    // smaLosses: [Price(NaN), Price(0.0), Price(0.5), Price(0.5), Price(1.5), Price(1.5)]

    // RSI calculation:
    // Index 0: NaN (index < period)
    // Index 1: NaN (index < period)
    // Index 2: avgGain=0.5, avgLoss=0.5. RS=1. RSI = 100 - 100/2 = 50.0
    // Index 3: avgGain=1.0, avgLoss=0.5. RS=2. RSI = 100 - 100/3 = 66.66
    // Index 4: avgGain=1.0, avgLoss=1.5. RS=0.666. RSI = 100 - 100/1.666 = 100 - 60 = 40.0
    // Index 5: avgGain=2.0, avgLoss=1.5. RS=1.333. RSI = 100 - 100/2.333 = 100 - 42.8 = 57.14

    println("Simple Prices: ${simplePrices.toList().map{ "%.2f".format(it.value) }}")
    println("RSI(2): ${rsi2.toList().map { if (it.isNaN()) "NaN" else "%.2f".format(it) }}")
    // Expected RSI(2): [NaN, NaN, 50.00, 66.67, 40.00, 57.14]


    // Test with insufficient data
    val tooShortPrices = listOf(10.0, 11.0).map { Price(it) }.toSeries() // Only 2 prices
    val rsiTooShort = calculateRSI(tooShortPrices, 2) // Needs 2+1=3 prices
    println("RSI(2) for too short series: ${rsiTooShort.toList().map { if (it.isNaN()) "NaN" else "%.2f".format(it) }}")
    // Expected: [NaN, NaN] because prices.component1() < period + 1 condition

    try {
        calculateRSI(prices, 0)
    } catch (e: IllegalArgumentException) {
        println("Caught expected error for period <= 0: ${e.message}")
    }
}
*/
