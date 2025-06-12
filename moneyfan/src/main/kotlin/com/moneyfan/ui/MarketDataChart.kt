package com.moneyfan.ui

import com.moneyfan.indicators.SmoothingUtils
import com.moneyfan.signals.MarketData
import com.moneyfan.signals.Signal
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MarketDataChart(
    private val period: Int = 14,
    private val threshold: BigDecimal = BigDecimal("0.5")
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun renderChart(marketData: List<MarketData>, smoothedValues: List<BigDecimal?>, signals: List<Signal>) {
        println("\nMarket Data Chart")
        println("=" * 50)
        
        marketData.forEachIndexed { index, data ->
            val timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(data.timestamp),
                ZoneId.systemDefault()
            )
            val smoothedValue = smoothedValues.getOrNull(index)
            val signal = signals.getOrNull(index)
            
            println("${dateFormatter.format(timestamp)} | " +
                   "Price: ${data.price.setScale(2)} | " +
                   "Smoothed: ${smoothedValue?.setScale(2) ?: "N/A"} | " +
                   "Signal: ${signal ?: "N/A"}")
        }
        
        println("=" * 50)
    }

    private operator fun String.times(n: Int): String = repeat(n)
} 