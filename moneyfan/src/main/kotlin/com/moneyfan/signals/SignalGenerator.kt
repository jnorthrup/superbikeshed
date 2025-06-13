package com.moneyfan.signals

import com.moneyfan.indicators.SmoothingUtils
import java.math.BigDecimal

enum class Signal {
    BUY, SELL, NEUTRAL
}

class SignalGenerator(
    private val period: Int = 14,
    private val threshold: BigDecimal = BigDecimal("0.5")
) {
    fun generateSignal(smoothedValues: List<BigDecimal?>): Signal {
        val lastValue = smoothedValues.lastOrNull() ?: return Signal.NEUTRAL
        return if (lastValue > threshold) Signal.BUY else Signal.SELL
    }

    fun processMarketData(marketDataList: List<MarketData>): List<Signal> {
        val smoothedValues = SmoothingUtils.wildersSmooth(
            marketDataList,
            period = period,
            initialSumProvider = { index, series ->
                series.subList(index - period + 1, index + 1)
                    .mapNotNull { it.price }
                    .fold(BigDecimal.ZERO) { acc, price -> acc.add(price) }
            },
            valueExtractor = { it?.price ?: BigDecimal.ZERO }
        )

        return smoothedValues.map { generateSignal(listOf(it)) }
    }
}

data class MarketData(
    val timestamp: Long,
    val price: BigDecimal,
    val volume: BigDecimal
) 