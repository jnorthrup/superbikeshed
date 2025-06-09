package com.moneyfan.demo.data

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TradingData(
    val symbol: String,
    val timeframe: String,
    val candles: List<Candle>,
    val indicators: Map<String, List<Double>> = emptyMap()
)

@Serializable
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val date: Instant get() = Instant.ofEpochMilli(timestamp)
} 