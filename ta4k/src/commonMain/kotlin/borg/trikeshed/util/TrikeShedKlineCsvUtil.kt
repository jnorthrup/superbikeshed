package borg.trikeshed.util

import kotlin.jvm.JvmInline

// Type aliases for CSV parsing following TrikeShed patterns
typealias KlineData = DoubleArray
typealias CsvRow = List<String>
typealias ParsedKlineRow = DoubleArray

@JvmInline
value class ColumnName(val value: String)

@JvmInline  
value class ColumnIndex(val value: Int)

// TrikeShed metadata for Kline CSV structure
val klineColumnNames = listOf(
    "Open_time", "Open", "High", "Low", "Close", "Volume", 
    "Close_time", "Quote_asset_volume", "Number_of_trades", 
    "Taker_buy_base_asset_volume", "Taker_buy_quote_asset_volume"
)

val klineColumnCount = klineColumnNames.size

fun parseKlineCsvLine(line: String): ParsedKlineRow? {
    val fields = line.split(',')
    if (fields.size < klineColumnCount) return null
    
    return try {
        doubleArrayOf(
            fields[0].trim().toLong().toDouble(),      // Open_time  
            fields[1].trim().toDouble(),               // Open
            fields[2].trim().toDouble(),               // High
            fields[3].trim().toDouble(),               // Low
            fields[4].trim().toDouble(),               // Close
            fields[5].trim().toDouble(),               // Volume
            fields[6].trim().toLong().toDouble(),      // Close_time
            fields[7].trim().toDouble(),               // Quote_asset_volume
            fields[8].trim().toInt().toDouble(),       // Number_of_trades
            fields[9].trim().toDouble(),               // Taker_buy_base_asset_volume
            fields[10].trim().toDouble()               // Taker_buy_quote_asset_volume
        )
    } catch (e: NumberFormatException) {
        null
    }
}

fun parseKlineCsv(csvText: String): List<ParsedKlineRow> {
    val lines = csvText.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    
    // Skip header line if present
    val dataLines = if (lines.first().contains("Open_time")) lines.drop(1) else lines
    return dataLines.mapNotNull { parseKlineCsvLine(it) }
}

// Helper function to extract specific column data
fun extractColumn(data: List<ParsedKlineRow>, columnIndex: ColumnIndex): KlineData {
    return data.map { row -> row[columnIndex.value] }.toDoubleArray()
}

// Named column extractors for convenience
fun extractOpenTime(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(0))
fun extractOpen(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(1)) 
fun extractHigh(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(2))
fun extractLow(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(3))
fun extractClose(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(4))
fun extractVolume(data: List<ParsedKlineRow>): KlineData = extractColumn(data, ColumnIndex(5))