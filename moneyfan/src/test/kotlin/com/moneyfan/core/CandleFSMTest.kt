package com.moneyfan.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class CandleFSMTest {
    @Test
    fun `test single tick processing`() {
        val fsm = CandleFSM(ChronoUnit.MINUTES, 1)
        val tick = Tick(
            timestamp = Instant.now(),
            price = BigDecimal("100.00"),
            volume = BigDecimal("1.0")
        )

        val candle = fsm.processTick(tick)
        assertNull(candle) // No candle should be returned for single tick
        assertEquals(CandleState.Collecting, fsm.getCurrentState())
        
        val currentCandle = fsm.getCurrentCandle()
        assertNotNull(currentCandle)
        assertEquals(BigDecimal("100.00"), currentCandle?.open)
        assertEquals(BigDecimal("100.00"), currentCandle?.high)
        assertEquals(BigDecimal("100.00"), currentCandle?.low)
        assertEquals(BigDecimal("100.00"), currentCandle?.close)
        assertEquals(BigDecimal("1.0"), currentCandle?.volume)
        assertEquals(1, currentCandle?.tickCount)
    }

    @Test
    fun `test multiple ticks in same period`() {
        val fsm = CandleFSM(ChronoUnit.MINUTES, 1)
        val baseTime = Instant.now()
        
        // First tick
        val tick1 = Tick(
            timestamp = baseTime,
            price = BigDecimal("100.00"),
            volume = BigDecimal("1.0")
        )
        assertNull(fsm.processTick(tick1))

        // Second tick in same minute
        val tick2 = Tick(
            timestamp = baseTime.plus(30, ChronoUnit.SECONDS),
            price = BigDecimal("101.00"),
            volume = BigDecimal("2.0")
        )
        assertNull(fsm.processTick(tick2))

        val currentCandle = fsm.getCurrentCandle()
        assertNotNull(currentCandle)
        assertEquals(BigDecimal("100.00"), currentCandle?.open)
        assertEquals(BigDecimal("101.00"), currentCandle?.high)
        assertEquals(BigDecimal("100.00"), currentCandle?.low)
        assertEquals(BigDecimal("101.00"), currentCandle?.close)
        assertEquals(BigDecimal("3.0"), currentCandle?.volume)
        assertEquals(2, currentCandle?.tickCount)
    }

    @Test
    fun `test candle completion on new period`() {
        val fsm = CandleFSM(ChronoUnit.MINUTES, 1)
        val baseTime = Instant.now()
        
        // First tick
        val tick1 = Tick(
            timestamp = baseTime,
            price = BigDecimal("100.00"),
            volume = BigDecimal("1.0")
        )
        assertNull(fsm.processTick(tick1))

        // Second tick in new minute
        val tick2 = Tick(
            timestamp = baseTime.plus(1, ChronoUnit.MINUTES),
            price = BigDecimal("101.00"),
            volume = BigDecimal("2.0")
        )
        val completedCandle = fsm.processTick(tick2)
        
        assertNotNull(completedCandle)
        assertEquals(BigDecimal("100.00"), completedCandle?.open)
        assertEquals(BigDecimal("100.00"), completedCandle?.high)
        assertEquals(BigDecimal("100.00"), completedCandle?.low)
        assertEquals(BigDecimal("100.00"), completedCandle?.close)
        assertEquals(BigDecimal("1.0"), completedCandle?.volume)
        assertEquals(1, completedCandle?.tickCount)
    }

    @Test
    fun `test reset functionality`() {
        val fsm = CandleFSM(ChronoUnit.MINUTES, 1)
        val tick = Tick(
            timestamp = Instant.now(),
            price = BigDecimal("100.00"),
            volume = BigDecimal("1.0")
        )

        fsm.processTick(tick)
        assertEquals(CandleState.Collecting, fsm.getCurrentState())
        assertNotNull(fsm.getCurrentCandle())

        fsm.reset()
        assertEquals(CandleState.Initial, fsm.getCurrentState())
        assertNull(fsm.getCurrentCandle())
    }
} 