package com.moneyfan.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus // Though not used in this specific refactor, good for consistency

class CandleFSMTest {
    @Test
    fun `test single tick processing`() {
        val fsm = CandleFSM(DateTimeUnit.MINUTE, 1)
        val tick = Tick(
            timestamp = Clock.System.now(),
            price = BigDecimal.parseString("100.00"),
            volume = BigDecimal.parseString("1.0")
        )

        val candle = fsm.processTick(tick)
        assertNull(candle) // No candle should be returned for single tick
        assertEquals(CandleState.Collecting, fsm.getCurrentState())

        val currentCandle = fsm.getCurrentCandle()
        assertNotNull(currentCandle)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.open)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.high)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.low)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.close)
        assertEquals(BigDecimal.parseString("1.0"), currentCandle.volume)
        assertEquals(1, currentCandle.tickCount)
    }

    @Test
    fun `test multiple ticks in same period`() {
        val fsm = CandleFSM(DateTimeUnit.MINUTE, 1)
        val baseTime = Clock.System.now()

        // First tick
        val tick1 = Tick(
            timestamp = baseTime,
            price = BigDecimal.parseString("100.00"),
            volume = BigDecimal.parseString("1.0")
        )
        assertNull(fsm.processTick(tick1))

        // Second tick in same minute
        val tick2 = Tick(
            timestamp = baseTime.plus(30, DateTimeUnit.SECOND),
            price = BigDecimal.parseString("101.00"),
            volume = BigDecimal.parseString("2.0")
        )
        assertNull(fsm.processTick(tick2))

        val currentCandle = fsm.getCurrentCandle()
        assertNotNull(currentCandle)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.open)
        assertEquals(BigDecimal.parseString("101.00"), currentCandle.high)
        assertEquals(BigDecimal.parseString("100.00"), currentCandle.low)
        assertEquals(BigDecimal.parseString("101.00"), currentCandle.close)
        assertEquals(BigDecimal.parseString("3.0"), currentCandle.volume) // 1.0 + 2.0
        assertEquals(2, currentCandle.tickCount)
    }

    @Test
    fun `test candle completion on new period`() {
        val fsm = CandleFSM(DateTimeUnit.MINUTE, 1)
        val baseTime = Clock.System.now()

        // First tick
        val tick1 = Tick(
            timestamp = baseTime,
            price = BigDecimal.parseString("100.00"),
            volume = BigDecimal.parseString("1.0")
        )
        assertNull(fsm.processTick(tick1))

        // Second tick in new minute
        // The CandleFSM's isNewCandlePeriod logic is crucial here.
        // It checks if truncatedCurrentTime >= (truncatedStartTime + intervalSize * unit)
        // So, baseTime.plus(1, DateTimeUnit.MINUTE) should trigger a new candle.
        val tick2Time = baseTime.plus(1, DateTimeUnit.MINUTE)
        val tick2 = Tick(
            timestamp = tick2Time,
            price = BigDecimal.parseString("101.00"),
            volume = BigDecimal.parseString("2.0")
        )
        val completedCandle = fsm.processTick(tick2)

        assertNotNull(completedCandle)
        assertEquals(BigDecimal.parseString("100.00"), completedCandle.open)
        assertEquals(BigDecimal.parseString("100.00"), completedCandle.high)
        assertEquals(BigDecimal.parseString("100.00"), completedCandle.low)
        assertEquals(BigDecimal.parseString("100.00"), completedCandle.close)
        assertEquals(BigDecimal.parseString("1.0"), completedCandle.volume)
        assertEquals(1, completedCandle.tickCount)

        // After completion, FSM should be collecting new tick data
        assertEquals(CandleState.Collecting, fsm.getCurrentState())
        val newCurrentCandle = fsm.getCurrentCandle()
        assertNotNull(newCurrentCandle)
        assertEquals(BigDecimal.parseString("101.00"), newCurrentCandle.open) // New candle starts with tick2's price
    }

    @Test
    fun `test reset functionality`() {
        val fsm = CandleFSM(DateTimeUnit.MINUTE, 1)
        val tick = Tick(
            timestamp = Clock.System.now(),
            price = BigDecimal.parseString("100.00"),
            volume = BigDecimal.parseString("1.0")
        )

        fsm.processTick(tick)
        assertEquals(CandleState.Collecting, fsm.getCurrentState())
        assertNotNull(fsm.getCurrentCandle())

        fsm.reset()
        assertEquals(CandleState.Initial, fsm.getCurrentState())
        assertNull(fsm.getCurrentCandle())
    }
}
