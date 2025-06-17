package borg.trikeshed.acapulco.model

import java.math.BigDecimal

/**
 * Represents a single kline (candlestick) from Binance.
 * Contains OHLCV data and additional metadata.
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
) 