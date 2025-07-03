package backtesting

import kotlin.test.*
import com.google.trike.series.* // Star import for Indexed, Cursor, RowVec, Join, ColumnMeta, etc.
import com.google.trike.series.memseries.* // Star import for MemSeries and related utilities
import com.google.trike.series.j // Explicit import for 'j' infix function if not covered by star
import data.klinesRecordMeta // Access to the shared klines data structure definition
import strategies.TradingSignal

/**
 * Mock implementation of [TradingStrategyInterface] for testing the [Backtester].
 * This strategy returns a predefined sequence of signals.
 */
private class MockTradingStrategy(private val signalsToReturn: List<TradingSignal>) : TradingStrategyInterface {
    private var signalIndex = 0

    override fun getSignal(
        currentPrice: Double,
        historicalPrices: Indexed<Double>,
        historicalDataPoint: RowVec
    ): TradingSignal {
        if (signalIndex < signalsToReturn.size) {
            return signalsToReturn[signalIndex++]
        }
        // Default to HOLD if the predefined list of signals is exhausted.
        return TradingSignal.HOLD
    }

    fun reset() {
        signalIndex = 0
    }
}

class BacktesterTest {

    private val testDelta = 0.00001 // Tolerance for Double comparisons

    /**
     * Helper function to create a [RowVec] representing a single kline,
     * conforming to the structure defined by [klinesRecordMeta].
     */
    private fun createKlineRowVec(
        timestamp: Long, open: Double, high: Double, low: Double, close: Double, volume: Double
    ): RowVec {
        // Uses the globally defined klinesRecordMeta (List<RecordMeta>) for schema.
        // Each cell in the RowVec is a Join of the value and its ColumnMeta provider.
        val meta = klinesRecordMeta

        val cells = listOf(
            (timestamp as Any?) j { meta[0] }, // Timestamp
            (open as Any?)      j { meta[1] }, // Open
            (high as Any?)      j { meta[2] }, // High
            (low as Any?)       j { meta[3] }, // Low
            (close as Any?)     j { meta[4] }, // Close
            (volume as Any?)    j { meta[5] }  // Volume
        )
        return MemSeries.ofJoins(cells) as RowVec // Cast to RowVec as per its type alias
    }

    /**
     * Helper function to create a [Cursor] from a list of kline [RowVec]s.
     * This is used to provide mock kline data to the [Backtester].
     */
    private fun createMockKlineCursor(klineData: List<RowVec>): Cursor {
        if (klineData.isEmpty()) {
            // If no data, return an empty cursor that still has the correct schema.
            return MemSeries.empty(SeriesSchema(klinesRecordMeta)).cursor()
        }
        // Create a MemSeries from the list of RowVecs and its schema, then get a cursor.
        return MemSeries.ofRowVecs(SeriesSchema(klinesRecordMeta), klineData).cursor()
    }

    @Test
    fun testBacktesterRun_BuyAndSellProfitable() {
        val initialEquity = 10000.0
        val backtester = Backtester(initialEquity)

        // Define a sequence of klines with specific prices for predictable test outcomes.
        val klines = listOf(
            createKlineRowVec(1678886400000L, 100.0, 101.0, 99.0, 100.0, 1000.0), // Index 0: BUY executed at 100.0
            createKlineRowVec(1678886400001L, 100.0, 110.0, 98.0, 105.0, 1200.0), // Index 1: Price changes, HOLD signal
            createKlineRowVec(1678886400002L, 105.0, 115.0, 103.0, 110.0, 1500.0)  // Index 2: SELL executed at 110.0
        )
        val mockKlineCursor = createMockKlineCursor(klines)

        // Program the mock strategy with a BUY, HOLD, SELL sequence.
        val mockStrategy = MockTradingStrategy(listOf(TradingSignal.BUY, TradingSignal.HOLD, TradingSignal.SELL))

        val result = backtester.run(mockKlineCursor, mockStrategy)

        assertEquals(2, result.trades.size, "Expected 2 trades (BUY, then SELL).")

        // Verify BUY trade details
        val buyTrade = result.trades[0]
        assertEquals(TradingSignal.BUY, buyTrade.signal, "First trade signal should be BUY.")
        assertEquals(100.0, buyTrade.price, testDelta, "BUY price mismatch.")
        assertEquals(klines[0].values[0].a as Long, buyTrade.timestamp, "BUY timestamp mismatch.")

        // Verify SELL trade details
        val sellTrade = result.trades[1]
        assertEquals(TradingSignal.SELL, sellTrade.signal, "Second trade signal should be SELL.")
        assertEquals(110.0, sellTrade.price, testDelta, "SELL price mismatch.")
        assertEquals(klines[2].values[0].a as Long, sellTrade.timestamp, "SELL timestamp mismatch.")

        // Verify financial outcome:
        // Initial: 10000.0 equity.
        // BUY at 100.0: Buys 10000.0 / 100.0 = 100 units. Equity becomes 0. Asset amount = 100.
        // Prices move.
        // SELL 100 units at 110.0: Equity becomes 100 * 110.0 = 11000.0. Asset amount = 0.
        val expectedFinalEquity = 11000.0
        assertEquals(expectedFinalEquity, result.finalEquity, testDelta, "Final equity calculation incorrect.")

        val expectedTotalReturn = (expectedFinalEquity - initialEquity) / initialEquity
        assertEquals(expectedTotalReturn, result.totalReturn, testDelta, "Total return calculation incorrect.")
    }

    @Test
    fun testBacktesterRun_NoTradesIfStrategyAlwaysHolds() {
        val initialEquity = 10000.0
        val backtester = Backtester(initialEquity)
        val klines = listOf(
            createKlineRowVec(1L, 100.0, 101.0, 99.0, 100.0, 1000.0),
            createKlineRowVec(2L, 100.0, 105.0, 98.0, 102.0, 1200.0)
        )
        val mockKlineCursor = createMockKlineCursor(klines)
        // Strategy is set to always HOLD.
        val mockStrategy = MockTradingStrategy(listOf(TradingSignal.HOLD, TradingSignal.HOLD))

        val result = backtester.run(mockKlineCursor, mockStrategy)

        assertEquals(0, result.trades.size, "Should be no trades if strategy always HOLDS.")
        assertEquals(initialEquity, result.finalEquity, testDelta, "Final equity should remain initial equity if no trades.")
        assertEquals(0.0, result.totalReturn, testDelta, "Total return should be 0.0 if no trades.")
    }

    @Test
    fun testBacktesterRun_BuyAndHold_PositionLiquidatedAtEnd() {
        val initialEquity = 10000.0
        val backtester = Backtester(initialEquity)
        val klines = listOf(
            createKlineRowVec(1L, 100.0, 101.0, 99.0, 100.0, 1000.0), // BUY signal
            createKlineRowVec(2L, 101.0, 105.0, 100.0, 102.0, 1200.0), // HOLD signal
            createKlineRowVec(3L, 102.0, 108.0, 101.0, 105.0, 1100.0)  // HOLD signal; position liquidated at this kline's close price (105.0)
        )
        val mockKlineCursor = createMockKlineCursor(klines)
        val mockStrategy = MockTradingStrategy(listOf(TradingSignal.BUY, TradingSignal.HOLD, TradingSignal.HOLD))

        val result = backtester.run(mockKlineCursor, mockStrategy)

        assertEquals(1, result.trades.size, "Should be 1 BUY trade recorded.")
        assertEquals(TradingSignal.BUY, result.trades[0].signal, "Signal of the first trade should be BUY.")
        assertEquals(100.0, result.trades[0].price, testDelta, "Price of BUY trade incorrect.")

        // Financial outcome:
        // Initial: 10000.0 equity.
        // BUY 100 units at 100.0.
        // End of data: Position of 100 units is liquidated at the last kline's close price (105.0).
        // Final equity = 100 units * 105.0 = 10500.0.
        val expectedFinalEquity = 10500.0
        assertEquals(expectedFinalEquity, result.finalEquity, testDelta, "Final equity after EOD liquidation incorrect.")
        val expectedTotalReturn = (expectedFinalEquity - initialEquity) / initialEquity
        assertEquals(expectedTotalReturn, result.totalReturn, testDelta, "Total return after EOD liquidation incorrect.")
    }

     @Test
    fun testBacktesterRun_WithEmptyKlineData() {
        val initialEquity = 10000.0
        val backtester = Backtester(initialEquity)
        val mockKlineCursor = createMockKlineCursor(emptyList()) // Empty kline data
        // Strategy signals won't be called if there's no data to iterate.
        val mockStrategy = MockTradingStrategy(listOf(TradingSignal.BUY))

        val result = backtester.run(mockKlineCursor, mockStrategy)

        assertEquals(0, result.trades.size, "No trades should occur with empty kline data.")
        assertEquals(initialEquity, result.finalEquity, testDelta, "Final equity should be initial equity with empty data.")
        assertEquals(0.0, result.totalReturn, testDelta, "Total return should be 0.0 with empty data.")
    }
}
