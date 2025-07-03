package strategies

import com.google.trike.series.* // Star import for Indexed, RowVec, etc.
import com.google.trike.series.memseries.* // Star import for MemSeries
import backtesting.TradingStrategyInterface // Specific import for the interface

/**
 * Implements the Kraken Skimmer trading strategy.
 * This strategy uses a baseline (e.g., a 20-period Simple Moving Average)
 * to make decisions for harvesting profits or rebalancing the position.
 *
 * Adheres to CLAUDE.md by using [Indexed] for data manipulation and in interfaces.
 */
class KrakenSkimmerStrategy : TradingStrategyInterface {

    private val baselinePeriod = 20 // Define baseline period, e.g., 20 for SMA20

    /**
     * Generates a trading signal based on the current price and historical data.
     *
     * @param currentPrice The current price for decision-making.
     * @param historicalPrices A [Indexed<Double>] of historical prices up to (but not including) the current point.
     * @param historicalDataPoint The full [RowVec] for the current data point (currently unused).
     * @return [TradingSignal] (BUY, SELL, or HOLD).
     */
    override fun getSignal(
        currentPrice: Double,
        historicalPrices: Indexed<Double>,
        historicalDataPoint: RowVec // Currently unused, available for future strategy enhancements
    ): TradingSignal {
        // For baseline calculation, the current price is appended to historical prices.
        val pricesForBaseline = MemSeries.ofDoubles(historicalPrices.values.toList() + currentPrice)

        // Ensure enough data for the baseline calculation.
        if (pricesForBaseline.size < baselinePeriod) {
            return TradingSignal.HOLD // Not enough data
        }

        val baselinePriceSeries = calculateBaseline(pricesForBaseline)

        if (baselinePriceSeries.isEmpty) {
            return TradingSignal.HOLD // Baseline calculation failed or yielded no result
        }

        // The decision is based on comparing the `currentPrice` against the latest baseline value.
        val latestBaselinePrice = baselinePriceSeries.values.lastOrNull() ?: return TradingSignal.HOLD

        // Harvest Condition: Current price is significantly above the baseline (e.g., > 3% profit).
        if (currentPrice > latestBaselinePrice * 1.03) {
            return TradingSignal.SELL
        }

        // Rebalance Condition: Current price is significantly below the baseline (e.g., < 4% drop).
        if (currentPrice < latestBaselinePrice * 0.96) { // 0.96 corresponds to a 4% drop
            return TradingSignal.BUY
        }

        return TradingSignal.HOLD // Default action if no other conditions are met
    }

    /**
     * Calculates the baseline price series using a Simple Moving Average (SMA).
     * The period for the SMA is defined by [baselinePeriod].
     *
     * CLAUDE.md suggests Indexed-native operations. This implementation uses `.values.toList()`
     * for pragmatic access to underlying data for windowed SMA calculation, similar to
     * justifications in `CarlosRSI2Strategy`. The result is wrapped in a new [Indexed<Double>].
     *
     * @param priceSeries The input [Indexed<Double>] of prices.
     * @return A [Indexed<Double>] containing the calculated baseline (SMA) values.
     */
    internal fun calculateBaseline(priceSeries: Indexed<Double>): Indexed<Double> {
        val prices = priceSeries.values.toList()
        if (prices.size < baselinePeriod) {
            return MemSeries.empty(priceSeries.schema()) // Not enough data for even one SMA value
        }

        val smaValues = mutableListOf<Double>()
        for (i in 0 .. prices.size - baselinePeriod) {
            val window = prices.subList(i, i + baselinePeriod)
            val sum = window.sum()
            smaValues.add(sum / baselinePeriod)
        }

        return if (smaValues.isNotEmpty()) MemSeries.ofDoubles(smaValues) else MemSeries.empty(priceSeries.schema())
    }
}
