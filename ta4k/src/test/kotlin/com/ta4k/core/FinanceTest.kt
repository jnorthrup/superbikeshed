package com.ta4k.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigDecimal

class FinanceTest {
    internal val testPrices = listOf(
        BigDecimal("100.00"),
        BigDecimal("110.00"),
        BigDecimal("105.00"),
        BigDecimal("115.00"),
        BigDecimal("120.00")
    )

    @Test
    fun `test gross return calculation`() {
        val expected = listOf(
            BigDecimal("1.10000000"),
            BigDecimal("0.95454545"),
            BigDecimal("1.09523810"),
            BigDecimal("1.04347826")
        )
        
        val actual = Finance.grossReturn(testPrices)
        assertEquals(expected, actual)
    }

    @Test
    fun `test log return calculation`() {
        val expected = listOf(
            BigDecimal("0.09531018"),
            BigDecimal("0.04652002"),
            BigDecimal("0.09116078"),
            BigDecimal("0.04255961")
        )
        
        val actual = Finance.logReturn(testPrices)
        assertEquals(expected, actual)
    }

    @Test
    fun `test net return calculation`() {
        val expected = listOf(
            BigDecimal("0.10000000"),
            BigDecimal("-0.04545455"),
            BigDecimal("0.09523810"),
            BigDecimal("0.04347826")
        )
        
        val actual = Finance.netReturn(testPrices)
        assertEquals(expected, actual)
    }

    @Test
    fun `test compound return calculation`() {
        val expected = listOf(
            BigDecimal("1.00000000"),
            BigDecimal("1.10000000"),
            BigDecimal("1.05000000"),
            BigDecimal("1.15000000"),
            BigDecimal("1.20000000")
        )
        
        val actual = Finance.compoundReturn(testPrices)
        assertEquals(expected, actual)
    }

    @Test
    fun `test percent return calculation`() {
        val expected = listOf(
            BigDecimal("0.00"),
            BigDecimal("10.00"),
            BigDecimal("5.00"),
            BigDecimal("15.00"),
            BigDecimal("20.00")
        )
        
        val actual = Finance.percentReturn(testPrices)
        assertEquals(expected, actual)
    }

    @Test
    fun `test calculate returns with different return types`() {
        val netReturns = Finance.calculateReturns(testPrices, ReturnType.NET)
        val grossReturns = Finance.calculateReturns(testPrices, ReturnType.GROSS)
        val logReturns = Finance.calculateReturns(testPrices, ReturnType.LOG)
        val compoundReturns = Finance.calculateReturns(testPrices, ReturnType.COMPOUND)
        val percentReturns = Finance.calculateReturns(testPrices, ReturnType.PERCENT)

        assertNotNull(netReturns)
        assertNotNull(grossReturns)
        assertNotNull(logReturns)
        assertNotNull(compoundReturns)
        assertNotNull(percentReturns)
        
        assertTrue(netReturns.isNotEmpty())
        assertTrue(grossReturns.isNotEmpty())
        assertTrue(logReturns.isNotEmpty())
        assertTrue(compoundReturns.isNotEmpty())
        assertTrue(percentReturns.isNotEmpty())
    }

    @Test
    fun `test returns with equal prices`() {
        val equalPrices = listOf(
            BigDecimal("100.00"),
            BigDecimal("100.00"),
            BigDecimal("100.00")
        )

        val grossReturns = Finance.grossReturn(equalPrices)
        val netReturns = Finance.netReturn(equalPrices)
        val logReturns = Finance.logReturn(equalPrices)

        assertEquals(listOf(BigDecimal.ONE, BigDecimal.ONE), grossReturns)
        assertEquals(listOf(BigDecimal.ZERO, BigDecimal.ZERO), netReturns)
        assertEquals(listOf(BigDecimal.ZERO, BigDecimal.ZERO), logReturns)
    }
} 