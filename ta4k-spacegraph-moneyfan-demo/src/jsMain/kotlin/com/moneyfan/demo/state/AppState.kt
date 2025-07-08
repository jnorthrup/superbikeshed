package com.moneyfan.demo.state

import com.moneyfan.demo.data.TradingData
import com.moneyfan.demo.visualization.ViewMode

data class AppState(
    val data: TradingData? = null,
    val selectedTimeframe: Timeframe = Timeframe.DAILY,
    val selectedIndicators: Set<Indicator> = setOf(Indicator.MOVING_AVERAGE),
    val viewMode: ViewMode = ViewMode.CANDLESTICK
) {
    fun updateData(newData: TradingData) = copy(data = newData)
    fun updateTimeframe(timeframe: Timeframe) = copy(selectedTimeframe = timeframe)
    fun updateIndicators(indicators: Set<Indicator>) = copy(selectedIndicators = indicators)
    fun updateViewMode(mode: ViewMode) = copy(viewMode = mode)
}

enum class Timeframe {
    MINUTE_1, MINUTE_5, MINUTE_15, MINUTE_30,
    HOUR_1, HOUR_4, DAILY, WEEKLY, MONTHLY
}

enum class Indicator {
    MOVING_AVERAGE,
    RSI,
    MACD,
    BOLLINGER_BANDS,
    VOLUME
} 