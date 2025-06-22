package com.ta4k.indicators

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
// import com.ta4k.trikeshedutils.toList // ADX uses getters, not a single .values property
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class ADXIndicatorTest {

    // Helper to create Kline, includes open price
    private fun kline(high: String, low: String, close: String, open: String = close, idx: Long = 0L) = Kline(
        openTimeMillis = idx * 1000, // Ensure unique time for each kline if it matters
        openPrice = BigDecimal(open),
        highPrice = BigDecimal(high),
        lowPrice = BigDecimal(low),
        closePrice = BigDecimal(close),
        volume = BigDecimal.ZERO,
        closeTimeMillis = idx * 1000 + 999,
        quoteAssetVolume = BigDecimal.ZERO,
        numberOfTrades = 0,
        takerBuyBaseAssetVolume = BigDecimal.ZERO,
        takerBuyQuoteAssetVolume = BigDecimal.ZERO
    )

    @Test
    fun `ADX period 3 calculation - step by step verification`() {
        val klines = listOf(
            // H,   L,   C,   O      | Idx | TR      | +DM     | -DM
            kline("10","9","9.5","9.5", 0), //0 | 1.0     | 0       | 0       (TR0=H-L, DM0=0)
            kline("11","9.5","10.5","9.5",1),//1 | 1.5     | 1.0     | 0       (H-pH=1, pL-L=0. H-pC=1.5, L-pC=0)
            kline("10.5","9","9.5","10.5",2),//2 | 1.5     | 0       | 0.5     (H-pH=-0.5, pL-L=0.5. H-pC=0, L-pC=1.5)
            kline("12","10","11.5","9.5",3), //3 | 2.0     | 1.5     | 0       (H-pH=1.5, pL-L=-1. H-pC=2.5, L-pC=0.5)
            kline("11","9.5","10","11.5",4), //4 | 1.5     | 0       | 0.5     (H-pH=-1, pL-L=0.5. H-pC=0, L-pC=1.5)
            kline("10","8","8.5","10",5),   //5 | 2.0     | 0       | 1.5     (H-pH=-1, pL-L=1.5. H-pC=1.5, L-pC=0)
            kline("11","9","10.5","8.5",6)  //6 | 2.5     | 1.0     | 0       (H-pH=1, pL-L=-1. H-pC=2.5, L-pC=0.5)
        ).toSeries()
        val period = 3
        val adxIndicator = ADXIndicator(klines, period)
        val resultScale = 2 // ADXIndicator default for final values
        val calcScale = 8   // ADXIndicator internal calculation scale for more precision in intermediate checks

        // Expected values based on Wilder's smoothing:
        // Sum first 'period' values for DM+, DM-, TR (from index 1 to period)
        // Smoothed values for DI start at index 'period' = 3

        // At index 3 (end of first full period for DI calculation):
        // Sum +DM[1..3] = 1.0 + 0 + 1.5 = 2.5
        // Sum -DM[1..3] = 0 + 0.5 + 0 = 0.5
        // Sum TR[1..3]  = 1.5 + 1.5 + 2.0 = 5.0
        // +DI[3] = (2.5 / 5.0) * 100 = 50.00
        // -DI[3] = (0.5 / 5.0) * 100 = 10.00
        // DX[3] = |50-10| / (50+10) * 100 = 40 / 60 * 100 = 66.66666667
        assertEquals(BigDecimal("50.00"), adxIndicator.getPlusDI(3))
        assertEquals(BigDecimal("10.00"), adxIndicator.getMinusDI(3))
        assertEquals(BigDecimal("66.67"), adxIndicator.getDX(3)) // Rounded to resultScale

        // At index 4:
        // Prev_S+DM = 2.5, Prev_S-DM = 0.5, Prev_STR = 5.0
        // Raw +DM[4]=0, Raw -DM[4]=0.5, Raw TR[4]=1.5
        // S+DM[4] = 2.5 - (2.5/3) + 0   = 2.5 - 0.83333333 = 1.66666667
        // S-DM[4] = 0.5 - (0.5/3) + 0.5 = 0.5 - 0.16666667 + 0.5 = 0.83333333
        // STR[4]  = 5.0 - (5.0/3) + 1.5 = 5.0 - 1.66666667 + 1.5 = 4.83333333
        // +DI[4] = (1.66666667 / 4.83333333) * 100 = 34.48275862 => 34.48
        // -DI[4] = (0.83333333 / 4.83333333) * 100 = 17.24137931 => 17.24
        // DX[4] = |34.48275862 - 17.24137931| / (34.48275862 + 17.24137931) * 100
        //       = 17.24137931 / 51.72413793 * 100 = 33.33333333 => 33.33
        assertEquals(BigDecimal("34.48"), adxIndicator.getPlusDI(4))
        assertEquals(BigDecimal("17.24"), adxIndicator.getMinusDI(4))
        assertEquals(BigDecimal("33.33"), adxIndicator.getDX(4))

        // At index 5 (period + period -1 = 3 + 3 - 1 = 5): First ADX value
        // Prev_S+DM = 1.66666667, Prev_S-DM = 0.83333333, Prev_STR = 4.83333333
        // Raw +DM[5]=0, Raw -DM[5]=1.5, Raw TR[5]=2.0
        // S+DM[5] = 1.66666667 - (1.66666667/3) + 0   = 1.66666667 - 0.55555556 = 1.11111111
        // S-DM[5] = 0.83333333 - (0.83333333/3) + 1.5 = 0.83333333 - 0.27777778 + 1.5 = 2.05555555
        // STR[5]  = 4.83333333 - (4.83333333/3) + 2.0 = 4.83333333 - 1.61111111 + 2.0 = 5.22222222
        // +DI[5] = (1.11111111 / 5.22222222) * 100 = 21.27659574 => 21.28
        // -DI[5] = (2.05555555 / 5.22222222) * 100 = 39.35828875 => 39.36
        // DX[5] = |21.27659574 - 39.35828875| / (21.27659574 + 39.35828875) * 100
        //       = 18.08169301 / 60.63488449 * 100 = 29.82057016 => 29.82
        assertEquals(BigDecimal("21.28"), adxIndicator.getPlusDI(5))
        assertEquals(BigDecimal("39.36"), adxIndicator.getMinusDI(5))
        assertEquals(BigDecimal("29.82"), adxIndicator.getDX(5))

        // ADX[5] is the SMA of DX[3], DX[4], DX[5]
        // ADX[5] = (66.66666667 + 33.33333333 + 29.82057016) / 3
        //        = 129.82057016 / 3 = 43.27352338 => 43.27
        assertEquals(BigDecimal("43.27"), adxIndicator.getADX(5))

        // At index 6:
        // Prev_ADX = 43.27352338 (internal precision)
        // Prev_S+DM=1.11111111, Prev_S-DM=2.05555555, Prev_STR=5.22222222
        // Raw +DM[6]=1.0, Raw -DM[6]=0, Raw TR[6]=2.5
        // S+DM[6] = 1.11111111 - (1.11111111/3) + 1.0 = 1.11111111 - 0.37037037 + 1.0 = 1.74074074
        // S-DM[6] = 2.05555555 - (2.05555555/3) + 0   = 2.05555555 - 0.68518518 = 1.37037037
        // STR[6]  = 5.22222222 - (5.22222222/3) + 2.5 = 5.22222222 - 1.74074074 + 2.5 = 5.98148148
        // +DI[6] = (1.74074074 / 5.98148148) * 100 = 29.10052911 => 29.10
        // -DI[6] = (1.37037037 / 5.98148148) * 100 = 22.91005291 => 22.91
        // DX[6] = |29.10052911 - 22.91005291| / (29.10052911 + 22.91005291) * 100
        //       = 6.1904762 / 52.01058202 * 100 = 11.90235689 => 11.90
        assertEquals(BigDecimal("11.90"), adxIndicator.getDX(6))

        // ADX[6] = ADX[5] - ADX[5]/3 + DX[6]
        //        = 43.27352338 - (43.27352338/3) + 11.90235689
        //        = 43.27352338 - 14.42450779 + 11.90235689 = 40.75137248 => 40.75
        assertEquals(BigDecimal("40.75"), adxIndicator.getADX(6))
    }


    @Test
    fun `ADX on empty series`() {
        val adx = ADXIndicator(emptyList<Kline>().toSeries(), 14)
        assertNull(adx.getADX(0))
        assertNull(adx.getPlusDI(0))
        assertNull(adx.getMinusDI(0))
        assertNull(adx.getDX(0))
    }

    @Test
    fun `ADX with insufficient data`() {
        val klines = listOf(kline("10","9","9.5","9.5",0)).toSeries()
        val adx14 = ADXIndicator(klines, 14)
        // First ADX for period 14 is at index 14 + (14-1) = 27
        assertNull(adx14.getADX(0))
        assertNull(adx14.getADX(13)) // DI not even available yet
        assertNull(adx14.getADX(26)) // DX available, but not enough DX for ADX SMA
        assertNull(adx14.getADX(27)) // If series only has 1 kline, this will be null
    }

    @Test
    fun `ADX check various indices`() {
        val klines = (0..30).map { i ->
            kline( (10 + i*0.1 + (if(i%2==0) 0.5 else -0.2)).toString(), // H
                   (10 + i*0.1 - (if(i%3==0) 0.4 else 0.1)).toString(), // L
                   (10 + i*0.1).toString(), // C
                   (10 + i*0.1 - (if(i%2==0) 0.1 else -0.1)).toString(), // O
                   i.toLong()
            )
        }.toSeries()
        val adx14 = ADXIndicator(klines, 14)
        // First DI at index 14. First DX at index 14. First ADX at index 14 + 13 = 27.
        assertNull(adx14.getPlusDI(13))
        assertNotNull(adx14.getPlusDI(14))
        assertNotNull(adx14.getMinusDI(14))
        assertNotNull(adx14.getDX(14))

        assertNull(adx14.getADX(26))
        assertNotNull(adx14.getADX(27))
        assertNotNull(adx14.getADX(28))
        assertNotNull(adx14.getADX(29))
        assertNotNull(adx14.getADX(30))
        assertNull(adx14.getADX(31)) // Out of bounds for klines
    }
}
