package com.ta4k.acapulco

import com.ta4k.core.model.Kline
import java.io.File
import java.math.BigDecimal

/**
 * Parser for Kline data from CSV files.
 */
object KlineCsvParser {
    /**
     * Parse a CSV file containing kline data.
     * @param filePath Path to the CSV file
     * @return List of parsed Klines
     */
    fun parseFile(filePath: String): List<Kline> {
        val file = File(filePath)
        require(file.exists()) { "File not found: $filePath" }
        
        return file.readLines()
            .drop(1) // Skip header
            .mapNotNull { line -> parseKlineCsvLine(line) }
    }
    
    internal fun parseKlineCsvLine(line: String): Kline? {
        return try {
            val parts = line.split(",")
            require(parts.size >= 11) { "Invalid CSV line format" }
            
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
            null // Skip malformed lines
        }
    }
} 