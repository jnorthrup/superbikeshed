package moneyfan.models

import kotlin.jvm.JvmInline

/**
 * Represents a trading symbol (e.g., "BTCUSDT", "DOGEUSDT").
 */
value class Symbol(val value: String) {
    override fun toString(): String = value // Optional: for easier logging if needed
}

/**
 * Represents a timestamp in milliseconds since the epoch.
 */
value class TimestampEpochMillis(val value: Long)

/**
 * Represents a price value.
 * Using Double for precision with financial data.
 */
value class Price(val value: Double) {
    operator fun compareTo(other: Price): Int = value.compareTo(other.value)

    companion object {
        /** Represents an undefined or non-applicable price, often used in indicators. */
        val UNDEFINED = Price(Double.NaN)
    }
}

/**
 * Represents a volume value.
 * Using Double for precision.
 */
value class Volume(val value: Double)

/**
 * Represents a single Kline (candlestick) data point.
 *
 * @property timestamp The start time of the kline interval.
 * @property open The opening price.
 * @property high The highest price.
 * @property low The lowest price.
 * @property close The closing price.
 * @property volume The volume traded in the base asset.
 * @property closeTime The end time of the kline interval.
 * @property quoteAssetVolume The volume traded in the quote asset.
 * @property numberOfTrades The number of trades.
 * @property takerBuyBaseAssetVolume The volume of the base asset bought by takers.
 * @property takerBuyQuoteAssetVolume The volume of the quote asset bought by takers.
 * @property ignore An ignored field, often present in Binance data.
 */
data class Kline(
    val timestamp: TimestampEpochMillis,
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price,
    val volume: Volume,
    val closeTime: TimestampEpochMillis, // Standard Binance CSV format includes close time
    val quoteAssetVolume: Volume,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: Volume,
    val takerBuyQuoteAssetVolume: Volume,
    val ignore: String?
) {
    companion object {
        /**
         * Creates a Kline object from a CSV line.
         * Assumes the standard Binance CSV format:
         * open_time,open,high,low,close,volume,close_time,quote_asset_volume,number_of_trades,taker_buy_base_asset_volume,taker_buy_quote_asset_volume,ignore
         *
         * @param csvLine A single line from a Kline CSV file.
         * @return A Kline object.
         * @throws IllegalArgumentException if the CSV line is malformed.
         */
        fun fromCsvLine(csvLine: String): Kline {
            val parts = csvLine.split(',')
            if (parts.size < 12) {
                throw IllegalArgumentException("CSV line does not have enough fields. Expected 12, got ${parts.size}. Line: '$csvLine'")
            }
            try {
                return Kline(
                    timestamp = TimestampEpochMillis(parts[0].toLong()),
                    open = Price(parts[1].toDouble()),
                    high = Price(parts[2].toDouble()),
                    low = Price(parts[3].toDouble()),
                    close = Price(parts[4].toDouble()),
                    volume = Volume(parts[5].toDouble()),
                    closeTime = TimestampEpochMillis(parts[6].toLong()),
                    quoteAssetVolume = Volume(parts[7].toDouble()),
                    numberOfTrades = parts[8].toLong(),
                    takerBuyBaseAssetVolume = Volume(parts[9].toDouble()),
                    takerBuyQuoteAssetVolume = Volume(parts[10].toDouble()),
                    ignore = parts.getOrNull(11) // Handle if ignore field is missing, though Binance usually includes it
                )
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Error parsing number in CSV line: '$csvLine'. Details: ${e.message}", e)
            }
        }
    }
}

/**
 * Represents metadata for a set of Kline data.
 *
 * @property symbol The trading symbol.
 * @property interval The kline interval (e.g., "1m", "1h").
 * @property dataSource The source of the data (e.g., "Binance").
 */
data class KlineMetadata(
    val symbol: Symbol, // Changed from String to Symbol
    val interval: String,
    val dataSource: String
    // Potentially add other fields like firstSeenTime, lastSeenTime if needed
)
