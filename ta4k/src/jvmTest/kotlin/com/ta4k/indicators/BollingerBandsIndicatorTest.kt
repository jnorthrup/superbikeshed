package com.ta4k.indicators

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt // Needed for manual std dev calculation in test

class BollingerBandsIndicatorTest {

    private fun kline(close: String, scale: Int = 2) = Kline( // Allow specifying scale for price
        openTimeMillis = 0L, openPrice = BigDecimal.ZERO, highPrice = BigDecimal.ZERO, lowPrice = BigDecimal.ZERO,
        closePrice = BigDecimal(close).setScale(scale, RoundingMode.UNNECESSARY), // Ensure consistent scale for test inputs
        volume = BigDecimal.ZERO, closeTimeMillis = 0L,
        quoteAssetVolume = BigDecimal.ZERO, numberOfTrades = 0, takerBuyBaseAssetVolume = BigDecimal.ZERO,
        takerBuyQuoteAssetVolume = BigDecimal.ZERO
    )

    private val defaultResultScale = 2 // Default scale for assertions if not derived from kline data

    @Test
    fun `Bollinger Bands calculation with period 3 and multiplier 2`() {
        val klines = listOf(
            kline("10.00"), kline("11.00"), kline("12.00"),
            kline("11.00"), kline("10.00"), kline("9.00")
        ).toIndexed()
        // resultScale in BBIndicator will be 2 based on first kline's closePrice scale
        val bb = BollingerBandsIndicator(klines, 3, BigDecimal("2.0"))

        assertNull(bb.getMiddleBandValue(0))
        assertNull(bb.getUpperBand(0))
        assertNull(bb.getLowerBand(0))
        assertNull(bb.getMiddleBandValue(1))

        // Index 2: Prices for SMA: 10.00, 11.00, 12.00. SMA = 11.00
        // Deviations: -1.00, 0.00, 1.00. Squared deviations: 1.0000, 0.0000, 1.0000. Sum = 2.0000.
        // Variance = 2.0000 / 3 = 0.66666667 (approx, using calcScale 8)
        // StdDev = sqrt(0.66666667) approx 0.81649658
        // BandOffset = 2 * 0.81649658 = 1.63299316
        // Upper = 11.00 + 1.63299316 = 12.63299316
        // Lower = 11.00 - 1.63299316 = 9.36700684
        assertEquals(BigDecimal("11.00"), bb.getMiddleBandValue(2)) // resultScale is 2
        assertEquals(BigDecimal("0.81649658"), bb.getStandardDeviation(2)) // Uses calculationScale 8
        assertEquals(BigDecimal("12.63"), bb.getUpperBand(2)) // Scaled to 2
        assertEquals(BigDecimal("9.37"), bb.getLowerBand(2))   // Scaled to 2

        // Index 3: Prices for SMA: 11.00, 12.00, 11.00. SMA = 11.33333333 (calc) -> 11.33 (resultScale 2)
        // Values: 11,12,11. SMA = 11.33333333
        // Deviations from SMA: -0.33333333, 0.66666667, -0.33333333
        // Sq Devs: 0.11111111, 0.44444444, 0.11111111 (approx) Sum = 0.66666666
        // Var = 0.66666666 / 3 = 0.22222222
        // StdDev = sqrt(0.22222222) approx 0.47140452
        // BandOffset = 2 * 0.47140452 = 0.94280904
        // Upper = 11.33333333 + 0.94280904 = 12.27614237
        // Lower = 11.33333333 - 0.94280904 = 10.39052429
        assertEquals(BigDecimal("11.33"), bb.getMiddleBandValue(3))
        assertEquals(BigDecimal("0.47140452"), bb.getStandardDeviation(3))
        assertEquals(BigDecimal("12.28"), bb.getUpperBand(3))
        assertEquals(BigDecimal("10.39"), bb.getLowerBand(3))
    }

    @Test
    fun `Bollinger Bands on empty series`() {
        val bb = BollingerBandsIndicator(emptyList<Kline>().toIndexed(), 20)
        assertNull(bb.getMiddleBandValue(0))
        assertNull(bb.getUpperBand(0))
        assertNull(bb.getLowerBand(0))
    }

    @Test
    fun `Bollinger Bands with period 1`() {
        // StdDev for period 1 is 0. So Upper and Lower bands are equal to SMA(1) = price.
        val klines = listOf(kline("10.00"), kline("11.00")).toIndexed()
        val bb = BollingerBandsIndicator(klines, 1) // resultScale will be 2

        assertEquals(BigDecimal("10.00"), bb.getMiddleBandValue(0))
        assertEquals(BigDecimal("10.00"), bb.getUpperBand(0))
        assertEquals(BigDecimal("10.00"), bb.getLowerBand(0))
        assertEquals(BigDecimal("0.00000000"), bb.getStandardDeviation(0)) // calcScale 8

        assertEquals(BigDecimal("11.00"), bb.getMiddleBandValue(1))
        assertEquals(BigDecimal("11.00"), bb.getUpperBand(1))
        assertEquals(BigDecimal("11.00"), bb.getLowerBand(1))
        assertEquals(BigDecimal("0.00000000"), bb.getStandardDeviation(1))
    }

    @Test
    fun `Bollinger Bands with constant price data`() {
        val klines = List(5) { kline("20.00") }.toIndexed() // resultScale will be 2
        val bb = BollingerBandsIndicator(klines, 3)

        // For index 2, 3, 4: SMA = 20.00, StdDev = 0.00
        for (i in 2..4) {
            assertEquals(BigDecimal("20.00"), bb.getMiddleBandValue(i))
            assertEquals(BigDecimal("0.00000000"), bb.getStandardDeviation(i)) // calcScale 8
            assertEquals(BigDecimal("20.00"), bb.getUpperBand(i))
            assertEquals(BigDecimal("20.00"), bb.getLowerBand(i))
        }
    }

    @Test
    fun `Bollinger Bands with different price scale`() {
        val klines = listOf(
            kline("10.1234", 4), kline("11.4321", 4), kline("12.5678", 4)
        ).toIndexed()
        // resultScale in BBIndicator will be 4
        val bb = BollingerBandsIndicator(klines, 3, BigDecimal("2.0"))

        // Index 2: Prices: 10.1234, 11.4321, 12.5678
        // SMA = (10.1234 + 11.4321 + 12.5678) / 3 = 34.1233 / 3 = 11.37443333
        // Result for middle band will be scaled to 4: 11.3744
        val sma2 = BigDecimal("11.37443333") // More precise for internal check
        val dev1 = BigDecimal("10.1234").subtract(sma2) // -1.25103333
        val dev2 = BigDecimal("11.4321").subtract(sma2) //  0.05766667
        val dev3 = BigDecimal("12.5678").subtract(sma2) //  1.19336667
        val sumSq = dev1.pow(2).add(dev2.pow(2)).add(dev3.pow(2)) // 1.56508889 + 0.00332544 + 1.42413444 = 2.99254877
        val variance = sumSq.divide(BigDecimal(3), 8, RoundingMode.HALF_UP) // 0.99751626
        val stdDev = BigDecimal(sqrt(variance.toDouble())).setScale(8, RoundingMode.HALF_UP) // sqrt(0.99751626) = 0.99875736

        assertEquals(BigDecimal("11.3744"), bb.getMiddleBandValue(2)) // Scaled to 4
        assertEquals(stdDev, bb.getStandardDeviation(2)) // Scaled to 8 (calcScale)

        val bandOffset = stdDev.multiply(BigDecimal("2.0")) // 1.99751472
        val expectedUpper = sma2.add(bandOffset).setScale(4, RoundingMode.HALF_UP) // 11.37443333 + 1.99751472 = 13.37194805 -> 13.3719
        val expectedLower = sma2.subtract(bandOffset).setScale(4, RoundingMode.HALF_UP) // 11.37443333 - 1.99751472 = 9.37691861 -> 9.3769

        assertEquals(expectedUpper, bb.getUpperBand(2))
        assertEquals(expectedLower, bb.getLowerBand(2))
    }
}
