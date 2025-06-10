package com.moneyfan.demo.model

import com.ta4k.core.model.Kline

/**
 * JS-compatible version of Kline using Double instead of BigDecimal
 */
data class JsKline(
    val openTimeMillis: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTimeMillis: Long,
    val quoteAssetVolume: Double,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: Double,
    val takerBuyQuoteAssetVolume: Double
)

/**
 * Convert ta4k-core Kline to JS-compatible Kline
 */
fun Kline.toJsKline(): JsKline = JsKline(
    openTimeMillis = openTimeMillis,
    openPrice = openPrice.toDouble(),
    highPrice = highPrice.toDouble(),
    lowPrice = lowPrice.toDouble(),
    closePrice = closePrice.toDouble(),
    volume = volume.toDouble(),
    closeTimeMillis = closeTimeMillis,
    quoteAssetVolume = quoteAssetVolume.toDouble(),
    numberOfTrades = numberOfTrades,
    takerBuyBaseAssetVolume = takerBuyBaseAssetVolume.toDouble(),
    takerBuyQuoteAssetVolume = takerBuyQuoteAssetVolume.toDouble()
)