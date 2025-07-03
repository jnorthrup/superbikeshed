package com.ta4k.core.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Type alias for Unix millisecond timestamps for clarity.
 */
typealias EpochMillis = Long

/**
 * Represents a single candlestick (Kline) data point.
 *
 * @property openTimeMillis The timestamp when the kline opened, in milliseconds since epoch.
 * @property openPrice The opening price for the kline period.
 * @property highPrice The highest price during the kline period.
 * @property lowPrice The lowest price during the kline period.
 * @property closePrice The closing price for the kline period.
 * @property volume The trading volume during the kline period (in base asset).
 * @property closeTimeMillis The timestamp when the kline closed, in milliseconds since epoch.
 * @property quoteAssetVolume The trading volume during the kline period (in quote asset).
 * @property numberOfTrades The number of trades that occurred during the kline period.
 * @property takerBuyBaseAssetVolume The volume of the base asset bought by takers.
 * @property takerBuyQuoteAssetVolume The volume of the quote asset bought by takers.
 */
data class Kline(
    val openTimeMillis: EpochMillis,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val closePrice: BigDecimal,
    val volume: BigDecimal,
    val closeTimeMillis: EpochMillis,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: BigDecimal,
    val takerBuyQuoteAssetVolume: BigDecimal
) {
    /**
     * The opening time as an [Instant].
     */
    val openTime: Instant
        get() = Instant.ofEpochMilli(openTimeMillis)

    /**
     * The closing time as an [Instant].
     */
    val closeTime: Instant
        get() = Instant.ofEpochMilli(closeTimeMillis)

    /**
     * Converts the [openTimeMillis] to a [LocalDateTime] in the specified (or UTC by default) time zone.
     * @param zoneOffset The zone offset to apply, defaults to UTC.
     * @return The [LocalDateTime] representation of the open time.
     */
    fun openLocalDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDateTime =
        LocalDateTime.ofInstant(openTime, zoneOffset)

    /**
     * Converts the [closeTimeMillis] to a [LocalDateTime] in the specified (or UTC by default) time zone.
     * @param zoneOffset The zone offset to apply, defaults to UTC.
     * @return The [LocalDateTime] representation of the close time.
     */
    fun closeLocalDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDateTime =
        LocalDateTime.ofInstant(closeTime, zoneOffset)
}

import borg.trikeshed.lib.Indexed // Import TrikeShed Indexed

/**
 * Type alias for a time series of [Kline] objects, now using TrikeShed's Indexed.
 */
typealias KlineSeries = Indexed<Kline>

/**
 * Represents a trading pair.
 *
 * @property baseAsset The base asset symbol (e.g., "BTC").
 * @property quoteAsset The quote asset symbol (e.g., "USDT").
 */
data class TradingPair(
    val baseAsset: String,
    val quoteAsset: String
) {
    override fun toString(): String = "$baseAsset$quoteAsset"

    companion object {
        /**
         * Creates a [TradingPair] from a concatenated symbol string (e.g., "BTCUSDT").
         * It assumes common quote asset suffixes like USDT, BUSD, BTC, ETH, EUR, USD.
         * This is a heuristic and might need refinement for more complex pair notations.
         * @param symbol The combined trading symbol.
         * @return A [TradingPair] instance, or null if parsing fails.
         */
        fun fromSymbol(symbol: String): TradingPair? {
            val commonQuoteAssets = listOf("USDT", "BUSD", "USDC", "BTC", "ETH", "EUR", "USD", "BNB", "TRY", "GBP", "AUD", "BRL") // Add more as needed
            commonQuoteAssets.forEach { quote ->
                if (symbol.endsWith(quote) && symbol.length > quote.length) {
                    val base = symbol.removeSuffix(quote)
                    if (base.isNotEmpty()) {
                        return TradingPair(base, quote)
                    }
                }
            }
            // Fallback for 3-letter quote assets if not in common list (e.g., less common ones)
            if (symbol.length > 3) {
                 val potentialQuote = symbol.takeLast(3)
                 val potentialBase = symbol.dropLast(3)
                 if (potentialBase.isNotEmpty() && potentialQuote.all { it.isLetter() && it.isUpperCase() }) {
                     return TradingPair(potentialBase, potentialQuote)
                 }
            }
            return null // Could not determine pair
        }
    }
}

/**
 * Represents a [Kline] along with its market context ([TradingPair] and interval).
 *
 * @property pair The trading pair for which this kline was recorded.
 * @property interval The time interval of the kline (e.g., "1m", "1h", "1d").
 * @property kline The actual kline data.
 */
data class MarketKline(
    val pair: TradingPair,
    val interval: String,
    val kline: Kline
)
