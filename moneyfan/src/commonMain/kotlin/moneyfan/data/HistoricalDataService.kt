package moneyfan.data

import moneyfan.models.Kline
import moneyfan.models.TimestampEpochMillis
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.emptySeries
import moneyfan.io.BinanceDataArchiveReader
import moneyfan.io.FileContentProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime

class HistoricalDataService(
    private val fileContentProvider: FileContentProvider
) {
    // In-memory cache for Kline series. Keyed by file path for simplicity.
    private val klineCache = mutableMapOf<String, Indexed<Kline>>()
    private val cacheMutex = Mutex()

    // Instantiate the archive reader with the provided file content provider.
    private val archiveReader = BinanceDataArchiveReader(fileContentProvider)

    /**
     * Retrieves historical Kline data for a given symbol, interval, and date range.
     *
     * For this initial version:
     * - File path convention: `data/${symbol}_${interval}_${year}.csv`.
     * - Uses `startDate.year` to determine the file to load.
     * - `endDate` is used for filtering data within that year's file.
     * - If the requested range spans multiple years, only data from `startDate.year` is considered.
     * - Implements in-memory caching for loaded data.
     *
     * @param symbol The trading symbol (e.g., "DOGEUSDT").
     * @param interval The kline interval (e.g., "1d").
     * @param startDate The start date of the desired data range.
     * @param endDate The end date of the desired data range.
     * @return A `Indexed<Kline>` containing the requested data, or `emptySeries()` if not found or an error occurs.
     */
    suspend fun getHistoricalKlines(
        symbol: String,
        interval: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Indexed<Kline> {
        // Simplified file path convention: uses only the year from startDate.
        // A more robust solution would handle multi-year ranges or more complex pathing.
        val year = startDate.year
        val filePath = "data/${symbol}_${interval}_${year}.csv" // Assumed data directory structure

        // Check cache first
        cacheMutex.withLock {
            if (klineCache.containsKey(filePath)) {
                // Apply date filtering to cached data if needed, as cache stores whole file's series
                val cachedSeries = klineCache[filePath]!!
                if (cachedSeries.isEmpty()) return emptySeries() // Should not happen if correctly cached

                val seriesStartDateMillis = cachedSeries.first().timestamp.value
                val seriesEndDateMillis = cachedSeries.last().timestamp.value

                val requestedStartMillis = startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                val requestedEndMillis = endDate.atTime(LocalTime(23, 59, 59, 999_999_999)).toInstant(TimeZone.UTC).toEpochMilliseconds()

                // If the cached series is already fully within the requested range (e.g. it was loaded for this exact range)
                // or if the cache stores the entire file and we need to filter it now.
                // For simplicity, the current BinanceDataArchiveReader.readKlinesFromCsvWithDateRange already filters.
                // So, if we cache per file path, we are caching the *entire* file's content as a Indexed.
                // The filtering should happen *after* cache retrieval if the cache is per-file.
                // Let's refine: cache the result of readKlinesFromCsv (entire file), then filter.

                // Alternative: cache key includes date range. For now, file path key.
                // If cache is per file path, it contains all data for that file (year).
                // We need to filter this series further.
                val startEpochMillis = startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                val endEpochMillis = endDate.atTime(LocalTime(23, 59, 59, 999_999_999)).toInstant(TimeZone.UTC).toEpochMilliseconds()

                // This filter assumes toList() and toSeries() are acceptable for now
                val filteredFromCache = cachedSeries.toList().filter {
                    it.timestamp.value >= startEpochMillis && it.timestamp.value <= endEpochMillis
                }.toSeries()
                return filteredFromCache
            }
        }

        // If not in cache, check if file exists and load
        if (!fileContentProvider.fileExists(filePath)) {
            return emptySeries()
        }

        // Convert LocalDate to TimestampEpochMillis for the archive reader
        // Start of the day for startDate
        val startTimestamp = TimestampEpochMillis(startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        // End of the day for endDate (23:59:59.999999999)
        val endTimestamp = TimestampEpochMillis(
            endDate.atTime(LocalTime(23, 59, 59, 999_999_999)) // Max time for the day
                .toInstant(TimeZone.UTC)
                .toEpochMilliseconds()
        )

        val klinesSeries = archiveReader.readKlinesFromCsvWithDateRange(
            filePath = filePath,
            startTime = startTimestamp,
            endTime = endTimestamp,
            skipHeader = true // Standard assumption for Binance CSVs
        )

        // Store the loaded (and already date-filtered) series in cache
        // If we want to cache the whole file's content, we should call readKlinesFromCsv instead
        // and then filter. For now, this caches the result of a specific date range query from a file.
        // Let's adjust to cache the entire file's data to make the cache more reusable for different date ranges within the same file.

        // Revised loading and caching strategy:
        // 1. Load WHOLE file if not in cache (keyed by filePath).
        // 2. Filter the loaded/cached series for the specific date range.

        val seriesToCache: Indexed<Kline>
        cacheMutex.withLock {
            // Double check cache in case another coroutine populated it while we were reading file
            if (klineCache.containsKey(filePath)) {
                seriesToCache = klineCache[filePath]!!
            } else {
                // Load the entire file if it's not in cache
                if (!fileContentProvider.fileExists(filePath)) return emptySeries() // Should be caught above, but defensive
                val fullFileSeries = archiveReader.readKlinesFromCsv(filePath, skipHeader = true)
                klineCache[filePath] = fullFileSeries
                seriesToCache = fullFileSeries
            }
        }

        // Now filter the (potentially newly cached or retrieved from cache) full series
        if (seriesToCache.isEmpty()) return emptySeries()

        val finalFilteredSeries = seriesToCache.toList().filter {
            it.timestamp.value >= startTimestamp.value && it.timestamp.value <= endTimestamp.value
        }.toSeries()

        return finalFilteredSeries
    }
}
