package moneyfan.strategies

import moneyfan.models.Kline
import moneyfan.models.Price
import moneyfan.models.TradingSignal
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α // For klines α {it.close}
import moneyfan.trikeshed.j // For constructing the output series
import moneyfan.indicators.calculateRSI
import moneyfan.indicators.calculateSMA
import moneyfan.models.Price.Companion.UNDEFINED as UNDEFINED_PRICE // Alias for clarity

/**
 * Implements the Carlos RSI(2) trading strategy.
 *
 * Strategy Rules:
 * Entry:
 *   - RSI(2) < 5
 *   - Trend Confirmation: Current Close > SMA(2) AND SMA(2) > SMA(15)
 * Exit:
 *   - RSI(2) > 95 OR
 *   - Trend Reversal: Current Close < SMA(2) OR SMA(2) < SMA(15)
 *
 * If neither entry nor exit conditions are met, or if any required indicator value
 * is undefined, the signal is HOLD.
 *
 * @param klines A Indexed of Kline objects representing historical price data.
 * @return A Indexed of TradingSignal objects of the same size as `klines`.
 */
fun executeCarlosRSI2Strategy(klines: Indexed<Kline>): Indexed<TradingSignal> {
    if (klines.isEmpty()) {
        return moneyfan.trikeshed.emptySeries()
    }

    // a. Extract Close Prices
    val closePrices = klines.α { it.close }

    // b. Calculate Indicators
    val rsi2 = calculateRSI(closePrices, 2)
    val sma2 = calculateSMA(closePrices, 2)
    val sma15 = calculateSMA(closePrices, 15)

    // c. Determine Trading Signals
    return klines.a j { index:Int ->
        val currentPrice = closePrices.b(index)
        val currentRsi = rsi2.b(index) // This is a Double
        val currentSma2 = sma2.b(index)
        val currentSma15 = sma15.b(index)

        // Handle Undefined Indicator Values
        if (currentPrice == UNDEFINED_PRICE ||
            currentRsi.isNaN() ||
            currentSma2 == UNDEFINED_PRICE ||
            currentSma15 == UNDEFINED_PRICE) {
            TradingSignal.HOLD
        } else {
            // Entry Condition
            val isRsiBuy = currentRsi < 5.0
            val isTrendConfirmed = currentPrice.value > currentSma2.value && currentSma2.value > currentSma15.value

            if (isRsiBuy && isTrendConfirmed) {
                TradingSignal.BUY
            } else {
                // Exit Condition
                // Note: The original description for Trend Reversal was "Current Close < SMA(2) OR SMA(2) < SMA(15)"
                // This can lead to exiting a position even if the trend is still strong but RSI is high.
                // Let's stick to the provided rules.
                val isRsiSell = currentRsi > 95.0
                val isTrendReversed = currentPrice.value < currentSma2.value || currentSma2.value < currentSma15.value

                if (isRsiSell || isTrendReversed) {
                    TradingSignal.SELL
                } else {
                    TradingSignal.HOLD
                }
            }
        }
    }
}

// --- Example Usage (Conceptual) ---
/*
fun main() {
    // Assume klineDataSeries is a Indexed<Kline> loaded elsewhere
    // For example, using HistoricalDataService and MockFileContentProvider as in previous examples

    // val klineDataSeries: Indexed<Kline> = ... create or load some Kline data ...
    // if (klineDataSeries.isNotEmpty()) {
    //     val signals = executeCarlosRSI2Strategy(klineDataSeries)
    //
    //     println("Signals generated (${signals.a} count):")
    //     signals.toList().forEachIndexed { index, signal ->
    //         val kline = klineDataSeries.b(index)
    //         val closePrice = kline.close.value
    //         val rsiVal = calculateRSI(klineDataSeries.α{it.close}, 2).b(index) // For display
    //         val sma2Val = calculateSMA(klineDataSeries.α{it.close}, 2).b(index).value // For display
    //         val sma15Val = calculateSMA(klineDataSeries.α{it.close}, 15).b(index).value // For display
    //
    //         println(
    //             "Index: $index, Date: ${moneyfan.examples.klineTimestampToLocalDateTimeString(kline.timestamp)}, " +
    //             "Close: $closePrice, RSI(2): ${if(rsiVal.isNaN()) "NaN" else "%.2f".format(rsiVal)}, " +
    //             "SMA(2): ${if(sma2Val.isNaN()) "NaN" else "%.2f".format(sma2Val)}, " +
    //             "SMA(15): ${if(sma15Val.isNaN()) "NaN" else "%.2f".format(sma15Val)}, " +
    //             "Signal: $signal"
    //         )
    //     }
    // } else {
    //     println("Kline data series is empty, no strategy execution.")
    // }
}
*/
