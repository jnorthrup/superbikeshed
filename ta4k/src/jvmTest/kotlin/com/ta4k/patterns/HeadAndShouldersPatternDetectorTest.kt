package com.ta4k.patterns

import com.ta4k.core.model.Kline
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class HeadAndShouldersPatternDetectorTest {

    // Helper to create a Kline instance for tests
    private fun kline(uniqueIdx: Int, high: String, low: String, close: String = low): Kline {
        // Using uniqueIdx to ensure klines are distinct if needed by underlying logic,
        // and to set different timestamps.
        return Kline(
            openTimeMillis = uniqueIdx.toLong() * 60000, // e.g., 1 minute apart
            openPrice = BigDecimal(close), // Simplified open
            highPrice = BigDecimal(high),
            lowPrice = BigDecimal(low),
            closePrice = BigDecimal(close),
            volume = BigDecimal("1000"), // Dummy volume
            closeTimeMillis = uniqueIdx.toLong() * 60000 + 59999,
            quoteAssetVolume = BigDecimal("100000"), // Dummy quote volume
            numberOfTrades = 100, // Dummy trades
            takerBuyBaseAssetVolume = BigDecimal("500"),
            takerBuyQuoteAssetVolume = BigDecimal("50000")
        )
    }

    // Helper to create a SwingPoint instance for tests
    private fun sp(klineIdx: Int, priceStr: String, type: SwingType, highStr: String = priceStr, lowStr: String = priceStr): SwingPoint {
        val price = BigDecimal(priceStr)
        val high = BigDecimal(highStr)
        val low = BigDecimal(lowStr)
        val actualKline = when(type) {
            SwingType.HIGH -> kline(klineIdx, highStr, if (high.subtract(BigDecimal("2")) > low) high.subtract(BigDecimal("2")).toPlainString() else lowStr, priceStr)
            SwingType.LOW -> kline(klineIdx, if (low.add(BigDecimal("2")) < high) low.add(BigDecimal("2")).toPlainString() else highStr, lowStr, priceStr)
        }
        return SwingPoint(actualKline, klineIdx, price, type)
    }


    @Test
    fun `detect classic Head and Shoulders pattern`() {
        val ls = sp(10, "100", SwingType.HIGH, highStr = "100", lowStr = "98")
        val n1 = sp(15, "95", SwingType.LOW, highStr = "97", lowStr = "95")
        val h =  sp(20, "105", SwingType.HIGH, highStr = "105", lowStr = "102") // Head
        val n2 = sp(25, "96", SwingType.LOW, highStr = "98", lowStr = "96")
        val rs = sp(30, "100.5", SwingType.HIGH, highStr = "101", lowStr = "99") // Right shoulder, slightly higher than LS but valid

        val swingPoints = listOf(ls, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()

        // Default config: shoulderHeadRatioMin = 0.5, shoulderHeadRatioMax = 0.98
        // Avg Neckline = (95+96)/2 = 95.5
        // Head Height = 105 - 95.5 = 9.5
        // LS Height from N1 = 100 - 95 = 5. Ratio = 5/9.5 = ~0.52 (ok)
        // RS Height from N2 = 100.5 - 96 = 4.5. Ratio = 4.5/9.5 = ~0.47 (This would fail default 0.5 min)
        // Let's adjust RS to pass: make rs.price = 101, N2 = 96. RS Height = 101-96 = 5. Ratio = 5/9.5 = ~0.52 (ok)
        val rsAdjusted = sp(30, "101", SwingType.HIGH, highStr = "101", lowStr = "99")
        val swingPointsAdjusted = listOf(ls, n1, h, n2, rsAdjusted)


        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPointsAdjusted)

        assertEquals(1, patterns.size)
        assertEquals("Head and Shoulders", patterns[0].name)
        assertEquals(ls.index, patterns[0].startIndex)
        assertEquals(rsAdjusted.index, patterns[0].endIndex)
        assertNotNull(patterns[0].points["head"])
        assertEquals(h.price, patterns[0].points["head"]?.price)
    }

    @Test
    fun `detect Inverse Head and Shoulders pattern`() {
        val ls = sp(10, "100", SwingType.LOW, highStr = "102", lowStr = "100")
        val n1 = sp(15, "105", SwingType.HIGH, highStr = "105", lowStr = "103")
        val h =  sp(20, "95", SwingType.LOW, highStr = "98", lowStr = "95") // Head
        val n2 = sp(25, "106", SwingType.HIGH, highStr = "106", lowStr = "104")
        val rs = sp(30, "99", SwingType.LOW, highStr = "101", lowStr = "99")

        val swingPoints = listOf(ls, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()

        // Avg Neckline = (105+106)/2 = 105.5
        // Head Depth = 105.5 - 95 = 10.5
        // LS Depth from N1 = 105 - 100 = 5. Ratio = 5/10.5 = ~0.47 (Fails default 0.5 min)
        // Let's adjust LS to pass: ls.price = 99. LS Depth = 105-99=6. Ratio = 6/10.5 = ~0.57 (ok)
        val lsAdjusted = sp(10, "99", SwingType.LOW, highStr = "102", lowStr = "99")
        // And RS depth from N2 = 106 - 99 = 7. Ratio = 7/10.5 = ~0.66 (ok)
        val swingPointsAdjusted = listOf(lsAdjusted, n1, h, n2, rs)


        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPointsAdjusted)

        assertEquals(1, patterns.size)
        assertEquals("Inverse Head and Shoulders", patterns[0].name)
        assertEquals(h.price, patterns[0].points["head"]?.price)
    }

    @Test
    fun `no pattern if head is not most extreme`() {
        val ls = sp(10, "105", SwingType.HIGH, highStr = "105", lowStr = "98") // LS is higher than head
        val n1 = sp(15, "97", SwingType.LOW, highStr = "97", lowStr = "95")
        val h =  sp(20, "100", SwingType.HIGH, highStr = "100", lowStr = "98")
        val n2 = sp(25, "98", SwingType.LOW, highStr = "98", lowStr = "96")
        val rs = sp(30, "101", SwingType.HIGH, highStr = "101", lowStr = "99")

        val swingPoints = listOf(ls, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()
        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPoints)
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun `no pattern if shoulder ratios are off - too small`() {
        val ls = sp(10, "100", SwingType.HIGH, highStr = "100", lowStr = "98")
        val n1 = sp(15, "95", SwingType.LOW, highStr = "97", lowStr = "95")
        val h =  sp(20, "120", SwingType.HIGH, highStr = "120", lowStr = "102")
        val n2 = sp(25, "96", SwingType.LOW, highStr = "98", lowStr = "96")
        val rs = sp(30, "101", SwingType.HIGH, highStr = "101", lowStr = "99")
        // Avg Neckline = (95+96)/2 = 95.5
        // Head Height = 120 - 95.5 = 24.5
        // LS Height from N1 = 100 - 95 = 5. Ratio = 5/24.5 ~ 0.20 (Too small, default min 0.5)

        val swingPoints = listOf(ls, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()
        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPoints)
        assertTrue(patterns.isEmpty(), "Pattern should be rejected due to LS shoulder/head ratio too small")
    }

    @Test
    fun `no pattern if shoulder ratios are off - too large (almost same as head)`() {
        val ls = sp(10, "104", SwingType.HIGH, highStr = "104", lowStr = "98")
        val n1 = sp(15, "95", SwingType.LOW, highStr = "97", lowStr = "95")
        val h =  sp(20, "105", SwingType.HIGH, highStr = "105", lowStr = "102")
        val n2 = sp(25, "96", SwingType.LOW, highStr = "98", lowStr = "96")
        val rs = sp(30, "101", SwingType.HIGH, highStr = "101", lowStr = "99")
        // Avg Neckline = (95+96)/2 = 95.5
        // Head Height = 105 - 95.5 = 9.5
        // LS Height from N1 = 104 - 95 = 9. Ratio = 9/9.5 ~ 0.947 (Ok, default max 0.98)
        // RS Height from N2 = 101 - 96 = 5. Ratio = 5/9.5 ~ 0.52 (Ok)
        // This should pass if RS is also large. Let's make LS too large.
        val lsTooLarge = sp(10, "104.8", SwingType.HIGH, highStr = "104.8", lowStr = "98") // LS Height = 104.8-95 = 9.8. Ratio = 9.8/9.5 = 1.03 (Too large)


        val swingPoints = listOf(lsTooLarge, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()
        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPoints)
        assertTrue(patterns.isEmpty(), "Pattern should be rejected due to LS shoulder/head ratio too large")
    }

     @Test
    fun `no pattern if pattern duration is too short`() {
        val ls = sp(10, "100", SwingType.HIGH, highStr = "100", lowStr = "98")
        val n1 = sp(11, "95", SwingType.LOW, highStr = "97", lowStr = "95") // Only 1 bar apart
        val h =  sp(12, "105", SwingType.HIGH, highStr = "105", lowStr = "102")
        val n2 = sp(13, "96", SwingType.LOW, highStr = "98", lowStr = "96")
        val rs = sp(14, "100", SwingType.HIGH, highStr = "100", lowStr = "98") // Duration 14-10 = 4 bars

        val swingPoints = listOf(ls, n1, h, n2, rs)
        val klines = swingPoints.map { it.kline }.toSeries()
        // Default min duration is 10
        val patterns = HeadAndShouldersPatternDetector.detect(klines, swingPoints, HeadAndShouldersPatternDetector.DetectorConfig(minPatternDuration = 5))
        assertTrue(patterns.isEmpty(), "Duration 4 should be less than minPatternDuration 5")

        val patternsPass = HeadAndShouldersPatternDetector.detect(klines, swingPoints, HeadAndShouldersPatternDetector.DetectorConfig(minPatternDuration = 3))
        assertEquals(1, patternsPass.size, "Duration 4 should be >= minPatternDuration 3")
    }
}
