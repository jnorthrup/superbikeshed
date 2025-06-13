package com.moneyfan.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.Instant

class SmoothingLayerTest {
    @Test
    fun `test wilder smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Wilder, period = 3)
        
        val value1 = smoother.smooth(BigDecimal("100"))
        assertEquals(BigDecimal("100"), value1)
        
        val value2 = smoother.smooth(BigDecimal("110"))
        assertEquals(BigDecimal("103.33333333"), value2)
        
        val value3 = smoother.smooth(BigDecimal("120"))
        assertEquals(BigDecimal("107.77777777"), value3)
    }

    @Test
    fun `test exponential smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Exponential, period = 3)
        
        val value1 = smoother.smooth(BigDecimal("100"))
        assertEquals(BigDecimal("100"), value1)
        
        val value2 = smoother.smooth(BigDecimal("110"))
        assertEquals(BigDecimal("105"), value2)
        
        val value3 = smoother.smooth(BigDecimal("120"))
        assertEquals(BigDecimal("112.5"), value3)
    }

    @Test
    fun `test hull smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Hull, period = 4)
        
        val value1 = smoother.smooth(BigDecimal("100"))
        assertEquals(BigDecimal("100"), value1)
        
        val value2 = smoother.smooth(BigDecimal("110"))
        assertTrue(value2 > BigDecimal("100"))
        assertTrue(value2 < BigDecimal("110"))
    }

    @Test
    fun `test kalman smoothing`() {
        val smoother = SmoothingLayer(SmoothingType.Kalman, period = 3)
        
        val value1 = smoother.smooth(BigDecimal("100"))
        assertEquals(BigDecimal("100"), value1)
        
        val value2 = smoother.smooth(BigDecimal("110"))
        assertTrue(value2 > BigDecimal("100"))
        assertTrue(value2 < BigDecimal("110"))
    }

    @Test
    fun `test tick smoothing`() {
        val tickSmoother = TickSmoother(SmoothingLayer(SmoothingType.Exponential, period = 3))
        val tick = Tick(
            timestamp = Instant.now(),
            price = BigDecimal("100"),
            volume = BigDecimal("1000")
        )
        
        val smoothedTick = tickSmoother.smoothTick(tick)
        assertEquals(BigDecimal("100"), smoothedTick.price)
        assertEquals(BigDecimal("1000"), smoothedTick.volume)
    }

    @Test
    fun `test candle smoothing`() {
        val candleSmoother = CandleSmoother(SmoothingLayer(SmoothingType.Exponential, period = 3))
        val candle = Candle(
            open = BigDecimal("100"),
            high = BigDecimal("110"),
            low = BigDecimal("90"),
            close = BigDecimal("105"),
            volume = BigDecimal("1000"),
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(60),
            tickCount = 10
        )
        
        val smoothedCandle = candleSmoother.smoothCandle(candle)
        assertEquals(BigDecimal("100"), smoothedCandle.open)
        assertEquals(BigDecimal("110"), smoothedCandle.high)
        assertEquals(BigDecimal("90"), smoothedCandle.low)
        assertEquals(BigDecimal("105"), smoothedCandle.close)
        assertEquals(BigDecimal("1000"), smoothedCandle.volume)
    }

    @Test
    fun `test reset functionality`() {
        val smoother = SmoothingLayer(SmoothingType.Exponential, period = 3)
        
        smoother.smooth(BigDecimal("100"))
        smoother.smooth(BigDecimal("110"))
        
        smoother.reset()
        
        val value = smoother.smooth(BigDecimal("100"))
        assertEquals(BigDecimal("100"), value)
    }
} 