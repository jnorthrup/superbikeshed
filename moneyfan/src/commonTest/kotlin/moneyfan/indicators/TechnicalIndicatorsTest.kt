package moneyfan.indicators

import moneyfan.models.Price
import moneyfan.trikeshed.Indexed // Placeholder import
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Placeholder Indexed implementation for testing
class TestIndexed<T>(private val items: List<T>) : Indexed<T> {
    override val a: Int = items.size
    override fun b(index: Int): T = items[index]
}

class TechnicalIndicatorsTest {

    private fun createPriceSeries(values: List<Double>): Indexed<Price> {
        return TestIndexed(values.map { Price(it) })
    }

    @Test
    fun testCalculateSMA() {
        val prices = createPriceSeries(listOf(10.0, 11.0, 12.0, 13.0, 14.0))

        // Test with period 3
        val sma3 = calculateSMA(prices, 3)
        assertEquals(5, sma3.a)
        assertTrue(sma3.b(0).isUndefined(), "SMA3 index 0 should be undefined")
        assertTrue(sma3.b(1).isUndefined(), "SMA3 index 1 should be undefined")
        assertEquals(11.0, sma3.b(2).value, 0.001, "SMA3 index 2") // (10+11+12)/3
        assertEquals(12.0, sma3.b(3).value, 0.001, "SMA3 index 3") // (11+12+13)/3
        assertEquals(13.0, sma3.b(4).value, 0.001, "SMA3 index 4") // (12+13+14)/3

        // Test with period longer than series
        val sma10 = calculateSMA(prices, 10)
        assertEquals(5, sma10.a)
        for (i in 0 until sma10.a) {
            assertTrue(sma10.b(i).isUndefined(), "SMA10 index $i should be undefined")
        }

        // Test with invalid period
        val sma0 = calculateSMA(prices, 0)
        assertEquals(5, sma0.a)
        for (i in 0 until sma0.a) {
            assertTrue(sma0.b(i).isUndefined(), "SMA0 index $i should be undefined")
        }

        // Test with undefined prices in window
        val pricesWithNaN = createPriceSeries(listOf(10.0, Double.NaN, 12.0, 13.0, 14.0))
        val sma3NaN = calculateSMA(pricesWithNaN, 3)
        assertTrue(sma3NaN.b(2).isUndefined(), "SMA3 index 2 with NaN in window should be undefined")
        assertEquals(13.0, sma3NaN.b(4).value, 0.001, "SMA3 index 4 should be calculable if prior NaN is out of window") // (12+13+14)/3 - assuming NaN behavior
    }

    @Test
    fun testCalculateRSI() {
        // Data from a known RSI example (e.g., Wikipedia or Investopedia)
        // Prices: 44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61, 46.28
        // For period 14, first RSI is at index 14 (or 13 if 0-indexed for changes)
        // This basic test will use a shorter period for simplicity
        val prices = createPriceSeries(listOf(
            10.0, 11.0, 10.5, 12.0, 11.5, 11.0, 10.0, 9.0, 9.5, 10.5
        )) // 10 data points

        // Test with period 3 (needs 3 changes, so 4 prices)
        // Changes: +1.0, -0.5, +1.5, -0.5, -0.5, -1.0, -1.0, +0.5, +1.0
        // RSI values will start from index 3 of the prices array
        val rsi3 = calculateRSI(prices, 3)
        assertEquals(10, rsi3.a)

        // First 3 RSI values (indices 0,1,2 for prices) should be NaN
        assertTrue(rsi3.b(0).isNaN(), "RSI3 index 0 should be NaN")
        assertTrue(rsi3.b(1).isNaN(), "RSI3 index 1 should be NaN")
        assertTrue(rsi3.b(2).isNaN(), "RSI3 index 2 should be NaN")

        // 1st RSI (index 3 for prices): Changes: +1.0, -0.5, +1.5
        // Gains: 1.0, 0, 1.5. Avg Gain = (1.0+0+1.5)/3 = 2.5/3 = 0.8333
        // Losses: 0, 0.5, 0. Avg Loss = (0+0.5+0)/3 = 0.5/3 = 0.1667
        // RS = 0.8333 / 0.1667 = 5.0
        // RSI = 100 - (100 / (1 + 5)) = 100 - (100/6) = 100 - 16.6667 = 83.3333
        assertEquals(83.333, rsi3.b(3), 0.001, "RSI3 index 3")

        // 2nd RSI (index 4 for prices): Prev AvgGain=0.8333, Prev AvgLoss=0.1667. Next change = -0.5
        // Current Gain = 0, Current Loss = 0.5
        // AvgGain = (0.8333*2 + 0)/3 = 0.5555
        // AvgLoss = (0.1667*2 + 0.5)/3 = 0.2778
        // RS = 0.5555 / 0.2778 = ~2.0
        // RSI = 100 - (100 / (1 + 2.0)) = 100 - 33.3333 = 66.6667
        assertEquals(66.667, rsi3.b(4), 0.001, "RSI3 index 4")

        // Test with insufficient data
        val shortPrices = createPriceSeries(listOf(10.0, 11.0))
        val rsiShort = calculateRSI(shortPrices, 3)
        assertEquals(2, rsiShort.a)
        assertTrue(rsiShort.b(0).isNaN())
        assertTrue(rsiShort.b(1).isNaN())

        // Test with prices that cause zero average loss (RSI should be 100)
        val allUpPrices = createPriceSeries(listOf(10.0, 11.0, 12.0, 13.0, 14.0))
        val rsiAllUp = calculateRSI(allUpPrices, 3)
        // First 3 NaN
        // Index 3: Gains: 1,1,1. AvgGain=1. Losses: 0,0,0. AvgLoss=0. RSI=100
        assertEquals(100.0, rsiAllUp.b(3), 0.001, "RSI3 index 3 for all up prices")
        assertEquals(100.0, rsiAllUp.b(4), 0.001, "RSI3 index 4 for all up prices")
    }
}
