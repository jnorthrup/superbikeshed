package com.ta4k.acapulco

import borg.trikeshed.lib.Indexed
import com.ta4k.core.model.Kline
import java.time.Instant

/**
 * Handles loading of DOGE historical data for backtesting.
 */
class DogeDataLoader {
    companion object {
        const val DOGE_SYMBOL = "DOGE"
        const val DEFAULT_TIMEFRAME = "1h"
    }

    /**
     * Load historical DOGE data for the specified time period.
     * @param startTime Start time for historical data
     * @param endTime End time for historical data
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Series of Klines for the specified period
     */
    suspend fun loadHistoricalData(
        startTime: Instant,
        endTime: Instant,
        timeframe: String = DEFAULT_TIMEFRAME
    ): Indexed<Kline> {
        val filePath = getDataFilePath(timeframe)
        val klines = KlineCsvParser.parseFile(filePath)
        return klines.filter { kline ->
            kline.openTimeMillis >= startTime.toEpochMilli() &&
            kline.openTimeMillis <= endTime.toEpochMilli()
        }.sortedBy { it.openTimeMillis }.toIndexed()
    }

    /**
     * Load the last N periods of DOGE data.
     * @param n Number of periods to load
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Series of Klines for the last N periods
     */
    suspend fun loadLastNPeriods(
        n: Int,
        timeframe: String = DEFAULT_TIMEFRAME
    ): Indexed<Kline> {
        val filePath = getDataFilePath(timeframe)
        val klines = KlineCsvParser.parseFile(filePath)
        return klines.takeLast(n).sortedBy { it.openTimeMillis }.toIndexed()
    }

    private fun getDataFilePath(timeframe: String): String {
        return when (timeframe) {
            "1h" -> "data/doge/klines/1h"
            "4h" -> "data/doge/klines/4h"
            "1d" -> "data/doge/klines/1d"
            else -> throw IllegalArgumentException("Unsupported timeframe: $timeframe")
        }
    }
} 