package moneyfan.strategies

import moneyfan.models.Kline
import moneyfan.models.Price
import moneyfan.models.TradingSignal
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α // For klines α {it.close}
import moneyfan.trikeshed.j // For constructing the output series
import moneyfan.indicators.calculateSMA
import moneyfan.models.Price.Companion.UNDEFINED as UNDEFINED_PRICE // Alias for clarity

/**
 * Implements the Kraken Skimmer trading strategy.
 *
 * The strategy generates signals based on price deviations from a baseline Simple Moving Average (SMA).
 * - A SELL signal (Harvest) is generated if the current price exceeds the baseline SMA by a certain percentage (harvestThreshold).
 * - A BUY signal (Rebalance) is generated if the current price falls below the baseline SMA by a certain percentage (rebalanceThreshold).
 *
 * @param klines A Indexed of Kline objects representing historical price data.
 * @param baselinePeriod The period for calculating the baseline SMA. Defaults to 20.
 * @param harvestThreshold The percentage above the baseline SMA to trigger a SELL signal (e.g., 0.03 for 3%). Defaults to 0.03.
 * @param rebalanceThreshold The percentage below the baseline SMA to trigger a BUY signal (e.g., 0.04 for 4%). Defaults to 0.04.
 * @return A Indexed of TradingSignal objects of the same size as `klines`.
 */
fun executeKrakenSkimmerStrategy(
    klines: Indexed<Kline>,
    baselinePeriod: Int = 20,
    harvestThreshold: Double = 0.03,
    rebalanceThreshold: Double = 0.04
): Indexed<TradingSignal> {

    if (klines.isEmpty()) {
        return moneyfan.trikeshed.emptySeries()
    }
    if (baselinePeriod <= 0) {
        throw IllegalArgumentException("baselinePeriod must be greater than 0, but was $baselinePeriod.")
    }
    if (harvestThreshold < 0) {
        throw IllegalArgumentException("harvestThreshold cannot be negative, but was $harvestThreshold.")
    }
    if (rebalanceThreshold < 0) {
        throw IllegalArgumentException("rebalanceThreshold cannot be negative, but was $rebalanceThreshold.")
    }

    // a. Extract Close Prices
    val closePrices = klines.α { it.close }

    // b. Calculate Baseline Price
    val baselineSma = calculateSMA(closePrices, baselinePeriod)

    // c. Determine Trading Signals
    return klines.a j { index:Int ->
        val currentPrice = closePrices.b(index)
        val currentBaseline = baselineSma.b(index)

        // Handle Undefined Values
        if (currentPrice == UNDEFINED_PRICE || currentBaseline == UNDEFINED_PRICE) {
            TradingSignal.HOLD
        } else {
            val currentPriceValue = currentPrice.value
            val currentBaselineValue = currentBaseline.value

            // Harvest Condition (SELL)
            if (currentPriceValue > currentBaselineValue * (1 + harvestThreshold)) {
                TradingSignal.SELL
            }
            // Rebalance Condition (BUY)
            else if (currentPriceValue < currentBaselineValue * (1 - rebalanceThreshold)) {
                TradingSignal.BUY
            }
            // Else (no trigger)
            else {
                TradingSignal.HOLD
            }
        }
    }
}

// --- Example Usage (Conceptual) ---
/*
fun main() {
    // Assume klineDataSeries is a Indexed<Kline> loaded elsewhere

    // val klineDataSeries: Indexed<Kline> = ... create or load some Kline data ...
    // if (klineDataSeries.isNotEmpty()) {
    //     val signals = executeKrakenSkimmerStrategy(klineDataSeries) // Using default parameters
    //
    //     println("Kraken Skimmer Signals generated (${signals.a} count):")
    //     signals.toList().forEachIndexed { index, signal ->
    //         val kline = klineDataSeries.b(index)
    //         val closePrice = kline.close.value
    //         val baselineSmaVal = calculateSMA(klineDataSeries.α{it.close}, 20).b(index).value // For display
    //
    //         println(
    //             "Index: $index, Date: ${moneyfan.examples.klineTimestampToLocalDateTimeString(kline.timestamp)}, " +
    //             "Close: $closePrice, BaselineSMA(20): ${if(baselineSmaVal.isNaN()) "NaN" else "%.2f".format(baselineSmaVal)}, " +
    //             "Signal: $signal"
    //         )
    //     }
    // } else {
    //     println("Kline data series is empty, no strategy execution.")
    // }


    // Example with custom parameters
    // val customSignals = executeKrakenSkimmerStrategy(
    //     klines = klineDataSeries,
    //     baselinePeriod = 30,
    //     harvestThreshold = 0.05,
    //     rebalanceThreshold = 0.05
    // )
    // Process customSignals...
}
*/
