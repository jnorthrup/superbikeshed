package moneyfan.io

import moneyfan.models.Kline
import moneyfan.models.KlineMetadata
import moneyfan.models.Symbol // Added import for Symbol
import moneyfan.models.TimestampEpochMillis
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.toSeries
import moneyfan.trikeshed.toList // For converting Indexed to List for filtering
import moneyfan.trikeshed.emptySeries // For returning empty series
// kotlinx.coroutines imports are not directly used here anymore for file ops,
// but might be relevant if the FileContentProvider implementation uses them.
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.withContext

class BinanceDataArchiveReader(
    internal val fileContentProvider: FileContentProvider // Changed from FileContentReader lambda
) {

    /**
     * Reads Kline data from a CSV file.
     *
     * @param filePath The path to the CSV file.
     * @param skipHeader Whether to skip the first line (header) of the CSV.
     * @return A Indexed of Kline objects.
     * @throws Exception if there's an error reading or parsing the file.
     */
    suspend fun readKlinesFromCsv(
        filePath: String,
        skipHeader: Boolean = true
    ): Indexed<Kline> {
        // fileContentProvider.fileExists(filePath) could be checked here if desired,
        // but readFileLines should ideally throw if file not found.
        // Depending on FileContentProvider's contract.
        val lines = fileContentProvider.readFileLines(filePath) // Changed from fileReader(filePath)

        if (lines.isEmpty() || (skipHeader && lines.size == 1 && lines.first().isNotBlank())) {
            // Handle empty file or file with only a header
            return emptySeries()
        }

        val klines = mutableListOf<Kline>()

        val dataLines = if (skipHeader && lines.isNotEmpty()) {
            lines.drop(1)
        } else {
            lines
        }

        for (line in dataLines) {
            if (line.isBlank()) continue // Skip empty lines
            try {
                klines.add(Kline.fromCsvLine(line))
            } catch (e: IllegalArgumentException) {
                // Log or handle per line parsing errors as needed
                // For now, we'll rethrow, but in a robust system, you might collect errors
                // or skip problematic lines with logging.
                throw IllegalArgumentException("Error parsing CSV line in file '$filePath': $line. Error: ${e.message}", e)
            }
        }
        return klines.toSeries()
    }

    /**
     * Reads Kline data from a CSV file and filters it by a date range.
     *
     * @param filePath The path to the CSV file.
     * @param startTime The start of the date range (inclusive).
     * @param endTime The end of the date range (inclusive).
     * @param skipHeader Whether to skip the first line (header) of the CSV.
     * @return A Indexed of Kline objects within the specified date range.
     */
    suspend fun readKlinesFromCsvWithDateRange(
        filePath: String,
        startTime: TimestampEpochMillis,
        endTime: TimestampEpochMillis,
        skipHeader: Boolean = true
    ): Indexed<Kline> {
        if (startTime.value > endTime.value) {
            return emptySeries() // Return empty series if range is invalid
        }
        // Assuming readKlinesFromCsv will handle file not found by throwing or returning empty.
        // If FileContentProvider.fileExists is cheap, could check filePath existence first.
        val allKlines: Indexed<Kline> = readKlinesFromCsv(filePath, skipHeader)
        if (allKlines.isEmpty()) {
            return emptySeries()
        }

        val filteredList = allKlines.toList().filter { it.timestamp.value >= startTime.value && it.timestamp.value <= endTime.value }
        return filteredList.toSeries()
    }

    /**
     * Parses Kline metadata from a JSON string.
     * This is a simplified placeholder for a real JSON parser.
     */
    internal fun parseKlineMetadataJson(jsonString: String): KlineMetadata {
        val symbolString = extractJsonValue(jsonString, "symbol")
            ?: throw IllegalArgumentException("Missing 'symbol' in metadata JSON.")
        val interval = extractJsonValue(jsonString, "interval")
            ?: throw IllegalArgumentException("Missing 'interval' in metadata JSON.")
        val dataSource = extractJsonValue(jsonString, "dataSource")
            ?: throw IllegalArgumentException("Missing 'dataSource' in metadata JSON.")

        return KlineMetadata(Symbol(symbolString), interval, dataSource) // Changed to use Symbol(symbolString)
    }

    internal fun extractJsonValue(jsonString: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(jsonString)?.groupValues?.get(1)
    }

    /**
     * Reads Kline metadata from a JSON file.
     *
     * @param metadataFilePath The path to the JSON metadata file.
     * @return A KlineMetadata object.
     * @throws Exception if there's an error reading or parsing the file.
     */
    suspend fun readKlineMetadata(metadataFilePath: String): KlineMetadata {
        val lines = fileContentProvider.readFileLines(metadataFilePath) // Changed from fileReader(metadataFilePath)
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Metadata file is empty: $metadataFilePath")
        }
        val jsonString = lines.joinToString("\n")
        try {
            return parseKlineMetadataJson(jsonString)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Error parsing metadata file '$metadataFilePath'. Error: ${e.message}", e)
        }
    }

    // Companion object might not be needed anymore if instantiation is direct
    // or handled by a DI framework. For now, it's harmless.
    companion object {
        // Example of how one might construct this with a real async reader
        // fun createWithDefaultReader(provider: FileContentProvider): BinanceDataArchiveReader {
        //     return BinanceDataArchiveReader(provider)
        // }
    }
}

// Test comments updated to reflect FileContentProvider usage for mocking.
// Unit tests for `BinanceDataArchiveReader.readKlinesFromCsv` would now require a mock FileContentProvider.
// ... (other test comments remain largely the same but would operate on Indexed and mock provider)
// Unit tests for `readKlinesFromCsvWithDateRange` also require mock FileContentProvider.
// ...
// Unit tests for `readKlineMetadata` also require mock FileContentProvider and would verify KlineMetadata.symbol is a Symbol object.
// ...
