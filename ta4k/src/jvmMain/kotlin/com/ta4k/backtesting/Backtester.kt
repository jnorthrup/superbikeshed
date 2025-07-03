package com.ta4k.backtesting

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Indexed // Using local TrikeShed Indexed
import borg.trikeshed.lib.size // Import Indexed extensions
import borg.trikeshed.lib.get  // Import Indexed extensions
import com.ta4k.strategy.Strategy
import com.ta4k.trading.TradingRecord
import com.ta4k.trading.entities.Order
import com.ta4k.trading.entities.OrderType
import com.ta4k.trading.entities.OrderStatus

/**
 * A basic backtesting engine that simulates a strategy over historical kline data.
 *
 * @property klineSeries The historical kline data to run the backtest on.
 * @property strategy The trading strategy to simulate.
 * @property initialCapital While not used for P&L calculations directly in this version
 *                          (P&L is absolute), it's a common concept. For now, it's illustrative.
 * @property defaultOrderAmount The default amount/quantity for orders if not specified by strategy.
 *                              For simplicity, using a fixed amount for now.
 */
class Backtester(
    private val klineSeries: Indexed<Kline>,
    private val strategy: Strategy,
    @Suppress("UNUSED_PARAMETER") private val initialCapital: BigDecimal = BigDecimal("100000"), // Illustrative, marked unused
    private val defaultOrderAmount: BigDecimal = BigDecimal("1")    // e.g., 1 unit of base asset
) {
    /**
     * Runs the backtest simulation.
     *
     * @return A [TradingRecord] containing all executed trades and the final state.
     */
    fun run(): TradingRecord {
        val tradingRecord = TradingRecord()

        if (klineSeries.size == 0) {
            System.err.println("Warning: Kline series is empty. Nothing to backtest.")
            return tradingRecord
        }

        val startIndex = strategy.warmUpPeriod
        if (startIndex >= klineSeries.size) {
            System.err.println("Warning: Warm-up period (${strategy.warmUpPeriod}) is too long for the kline series size (${klineSeries.size}). No trading will occur.")
            return tradingRecord
        }

        for (index in startIndex until klineSeries.size) {
            val currentKline = klineSeries[index]
            val currentOpenPosition = tradingRecord.getOpenPosition()

            // Update open position's market price for unrealized P&L tracking
            currentOpenPosition?.let {
                tradingRecord.updateOpenPositionMarketPrice(index, currentKline.closePrice)
            }

            val signalOrder = strategy.generateSignal(index, currentOpenPosition)

            if (signalOrder != null) {
                // Process the signal
                if (currentOpenPosition == null) { // No open position, looking for an entry
                    // Strategy should signal BUY for long entry, SELL for short entry.
                    // We only process if the order type is valid for an entry.
                    // (e.g. a strategy might mistakenly signal SELL when it meant to close a long, but there's no position)
                    if (signalOrder.type == OrderType.BUY || signalOrder.type == OrderType.SELL) {
                        // Entry Signal
                        val entryAmount = if (signalOrder.amount.compareTo(BigDecimal.ZERO) > 0) signalOrder.amount else defaultOrderAmount

                        val filledEntryOrder = signalOrder.copy(
                            filledPrice = currentKline.closePrice, // Execute at current bar's close
                            filledAmount = entryAmount,
                            status = OrderStatus.FILLED,
                            klineIndex = index
                        )
                        tradingRecord.openPosition(filledEntryOrder, index, currentKline.closePrice)
                        // System.out.println("Index $index: Opened ${filledEntryOrder.type} position @ ${filledEntryOrder.filledPrice} for ${filledEntryOrder.filledAmount}")
                    }
                } else { // Already in a position, looking for an exit
                    val positionType = currentOpenPosition.type
                    // Exit if signal type is opposite to current position type
                    if ((positionType == OrderType.BUY && signalOrder.type == OrderType.SELL) ||
                        (positionType == OrderType.SELL && signalOrder.type == OrderType.BUY)) {
                        // Exit Signal
                        val exitAmount = currentOpenPosition.amount // Close the full position amount

                        val filledExitOrder = signalOrder.copy(
                            filledPrice = currentKline.closePrice, // Execute at current bar's close
                            filledAmount = exitAmount,
                            status = OrderStatus.FILLED,
                            klineIndex = index
                        )
                        tradingRecord.closePosition(filledExitOrder)
                        // trade?.let {
                        //    System.out.println("Index $index: Closed ${positionType} position @ ${filledExitOrder.filledPrice}. P&L: ${it.profitLoss}")
                        // }
                    }
                    // Note: This simple backtester does not handle:
                    // - Scaling out (partial exits) based on signalOrder.amount if it's less than position amount.
                    // - Scaling in / pyramiding (if signal is same type as open position).
                    // - Stop-loss / take-profit orders generated by strategy (would need order management).
                }
            }
        }
        // If a position is still open at the end, update its final mark-to-market price
        tradingRecord.getOpenPosition()?.let {
            if (klineSeries.size > 0) { // Ensure there's at least one kline to get a price from
                 val lastKlineIndex = klineSeries.size -1
                 val lastPrice = klineSeries[lastKlineIndex].closePrice
                 tradingRecord.updateOpenPositionMarketPrice(lastKlineIndex, lastPrice)
            }
        }
        return tradingRecord
    }
}
