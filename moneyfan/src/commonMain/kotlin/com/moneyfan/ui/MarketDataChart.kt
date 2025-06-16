package com.moneyfan.ui

import com.moneyfan.indicators.SmoothingUtils
import com.moneyfan.signals.MarketData
import com.moneyfan.signals.Signal
// import com.moneyfan.core.BigDecimal // Assuming MarketData uses ionspin already
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode // For setScale
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
// DateTimeFormatter equivalent will be handled by manual string formatting for now.

class MarketDataChart(
    private val period: Int = 14, // Assuming this might be used later for chart config
    private val threshold: BigDecimal = BigDecimal.parseString("0.5") // Example threshold
) {
    // private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss") // Replace with manual formatting

    fun renderChart(marketData: List<MarketData>, smoothedValues: List<BigDecimal?>, signals: List<Signal>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("\nMarket Data Chart (Timestamp | Price | Smoothed | Signal)")
        stringBuilder.appendLine("=" * 50)

        marketData.forEachIndexed { index, data ->
            val ktInstant = Instant.fromEpochMilliseconds(data.timestamp)
            val timestamp = ktInstant.toLocalDateTime(TimeZone.currentSystemDefault())

            val smoothedValue = smoothedValues.getOrNull(index)
            val signal = signals.getOrNull(index)

            // Manual timestamp formatting (HH:mm:ss)
            val formattedTimestamp = "${timestamp.hour.toString().padStart(2, '0')}:" +
                                     "${timestamp.minute.toString().padStart(2, '0')}:" +
                                     "${timestamp.second.toString().padStart(2, '0')}"

            val priceStr = data.price.setScale(2, RoundingMode.HALF_UP).toString()
            val smoothedStr = smoothedValue?.setScale(2, RoundingMode.HALF_UP)?.toString() ?: "N/A"

            stringBuilder.appendLine("$formattedTimestamp | Price: $priceStr | Smoothed: $smoothedStr | Signal: ${signal ?: "N/A"}")
        }
        stringBuilder.appendLine("=" * 50)
        return stringBuilder.toString()
    }

    private operator fun String.times(n: Int): String = repeat(n)
}