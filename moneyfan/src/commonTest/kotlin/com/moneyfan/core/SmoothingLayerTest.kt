package com.moneyfan.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode // Not used in tests, but good for consistency if needed
import kotlinx.datetime.Clock // For Instant.now() equivalent
import kotlinx.datetime.Instant // For Tick and Candle data classes

// Helper for BigDecimal comparison with tolerance if needed, though direct equals should work for these exact values
fun assertBigDecimalEquals(expected: String, actual: BigDecimal, message: String? = null) {
    assertEquals(BigDecimal.parseString(expected), actual, message)
}

class SmoothingLayerTest {
    @Test
    fun `test wilder smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Wilder, period = 3, calculationScale = 8) // Match scale from main code

        val value1 = smoother.smooth(BigDecimal.parseString("100"))
        assertBigDecimalEquals("100.00000000", value1) // Expect scale from smoother

        val value2 = smoother.smooth(BigDecimal.parseString("110"))
        // (100.00000000 * 2 + 110) / 3 = 310 / 3 = 103.33333333
        assertBigDecimalEquals("103.33333333", value2)

        val value3 = smoother.smooth(BigDecimal.parseString("120"))
        // (103.33333333 * 2 + 120) / 3 = (206.66666666 + 120) / 3 = 326.66666666 / 3 = 108.88888888 (approx)
        // Let's recheck the calculation for Wilder in SmoothingLayer:
        // prev = 103.33333333. (prev * (periodBd - 1) + value) / periodBd
        // (103.33333333 * 2 + 120) / 3 = (206.66666666 + 120) / 3 = 326.66666666 / 3 = 108.8888888866...
        // Rounded to 8 places: 108.88888889
        assertBigDecimalEquals("108.88888889", value3)
    }

    @Test
    fun `test exponential smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Exponential, period = 3, calculationScale = 8)
        // Alpha = 2 / (3 + 1) = 0.5

        val value1 = smoother.smooth(BigDecimal.parseString("100"))
        assertBigDecimalEquals("100.00000000", value1) // Initial value, scaled

        val value2 = smoother.smooth(BigDecimal.parseString("110"))
        // prev = 100. prev * (1-alpha) + value * alpha = 100 * 0.5 + 110 * 0.5 = 50 + 55 = 105
        assertBigDecimalEquals("105.00000000", value2)

        val value3 = smoother.smooth(BigDecimal.parseString("120"))
        // prev = 105. prev * (1-alpha) + value * alpha = 105 * 0.5 + 120 * 0.5 = 52.5 + 60 = 112.5
        assertBigDecimalEquals("112.50000000", value3)
    }

    @Test
    fun `test hull smoothing`() {
        // Hull smoothing is more complex and its simplified version in SmoothingLayer
        // behaves like a responsive EMA. Exact values are harder to predict without
        // running the specific simplified logic. Tests will check for general behavior.
        val smoother = SmoothingLayer(SmoothingType.Hull, period = 4, calculationScale = 8)

        val val100 = BigDecimal.parseString("100")
        val val110 = BigDecimal.parseString("110")

        val value1 = smoother.smooth(val100)
        assertBigDecimalEquals("100.00000000", value1)

        val value2 = smoother.smooth(val110)
        // Expect value to be between 100 and 110, and responsive
        assertTrue(value2 > val100, "Hull smoothed value should be greater than previous if price increases")
        // Depending on the Hull logic, it can overshoot or undershoot briefly.
        // For this simplified version, it's likely to be between.
         assertTrue(value2 < val110 || value2 == val110, "Hull smoothed value often follows price but smoothed")


        val value3 = smoother.smooth(BigDecimal.parseString("105")) // Price goes down
         assertTrue(value3 < value2, "Hull smoothed value should decrease if price decreases from last point")
    }

    @Test
    fun `test kalman smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Kalman, period = 3, calculationScale = 8) // Period not directly used by this Kalman

        val val100 = BigDecimal.parseString("100")
        val val110 = BigDecimal.parseString("110")

        val value1 = smoother.smooth(val100)
        // Initial value might be slightly different due to Kalman prediction step if previousValue is null
        // In current implementation, if previousValue is null, prediction = value, so smoothed = value.
        assertBigDecimalEquals("100.00000000", value1)

        val value2 = smoother.smooth(val110)
        assertTrue(value2 > val100, "Kalman smoothed value should be greater than previous if price increases")
        assertTrue(value2 < val110, "Kalman smoothed value should be less than current price (smoothed towards it)")
    }

    @Test
    fun `test tick smoothing`() {
        val tickSmoother = TickSmoother(SmoothingLayer(SmoothingType.Exponential, period = 3, calculationScale = 8))
        val tick = Tick(
            timestamp = Clock.System.now(), // kotlinx.datetime.Instant
            price = BigDecimal.parseString("100"),
            volume = BigDecimal.parseString("1000")
        )

        val smoothedTick = tickSmoother.smoothTick(tick)
        // First tick, price and volume should be scaled but effectively the same numeric value
        assertBigDecimalEquals("100.00000000", smoothedTick.price)
        assertBigDecimalEquals("1000.00000000", smoothedTick.volume)
    }

    @Test
    fun `test candle smoothing`() {
        val candleSmoother = CandleSmoother(SmoothingLayer(SmoothingType.Exponential, period = 3, calculationScale = 8))
        val now = Clock.System.now()
        val candle = Candle(
            open = BigDecimal.parseString("100"),
            high = BigDecimal.parseString("110"),
            low = BigDecimal.parseString("90"),
            close = BigDecimal.parseString("105"),
            volume = BigDecimal.parseString("1000"),
            startTime = now,
            endTime = now.plus(60, kotlinx.datetime.DateTimeUnit.SECOND),
            tickCount = 10
        )

        val smoothedCandle = candleSmoother.smoothCandle(candle)
        // As it's the first candle processed by each internal smoother call, values remain same but scaled
        assertBigDecimalEquals("100.00000000", smoothedCandle.open)
        assertBigDecimalEquals("110.00000000", smoothedCandle.high)
        assertBigDecimalEquals("90.00000000", smoothedCandle.low)
        assertBigDecimalEquals("105.00000000", smoothedCandle.close)
        assertBigDecimalEquals("1000.00000000", smoothedCandle.volume)
    }

    @Test
    fun `test reset functionality`() {
        val smoother = SmoothingLayer(SmoothingType.Exponential, period = 3, calculationScale = 8)

        smoother.smooth(BigDecimal.parseString("100"))
        smoother.smooth(BigDecimal.parseString("110")) // This will be 105.00000000

        smoother.reset()

        val value = smoother.smooth(BigDecimal.parseString("100"))
        assertBigDecimalEquals("100.00000000", value) // After reset, first value is itself (scaled)
    }
}
