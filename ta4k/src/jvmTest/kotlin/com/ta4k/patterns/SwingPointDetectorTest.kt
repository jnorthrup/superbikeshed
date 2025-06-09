package com.ta4k.patterns

import com.ta4k.core.model.Kline
import com.ta4k.trikeshedutils.toSeries // Planned new location
import borg.trikeshed.core.size
import borg.trikeshed.core.Series // Should be imported if Series is explicitly typed
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class SwingPointDetectorTest {

    private fun kline(high: String, low: String, close: String = low) = Kline(
        openTimeMillis = 0L, openPrice = BigDecimal(close), highPrice = BigDecimal(high),
        lowPrice = BigDecimal(low), closePrice = BigDecimal(close), volume = BigDecimal.ZERO,
        closeTimeMillis = 0L, quoteAssetVolume = BigDecimal.ZERO, numberOfTrades = 0,
        takerBuyBaseAssetVolume = BigDecimal.ZERO, takerBuyQuoteAssetVolume = BigDecimal.ZERO
    )

    @Test
    fun `detect basic swing high with strength 1`() {
        val klines = listOf(
            kline("10", "9"),  // 0
            kline("12", "10"), // 1 (potential high)
            kline("10", "9")   // 2
        ).toSeries()
        val points = SwingPointDetector.detectSwingPoints(klines, 1)
        assertEquals(1, points.size)
        assertEquals(1, points[0].index)
        assertEquals(SwingType.HIGH, points[0].type)
        assertEquals(BigDecimal("12"), points[0].price)
    }

    @Test
    fun `detect basic swing low with strength 1`() {
        val klines = listOf(
            kline("12", "10"), // 0
            kline("11", "8"),  // 1 (potential low)
            kline("12", "10")  // 2
        ).toSeries()
        val points = SwingPointDetector.detectSwingPoints(klines, 1)
        assertEquals(1, points.size)
        assertEquals(1, points[0].index)
        assertEquals(SwingType.LOW, points[0].type)
        assertEquals(BigDecimal("8"), points[0].price)
    }

    @Test
    fun `no swing points if not enough data for strength`() {
        val klinesShort = listOf(kline("10", "9"), kline("12", "10")).toSeries() // Only 2 klines
        val pointsShort = SwingPointDetector.detectSwingPoints(klinesShort, 1) // Needs 2*1+1 = 3 klines
        assertTrue(pointsShort.isEmpty())

        val klinesEnoughForS1 = listOf(kline("10", "9"), kline("12", "10"), kline("11","9")).toSeries()
        val pointsS1 = SwingPointDetector.detectSwingPoints(klinesEnoughForS1, 1)
        // klinesEnoughForS1[1].high (12) > klinesEnoughForS1[0].high (10) AND klinesEnoughForS1[1].high (12) > klinesEnoughForS1[2].high (11) -> SH
        assertEquals(1, pointsS1.size)
        assertEquals(SwingType.HIGH, pointsS1[0].type)
        assertEquals(1, pointsS1[0].index)


        val pointsS2 = SwingPointDetector.detectSwingPoints(klinesEnoughForS1, 2) // Needs 2*2+1 = 5 klines
        assertTrue(pointsS2.isEmpty())
    }

    @Test
    fun `multiple swing points with strength 1`() {
        val klines = listOf(
            kline("10", "9"),  // 0
            kline("12", "10"), // 1 SH (12>10, 12>10)
            kline("10", "8"),  // 2 SL (10 not > 12. Low: 8<10, 8<9)
            kline("11", "9"),  // 3 (High: 11 not > 10. Low: 9 not < 8)
            kline("9", "7"),   // 4 (Low: 7<9, 7<8)
            kline("10", "8")   // 5
        ).toSeries()
        val points = SwingPointDetector.detectSwingPoints(klines, 1)
        assertEquals(3, points.size)

        assertEquals(1, points[0].index)
        assertEquals(SwingType.HIGH, points[0].type)
        assertEquals(BigDecimal("12"), points[0].price)

        assertEquals(2, points[1].index)
        assertEquals(SwingType.LOW, points[1].type)
        assertEquals(BigDecimal("8"), points[1].price)

        assertEquals(4, points[2].index)
        assertEquals(SwingType.LOW, points[2].type)
        assertEquals(BigDecimal("7"), points[2].price)
    }

    @Test
    fun `swing points with strength 2`() {
        val klines = listOf(
            kline("10", "9"),   // 0
            kline("11", "10"),  // 1
            kline("15", "12"),  // 2 SH (15 > 11,10 and 15 > 13,12)
            kline("13", "11"),  // 3
            kline("12", "10"),  // 4
            kline("14", "11"),  // 5 (Not SH: 14 not > 12 to its left with strength 2)
            kline("10", "8")    // 6
        ).toSeries()
        // Strength 2: needs 2 left, 2 right. Candidate index starts at 2, ends at klines.size - 1 - 2 = 6 - 1 - 2 = 3.
        // Candidate i=2 (val 15):
        //   Left: klines[1].H=11, klines[0].H=10. (15 > 11 && 15 > 10) -> True
        //   Right: klines[3].H=13, klines[4].H=12. (15 > 13 && 15 > 12) -> True. So, index 2 is SH.
        // Candidate i=3 (kline H=13, L=11):
        //   Check SH: Left klines[2].H=15. (13 <= 15) -> False. Not SH.
        //   Check SL: Left klines[2].L=12, klines[1].L=10. (11 >= 10) -> False. Not SL.
        // Candidate i=4 (kline H=12, L=10) - this is max index for strength 2 (size-1-strength = 7-1-2 = 4)
        //   Check SH: Left klines[3].H=13, klines[2].H=15. (12 <= 13) -> False. Not SH
        //   Check SL: Left klines[3].L=11, klines[2].L=12. (10 < 11 && 10 < 12) -> True
        //             Right klines[5].L=11, klines[6].L=8. (10 >= 8) -> False. Not SL.
        // The loop is `for (i in strength until klineSeries.size - strength)`
        // So for strength 2, size 7: `for (i in 2 until 7-2=5)`. So indices 2, 3, 4.

        val points = SwingPointDetector.detectSwingPoints(klines, 2)
        assertEquals(1, points.size, "Expected one swing high with strength 2")
        assertEquals(2, points[0].index)
        assertEquals(SwingType.HIGH, points[0].type)
        assertEquals(BigDecimal("15"), points[0].price)
    }

    @Test
    fun `plateau or equal highs should not be swing high`() {
        val klines = listOf(
            kline("10", "9"),  //0
            kline("12", "10"), //1
            kline("12", "10"), //2 Candidate. 12 not > klines[1].H (12). Not SH.
            kline("10", "9")   //3
        ).toSeries()
        val points = SwingPointDetector.detectSwingPoints(klines, 1)
        assertTrue(points.isEmpty(), "Plateau highs should not be identified as swing highs by this strict definition")
    }

    @Test
    fun `exclusive swing high and low detection`() {
        // A very specific candle that is higher than neighbors and lower than neighbors
        val klines = listOf(
            kline("10", "9"),    // 0
            kline("5", "4"),     // 1
            kline("15", "3"),    // 2 - Candidate. High=15, Low=3
            kline("5", "4"),     // 3
            kline("10", "9")     // 4
        ).toSeries()
        // For index 2 (strength 2):
        // High: 15 > klines[0].H(10) && 15 > klines[1].H(5). AND 15 > klines[3].H(5) && 15 > klines[4].H(10). YES, it's a Swing High.
        // Since it's a Swing High, it should not be tested as a Swing Low.

        val points = SwingPointDetector.detectSwingPoints(klines, 2)
        assertEquals(1, points.size, "Should detect only one swing point (High due to precedence)")
        assertEquals(SwingType.HIGH, points[0].type)
        assertEquals(BigDecimal("15"), points[0].price)
        assertEquals(2, points[0].index)
    }

    @Test
    fun `no swing points in flat series`() {
        val klines = listOf(
            kline("10", "9"), kline("10", "9"), kline("10", "9"),
            kline("10", "9"), kline("10", "9")
        ).toSeries()
        val points = SwingPointDetector.detectSwingPoints(klines, 1)
        assertTrue(points.isEmpty())
    }

    @Test
    fun `swing low at the end of data with sufficient strength`() {
         val klines = listOf(
            kline("15","12"), //0
            kline("14","11"), //1
            kline("10","8"),  //2 Candidate SL (8 < 11,12 and 8 < 9,10)
            kline("12","9"),  //3
            kline("13","10")  //4
        ).toSeries()
        // Strength 2. Loop for i in 2 until (5-2)=3. So only i=2 is candidate.
        // i=2: Kline H=10, L=8
        // SH Check: 10 not > klines[1].H(14). Not SH.
        // SL Check: 8 < klines[1].L(11) && 8 < klines[0].L(12). AND 8 < klines[3].L(9) && 8 < klines[4].L(10). YES.
        val points = SwingPointDetector.detectSwingPoints(klines, 2)
        assertEquals(1, points.size)
        assertEquals(2, points[0].index)
        assertEquals(SwingType.LOW, points[0].type)
        assertEquals(BigDecimal("8"), points[0].price)
    }
}
