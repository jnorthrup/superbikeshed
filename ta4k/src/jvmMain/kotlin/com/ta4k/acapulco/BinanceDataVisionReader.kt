package borg.trikeshed.acapulco

import com.ta4k.core.model.Kline
import borg.trikeshed.acapulco.model.DataBinanceVision
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Reads and processes Binance Data Vision archive files.
 * Supports both CSV and compressed formats.
 */
actual class BinanceDataVisionReader {
    actual companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
        private val BASE_URL = "https://data.binance.vision/data/spot"
        
        /**
         * Reads a Binance Data Vision archive file and returns a Series of Klines.
         * @param filePath Path to the archive file
         * @return Series of Klines
         */
        actual fun readArchive(filePath: String): Series<Kline> {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("Archive file not found: $filePath")
            }

            return when {
                filePath.endsWith(".csv") -> readCsvArchive(file)
                filePath.endsWith(".zip") -> readZipArchive(file)
                else -> throw IllegalArgumentException("Unsupported archive format: $filePath")
            }
        }
        
        /**
         * Fetches klines from Binance Data Vision archives for a specific symbol and time range
         */
        actual suspend fun fetchKlines(
            symbol: String,
            interval: String,
            startDate: String?,
            endDate: String?,
            cacheDir: String
        ): Series<Kline> = withContext(Dispatchers.IO) {
            val cachePath = File(cacheDir.replace("~", System.getProperty("user.home")))
            cachePath.mkdirs()
            
            val symbolDir = File(cachePath, "klines/$interval/$symbol")
            symbolDir.mkdirs()
            
            val klineSeries = mutableListOf<Series<Kline>>()
            
            // Determine date range
            val currentDate = if (endDate != null) endDate else {
                val now = java.time.LocalDate.now()
                "${now.year}-${String.format("%02d", now.monthValue)}"
            }
            
            val start = if (startDate != null) startDate else {
                val now = java.time.LocalDate.now().minusMonths(1)
                "${now.year}-${String.format("%02d", now.monthValue)}"
            }
            
            // Generate list of year-months to fetch
            val monthsToFetch = generateMonthRange(start, currentDate)
            
            // Fetch each month
            monthsToFetch.forEach { yearMonth ->
                try {
                    val monthKlines = fetchMonthKlines(symbol, interval, yearMonth, cacheDir)
                    if (monthKlines.size > 0) {
                        klineSeries.add(monthKlines)
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to fetch $symbol $interval for $yearMonth: ${e.message}")
                }
            }
            
            // Combine all series
            if (klineSeries.isNotEmpty()) {
                combineKlineSeries(klineSeries)
            } else {
                Series.of(0) { error("No klines fetched") }
            }
        }
        
        /**
         * Downloads and processes a single month of kline data
         */
        actual suspend fun fetchMonthKlines(
            symbol: String,
            interval: String,
            yearMonth: String,
            cacheDir: String
        ): Series<Kline> = withContext(Dispatchers.IO) {
            val cachePath = File(cacheDir.replace("~", System.getProperty("user.home")))
            val symbolDir = File(cachePath, "klines/$interval/$symbol")
            symbolDir.mkdirs()
            
            val fileName = "${symbol}-${interval}-${yearMonth}.zip"
            val zipFile = File(symbolDir, fileName)
            val csvFile = File(symbolDir, "${symbol}-${interval}-${yearMonth}.csv")
            
            // Check if we already have the CSV file
            if (csvFile.exists()) {
                return@withContext readCsvArchive(csvFile)
            }
            
            // Download zip file if not exists
            if (!zipFile.exists()) {
                val url = "$BASE_URL/monthly/klines/$symbol/$interval/$fileName"
                downloadFile(url, zipFile.absolutePath)
            }
            
            // Extract and process
            if (zipFile.exists()) {
                val extractedKlines = extractZipArchive(zipFile)
                // Save as CSV for future use
                saveKlinesToCsv(extractedKlines, csvFile)
                extractedKlines
            } else {
                Series.of(0) { error("Failed to download $fileName") }
            }
        }
        
        /**
         * Downloads and processes daily kline data for recent periods
         */
        actual suspend fun fetchDailyKlines(
            symbol: String,
            interval: String,
            days: Int,
            cacheDir: String
        ): Series<Kline> = withContext(Dispatchers.IO) {
            val cachePath = File(cacheDir.replace("~", System.getProperty("user.home")))
            val symbolDir = File(cachePath, "klines/$interval/$symbol")
            symbolDir.mkdirs()
            
            val klineSeries = mutableListOf<Series<Kline>>()
            val endDate = java.time.LocalDate.now()
            
            // Fetch last N days
            for (i in 0 until days) {
                val date = endDate.minusDays(i.toLong())
                val dateStr = "${date.year}-${String.format("%02d", date.monthValue)}-${String.format("%02d", date.dayOfMonth)}"
                
                try {
                    val dailyKlines = fetchDailyKlineFile(symbol, interval, dateStr, symbolDir)
                    if (dailyKlines.size > 0) {
                        klineSeries.add(dailyKlines)
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to fetch daily klines for $dateStr: ${e.message}")
                }
            }
            
            if (klineSeries.isNotEmpty()) {
                combineKlineSeries(klineSeries)
            } else {
                Series.of(0) { error("No daily klines fetched") }
            }
        }
        
        /**
         * Combines multiple kline series into a single sorted series
         */
        actual fun combineKlineSeries(klineSeries: List<Series<Kline>>): Series<Kline> {
            if (klineSeries.isEmpty()) {
                return Series.of(0) { error("No kline series to combine") }
            }
            
            if (klineSeries.size == 1) {
                return klineSeries.first()
            }
            
            // Flatten all klines
            val allKlines = mutableListOf<Kline>()
            klineSeries.forEach { series ->
                series.play.forEach { kline ->
                    allKlines.add(kline)
                }
            }
            
            // Sort by open time
            val sortedKlines = allKlines.sortedBy { it.openTimeMillis }
            
            // Remove duplicates (same open time)
            val uniqueKlines = mutableListOf<Kline>()
            var lastOpenTime = -1L
            
            sortedKlines.forEach { kline ->
                if (kline.openTimeMillis != lastOpenTime) {
                    uniqueKlines.add(kline)
                    lastOpenTime = kline.openTimeMillis
                }
            }
            
            return Series.of(uniqueKlines.size) { i -> uniqueKlines[i] }
        }
        
        /**
         * Filters klines by time range
         */
        actual fun filterKlinesByTimeRange(
            klines: Series<Kline>,
            startTime: Long,
            endTime: Long
        ): Series<Kline> {
            val filteredKlines = klines.play.filter { kline ->
                kline.openTimeMillis >= startTime && kline.openTimeMillis <= endTime
            }
            
            return Series.of(filteredKlines.size) { i -> filteredKlines[i] }
        }

        private fun readCsvArchive(file: File): Series<Kline> {
            val klines = mutableListOf<Kline>()
            
            file.bufferedReader().use { reader ->
                // Skip header if present
                val firstLine = reader.readLine()
                if (firstLine?.contains("Open_time") == true) {
                    // Header present, skip it
                } else {
                    // No header, reset reader
                    reader.reset()
                }

                // Process data lines
                reader.forEachLine { line ->
                    parseKlineCsvLine(line)?.let { klines.add(it) }
                }
            }

            return Series.of(klines.size) { i -> klines[i] }
        }

        private fun readZipArchive(file: File): Series<Kline> {
            return extractZipArchive(file)
        }
        
        private fun extractZipArchive(zipFile: File): Series<Kline> {
            val klines = mutableListOf<Kline>()
            
            ZipInputStream(zipFile.inputStream()).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".csv")) {
                        zipStream.bufferedReader().use { reader ->
                            // Skip header if present
                            val firstLine = reader.readLine()
                            if (firstLine?.contains("Open_time") == true) {
                                // Header present, skip it
                            } else {
                                // No header, reset reader
                                reader.reset()
                            }

                            // Process data lines
                            reader.forEachLine { line ->
                                parseKlineCsvLine(line)?.let { klines.add(it) }
                            }
                        }
                    }
                    entry = zipStream.nextEntry
                }
            }

            return Series.of(klines.size) { i -> klines[i] }
        }
        
        private suspend fun downloadFile(url: String, filePath: String) {
            withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    
                    connection.getInputStream().use { input ->
                        File(filePath).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Failed to download $url: ${e.message}", e)
                }
            }
        }
        
        private suspend fun fetchDailyKlineFile(
            symbol: String,
            interval: String,
            dateStr: String,
            symbolDir: File
        ): Series<Kline> {
            val fileName = "${symbol}-${interval}-${dateStr}.zip"
            val zipFile = File(symbolDir, fileName)
            val csvFile = File(symbolDir, "${symbol}-${interval}-${dateStr}.csv")
            
            // Check if we already have the CSV file
            if (csvFile.exists()) {
                return readCsvArchive(csvFile)
            }
            
            // Download zip file if not exists
            if (!zipFile.exists()) {
                val url = "$BASE_URL/daily/klines/$symbol/$interval/$fileName"
                try {
                    downloadFile(url, zipFile.absolutePath)
                } catch (e: Exception) {
                    // Daily files might not exist for all dates, return empty series
                    return Series.of(0) { error("No data for $dateStr") }
                }
            }
            
            // Extract and process
            if (zipFile.exists()) {
                val extractedKlines = extractZipArchive(zipFile)
                // Save as CSV for future use
                saveKlinesToCsv(extractedKlines, csvFile)
                extractedKlines
            } else {
                Series.of(0) { error("No data for $dateStr") }
            }
        }
        
        private fun saveKlinesToCsv(klines: Series<Kline>, csvFile: File) {
            csvFile.bufferedWriter().use { writer ->
                // Write header
                writer.write("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore\n")
                
                // Write data
                klines.play.forEach { kline ->
                    writer.write("${kline.openTimeMillis},${kline.openPrice},${kline.highPrice},${kline.lowPrice},${kline.closePrice},${kline.volume},${kline.closeTimeMillis},${kline.quoteAssetVolume},${kline.numberOfTrades},${kline.takerBuyBaseAssetVolume},${kline.takerBuyQuoteAssetVolume},\n")
                }
            }
        }
        
        private fun generateMonthRange(start: String, end: String): List<String> {
            val months = mutableListOf<String>()
            val startDate = java.time.YearMonth.parse(start)
            val endDate = java.time.YearMonth.parse(end)
            
            var current = startDate
            while (!current.isAfter(endDate)) {
                months.add("${current.year}-${String.format("%02d", current.monthValue)}")
                current = current.plusMonths(1)
            }
            
            return months
        }

        private fun parseKlineCsvLine(line: String): Kline? {
            val parts = line.split(",")
            if (parts.size < 11) return null // Invalid line format

            return try {
                Kline(
                    openTimeMillis = parts[0].toLong(),
                    openPrice = BigDecimal(parts[1]),
                    highPrice = BigDecimal(parts[2]),
                    lowPrice = BigDecimal(parts[3]),
                    closePrice = BigDecimal(parts[4]),
                    volume = BigDecimal(parts[5]),
                    closeTimeMillis = parts[6].toLong(),
                    quoteAssetVolume = BigDecimal(parts[7]),
                    numberOfTrades = parts[8].toInt(),
                    takerBuyBaseAssetVolume = BigDecimal(parts[9]),
                    takerBuyQuoteAssetVolume = BigDecimal(parts[10])
                )
            } catch (e: Exception) {
                null // Skip invalid lines
            }
        }
    }
} 