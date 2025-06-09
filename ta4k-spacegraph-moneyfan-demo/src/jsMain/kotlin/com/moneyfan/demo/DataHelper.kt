package com.moneyfan.demo

import com.moneyfan.demo.model.JsKline
import borg.trikeshed.core.Series

/**
 * Simplified DataHelper for Kotlin/JS environment
 */
object DataHelper {
    fun parseKlineCsv(csvText: String): List<JsKline> {
        val lines = csvText.lines()
        val klines = mutableListOf<JsKline>()
        
        // Skip header line
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val fields = line.split(",")
            if (fields.size < 11) continue
            
            try {
                klines.add(JsKline(
                    openTimeMillis = fields[0].toLong(),
                    openPrice = fields[1].toDouble(),
                    highPrice = fields[2].toDouble(),
                    lowPrice = fields[3].toDouble(),
                    closePrice = fields[4].toDouble(),
                    volume = fields[5].toDouble(),
                    closeTimeMillis = fields[6].toLong(),
                    quoteAssetVolume = fields[7].toDouble(),
                    numberOfTrades = fields[8].toInt(),
                    takerBuyBaseAssetVolume = fields[9].toDouble(),
                    takerBuyQuoteAssetVolume = fields[10].toDouble()
                ))
            } catch (e: Exception) {
                // Skip malformed lines
            }
        }
        return klines
    }

    fun <T> List<T>.toSeries(): Series<T> {
        return ListSeries(this)
    }
}

class ListSeries<T>(private val list: List<T>) : Series<T> {
    override val size: Int get() = list.size
    override fun get(index: Int): T = list[index]
}
