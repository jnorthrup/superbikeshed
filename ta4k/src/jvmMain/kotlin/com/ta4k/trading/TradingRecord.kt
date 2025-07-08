package com.ta4k.trading

import com.ta4k.trading.entities.Order
import com.ta4k.trading.entities.OrderType
import com.ta4k.trading.entities.Position
import com.ta4k.trading.entities.Trade
import com.ta4k.trading.entities.OrderStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Manages the state of trades and open positions for a trading session or backtest.
 */
class TradingRecord {
    internal val trades = mutableListOf<Trade>()
    internal var currentPosition: Position? = null

    /**
     * Returns an immutable list of all executed trades.
     */
    fun getTrades(): List<Trade> = trades.toList()

    /**
     * Returns the currently open position, or null if no position is open.
     */
    fun getOpenPosition(): Position? = currentPosition

    /**
     * Opens a new position based on the provided entry order.
     * Assumes the order's filledPrice and filledAmount are set externally (by the backtester).
     *
     * @param entryOrder The (filled) order that initiates the position.
     * @param currentKlineIndex The kline index at which this position is being recognized as open.
     * @param currentPrice The price at currentKlineIndex, used for initial P&L calculation of the position.
     * @return True if the position was successfully opened, false otherwise (e.g., if a position is already open).
     */
    fun openPosition(entryOrder: Order, currentKlineIndex: Int, currentPrice: BigDecimal): Boolean {
        if (currentPosition != null) {
            // Log or handle error: Cannot open a new position when one is already open (for simple strategies)
            System.err.println("Warning: Attempted to open a position while another is already open. Order ID: ${entryOrder.id}")
            return false
        }
        if (entryOrder.status != OrderStatus.FILLED || entryOrder.filledPrice == null || entryOrder.filledAmount == null || entryOrder.filledAmount!! <= BigDecimal.ZERO) {
            // Log or handle error: Entry order must be filled and have valid price/amount
            System.err.println("Warning: Attempted to open position with an order not properly filled. Order ID: ${entryOrder.id}, Status: ${entryOrder.status}")
            return false
        }
        currentPosition = Position(
            entryOrder = entryOrder,
            currentKlineIndex = currentKlineIndex,
            currentPrice = currentPrice
        )
        return true
    }

    /**
     * Closes the currently open position based on the provided exit order.
     * Calculates profit/loss for the trade and adds it to the record.
     * Assumes the order's filledPrice and filledAmount are set externally (by the backtester).
     *
     * @param exitOrder The (filled) order that closes the position.
     * @return The executed [Trade] if the position was successfully closed, null otherwise (e.g., no open position).
     */
    fun closePosition(exitOrder: Order): Trade? {
        val positionToClose = currentPosition
        if (positionToClose == null) {
            System.err.println("Warning: Attempted to close a position when none is open. Order ID: ${exitOrder.id}")
            return null
        }
        if (exitOrder.status != OrderStatus.FILLED || exitOrder.filledPrice == null || exitOrder.filledAmount == null || exitOrder.filledAmount!! <= BigDecimal.ZERO) {
            System.err.println("Warning: Attempted to close position with an order not properly filled. Order ID: ${exitOrder.id}, Status: ${exitOrder.status}")
            return null
        }

        // Ensure exit order type is opposite to entry order type for a simple close.
        // (e.g., BUY to open, SELL to close; or SELL to open, BUY to close)
        if (exitOrder.type == positionToClose.entryOrder.type) {
            System.err.println("Warning: Exit order type ${exitOrder.type} is the same as entry order type. This might indicate logic to increase position rather than close.")
            // Depending on strategy (e.g. pyramiding), this might be valid. For simple close, it's often an error.
            // For now, we'll allow it, but it's a point of attention.
        }


        // Basic check: exit amount should ideally match position amount for full close
        if (exitOrder.filledAmount!! < positionToClose.amount) {
             System.err.println("Warning: Exit amount ${exitOrder.filledAmount} is less than position amount ${positionToClose.amount}. This constitutes a partial close. Full P&L calculation here assumes full close with entry amount.")
             // For simplicity, this example calculates P&L based on the entry amount, assuming the intent was a full close.
             // True partial close P&L would require tracking remaining position size.
        } else if (exitOrder.filledAmount!! > positionToClose.amount) {
            System.err.println("Warning: Exit amount ${exitOrder.filledAmount} is greater than position amount ${positionToClose.amount}. Assuming full close based on original position amount.")
        }


        val entryOrder = positionToClose.entryOrder
        val pnl: BigDecimal
        val tradeAmount = positionToClose.amount // Use the amount from the open position (which came from filled entry order)

        pnl = when (entryOrder.type) {
            OrderType.BUY -> (exitOrder.filledPrice!! - entryOrder.filledPrice!!).multiply(tradeAmount)
            OrderType.SELL -> (entryOrder.filledPrice!! - exitOrder.filledPrice!!).multiply(tradeAmount)
        }

        val scaledPnl = pnl.setScale(2, RoundingMode.HALF_UP)

        val trade = Trade(
            entryOrder = entryOrder,
            exitOrder = exitOrder,
            profitLoss = scaledPnl
        )
        trades.add(trade)
        currentPosition = null
        return trade
    }

    /**
     * Updates the current market price for the open position, which affects its unrealized P&L.
     * @param klineIndex The current kline index.
     * @param marketPrice The current market price.
     */
    fun updateOpenPositionMarketPrice(klineIndex: Int, marketPrice: BigDecimal) {
        currentPosition?.let {
            it.currentKlineIndex = klineIndex
            it.currentPrice = marketPrice
        }
    }

    /**
     * Calculates the total profit or loss from all executed trades.
     */
    fun getTotalProfitLoss(): BigDecimal {
        return trades.fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.profitLoss) }
            .setScale(2, RoundingMode.HALF_UP)
    }
}
