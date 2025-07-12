package moneyfan

import moneyfan.models.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toIdx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

actual class BinanceDataVisionReader {
    actual companion object {
        internal val DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
        internal val BASE_URL = "https://data.binance.vision/data/spot"

        actual fun readArchive(filePath: String): Indexed<Kline> {
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

        actual suspend fun fetchKlines(
            symbol: String,
            interval: String,
            startDate: String?,
            endDate: String?,
            cacheDir: String
        ): Indexed<Kline> = withContext(Dispatchers.IO) {
            val cachePath = File(cacheDir.replace("~", System.getProperty("user.home")))
            cachePath.mkdirs()

            val symbolDir = File(cachePath, "klines/$interval/$symbol")
            symbolDir.mkdirs()

            val klineIndexed = mutableListOf<Indexed<Kline>>()

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
                    if (monthKlines.component1() > 0) {
                        klineIndexed.add(monthKlines)
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to fetch $symbol $interval for $yearMonth: ${e.message}")
                }
            }

            // Combine all series
            if (klineIndexed.isNotEmpty()) {
                combineKlineSeries(klineIndexed)
            } else {
                0 j { error("No klines fetched") }
            }
        }

        actual suspend fun fetchMonthKlines(
            symbol: String,
            interval: String,
            yearMonth: String,
            cacheDir: String
        ): Indexed<Kline> = withContext(Dispatchers.IO) {
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
                0 j { error("Failed to download $fileName") }
            }
        }

        actual suspend fun fetchDailyKlines(
            symbol: String,
            interval: String,
            days: Int,
            cacheDir: String
        ): Indexed<Kline> = withContext(Dispatchers.IO) {
            val cachePath = File(cacheDir.replace("~", System.getProperty("user.home")))
            val symbolDir = File(cachePath, "klines/$interval/$symbol")
            symbolDir.mkdirs()

            val klineIndexed = mutableListOf<Indexed<Kline>>()
            val endDate = java.time.LocalDate.now()

            // Fetch last N days
            for (i in 0 until days) {
                val date = endDate.minusDays(i.toLong())
                val dateStr = "${date.year}-${String.format("%02d", date.monthValue)}-${String.format("%02d", date.dayOfMonth)}"

                try {
                    val dailyKlines = fetchDailyKlineFile(symbol, interval, dateStr, symbolDir)
                    if (dailyKlines.component1() > 0) {
                        klineIndexed.add(dailyKlines)
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to fetch daily klines for $dateStr: ${e.message}")
                }
            }

            if (klineIndexed.isNotEmpty()) {
                combineKlineSeries(klineIndexed)
            } else {
                0 j { error("No daily klines fetched") }
            }
        }

        actual fun combineKlineSeries(klineIndexed: List<Indexed<Kline>>): Indexed<Kline> {
            if (klineIndexed.isEmpty()) {
                return 0 j { error("No kline series to combine") }
            }

            if (klineIndexed.size == 1) {
                return klineIndexed.first()
            }

            // Flatten all klines
            val allKlines = mutableListOf<Kline>()
            klineIndexed.forEach { series ->
                series.play.forEach { kline ->
                    allKlines.add(kline)
                }
            }

            // Sort by open time
            val sortedKlines = allKlines.sortedBy { it.timestamp.value }

            // Remove duplicates (same open time)
            val uniqueKlines = mutableListOf<Kline>()
            var lastOpenTime = -1L

            sortedKlines.forEach { kline ->
                if (kline.timestamp.value != lastOpenTime) {
                    uniqueKlines.add(kline)
                    lastOpenTime = kline.timestamp.value
                }
            }

            return uniqueKlines.toIdx()
        }

        actual fun filterKlinesByTimeRange(
            klines: Indexed<Kline>,
            startTime: Long,
            endTime: Long
        ): Indexed<Kline> {
            val filteredKlines = klines.play.filter { kline ->
                kline.timestamp.value >= startTime && kline.timestamp.value <= endTime
            }

            return filteredKlines.toIdx()
        }

        internal fun readCsvArchive(file: File): Indexed<Kline> {
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

            return klines.toIdx()
        }

        internal fun readZipArchive(file: File): Indexed<Kline> {
            return extractZipArchive(file)
        }

        internal fun extractZipArchive(zipFile: File): Indexed<Kline> {
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

            return klines.toIdx()
        }

        internal suspend fun downloadFile(url: String, filePath: String) {
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

        internal suspend fun fetchDailyKlineFile(
            symbol: String,
            interval: String,
            dateStr: String,
            symbolDir: File
        ): Indexed<Kline> {
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
                    return 0 j { error("No data for $dateStr") }
                }
            }

            // Extract and process
            if (zipFile.exists()) {
                val extractedKlines = extractZipArchive(zipFile)
                // Save as CSV for future use
                saveKlinesToCsv(extractedKlines, csvFile)
                extractedKlines
            } else {
                0 j { error("No data for $dateStr") }
            }
        }

        internal fun saveKlinesToCsv(klines: Indexed<Kline>, csvFile: File) {
            csvFile.bufferedWriter().use { writer ->
                // Write header
                writer.write("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore\n")

                // Write data
                klines.play.forEach { kline ->
                    writer.write("${kline.timestamp.value},${kline.open.value},${kline.high.value},${kline.low.value},${kline.close.value},${kline.volume.value},${kline.closeTime.value},${kline.quoteAssetVolume.value},${kline.numberOfTrades},${kline.takerBuyBaseAssetVolume.value},${kline.takerBuyQuoteAssetVolume.value},${kline.ignore ?: ""}\n")
                }
            }
        }

        internal fun generateMonthRange(start: String, end: String): List<String> {
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

        internal fun parseKlineCsvLine(line: String): Kline? {
            val parts = line.split(",")
            if (parts.size < 12) return null // Invalid line format

            return try {
                Kline(
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
                    ignore = parts.getOrNull(11)
                )
            } catch (e: Exception) {
                null // Skip invalid lines
            }
        }
    }
} 