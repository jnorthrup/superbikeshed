package com.moneyfan.core

import com.ionspin.kotlin.bignum.decimal.BigDecimal
// import com.ionspin.kotlin.bignum.decimal.RoundingMode // Not used yet
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.truncatedTo
// We might need this if comparison logic changes, but isAfter should work
// import kotlinx.datetime.compareTo

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
    private val interval: DateTimeUnit = DateTimeUnit.MINUTE,
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
        val truncatedCurrentTime = currentTime.truncatedTo(interval)
        val truncatedStartTime = startTime.truncatedTo(interval)

        // Calculate the end of the current candle's period
        val periodEnd = truncatedStartTime.plus(intervalSize, interval)

        // A new candle period starts if the current truncated time is at or after the periodEnd.
        // Example: If interval is 1 MINUTE, startTime is 10:00:30 (truncated to 10:00:00).
        // periodEnd will be 10:01:00.
        // If currentTime is 10:01:00 or later, it's a new period.
        // If currentTime is 10:00:59, it's not a new period.
        return truncatedCurrentTime >= periodEnd
    }

    fun getCurrentState(): CandleState = currentState

    fun getCurrentCandle(): Candle? = currentCandle

    fun reset() {
        currentState = CandleState.Initial
        currentCandle = null
        currentTickCount = 0
    }
} 