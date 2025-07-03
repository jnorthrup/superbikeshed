package com.ta4k.parsing

import com.ta4k.core.model.Kline
import java.io.File
import java.io.Reader
import java.math.BigDecimal

/**
 * Object responsible for parsing Kline data from CSV formatted input.
 */
object KlineCsvParser {

    // Expected header columns for validation
    private val EXPECTED_HEADER_COLUMNS = listOf(
        "Open_time", "Open", "High", "Low", "Close", "Volume",
        "Close_time", "Quote_asset_volume", "Number_of_trades",
        "Taker_buy_base_asset_volume", "Taker_buy_quote_asset_volume", "Ignore"
    )

    /**
     * Represents the result of a parsing operation.
     * It can either be a [Success] containing the list of klines,
     * or a [Failure] containing a list of error messages.
     */
    sealed class ParseResult {
        data class Success(val klines: Indexed<Kline>) : ParseResult() // KlineSeries is now Indexed<Kline>
        data class Failure(val errors: List<String>) : ParseResult()
    }

    /**
     * Parses Kline data from the given [Reader].
     *
     * @param reader The reader to source CSV data from.
     * @param strict If true, parsing will fail on the first error encountered.
     *               If false, errors will be collected, and parsing will attempt to continue.
     * @return A [ParseResult]แตก [ParseResult.Success] with the parsed klines or [ParseResult.Failure] with error messages.
     */
    fun parse(reader: Reader, strict: Boolean = false): ParseResult {
        val klines = mutableListOf<Kline>()
        val errors = mutableListOf<String>()
        var lineCount = 0
        var actualHeaders: List<String> = emptyList()


        reader.useLines { lines ->
            val lineIterator = lines.iterator()

            // 1. Validate Header
            if (!lineIterator.hasNext()) {
                errors.add("CSV is empty or could not be read.")
                return ParseResult.Failure(errors)
            }
            lineCount++
            val headerLine = lineIterator.next()
            actualHeaders = headerLine.split(',').map { it.trim() }

            if (actualHeaders != EXPECTED_HEADER_COLUMNS) {
                val errorMsg = "CSV header does not match expected. Expected: '$EXPECTED_HEADER_COLUMNS', Got: '$actualHeaders'"
                errors.add(errorMsg)
                if (strict) return ParseResult.Failure(errors)
                // If not strict, we log the error and attempt to parse anyway, assuming column order.
            }

            // 2. Parse Data Lines
            while (lineIterator.hasNext()) {
                lineCount++
                val line = lineIterator.next()
                if (line.isBlank()) { // Skip blank lines
                    continue
                }

                val fields = line.split(',').map { it.trim() }

                if (fields.size < 11) { // Minimum fields needed for Kline, 12th is 'Ignore'
                    val errorMsg = "Line $lineCount: Malformed - Incorrect number of fields (${fields.size}). Expected at least 11. Content: $line"
                    errors.add(errorMsg)
                    if (strict) return ParseResult.Failure(errors)
                    continue // Skip this line and try next
                }

                try {
                    val kline = Kline(
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
                        // fields[11] (Ignore) is deliberately ignored
                    )
                    klines.add(kline)
                } catch (e: NumberFormatException) {
                    val errorMsg = "Line $lineCount: Malformed - Error parsing number. Content: $line. Error: ${e.message}"
                    errors.add(errorMsg)
                    if (strict) return ParseResult.Failure(errors)
                } catch (e: IndexOutOfBoundsException) {
                    // Should be caught by fields.size check, but as a safeguard
                    val errorMsg = "Line $lineCount: Malformed - Not enough fields. Content: $line. Error: ${e.message}"
                    errors.add(errorMsg)
                    if (strict) return ParseResult.Failure(errors)
                } catch (e: Exception) {
                    val errorMsg = "Line $lineCount: Malformed - Unexpected error. Content: $line. Error: ${e.message}"
                    errors.add(errorMsg)
                    if (strict) return ParseResult.Failure(errors)
                }
            }
        }

        // Determine final result
        // Convert the mutableList to Indexed<Kline>
        val finalKlineSeries = klines.toSeries() // Explicit call to toSeries

        return if (errors.isNotEmpty()) {
            if (strict || finalKlineSeries.size == 0) { // If strict, any error is failure. If not strict but no klines parsed, also failure.
                ParseResult.Failure(errors)
            } else {
                // Not strict, and some klines were parsed despite errors. Return success with klines, errors are available.
                ParseResult.Success(finalKlineSeries)
            }
        } else if (finalKlineSeries.size == 0 && lineCount <=1 && actualHeaders != EXPECTED_HEADER_COLUMNS && actualHeaders.isNotEmpty()) {
            // Special case: only a header line was present AND it was incorrect, and no data lines.
            // Errors list would already contain the header error.
             ParseResult.Failure(errors)
        }
         else if (finalKlineSeries.size == 0 && lineCount <=1 ) {
            // Only header line was present (and it was valid), or file was empty after header check.
            ParseResult.Success(finalKlineSeries) // No data, but no parsing errors for data lines.
        }
        else {
            ParseResult.Success(finalKlineSeries)
        }
    }

    /**
     * Convenience overload to parse Kline data directly from a [File].
     *
     * @param file The CSV file to parse.
     * @param strict If true, parsing will fail on the first error encountered.
     * @return A [ParseResult].
     */
    fun parse(file: File, strict: Boolean = false): ParseResult {
        return try {
            file.bufferedReader().use { reader ->
                parse(reader, strict)
            }
        } catch (e: Exception) {
            ParseResult.Failure(listOf("Failed to read file ${file.path}: ${e.message}"))
        }
    }
}
