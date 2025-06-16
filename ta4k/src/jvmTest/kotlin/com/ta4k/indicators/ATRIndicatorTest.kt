package com.ta4k.indicators

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
import com.ta4k.trikeshedutils.toList   // Planned new location
import borg.trikeshed.lib.size
import borg.trikeshed.lib.Series // Should be imported if Series is explicitly typed
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.math.RoundingMode

class ATRIndicatorTest {

    // Helper to create Kline with H, L, C. Other fields are dummy.
    private fun kline(high: String, low: String, close: String) = Kline(
        openTimeMillis = 0L, openPrice = BigDecimal.ZERO, highPrice = BigDecimal(high),
        lowPrice = BigDecimal(low), closePrice = BigDecimal(close), volume = BigDecimal.ZERO,
        closeTimeMillis = 0L, quoteAssetVolume = BigDecimal.ZERO, numberOfTrades = 0,
        takerBuyBaseAssetVolume = BigDecimal.ZERO, takerBuyQuoteAssetVolume = BigDecimal.ZERO
    )

    @Test
    fun `ATR calculation example from StockCharts`() {
        // Data from StockCharts ATR example (14-period)
        // Adjusted to use TRs calculated by the indicator's logic for consistency.
        val klines = listOf(
            kline("23.50","23.20","23.38"), // TR[0] = 0.30
            kline("23.41","23.01","23.01"), // TR[1] = Max(0.40, abs(23.41-23.38)=0.03, abs(23.01-23.38)=0.37) -> 0.40
            kline("23.20","22.90","23.15"), // TR[2] = Max(0.30, abs(23.20-23.01)=0.19, abs(22.90-23.01)=0.11) -> 0.30
            kline("23.30","23.00","23.07"), // TR[3] = Max(0.30, abs(23.30-23.15)=0.15, abs(23.00-23.15)=0.15) -> 0.30
            kline("23.25","23.03","23.10"), // TR[4] = Max(0.22, abs(23.25-23.07)=0.18, abs(23.03-23.07)=0.04) -> 0.22
            kline("23.60","23.25","23.48"), // TR[5] = Max(0.35, abs(23.60-23.10)=0.50, abs(23.25-23.10)=0.15) -> 0.50
            kline("23.70","23.30","23.30"), // TR[6] = Max(0.40, abs(23.70-23.48)=0.22, abs(23.30-23.48)=0.18) -> 0.40
            kline("23.40","22.80","22.80"), // TR[7] = Max(0.60, abs(23.40-23.30)=0.10, abs(22.80-23.30)=0.50) -> 0.60
            kline("23.30","22.95","23.10"), // TR[8] = Max(0.35, abs(23.30-22.80)=0.50, abs(22.95-22.80)=0.15) -> 0.50
            kline("23.35","23.01","23.01"), // TR[9] = Max(0.34, abs(23.35-23.10)=0.25, abs(23.01-23.10)=0.09) -> 0.34
            kline("23.10","22.80","23.00"), // TR[10]= Max(0.30, abs(23.10-23.01)=0.09, abs(22.80-23.01)=0.21) -> 0.30
            kline("23.45","23.10","23.20"), // TR[11]= Max(0.35, abs(23.45-23.00)=0.45, abs(23.10-23.00)=0.10) -> 0.45
            kline("23.80","23.40","23.70"), // TR[12]= Max(0.40, abs(23.80-23.20)=0.60, abs(23.40-23.20)=0.20) -> 0.60
            kline("23.71","23.07","23.60")  // TR[13]= Max(0.64, abs(23.71-23.70)=0.01, abs(23.07-23.70)=0.63) -> 0.64
        ).toSeries()

        val expectedTrValues = listOf(
            "0.30", "0.40", "0.30", "0.30", "0.22", "0.50", "0.40", "0.60",
            "0.50", "0.34", "0.30", "0.45", "0.60", "0.64"
        ).map { BigDecimal(it) }

        val period = 14
        val atr14 = ATRIndicator(klines, period)
        val resultScale = 4 // Matching ATRIndicator's resultScale for assertion

        // Test True Range values (scaled)
        for (i in 0 until klines.size) { // Use klines.size for Series
            assertEquals(expectedTrValues[i].setScale(resultScale, RoundingMode.HALF_UP), atr14.getTrueRange(i))
        }

        // First ATR (SMA of 14 TRs) - ATR is available at index period-1 (i.e., 13)
        var sumOfFirstPeriodTRs = BigDecimal.ZERO
        for(i in 0 until period) {
            sumOfFirstPeriodTRs += expectedTrValues[i]
        }
        val expectedFirstAtr = sumOfFirstPeriodTRs.divide(BigDecimal(period), resultScale, RoundingMode.HALF_UP)
        assertEquals(expectedFirstAtr, atr14.getValue(13))


        // Next ATR value (Day 15)
        val klinesListDay15 = klines.toList() + listOf(kline("23.57","23.25","23.25")) // Prev Close = 23.60 (from klines[13])
        val klinesDay15 = klinesListDay15.toSeries()
        // TR for day 15: H-L = 23.57-23.25 = 0.32
        // H-PC = abs(23.57-23.60) = 0.03
        // L-PC = abs(23.25-23.60) = 0.35. Max TR = 0.35
        val currentTRDay15 = BigDecimal("0.35")

        val atr14Day15 = ATRIndicator(klinesDay15, period) // Re-instantiate for the new data point

        val prevAtr = atr14Day15.getValue(13)!! // This is expectedFirstAtr
        val expectedNextAtr = (prevAtr.multiply(BigDecimal(period - 1)).add(currentTRDay15))
                                .divide(BigDecimal(period), resultScale, RoundingMode.HALF_UP)
        assertEquals(expectedNextAtr, atr14Day15.getValue(14))
    }

    @Test
    fun `ATR on empty series`() {
        val atr = ATRIndicator(emptyList<Kline>().toSeries(), 14)
        assertNull(atr.getValue(0))
        assertTrue(atr.values.toList().isEmpty())
    }

    @Test
    fun `ATR with period 1`() {
        // ATR(1) is just the True Range for that day
        val klines = listOf(kline("10","9","9.5"), kline("11","9.5","10.5")).toSeries()
        val atr = ATRIndicator(klines, 1)
        val resultScale = 4

        // TR[0] = 10-9 = 1. ATR[0] (SMA of TR[0]) = 1/1 = 1
        assertEquals(BigDecimal("1.0000").setScale(resultScale), atr.getValue(0))

        // TR[1] = Max(11-9.5=1.5, abs(11-9.5)=1.5, abs(9.5-9.5)=0) = 1.5
        // ATR[1] = (ATR[0]*(1-1) + TR[1])/1 = TR[1] = 1.5
        assertEquals(BigDecimal("1.5000").setScale(resultScale), atr.getValue(1))
    }

    @Test
    fun `ATR values property`() {
        val klines = listOf(
            kline("10","9","9.5"),      // TR0 = 1.0
            kline("11","9.5","10.5"),   // TR1 = 1.5 (H-L=1.5, H-PC=1.5, L-PC=0)
            kline("12","10","11.5")     // TR2 = 2.0 (H-L=2.0, H-PC=1.5, L-PC=0.5)
        ).toSeries()
        val period = 2
        val atr = ATRIndicator(klines, period)
        val resultScale = 4

        // ATR[0] = null (needs 'period' TRs for first SMA)
        // ATR[1] = (TR0+TR1)/2 = (1.0+1.5)/2 = 1.25
        // ATR[2] = (ATR[1]*(period-1) + TR2)/period = (1.25*1 + 2.0)/2 = 1.625

        val allValues = atr.values.toList()
        assertEquals(3, allValues.size)
        assertNull(allValues[0]) // ATR not available at index 0 for period 2
        assertEquals(BigDecimal("1.2500").setScale(resultScale), allValues[1])
        assertEquals(BigDecimal("1.6250").setScale(resultScale), allValues[2])
    }

    @Test
    fun `ATR with insufficient data for period`() {
        val klines = listOf(kline("10","9","9.5")).toSeries() // 1 data point
        val atr = ATRIndicator(klines, 3) // period 3
        assertNull(atr.getValue(0)) // ATR[0] needs 3 TRs, TR[0], TR[1], TR[2]
        assertNull(atr.getValue(1))
        assertNull(atr.getValue(2))
    }
}
