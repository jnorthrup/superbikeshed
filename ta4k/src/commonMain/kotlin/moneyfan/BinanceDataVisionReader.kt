package moneyfan

import moneyfan.models.Kline
import borg.trikeshed.lib.Indexed

/**
 * Reads and processes Binance Data Vision archive files.
 * Supports both CSV and compressed formats.
 */
expect class BinanceDataVisionReader() {
    companion object {
        /**
         * Reads a Binance Data Vision archive file and returns a Series of Klines.
         * @param filePath Path to the archive file
         * @return Series of Klines
         */
        fun readArchive(filePath: String): Indexed<Kline>
        
        /**
         * Fetches klines from Binance Data Vision archives for a specific symbol and time range
         * @param symbol Trading symbol (e.g., "BTCUSDT")
         * @param interval Time interval (e.g., "1m", "1h", "1d")
         * @param startDate Start date in YYYY-MM format
         * @param endDate End date in YYYY-MM format
         * @param cacheDir Directory to cache downloaded files
         * @return Series of Klines
         */
        suspend fun fetchKlines(
            symbol: String,
            interval: String = "1m",
            startDate: String? = null,
            endDate: String? = null,
            cacheDir: String = "~/mpdata/cache"
        ): Indexed<Kline>
        
        /**
         * Downloads and processes a single month of kline data
         * @param symbol Trading symbol
         * @param interval Time interval
         * @param yearMonth Year and month in YYYY-MM format
         * @param cacheDir Cache directory
         * @return Series of Klines
         */
        suspend fun fetchMonthKlines(
            symbol: String,
            interval: String,
            yearMonth: String,
            cacheDir: String
        ): Indexed<Kline>
        
        /**
         * Downloads and processes daily kline data for recent periods
         * @param symbol Trading symbol
         * @param interval Time interval
         * @param days Number of days to fetch
         * @param cacheDir Cache directory
         * @return Series of Klines
         */
        suspend fun fetchDailyKlines(
            symbol: String,
            interval: String,
            days: Int = 30,
            cacheDir: String = "~/mpdata/cache"
        ): Indexed<Kline>
        
        /**
         * Combines multiple kline series into a single sorted series
         * @param klineIndexed List of kline series to combine
         * @return Combined and sorted Series of Klines
         */
        fun combineKlineSeries(klineIndexed: List<Indexed<Kline>>): Indexed<Kline>
        
        /**
         * Filters klines by time range
         * @param klines Series of klines to filter
         * @param startTime Start time in milliseconds
         * @param endTime End time in milliseconds
         * @return Filtered Series of Klines
         */
        fun filterKlinesByTimeRange(
            klines: Indexed<Kline>,
            startTime: Long,
            endTime: Long
        ): Indexed<Kline>
    }
} 