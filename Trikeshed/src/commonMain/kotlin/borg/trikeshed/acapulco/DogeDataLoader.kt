package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.acapulco.model.Kline
import com.ta4k.parsing.KlineCsvParser
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Handles loading and processing DOGE historical data.
 */
class DogeDataLoader {
    companion object {
        private const val DOGE_SYMBOL = "DOGEUSDT"
        private const val DEFAULT_TIMEFRAME = "1h"
        
        /**
         * Loads DOGE historical data for the specified time range.
         * @param startTime Start time in milliseconds
         * @param endTime End time in milliseconds
         * @param timeframe Timeframe (e.g., "1h", "4h", "1d")
         * @return Series of Klines containing the historical data
         */
        fun loadHistoricalData(
            startTime: Long,
            endTime: Long,
            timeframe: String = DEFAULT_TIMEFRAME
        ): Series<Kline> {
            val filePath = getDataFilePath(timeframe)
            val file = File(filePath)
            
            if (!file.exists()) {
                throw IllegalArgumentException("Data file not found: $filePath")
            }
            
            val result = KlineCsvParser.parse(file.reader())
            when (result) {
                is KlineCsvParser.ParseResult.Success -> {
                    val klines = result.klines
                    val filteredKlines = klines.filter { kline: Kline -> 
                        kline.openTimeMillis in startTime..endTime 
                    }.sortedBy { kline: Kline -> kline.openTimeMillis }
                    
                    return Series(filteredKlines)
                }
                is KlineCsvParser.ParseResult.Failure -> {
                    throw IllegalArgumentException("Failed to parse CSV file: ${result.errors.joinToString()}")
                }
            }
        }
        
        /**
         * Gets the file path for DOGE data based on timeframe.
         */
        private fun getDataFilePath(timeframe: String): String {
            return when (timeframe) {
                "1h" -> "data/doge_1h.csv"
                "4h" -> "data/doge_4h.csv"
                "1d" -> "data/doge_1d.csv"
                else -> throw IllegalArgumentException("Unsupported timeframe: $timeframe")
            }
        }
        
        /**
         * Loads the last N periods of DOGE data.
         * @param periods Number of periods to load
         * @param timeframe Timeframe (e.g., "1h", "4h", "1d")
         * @return Series of Klines containing the historical data
         */
        fun loadLastNPeriods(
            periods: Int,
            timeframe: String = DEFAULT_TIMEFRAME
        ): Series<Kline> {
            val endTime = Instant.now().toEpochMilli()
            val startTime = when (timeframe) {
                "1h" -> endTime - (periods * 60 * 60 * 1000)
                "4h" -> endTime - (periods * 4 * 60 * 60 * 1000)
                "1d" -> endTime - (periods * 24 * 60 * 60 * 1000)
                else -> throw IllegalArgumentException("Unsupported timeframe: $timeframe")
            }
            
            return loadHistoricalData(startTime, endTime, timeframe)
        }
    }
} 