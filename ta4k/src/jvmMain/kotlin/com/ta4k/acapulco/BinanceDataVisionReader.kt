package borg.trikeshed.acapulco

import borg.trikeshed.acapulco.model.DataBinanceVision
import borg.trikeshed.acapulco.model.Kline
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import java.io.BufferedReader
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * Reads and processes Binance Data Vision archive files.
 * Supports both CSV and compressed formats.
 */
actual class BinanceDataVisionReader {
    actual companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
        
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

            return klines.size j { klines[it] }
        }

        private fun readZipArchive(file: File): Series<Kline> {
            val klines = mutableListOf<Kline>()
            
            ZipInputStream(file.inputStream()).use { zipStream ->
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

            return klines.size j { klines[it] }
        }

        private fun parseKlineCsvLine(line: String): Kline? {
            val parts = line.split(",")
            if (parts.size < 11) return null // Invalid line format

            return try {
                Kline(
                    openTimeMillis = Instant.parse(parts[0]).toEpochMilli(),
                    openPrice = BigDecimal(parts[1]),
                    highPrice = BigDecimal(parts[2]),
                    lowPrice = BigDecimal(parts[3]),
                    closePrice = BigDecimal(parts[4]),
                    volume = BigDecimal(parts[5]),
                    closeTimeMillis = Instant.parse(parts[6]).toEpochMilli(),
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