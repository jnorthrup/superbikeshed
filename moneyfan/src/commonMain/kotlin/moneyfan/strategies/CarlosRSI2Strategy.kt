package moneyfan.strategies

import moneyfan.models.Kline
import moneyfan.models.Price
import moneyfan.models.TradingSignal
// Placeholders for TrikeShed types and functions. These might need adjustment.
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α
import moneyfan.trikeshed.j
import moneyfan.trikeshed.emptySeries
import moneyfan.indicators.calculateRSI
import moneyfan.indicators.calculateSMA

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
 * @param klines An Indexed series of Kline objects representing historical price data.
 * @return An Indexed series of TradingSignal objects of the same size as `klines`.
 */
fun executeCarlosRSI2Strategy(klines: Indexed<Kline>): Indexed<TradingSignal> {
    if (klines.a == 0) { // Use .a for size from TrikeShed's Indexed
        return emptySeries() // Assuming emptySeries() creates an empty Indexed<TradingSignal>
    }

    // a. Extract Close Prices
    // Assuming klines.α { it.close } works as intended by TrikeShed
    val closePrices = object : Indexed<Price> {
        override val a: Int = klines.a
        override fun b(index: Int): Price = klines.b(index).close
    }

    // b. Calculate Indicators
    val rsi2 = calculateRSI(closePrices, 2)
    val sma2 = calculateSMA(closePrices, 2)
    val sma15 = calculateSMA(closePrices, 15)

    // c. Determine Trading Signals
    // Assuming klines.a j { index -> ... } creates an Indexed<TradingSignal>
    return object : Indexed<TradingSignal> {
        override val a: Int = klines.a
        override fun b(index: Int): TradingSignal {
            val currentPrice = closePrices.b(index)
            val currentRsi = rsi2.b(index) // This is a Double
            val currentSma2 = sma2.b(index)
            val currentSma15 = sma15.b(index)

            // Handle Undefined Indicator Values
            if (currentPrice.isUndefined() ||
                currentRsi.isNaN() ||
                currentSma2.isUndefined() ||
                currentSma15.isUndefined()) {
                return TradingSignal.HOLD
            }

            // Entry Condition
            val isRsiBuy = currentRsi < 5.0
            val isTrendConfirmed = currentPrice.value > currentSma2.value && currentSma2.value > currentSma15.value

            if (isRsiBuy && isTrendConfirmed) {
                return TradingSignal.BUY
            } else {
                // Exit Condition
                val isRsiSell = currentRsi > 95.0
                val isTrendReversed = currentPrice.value < currentSma2.value || currentSma2.value < currentSma15.value

                if (isRsiSell || isTrendReversed) {
                    return TradingSignal.SELL
                } else {
                    return TradingSignal.HOLD
                }
            }
        }
    }
}
