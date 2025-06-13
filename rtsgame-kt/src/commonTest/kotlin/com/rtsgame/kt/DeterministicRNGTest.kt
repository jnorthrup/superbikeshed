package com.rtsgame.kt

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DeterministicRNGTest {

    @Test
    fun testDeterminism() {
        val rng1 = DeterministicRNG(12345L)
        val rng2 = DeterministicRNG(12345L)

        val sequence1 = List(10) { rng1.getRandom() }
        val sequence2 = List(10) { rng2.getRandom() }

        assertEquals(sequence1, sequence2, "Sequences with the same seed should be identical.")
    }

    @Test
    fun testGetRandomRange() {
        val rng = DeterministicRNG(System.currentTimeMillis())
        for (i in 0..999) {
            val value = rng.getRandom()
            assertTrue(value >= 0.0 && value < 1.0, "getRandom() value $value should be in [0.0, 1.0)")
        }
    }

    @Test
    fun testGetIntRange() {
        val rng = DeterministicRNG(System.currentTimeMillis())
        val min = 5
        val max = 10 // Max is inclusive in the current implementation
        for (i in 0..999) {
            val value = rng.getInt(min, max)
            assertTrue(value >= min && value <= max, "getInt() value $value should be in [$min, $max]")
        }
    }

    @Test
    fun testGetFloatRange() {
        val rng = DeterministicRNG(System.currentTimeMillis())
        for (i in 0..999) {
            val value = rng.getFloat()
            assertTrue(value >= 0.0f && value < 1.0f, "getFloat() value $value should be in [0.0f, 1.0f)")
        }
    }

    @Test
    fun testSpecificSequence() {
        // Test a short sequence to catch regressions in LCG parameters or implementation
        val rng = DeterministicRNG(42L)
        val expectedValues = listOf(
            0.7066651564091444, // (1664525 * 42 + 1013904223) % 2^32 / 2^32
            0.011829541064798832,
            0.4908373896032572
        )
        for (i in expectedValues.indices) {
            val actual = rng.getRandom()
            assertEquals(expectedValues[i], actual, "Value at index $i differs from expected.")
        }
    }
}
