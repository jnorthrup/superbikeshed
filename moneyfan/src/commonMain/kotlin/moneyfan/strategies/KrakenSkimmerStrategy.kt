package moneyfan.strategies

import moneyfan.models.Kline
import moneyfan.models.Price
import moneyfan.models.TradingSignal
// Placeholders for TrikeShed types and functions. These might need adjustment.
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.α
import moneyfan.trikeshed.j
import moneyfan.trikeshed.emptySeries
import moneyfan.indicators.calculateSMA

/**
 * Implements the Kraken Skimmer trading strategy.
 *
 * The strategy generates signals based on price deviations from a baseline Simple Moving Average (SMA).
 * - A SELL signal (Harvest) is generated if the current price exceeds the baseline SMA by a certain percentage (harvestThreshold).
 * - A BUY signal (Rebalance) is generated if the current price falls below the baseline SMA by a certain percentage (rebalanceThreshold).
 *
 * @param klines An Indexed series of Kline objects representing historical price data.
 * @param baselinePeriod The period for calculating the baseline SMA. Defaults to 20.
 * @param harvestThreshold The percentage above the baseline SMA to trigger a SELL signal (e.g., 0.03 for 3%). Defaults to 0.03.
 * @param rebalanceThreshold The percentage below the baseline SMA to trigger a BUY signal (e.g., 0.04 for 4%). Defaults to 0.04.
 * @return An Indexed series of TradingSignal objects of the same size as `klines`.
 */
fun executeKrakenSkimmerStrategy(
    klines: Indexed<Kline>,
    baselinePeriod: Int = 20,
    harvestThreshold: Double = 0.03,
    rebalanceThreshold: Double = 0.04
): Indexed<TradingSignal> {

    if (klines.a == 0) {
        return emptySeries() // Assuming emptySeries() creates an empty Indexed<TradingSignal>
    }
    if (baselinePeriod <= 0) {
        // Consider logging a warning or throwing IllegalArgumentException as in chronicle
        // For now, return HOLD for all if params are invalid, to prevent runtime errors.
        return object : Indexed<TradingSignal> {
            override val a: Int = klines.a
            override fun b(index: Int): TradingSignal = TradingSignal.HOLD
        }
    }
    // harvestThreshold and rebalanceThreshold checks from chronicle are good practice,
    // but for now, let's assume valid inputs as per primary task of re-creating.

    // a. Extract Close Prices
    val closePrices = object : Indexed<Price> {
        override val a: Int = klines.a
        override fun b(index: Int): Price = klines.b(index).close
    }

    // b. Calculate Baseline Price
    val baselineSma = calculateSMA(closePrices, baselinePeriod)

    // c. Determine Trading Signals
    return object : Indexed<TradingSignal> {
        override val a: Int = klines.a
        override fun b(index: Int): TradingSignal {
            val currentPrice = closePrices.b(index)
            val currentBaseline = baselineSma.b(index)

            // Handle Undefined Values
            if (currentPrice.isUndefined() || currentBaseline.isUndefined()) {
                return TradingSignal.HOLD
            }

            val currentPriceValue = currentPrice.value
            val currentBaselineValue = currentBaseline.value

            // Harvest Condition (SELL)
            if (currentPriceValue > currentBaselineValue * (1 + harvestThreshold)) {
                return TradingSignal.SELL
            }
            // Rebalance Condition (BUY)
            else if (currentPriceValue < currentBaselineValue * (1 - rebalanceThreshold)) {
                return TradingSignal.BUY
            }
            // Else (no trigger)
            else {
                return TradingSignal.HOLD
            }
        }
    }
}
