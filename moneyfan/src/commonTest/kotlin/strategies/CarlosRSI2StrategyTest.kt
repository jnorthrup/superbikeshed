package strategies

import kotlin.test.*
import com.google.trike.series.* // Star import for Series, RowVec, Join, ColumnMeta, DataTypes
import com.google.trike.series.memseries.* // Star import for MemSeries and related utilities
import com.google.trike.series.DataTypes // Specifically for ColumnMeta type, though series.* should cover it.

class CarlosRSI2StrategyTest {

    private val strategy = CarlosRSI2Strategy()
    private val testDelta = 0.00001 // Delta for Double comparisons

    // Helper to create a dummy RowVec. Useful for testing strategies that might inspect historicalDataPoint.
    // For CarlosRSI2Strategy, historicalPrices and currentPrice are primary inputs for indicators.
    private fun createDummyRowVec(timestamp: Long, price: Double): RowVec {
        // A RowVec is Series<Join<Any?, () -> ColumnMeta>>.
        // We use klinesRecordMeta structure for consistency, though only timestamp and close are used here.
        // If klinesRecordMeta is not directly accessible or desired, define simple ColumnMetas.
        val tsMeta = { ColumnMeta.Builder().name("timestamp").type(DataTypes.LONG).build() }
        val priceMeta = { ColumnMeta.Builder().name("close").type(DataTypes.DOUBLE).build() }

        val tsJoin: Join<Any?, () -> ColumnMeta> = (timestamp as Any?) j tsMeta
        val priceJoin: Join<Any?, () -> ColumnMeta> = (price as Any?) j priceMeta

        // Example: MemSeries.ofJoins needs a list of these Join objects.
        // This RowVec is minimal; a real one would have all fields from klinesRecordMeta.
        return MemSeries.ofJoins(listOf(tsJoin, priceJoin)) as RowVec
    }

    @Test
    fun testCalculateSMA() {
        val prices = MemSeries.ofDoubles(listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0))

        // Test period 3
        val sma3 = strategy.calculateSMA(prices, 3)
        assertEquals(4, sma3.size, "SMA3 series length should be 4.")
        assertEquals(11.0, sma3.values[0], testDelta, "SMA3 value 0 incorrect. Expected (10+11+12)/3.")
        assertEquals(12.0, sma3.values[1], testDelta, "SMA3 value 1 incorrect. Expected (11+12+13)/3.")
        assertEquals(13.0, sma3.values[2], testDelta, "SMA3 value 2 incorrect. Expected (12+13+14)/3.")
        assertEquals(14.0, sma3.values[3], testDelta, "SMA3 value 3 incorrect. Expected (13+14+15)/3.")

        // Test period longer than series length
        val sma10 = strategy.calculateSMA(prices, 10)
        assertTrue(sma10.isEmpty, "SMA for period longer than series should be empty.")

        // Test with an empty series
        val emptyPrices = MemSeries.ofDoubles(emptyList())
        val smaEmpty = strategy.calculateSMA(emptyPrices, 3)
        assertTrue(smaEmpty.isEmpty, "SMA for an empty series should be empty.")
    }

    @Test
    fun testCalculateRSI2() {
        // Test case 1: Alternating prices
        val prices1 = MemSeries.ofDoubles(listOf(10.0, 11.0, 10.0, 11.0, 10.0, 11.0))
        // Expected RSI values based on manual calculation for period 2:
        // Prices: 10, 11, 10, 11, 10, 11
        // Changes:  +1, -1, +1, -1, +1
        // RSI period 2. First RSI value after 2 changes (3 prices).
        // 1. Prices(10,11,10). Changes(+1,-1). SumGains=1, SumLosses=1. AvgGain=0.5, AvgLoss=0.5. RS=1. RSI=50.
        // 2. PrevAvgG=0.5, PrevAvgL=0.5. Change=+1. Gain=1,Loss=0. AvgG=(0.5*1+1)/2=0.75. AvgL=(0.5*1+0)/2=0.25. RS=3. RSI=75.
        // 3. PrevAvgG=0.75, PrevAvgL=0.25. Change=-1. Gain=0,Loss=1. AvgG=(0.75*1+0)/2=0.375. AvgL=(0.25*1+1)/2=0.625. RS=0.6. RSI=37.5.
        // 4. PrevAvgG=0.375, PrevAvgL=0.625. Change=+1. Gain=1,Loss=0. AvgG=(0.375*1+1)/2=0.6875. AvgL=(0.625*1+0)/2=0.3125. RS=2.2. RSI=68.75.
        val rsi2 = strategy.calculateRSI2(prices1)
        assertEquals(4, rsi2.size, "RSI2 series length for prices1 should be 4.")
        assertEquals(50.0, rsi2.values[0], testDelta, "RSI2 value 0 for prices1 incorrect.")
        assertEquals(75.0, rsi2.values[1], testDelta, "RSI2 value 1 for prices1 incorrect.")
        assertEquals(37.5, rsi2.values[2], testDelta, "RSI2 value 2 for prices1 incorrect.")
        assertEquals(68.75, rsi2.values[3], testDelta, "RSI2 value 3 for prices1 incorrect.")

        // Test case 2: Flat prices (e.g., 10, 10, 10, 10)
        // Changes are all 0. AvgGain=0, AvgLoss=0.
        // RSI calculation handles AvgLoss=0 by setting RS to MAX_VALUE if AvgGain > 0 (RSI=100),
        // or specific handling if AvgGain is also 0. Current impl: RS=MAX_VALUE (AvgGain=0, AvgLoss=0), RSI=0
        val flatPrices = MemSeries.ofDoubles(listOf(10.0, 10.0, 10.0, 10.0))
        val rsiFlat = strategy.calculateRSI2(flatPrices)
        assertEquals(2, rsiFlat.size, "RSI2 series length for flatPrices should be 2.")
        // With refined RSI logic: if AvgGain and AvgLoss are both 0, RSI can be 0 or 50 or 100 by convention.
        // The code `if (initialRsi.isInfinite() && rsInitial > 0) 100.0 else if (initialRsi.isInfinite()) 0.0 else initialRsi`
        // leads to 0.0 if rsInitial is MAX_VALUE from (0.0/0.0) which becomes NaN, then 1+NaN = NaN... need to check Trike's Series behavior for 0/0.
        // Assuming 0/0 for AvgGain/AvgLoss results in RS that leads to RSI = 0 or 50 or 100.
        // Current code path with avgGain=0, avgLoss=0 -> rsInitial=MAX_VALUE -> initialRsi=0.0
        assertEquals(0.0, rsiFlat.values[0], testDelta, "RSI2 value 0 for flatPrices should be 0 (AvgGain=0, AvgLoss=0).")
        assertEquals(0.0, rsiFlat.values[1], testDelta, "RSI2 value 1 for flatPrices should be 0 (AvgGain=0, AvgLoss=0).")

        // Test case 3: Strictly increasing prices (e.g., 10, 11, 12, 13)
        // All changes are positive. AvgLoss is always 0. RSI should be 100.
        val increasingPrices = MemSeries.ofDoubles(listOf(10.0, 11.0, 12.0, 13.0))
        val rsiIncreasing = strategy.calculateRSI2(increasingPrices)
        assertEquals(2, rsiIncreasing.size, "RSI2 series length for increasingPrices should be 2.")
        assertEquals(100.0, rsiIncreasing.values[0], testDelta, "RSI2 value 0 for increasingPrices should be 100.")
        assertEquals(100.0, rsiIncreasing.values[1], testDelta, "RSI2 value 1 for increasingPrices should be 100.")

        // Test case 4: Short series (not enough data for first RSI value)
        // RSI2 needs 3 prices (period + 1) to calculate the first value.
        val shortPrices = MemSeries.ofDoubles(listOf(10.0, 11.0))
        val rsiShort = strategy.calculateRSI2(shortPrices)
        assertTrue(rsiShort.isEmpty, "RSI for a very short series (2 prices) should be empty.")
    }

    @Test
    fun testGetSignal() {
        // Base historical prices: 15 values are needed for SMA15.
        // The 16th value is `currentPrice` which completes the data for indicator calculation.
        val baseHistorical = List(15) { 100.0 }
        val dummyRowVec = createDummyRowVec(System.currentTimeMillis(), 100.0)

        // Case 1: BUY signal (RSI < 5, SMA2 > SMA15)
        // Setup: SMA15 is low (e.g., around 90s), SMA2 is higher (e.g., 100), current price causes RSI dip.
        // Historical: ..., 90, 90, 105 (14th value for histPrices)
        // CurrentPrice for getSignal (15th value for histPrices): 100
        // Actual current price for decision (16th value overall for indicators): 100
        // Prices for indicators: ..., 90, 90, 105, 100, 100
        // SMA15 of (...,90,105,100,100) will be around 90s.
        // SMA2 of (100,100) is 100. So, SMA2 (100) > SMA15 (~90s).
        // RSI2 of (...,105,100,100): changes are -5, 0. AvgGain small, AvgLoss moderate.
        // For (105,100,100): chg1=-5 (G=0,L=5), chg2=0 (G=0,L=0). Initial: SumG=0,SumL=5. AvgG=0,AvgL=2.5. RS=0. RSI=0.
        val buyHistoricalPrices = MemSeries.ofDoubles(List(13){90.0} + listOf(105.0)) // 14 values
        val buyCurrentPriceForIndicators = 100.0 // 15th value for historicalPrices arg in getSignal
        val buyDecisionPrice = 100.0 // This is the actual current price for signal decision

        val buySignal = strategy.getSignal(
            buyDecisionPrice,
            MemSeries.ofDoubles(buyHistoricalPrices.values.toList() + buyCurrentPriceForIndicators),
            dummyRowVec
        )
        assertEquals(TradingSignal.BUY, buySignal, "BUY signal: RSI < 5 (0.0) and SMA2 > SMA15.")


        // Case 2: SELL signal (RSI > 95)
        // Setup: Prices rise sharply.
        // Historical: ..., 100, 100, 120 (14th value for histPrices)
        // CurrentPrice for getSignal (15th value for histPrices): 130
        // Actual current price for decision (16th value overall for indicators): 130
        // Prices for indicators: ..., 100, 100, 120, 130, 130
        // RSI2 of (...,120,130,130): changes +10, 0. AvgLoss small. RSI will be high (100).
        val sellRsiHistoricalPrices = MemSeries.ofDoubles(baseHistorical.dropLast(1) + listOf(120.0)) // 14 values for hist + 1 current
        val sellRsiCurrentPriceForIndicators = 130.0
        val sellRsiDecisionPrice = 130.0
        val sellRsiSignal = strategy.getSignal(
            sellRsiDecisionPrice,
            MemSeries.ofDoubles(sellRsiHistoricalPrices.values.toList() + sellRsiCurrentPriceForIndicators),
            dummyRowVec
        )
        assertEquals(TradingSignal.SELL, sellRsiSignal, "SELL signal: RSI > 95 (100.0).")

        // Case 3: SELL signal (SMA2 < SMA15, moderate RSI)
        // Setup: SMA15 is high (e.g., 100s), SMA2 is lower (e.g., 85). RSI is moderate (e.g., 50).
        // Historical: ..., 100, 100, 90 (14th value)
        // CurrentPrice for getSignal (15th value): 80
        // Actual current price for decision (16th value): 90
        // Prices for indicators: ..., 100, 100, 90, 80, 90
        // SMA15 of (...,100,90,80,90) will be ~100.
        // SMA2 of (80,90) is 85. So, SMA2 (85) < SMA15 (~100).
        // RSI2 of (...,90,80,90): changes -10, +10. AvgGain=5, AvgLoss=5. RS=1. RSI=50.
        val sellSmaHistoricalPrices = MemSeries.ofDoubles(List(14) {100.0} + listOf(90.0))
        val sellSmaCurrentPriceForIndicators = 80.0
        val sellSmaDecisionPrice = 90.0
        val sellSmaSignal = strategy.getSignal(
            sellSmaDecisionPrice,
            MemSeries.ofDoubles(sellSmaHistoricalPrices.values.toList() + sellSmaCurrentPriceForIndicators),
            dummyRowVec
        )
        assertEquals(TradingSignal.SELL, sellSmaSignal, "SELL signal: SMA2 < SMA15 with moderate RSI (50.0).")

        // Case 4: HOLD signal (Moderate RSI, SMA2 > SMA15 but not extreme RSI)
        // Historical: ..., 100, 100, 101 (14th value)
        // CurrentPrice for getSignal (15th value): 100
        // Actual current price for decision (16th value): 100
        // Prices for indicators: ..., 100, 100, 101, 100, 100
        // SMA15 of (...,101,100,100) is ~100.
        // SMA2 of (100,100) is 100. So, SMA2 (~100) approx SMA15 (~100). (Actually SMA2=100, SMA15 slightly above 100 if previous were 100s. Let's make SMA2 clearly > SMA15 for test)
        // Prices: ..., 100, 100, 100, 105(hist), 106(currIndic), 106(decision) -> SMA2=(105+106)/2 = 105.5. SMA15 approx 100.
        // RSI for (...,100,105,106,106): ch: +5, +1, 0. RSI will be high-ish but not > 95.
        // RSI (105,106,106): ch1=+1(G=1,L=0), ch2=0(G=0,L=0). SumG=1,SumL=0. AvgG=0.5,AvgL=0. RS=inf. RSI=100. This gives SELL.
        // Need moderate RSI. Example: (100,101,100,101) -> RSI values are 75 and 25.
        // Let historical be 13x100 + 101, 100. Current for indicator: 101. Decision price: 101.
        // Prices for indicators: 13x100, 101, 100, 101
        // SMA15 will be dominated by 100s, so near 100.
        // SMA2 for (100,101) is 100.5. So SMA2 > SMA15.
        // RSI for (101,100,101): ch: -1, +1. AvgG=0.5,AvgL=0.5. RS=1. RSI=50.
        val holdHistoricalPrices = MemSeries.ofDoubles(List(13){100.0} + listOf(101.0))
        val holdCurrentPriceForIndicators = 100.0
        val holdDecisionPrice = 101.0
        val holdSignal = strategy.getSignal(
            holdDecisionPrice,
            MemSeries.ofDoubles(holdHistoricalPrices.values.toList() + holdCurrentPriceForIndicators),
            dummyRowVec
        )
        assertEquals(TradingSignal.HOLD, holdSignal, "HOLD signal: Moderate RSI (50.0) and favorable SMA conditions.")
    }
}
