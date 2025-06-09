package com.ta4k.indicators.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.math.RoundingMode

class SmoothingUtilsTest {

    @Test
    fun `wildersSmooth basic test with BigDecimal list`() {
        val rawValues = listOf(
            BigDecimal("10"), BigDecimal("11"), BigDecimal("12"), // Period 3. First smoothed value at index 2.
            BigDecimal("13"),
            BigDecimal("10")
        )

        val period = 3
        val calculationScale = 4 // For assertions

        val smoothed = SmoothingUtils.wildersSmooth(
            series = rawValues,
            period = period,
            initialSumProvider = { index, list ->
                // For index == period - 1 (i.e., 2 for period 3), sum list[0]..list[2]
                var sum = BigDecimal.ZERO
                for (k in 0..index) { // Sums the first 'period' elements from the rawValues list
                    sum += list[k] // Assuming list elements are BigDecimal
                }
                sum
            },
            valueExtractor = { it ?: BigDecimal.ZERO }, // Value extractor for the raw values
            calculationScale = calculationScale
        )

        // Expected:
        // smoothed[0] = null
        // smoothed[1] = null
        // smoothed[2] (SMA of 10,11,12) = (10+11+12)/3 = 33/3 = 11.0000
        // smoothed[3] = (smoothed[2]*(3-1) + rawValues[3])/3 = (11*2 + 13)/3 = (22+13)/3 = 35/3 = 11.6667
        // smoothed[4] = (smoothed[3]*(3-1) + rawValues[4])/3 = (11.6667*2 + 10)/3 = (23.3334 + 10)/3 = 33.3334/3 = 11.1111 (approx)

        assertNull(smoothed[0], "Smoothed value at index 0 should be null")
        assertNull(smoothed[1], "Smoothed value at index 1 should be null")
        assertEquals(BigDecimal("11.0000"), smoothed[2]?.setScale(calculationScale, RoundingMode.HALF_UP), "First smoothed value (SMA)")
        assertEquals(BigDecimal("11.6667"), smoothed[3]?.setScale(calculationScale, RoundingMode.HALF_UP), "Second smoothed value")
        // Calculation with scale 4: 11.6667 * 2 = 23.3334. 23.3334 + 10 = 33.3334. 33.3334 / 3 = 11.111133... -> 11.1111
        assertEquals(BigDecimal("11.1111"), smoothed[4]?.setScale(calculationScale, RoundingMode.HALF_UP), "Third smoothed value")
    }

    @Test
    fun `wildersSmooth with list of nullable BigDecimals`() {
        val rawValuesNullable: List<BigDecimal?> = listOf(
            null,             // index 0
            BigDecimal("10"), // index 1
            BigDecimal("11"), // index 2. First avg uses raw[0],raw[1],raw[2]
            BigDecimal("12"), // index 3
            BigDecimal("13")  // index 4
        )

        val period = 3
        val calculationScale = 4

        val smoothed = SmoothingUtils.wildersSmooth(
            series = rawValuesNullable,
            period = period,
            initialSumProvider = { index, list -> // index is period-1 = 2
                var sum = BigDecimal.ZERO
                // Sum elements from list[0] to list[index] (i.e., list[2])
                for (k in 0..index) {
                    sum += list[k] ?: BigDecimal.ZERO // Handle nulls in sum
                }
                sum
            },
            valueExtractor = { it ?: BigDecimal.ZERO },
            calculationScale = calculationScale
        )
        // Expected:
        // smoothed[0] = null
        // smoothed[1] = null
        // smoothed[2] (SMA of raw[0],raw[1],raw[2]) = (0+10+11)/3 = 21/3 = 7.0000
        // smoothed[3] (using raw[3]=12) = (smoothed[2]*(3-1) + raw[3])/3 = (7*2 + 12)/3 = (14+12)/3 = 26/3 = 8.6667
        // smoothed[4] (using raw[4]=13) = (smoothed[3]*(3-1) + raw[4])/3 = (8.6667*2 + 13)/3 = (17.3334+13)/3 = 30.3334/3 = 10.1111

        assertNull(smoothed[0])
        assertNull(smoothed[1])
        assertEquals(BigDecimal("7.0000"), smoothed[2]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("8.6667"), smoothed[3]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("10.1111"), smoothed[4]?.setScale(calculationScale, RoundingMode.HALF_UP))
    }

    data class TestKline(val price: BigDecimal?, val id: Int) // Simple class for testing generic type

    @Test
    fun `wildersSmooth with custom type and extractor`() {
        val klines = listOf(
            TestKline(BigDecimal("20"), 0),
            TestKline(BigDecimal("22"), 1),
            TestKline(BigDecimal("21"), 2), // First avg at index 2 (period 3)
            TestKline(BigDecimal("24"), 3),
            TestKline(null, 4) // Test null handling in valueExtractor for current value
        )
        val period = 3
        val calculationScale = 4

        val smoothed = SmoothingUtils.wildersSmooth<TestKline>(
            series = klines,
            period = period,
            initialSumProvider = { index, list ->
                var sum = BigDecimal.ZERO
                for (k in 0..index) {
                    sum += list[k].price ?: BigDecimal.ZERO
                }
                sum
            },
            valueExtractor = { it?.price ?: BigDecimal.ZERO }, // Extract price, default to 0 if kline or price is null
            calculationScale = calculationScale
        )
        // Expected:
        // smoothed[0] = null
        // smoothed[1] = null
        // smoothed[2] (SMA of 20,22,21) = (20+22+21)/3 = 63/3 = 21.0000
        // smoothed[3] (using klines[3].price=24) = (21*2 + 24)/3 = (42+24)/3 = 66/3 = 22.0000
        // smoothed[4] (using klines[4].price=null -> 0) = (22*2 + 0)/3 = 44/3 = 14.6667

        assertNull(smoothed[0])
        assertNull(smoothed[1])
        assertEquals(BigDecimal("21.0000"), smoothed[2]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("22.0000"), smoothed[3]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("14.6667"), smoothed[4]?.setScale(calculationScale, RoundingMode.HALF_UP))
    }


    @Test
    fun `wildersSmooth with insufficient data`() {
        val rawValues = listOf(BigDecimal("10"), BigDecimal("11")) // Size 2
        // Period 3 needs at least 3 elements for the first value.
        val smoothed = SmoothingUtils.wildersSmooth(rawValues, 3, { _, list -> list.sumOf { it } }, { it!! })
        assertEquals(2, smoothed.size)
        assertNull(smoothed[0], "Insufficient data, should be null")
        assertNull(smoothed[1], "Insufficient data, should be null")
    }

    @Test
    fun `wildersSmooth with period 1`() {
        // With period 1, SMA(1) is the value itself.
        // Wilder's: New = (Old*(1-1) + Current)/1 = Current. So, it should just be the original series.
        val rawValues = listOf(BigDecimal("10"), BigDecimal("11"), BigDecimal("12"))
        val period = 1
        val calculationScale = 4
        val smoothed = SmoothingUtils.wildersSmooth(
            series = rawValues,
            period = period,
            initialSumProvider = { index, list -> list[index] }, // Sum of 1 element is the element itself
            valueExtractor = { it ?: BigDecimal.ZERO },
            calculationScale = calculationScale
        )
        // Expected:
        // smoothed[0] (SMA of raw[0]) = 10
        // smoothed[1] = (smoothed[0]*(1-1) + raw[1])/1 = raw[1] = 11
        // smoothed[2] = (smoothed[1]*(1-1) + raw[2])/1 = raw[2] = 12
        assertEquals(BigDecimal("10.0000"), smoothed[0]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("11.0000"), smoothed[1]?.setScale(calculationScale, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("12.0000"), smoothed[2]?.setScale(calculationScale, RoundingMode.HALF_UP))
    }


    @Test
    fun `wildersSmooth with empty list`() {
        val smoothed = SmoothingUtils.wildersSmooth(emptyList<BigDecimal>(), 3, { _, _ -> BigDecimal.ZERO }, { it!! })
        assertTrue(smoothed.isEmpty())
    }
}
