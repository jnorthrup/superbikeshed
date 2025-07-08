package com.ta4k.core.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Represents a single candlestick (kline) data point.
 * Contains OHLCV data and additional trading information.
 */
data class Kline(
    val openTimeMillis: Long,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val closePrice: BigDecimal,
    val volume: BigDecimal,
    val closeTimeMillis: Long,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: BigDecimal,
    val takerBuyQuoteAssetVolume: BigDecimal
) {
    val openTime: Instant
        get() = Instant.ofEpochMilli(openTimeMillis)
    
    val closeTime: Instant
        get() = Instant.ofEpochMilli(closeTimeMillis)
    
    fun openTimeAsLocalDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDateTime =
        LocalDateTime.ofInstant(openTime, zoneOffset)
    
    fun closeTimeAsLocalDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDateTime =
        LocalDateTime.ofInstant(closeTime, zoneOffset)
}

/**
 * Represents a trading pair.
 */
data class TradingPair(
    val baseAsset: String,
    val quoteAsset: String
) {
    val symbol: String
        get() = "${baseAsset}${quoteAsset}"
    
    override fun toString(): String = symbol
}
