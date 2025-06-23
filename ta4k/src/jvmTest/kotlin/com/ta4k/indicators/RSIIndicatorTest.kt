package com.ta4k.indicators

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
import com.ta4k.trikeshedutils.toList   // Planned new location
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class RSIIndicatorTest {

    private fun kline(close: String) = Kline(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal(close), BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO)

    @Test
    fun `RSI calculation example known source - simple case`() {
        // Test case from https://school.stockcharts.com/doku.php?id=technical_indicators:relative_strength_index_rsi
        // Simplified to match implementation's first SMA then Wilder's
        // Prices: 10, 12, 11, 13, 12, 14, 13, 15. Period 3
        // Index 0: 10
        // Index 1: 12 (Gain: 2, Loss: 0)
        // Index 2: 11 (Gain: 0, Loss: 1)
        // Index 3: 13 (Gain: 2, Loss: 0) -- End of first period for RSI(3). RSI calculated here.
        // Raw Gains for period: (P1-P0)=2, (P2-P1)=0, (P3-P2)=2. Note: these are for prices at index 1,2,3.
        // Actual gains stored at avgGains[1], avgGains[2], avgGains[3]
        // avgGains[1]=2, avgLosses[1]=0
        // avgGains[2]=0, avgLosses[2]=1
        // avgGains[3]=2, avgLosses[3]=0
        // First Smoothed AvgGain = (2+0+2)/3 = 4/3 = 1.33333333
        // First Smoothed AvgLoss = (0+1+0)/3 = 1/3 = 0.33333333
        // RS = 1.33333333 / 0.33333333 = 4.0
        // RSI = 100 - (100 / (1 + 4)) = 100 - 20 = 80.00

        val simplePrices = listOf("10", "12", "11", "13", "12", "14", "13", "15").map { kline(it) }.toIndexed()
        val rsi3 = RSIIndicator(simplePrices, 3)

        assertNull(rsi3.getValue(0))
        assertNull(rsi3.getValue(1))
        assertNull(rsi3.getValue(2)) // RSI starts at index 'period' which is 3 for a 3-period RSI
        assertEquals(BigDecimal("80.00"), rsi3.getValue(3))

        // Index 4: Price 12 (Change from P3: 13 -> 12 is -1. Gain:0, Loss:1)
        // PrevSmoothedAvgGain = 1.33333333 (from avgGains[3])
        // PrevSmoothedAvgLoss = 0.33333333 (from avgLosses[3])
        // Current (raw) Gain = 0, Current (raw) Loss = 1
        // SmoothedAvgGain_i4 = (PrevSmoothedAvgGain * (period-1) + CurrentGain) / period
        //                    = (1.33333333 * 2 + 0) / 3 = 2.66666666 / 3 = 0.88888888
        // SmoothedAvgLoss_i4 = (PrevSmoothedAvgLoss * (period-1) + CurrentLoss) / period
        //                    = (0.33333333 * 2 + 1) / 3 = (0.66666666 + 1) / 3 = 1.66666666 / 3 = 0.55555555
        // RS_i4 = 0.88888888 / 0.55555555 = 1.60000000... (approx 1.6)
        // RSI_i4 = 100 - (100 / (1 + 1.6)) = 100 - (100 / 2.6) = 100 - 38.461538... = 61.538...
        // Expected: 61.54
        assertEquals(BigDecimal("61.54"), rsi3.getValue(4))

        // Index 5: Price 14 (Change from P4: 12 -> 14 is +2. Gain:2, Loss:0)
        // PrevSmoothedAvgGain = 0.88888888 (from avgGains[4])
        // PrevSmoothedAvgLoss = 0.55555555 (from avgLosses[4])
        // Current (raw) Gain = 2, Current (raw) Loss = 0
        // SmoothedAvgGain_i5 = (0.88888888 * 2 + 2) / 3 = (1.77777776 + 2) / 3 = 3.77777776 / 3 = 1.25925925
        // SmoothedAvgLoss_i5 = (0.55555555 * 2 + 0) / 3 = 1.11111110 / 3 = 0.37037036
        // RS_i5 = 1.25925925 / 0.37037036 = 3.4000... (approx 3.4)
        // RSI_i5 = 100 - (100 / (1 + 3.4)) = 100 - (100 / 4.4) = 100 - 22.7272... = 77.2727...
        // Expected: 77.27
        assertEquals(BigDecimal("77.27"), rsi3.getValue(5))
    }

    @Test
    fun `RSI all upward movement`() {
        val allUp = listOf(10,11,12,13,14,15,16,17,18,19,20,21,22,23).map { kline(it.toString())}.toIndexed()
        val rsiAllUp = RSIIndicator(allUp, 13) // 13 price changes for 14 prices (index 0 to 13)
                                               // RSI calculable at index 13
        // All gains, no losses. AvgLoss should be 0. RSI should be 100.
        assertNull(rsiAllUp.getValue(12))
        assertEquals(BigDecimal("100.00"), rsiAllUp.getValue(13))
    }

    @Test
    fun `RSI all downward movement`() {
        val allDown = listOf(23,22,21,20,19,18,17,16,15,14,13,12,11,10).map { kline(it.toString())}.toIndexed()
        val rsiAllDown = RSIIndicator(allDown, 13)
        // All losses, no gains. AvgGain should be 0. RSI should be 0.
        assertNull(rsiAllDown.getValue(12))
        assertEquals(BigDecimal("0.00"), rsiAllDown.getValue(13))
    }

    @Test
    fun `RSI period 1`() {
        val prices = listOf(kline("10"), kline("11"), kline("10"), kline("10")).toIndexed()
        val rsi = RSIIndicator(prices, 1)
        // Index 0: null
        // Index 1: P1=11, P0=10. Gain=1, Loss=0. AvgGain=1, AvgLoss=0. RSI=100.
        // Index 2: P2=10, P1=11. Gain=0, Loss=1. PrevAvgGain=1, PrevAvgLoss=0.
        //          SmoothedAvgGain = (1*0 + 0)/1 = 0. SmoothedAvgLoss = (0*0 + 1)/1 = 1. RSI=0.
        // Index 3: P3=10, P2=10. Gain=0, Loss=0. PrevAvgGain=0, PrevAvgLoss=1.
        //          SmoothedAvgGain = (0*0 + 0)/1 = 0. SmoothedAvgLoss = (1*0 + 0)/1 = 0. RSI=100.

        assertNull(rsi.getValue(0))
        assertEquals(BigDecimal("100.00"), rsi.getValue(1))
        assertEquals(BigDecimal("0.00"), rsi.getValue(2))
        assertEquals(BigDecimal("100.00"), rsi.getValue(3))
    }

    @Test
    fun `RSI on empty series`() {
        val rsi = RSIIndicator(emptyList<Kline>().toIndexed(), 14)
        assertNull(rsi.getValue(0))
        assertTrue(rsi.values.toList().isEmpty())
    }

    @Test
    fun `RSI values property`() {
        val simplePrices = listOf("10", "12", "11", "13", "12").map { kline(it) }.toIndexed() // size 5
        val rsi3 = RSIIndicator(simplePrices, 3) // period 3
        val allValues = rsi3.values.toList() // Should calculate up to index 4

        assertEquals(5, allValues.size)
        assertNull(allValues[0])
        assertNull(allValues[1])
        assertNull(allValues[2]) // RSI starts at index 'period' = 3
        assertEquals(BigDecimal("80.00"), allValues[3])
        assertEquals(BigDecimal("61.54"), allValues[4])
    }

    @Test
    fun `RSI with insufficient data for period`() {
        val prices = listOf(kline("10"), kline("11")).toIndexed() // 2 data points
        val rsi = RSIIndicator(prices, 3) // period 3
        assertNull(rsi.getValue(0))
        assertNull(rsi.getValue(1))
        // Should also be null for index 2 as it's where RSI would be calculated if data existed
        assertNull(rsi.getValue(2))
    }
}
