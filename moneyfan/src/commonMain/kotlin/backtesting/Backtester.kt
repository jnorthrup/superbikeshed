package backtesting

import com.google.trike.series.* // Star import for Indexed, Cursor, RowVec, Join, ColumnMeta etc.
import com.google.trike.series.memseries.* // Star import for MemSeries
import strategies.TradingSignal // Specific import for enum from another package

/**
 * Represents an executed trade during a backtest.
 * @param signal The [TradingSignal] that triggered the trade (BUY, SELL). Note: HOLD signals don't generate trades.
 * @param price The price at which the asset was bought or sold.
 * @param timestamp The timestamp of the kline/data point at which the trade occurred.
 */
data class Trade(val signal: TradingSignal, val price: Double, val timestamp: Long)

/**
 * Encapsulates the results of a backtesting run.
 * @param trades A list of [Trade] objects representing all executed trades.
 * @param totalReturn The overall return of the strategy, calculated as (finalEquity - initialEquity) / initialEquity.
 * @param finalEquity The value of the portfolio at the end of the backtest.
 */
data class BacktestResult(
    val trades: List<Trade>,
    val totalReturn: Double,
    val finalEquity: Double
)

/**
 * Interface for trading strategies to be used with the Backtester.
 */
interface TradingStrategyInterface {
    /**
     * Generates a trading signal based on the current price and historical data.
     * @param currentPrice The current price for decision making.
     * @param historicalPrices A series of historical prices up to the current point.
     * @param historicalDataPoint The full RowVec for the current data point, offering more context.
     * @return TradingSignal The generated trading signal.
     */
    fun getSignal(
        currentPrice: Double,
        historicalPrices: Indexed<Double>,
        historicalDataPoint: RowVec // RowVec is Indexed<Join<Any?, () -> ColumnMeta>>
    ): TradingSignal
}

/**
 * Performs backtesting of trading strategies against historical kline data.
 * It simulates trade execution based on signals from a [TradingStrategyInterface]
 * and calculates performance metrics.
 *
 * @param initialEquity The starting capital for the backtest.
 */
class Backtester(val initialEquity: Double) {

    // Helper function to extract (timestamp, closePrice) pairs from a list of RowVecs.
    // Assumes 'timestamp' is at index 0 and 'close' is at index 4 within each RowVec,
    // conforming to klinesRecordMeta structure.
    private fun extractTimestampsAndClosePrices(allKlineData: List<RowVec>): List<Pair<Long, Double>> {
        return allKlineData.mapNotNull { rowVec ->
            // Ensure RowVec has enough columns as per klinesRecordMeta (at least 5 for close price).
            // Index 4 for 'close' means size must be at least 5.
            // Index 0 for 'timestamp'.
            if (rowVec.size >= 5) {
                val timestamp = rowVec.values.getOrNull(0)?.a as? Long
                val closePrice = rowVec.values.getOrNull(4)?.a as? Double
                if (timestamp != null && closePrice != null) Pair(timestamp, closePrice) else null
            } else {
                null // RowVec is too short or data types are incorrect.
            }
        }
    }

    /**
     * Runs the backtest for a given strategy using kline data provided by a [Cursor].
     *
     * @param klineCursor A [Cursor] that provides kline data, where each item is a [RowVec].
     *                    The structure of [RowVec]s should conform to `klinesRecordMeta`.
     * @param strategy The [TradingStrategyInterface] implementation to be tested.
     * @return [BacktestResult] containing the list of trades, total return, and final equity.
     */
    fun run(klineCursor: Cursor, strategy: TradingStrategyInterface): BacktestResult {
        var currentEquity = initialEquity
        var positionAssetAmount = 0.0 // Represents the quantity of the asset held.
        val trades = mutableListOf<Trade>()

        // CLAUDE.md Guideline: Prefer direct Cursor iteration if memory allows and simplifies access.
        // Here, klineCursor.toList() loads all data into memory. This is a pragmatic choice
        // that simplifies creating sub-series of historical prices for the strategy.
        // For very large datasets, an on-demand streaming approach for historical data
        // might be necessary, which would complicate the strategy interface or internal data handling.
        val allKlineData: List<RowVec> = klineCursor.toList()

        if (allKlineData.isEmpty()) {
            return BacktestResult(trades = emptyList(), totalReturn = 0.0, finalEquity = initialEquity)
        }

        // Extract time-price pairs once for efficient iteration. This list drives the main loop.
        // This step assumes that the essential data for the backtesting loop are timestamps and close prices.
        val timestampsAndClosePrices = extractTimestampsAndClosePrices(allKlineData)

        if (timestampsAndClosePrices.isEmpty() && allKlineData.isNotEmpty()){
            // This implies that all RowVecs in klineCursor were malformed or did not contain
            // the expected timestamp and close price data according to `extractTimestampsAndClosePrices`.
            return BacktestResult(trades = emptyList(), totalReturn = 0.0, finalEquity = initialEquity)
        }

        for (i in timestampsAndClosePrices.indices) {
            val currentTimestamp = timestampsAndClosePrices[i].first
            val currentPrice = timestampsAndClosePrices[i].second
            // Retrieve the original RowVec for the current point to pass to the strategy.
            // This relies on a 1:1 correspondence between `allKlineData` and `timestampsAndClosePrices`.
            val currentRowVec = allKlineData[i]

            // Prepare historical prices for the strategy: all prices *before* the current one.
            // This subList creates a view, and .map subsequently creates a new list of Doubles.
            val historicalPricesData = timestampsAndClosePrices.subList(0, i).map { it.second }
            val historicalPricesSeries = MemSeries.ofDoubles(historicalPricesData)

            // Get signal from the strategy based on current market conditions and historical context.
            val signal = strategy.getSignal(currentPrice, historicalPricesSeries, currentRowVec)

            when (signal) {
                TradingSignal.BUY -> {
                    if (currentEquity > 0) { // Can only buy if there's equity.
                        // Simple portfolio logic: use all available equity to buy the asset.
                        positionAssetAmount = currentEquity / currentPrice
                        currentEquity = 0.0 // Equity is now held in the asset.
                        trades.add(Trade(TradingSignal.BUY, currentPrice, currentTimestamp))
                    }
                }
                TradingSignal.SELL -> {
                    if (positionAssetAmount > 0) { // Can only sell if holding the asset.
                        currentEquity = positionAssetAmount * currentPrice // Convert asset back to equity.
                        positionAssetAmount = 0.0 // No asset held after selling.
                        trades.add(Trade(TradingSignal.SELL, currentPrice, currentTimestamp))
                    }
                }
                TradingSignal.HOLD -> {
                    // No change in position or equity.
                }
            }
        }

        // After iterating through all kline data, liquidate any remaining position at the last known price.
        if (positionAssetAmount > 0) {
            val lastPrice = timestampsAndClosePrices.lastOrNull()?.second
            if (lastPrice != null && lastPrice > 0) { // Ensure there's a valid price for liquidation.
                 currentEquity = positionAssetAmount * lastPrice
                 positionAssetAmount = 0.0 // Position is now zero.
                 // Optionally, record this liquidation as a distinct trade type if analysis requires it:
                 // trades.add(Trade(TradingSignal.SELL, lastPrice, timestampsAndClosePrices.last().first))
                 // Or use a specific "LIQUIDATE" signal if defined.
            } else {
                // If no valid last price, the value of positionAssetAmount is uncertain or lost.
                // In this simple model where currentEquity is 0 when position is held,
                // failing to liquidate means the asset's value isn't recovered into finalEquity.
            }
        }

        val finalEquity = currentEquity
        // Calculate total return, handling division by zero if initialEquity was 0.
        val totalReturn = if (initialEquity == 0.0) 0.0 else (finalEquity - initialEquity) / initialEquity

        return BacktestResult(trades, totalReturn, finalEquity)
    }
}
