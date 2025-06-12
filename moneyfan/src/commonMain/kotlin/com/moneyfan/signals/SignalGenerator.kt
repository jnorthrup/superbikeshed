package com.moneyfan.signals

import com.moneyfan.indicators.SmoothingUtils
import com.ionspin.kotlin.bignum.decimal.BigDecimal

enum class Signal {
    BUY, SELL, NEUTRAL
}

class SignalGenerator(
    private val period: Int = 14,
    private val threshold: BigDecimal = BigDecimal.parseString("0.5")
) {
    fun generateSignal(smoothedValues: List<BigDecimal?>): Signal {
        val lastValue = smoothedValues.lastOrNull() ?: return Signal.NEUTRAL
        return if (lastValue > threshold) Signal.BUY else Signal.SELL
    }

    fun processMarketData(marketDataList: List<MarketData>): List<Signal> {
        val smoothedValues = SmoothingUtils.wildersSmooth(
            marketDataList,
            period = period,
            initialSumProvider = { _, subSeries -> // index is not needed if we pass subSeries
                subSeries.mapNotNull { it.price }
                    .fold(BigDecimal.ZERO) { acc, price -> acc + price }
            },
            valueExtractor = { it?.price ?: BigDecimal.ZERO }
        )

        // The generateSignal function expects a list, but we are processing one smoothed value at a time here.
        // The original generateSignal is designed for a list representing a history to derive one signal.
        // This mapping implies we want a signal for *each* smoothed value.
        return smoothedValues.map { smoothedValue ->
            if (smoothedValue == null) Signal.NEUTRAL
            else if (smoothedValue > threshold) Signal.BUY
            else if (smoothedValue < threshold.negate()) Signal.SELL // Assuming symmetric thresholds for SELL
            else Signal.NEUTRAL
        }
    }
}

data class MarketData(
    val timestamp: Long,
    val price: BigDecimal,
    val volume: BigDecimal
)