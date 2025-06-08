package com.example.demo

import com.ta4k.core.model.Kline
import com.ta4k.core.model.BigDecimal // Assuming ta4k.core.model.BigDecimal
import borg.trikeshed.core.Series
import borg.trikeshed.core.j
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Response

/**
 * Fetches kline data from a given CSV URL and parses it into a DSEL Series<Kline>.
 * Intended for Kotlin/JS browser environment.
 *
 * @param csvUrl The URL to fetch the CSV data from.
 * @return A DSEL Series<Kline> if successful, null otherwise.
 */
suspend fun fetchAndParseKlineDataToSeries(csvUrl: String): Series<Kline>? {
    console.log("DataHelper: Fetching kline data from: $csvUrl")
    try {
        val response: Response = window.fetch(csvUrl).await()
        if (!response.ok) {
            console.error("DataHelper: Failed to fetch CSV '$csvUrl': ${response.status} ${response.statusText}")
            return null
        }
        val csvText = response.text().await()
        if (csvText.isBlank()) {
            console.error("DataHelper: Fetched CSV from '$csvUrl' is empty.")
            return null
        }
        console.log("DataHelper: CSV data fetched successfully (${csvText.length} chars). Parsing...")

        val parseResult = KlineCsvParserHelper.parseCsvTextToKlineList(csvText)

        return when (parseResult) {
            is KlineCsvParserHelper.ParseResult.Success -> {
                if (parseResult.klines.isEmpty()) {
                    console.warn("DataHelper: CSV parsed successfully but resulted in zero klines.")
                    0 j { throw IndexOutOfBoundsException("Empty kline series from parser") }
                } else {
                    console.log("DataHelper: Successfully parsed ${parseResult.klines.size} klines.")
                    parseResult.klines.toSeries() // Uses the extension function below
                }
            }
            is KlineCsvParserHelper.ParseResult.Failure -> {
                console.error("DataHelper: Failed to parse Klines from CSV '$csvUrl': ${parseResult.errors.joinToString("\n")}")
                null
            }
        }
    } catch (e: Exception) {
        console.error("DataHelper: Error fetching or parsing kline data from '$csvUrl': ${e.message}", e)
        return null
    }
}

/**
 * Helper object to parse CSV text into a List of Kline objects.
 * This is a simplified parser for the demo. A robust solution might adapt
 * the JVM KlineCsvParser from ta4k or use a dedicated JS CSV library.
 */
object KlineCsvParserHelper {
    sealed class ParseResult {
        data class Success(val klines: List<Kline>) : ParseResult()
        data class Failure(val errors: List<String>) : ParseResult()
    }

    fun parseCsvTextToKlineList(csvText: String): ParseResult {
        val lines = csvText.lines()
        val errors = mutableListOf<String>()
        val klines = mutableListOf<Kline>()

        if (lines.size < 2) { // Header + at least one data line
            return ParseResult.Failure(listOf("CSV has no data lines (only header or empty). Line count: ${lines.size}"))
        }

        // Skip header: lines[0] - assumed to be:
        // Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore
        for (lineNumberIdx in 1 until lines.size) {
            val line = lines[lineNumberIdx]
            if (line.isBlank()) continue

            val fields = line.split(',')
            if (fields.size < 12) { // Expecting 12 fields
                errors.add("Line ${lineNumberIdx + 1}: Malformed - Incorrect number of fields (${fields.size}). Expected 12. Content: $line")
                continue
            }

            try {
                klines.add(Kline(
                    openTimeMillis = fields[0].toLong(),
                    openPrice = BigDecimal(fields[1]),
                    highPrice = BigDecimal(fields[2]),
                    lowPrice = BigDecimal(fields[3]),
                    closePrice = BigDecimal(fields[4]),
                    volume = BigDecimal(fields[5]),
                    closeTimeMillis = fields[6].toLong(),
                    quoteAssetVolume = BigDecimal(fields[7]),
                    numberOfTrades = fields[8].toInt(),
                    takerBuyBaseAssetVolume = BigDecimal(fields[9]),
                    takerBuyQuoteAssetVolume = BigDecimal(fields[10])
                    // fields[11] (Ignore) is ignored by Kline constructor
                ))
            } catch (e: Exception) {
                errors.add("Line ${lineNumberIdx + 1}: Malformed - Error parsing fields. Content: $line. Error: ${e.message}")
            }
        }

        if (klines.isEmpty() && errors.isEmpty() && lines.size > 1) {
             errors.add("No valid kline data found after parsing ${lines.size -1} potential data line(s), but no specific parsing errors occurred. Check CSV content structure.")
        }

        return if (errors.isNotEmpty() && klines.isEmpty()) { // Only return hard failure if no klines AND errors
            ParseResult.Failure(errors)
        } else {
            if (errors.isNotEmpty()) { // Log errors if some klines were still parsed
                 console.warn("DataHelper: Encountered ${errors.size} error(s) during CSV parsing (some lines may have been skipped):
${errors.joinToString("\n")}")
            }
            ParseResult.Success(klines) // Success if any klines parsed, even with some errors
        }
    }
}

/**
 * Extension function to convert a List<T> to a DSEL Series<T>.
 *
 * @receiver List<T> The list to convert.
 * @return Series<T> The DSEL Series representation of the list.
 */
fun <T> List<T>.toSeries(): Series<T> {
    val sourceList = this
    if (sourceList.isEmpty()) {
        // Return a Series of size 0. Accessing its accessor will throw.
        return 0 j { throw IndexOutOfBoundsException("Accessing element in an empty series (created from an empty list).") }
    }
    return sourceList.size j { index -> sourceList[index] }
}
