package com.ta4k.indicators

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
import com.ta4k.trikeshedutils.toList   // Planned new location
import borg.trikeshed.core.size
import borg.trikeshed.core.Series // Should be imported if Series is explicitly typed
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.math.RoundingMode

class SMAIndicatorTest {

    private fun createDummyKline(closePrice: String) = Kline(
        openTimeMillis = 0L, openPrice = BigDecimal.ZERO, highPrice = BigDecimal.ZERO, lowPrice = BigDecimal.ZERO,
        closePrice = BigDecimal(closePrice), volume = BigDecimal.ZERO, closeTimeMillis = 0L,
        quoteAssetVolume = BigDecimal.ZERO, numberOfTrades = 0, takerBuyBaseAssetVolume = BigDecimal.ZERO,
        takerBuyQuoteAssetVolume = BigDecimal.ZERO
    )

    @Test
    fun `SMA calculation with period 3`() {
        val klines = listOf(
            createDummyKline("10"), createDummyKline("11"), createDummyKline("12"),
            createDummyKline("13"), createDummyKline("14")
        ).toSeries()
        val sma = SMAIndicator(klines, 3)

        assertNull(sma.getValue(0))
        assertNull(sma.getValue(1))
        // For (10+11+12)/3 = 11. Result scale depends on calculation logic.
        // The implementation uses input scale + 4. If input is "10" (scale 0), then 0+4 = 4.
        assertEquals(BigDecimal("11.0000"), sma.getValue(2)?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("12.0000"), sma.getValue(3)?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("13.0000"), sma.getValue(4)?.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `SMA with period larger than series size`() {
        val klines = listOf(createDummyKline("10"), createDummyKline("11")).toSeries()
        val sma = SMAIndicator(klines, 3)
        assertNull(sma.getValue(0))
        assertNull(sma.getValue(1))
        assertNull(sma.getValue(2)) // Also check index that would be first calc point if enough data
    }

    @Test
    fun `SMA on empty series`() {
        val sma = SMAIndicator(emptyList<Kline>().toSeries(), 3)
        assertNull(sma.getValue(0), "getValue(0) on empty series should be null")
        assertTrue(sma.values.toList().isEmpty(), "values list on empty series should be empty")
    }

    @Test
    fun `SMA with period 1`() {
        val klines = listOf(createDummyKline("10.123"), createDummyKline("11.456")).toSeries()
        val sma = SMAIndicator(klines, 1)
        // Scale of "10.123" is 3. Calc scale 3+4=7. Then setScale to 4 for assertion.
        assertEquals(BigDecimal("10.1230"), sma.getValue(0)?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("11.4560"), sma.getValue(1)?.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `SMA requesting out of bounds index`() {
        val klines = listOf(createDummyKline("10")).toSeries()
        val sma = SMAIndicator(klines, 1)
        assertNotNull(sma.getValue(0))
        assertNull(sma.getValue(1))
        assertNull(sma.getValue(-1))
    }

    @Test
    fun `SMA using open price`() {
         val klinesCustom = listOf(
            Kline(0L, BigDecimal("10.5"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("100"), BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO),
            Kline(0L, BigDecimal("11.5"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("100"), BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO),
            Kline(0L, BigDecimal("12.5"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("100"), BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO)
        ).toSeries()
        // Open price "10.5" has scale 1. Calculation scale 1+4=5.
        val sma = SMAIndicator(klinesCustom, 2) { it.openPrice }
        assertNull(sma.getValue(0))
        assertEquals(BigDecimal("11.0000"), sma.getValue(1)?.setScale(4, RoundingMode.HALF_UP)) // (10.5+11.5)/2 = 11
        assertEquals(BigDecimal("12.0000"), sma.getValue(2)?.setScale(4, RoundingMode.HALF_UP)) // (11.5+12.5)/2 = 12
    }

    @Test
    fun `SMA values property calculates all values`() {
        val klines = listOf(
            createDummyKline("10"), createDummyKline("11"), createDummyKline("12"),
            createDummyKline("13"), createDummyKline("14")
        ).toSeries()
        val sma = SMAIndicator(klines, 3)
        val allValues = sma.values.toList() // Convert Series to List
        assertEquals(klines.size, allValues.size) // klines.size works on Series
        assertNull(allValues[0])
        assertNull(allValues[1])
        assertEquals(BigDecimal("11.0000"), allValues[2]?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("12.0000"), allValues[3]?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("13.0000"), allValues[4]?.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `SMA getValue after accessing values property`() {
        val klines = listOf(createDummyKline("10"), createDummyKline("11"), createDummyKline("12")).toSeries()
        val sma = SMAIndicator(klines, 2)

        // Access values property first
        val allValues = sma.values.toList() // Convert Series to List
        assertEquals(3, allValues.size)
        assertNull(allValues[0])
        assertEquals(BigDecimal("10.5000"), allValues[1]?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("11.5000"), allValues[2]?.setScale(4, RoundingMode.HALF_UP))

        // Then use getValue, should use cached values
        assertEquals(BigDecimal("10.5000"), sma.getValue(1)?.setScale(4, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("11.5000"), sma.getValue(2)?.setScale(4, RoundingMode.HALF_UP))
        assertNull(sma.getValue(0))
    }

    @Test
    fun `SMA with klines having different scales`() {
        val klines = listOf(
            createDummyKline("10.1"), // scale 1
            createDummyKline("11.12"), // scale 2
            createDummyKline("12.123") // scale 3
        ).toSeries()
        val sma = SMAIndicator(klines, 2)

        // (10.1 + 11.12) / 2 = 21.22 / 2 = 10.61. Input scales are 1 and 2. Max is 2. Calc scale 2+4=6.
        // klineSeries[currentIndex] is klines[1] (11.12, scale 2)
        // klinePropertySelector(klineSeries[currentIndex]).scale() will be 2.
        // sum.divide(BigDecimal(period), 2 + 4, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("10.6100"), sma.getValue(1)?.setScale(4, RoundingMode.HALF_UP))

        // (11.12 + 12.123) / 2 = 23.243 / 2 = 11.6215. Input scales are 2 and 3. Max is 3. Calc scale 3+4=7.
        // klineSeries[currentIndex] is klines[2] (12.123, scale 3)
        // klinePropertySelector(klineSeries[currentIndex]).scale() will be 3.
        // sum.divide(BigDecimal(period), 3 + 4, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("11.6215"), sma.getValue(2)?.setScale(4, RoundingMode.HALF_UP))
    }
}
