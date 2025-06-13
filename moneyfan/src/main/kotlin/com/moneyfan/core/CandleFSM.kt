package com.moneyfan.core

import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed class CandleState {
    object Initial : CandleState()
    object Collecting : CandleState()
    object Complete : CandleState()
    data class Error(val message: String) : CandleState()
}

data class Tick(
    val timestamp: Instant,
    val price: BigDecimal,
    val volume: BigDecimal
)

data class Candle(
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val startTime: Instant,
    val endTime: Instant,
    val tickCount: Int
)

class CandleFSM(
    private val interval: ChronoUnit = ChronoUnit.MINUTES,
    private val intervalSize: Long = 1
) {
    private var currentState: CandleState = CandleState.Initial
    private var currentCandle: Candle? = null
    private var currentTickCount: Int = 0

    fun processTick(tick: Tick): Candle? {
        return when (currentState) {
            is CandleState.Initial -> handleInitialState(tick)
            is CandleState.Collecting -> handleCollectingState(tick)
            is CandleState.Complete -> handleCompleteState(tick)
            is CandleState.Error -> handleErrorState(tick)
        }
    }

    private fun handleInitialState(tick: Tick): Candle? {
        currentState = CandleState.Collecting
        currentCandle = Candle(
            open = tick.price,
            high = tick.price,
            low = tick.price,
            close = tick.price,
            volume = tick.volume,
            startTime = tick.timestamp,
            endTime = tick.timestamp,
            tickCount = 1
        )
        currentTickCount = 1
        return null
    }

    private fun handleCollectingState(tick: Tick): Candle? {
        val candle = currentCandle ?: return null
        
        if (isNewCandlePeriod(tick.timestamp, candle.startTime)) {
            currentState = CandleState.Complete
            return candle
        }

        currentCandle = candle.copy(
            high = candle.high.max(tick.price),
            low = candle.low.min(tick.price),
            close = tick.price,
            volume = candle.volume.add(tick.volume),
            endTime = tick.timestamp,
            tickCount = ++currentTickCount
        )
        return null
    }

    private fun handleCompleteState(tick: Tick): Candle? {
        val completedCandle = currentCandle
        currentState = CandleState.Initial
        currentCandle = null
        currentTickCount = 0
        return completedCandle
    }

    private fun handleErrorState(tick: Tick): Candle? {
        // Reset on error and start new candle
        currentState = CandleState.Initial
        currentCandle = null
        currentTickCount = 0
        return null
    }

    private fun isNewCandlePeriod(currentTime: Instant, startTime: Instant): Boolean {
        return when (interval) {
            ChronoUnit.MINUTES -> {
                val currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES)
                val startMinute = startTime.truncatedTo(ChronoUnit.MINUTES)
                currentMinute.isAfter(startMinute.plus(intervalSize, ChronoUnit.MINUTES))
            }
            ChronoUnit.HOURS -> {
                val currentHour = currentTime.truncatedTo(ChronoUnit.HOURS)
                val startHour = startTime.truncatedTo(ChronoUnit.HOURS)
                currentHour.isAfter(startHour.plus(intervalSize, ChronoUnit.HOURS))
            }
            ChronoUnit.DAYS -> {
                val currentDay = currentTime.truncatedTo(ChronoUnit.DAYS)
                val startDay = startTime.truncatedTo(ChronoUnit.DAYS)
                currentDay.isAfter(startDay.plus(intervalSize, ChronoUnit.DAYS))
            }
            else -> throw IllegalArgumentException("Unsupported interval: $interval")
        }
    }

    fun getCurrentState(): CandleState = currentState

    fun getCurrentCandle(): Candle? = currentCandle

    fun reset() {
        currentState = CandleState.Initial
        currentCandle = null
        currentTickCount = 0
    }
} 