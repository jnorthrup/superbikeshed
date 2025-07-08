package com.ta4k.strategy

import com.ta4k.core.model.Kline
import com.ta4k.trading.entities.Order
import com.ta4k.trading.entities.Position

/**
 * Base interface for a trading strategy.
 * A strategy defines the logic for generating entry and exit signals based on market data.
 */
interface Strategy {
    /**
     * The kline series on which this strategy operates.
     */
    val klineSeries: Indexed<Kline>

    /**
     * The number of klines required to warm up all indicators used by this strategy.
     * The backtester should start generating signals from this index onwards.
     * For strategies without indicators needing warm-up, this can be 0.
     */
    val warmUpPeriod: Int
        get() = 0 // Default to 0 if not overridden

    /**
     * Generates a trading signal (an Order) based on the kline data at the given index
     * and the current open position (if any).
     *
     * @param index The current kline index in the [klineSeries].
     * @param currentPosition The currently open [Position], or null if no position is open.
     * @return An [Order] object if a trading signal (entry or exit) is generated, otherwise null.
     *         The order type (BUY/SELL) will determine if it's an entry or exit signal
     *         in conjunction with the [currentPosition] state.
     */
    fun generateSignal(index: Int, currentPosition: Position?): Order?
}
