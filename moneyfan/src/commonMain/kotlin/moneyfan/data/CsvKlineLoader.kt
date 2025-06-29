package moneyfan.data

import moneyfan.models.Kline
import moneyfan.models.Price
// Placeholder for TrikeShed's Indexed type. This might need adjustment later.
import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.emptySeries

object CsvKlineLoader {

    /**
     * Parses a CSV string into an Indexed<Kline>.
     * Assumes CSV format: timestamp,open,high,low,close,volume
     * Each line represents a Kline.
     * Header is ignored if present.
     *
     * @param csvData The string containing CSV formatted Kline data.
     * @return An Indexed<Kline> parsed from the CSV data.
     *         Returns an empty series if csvData is empty or only contains a header.
     */
    fun loadKlinesFromCsvString(csvData: String): Indexed<Kline> {
        val lines = csvData.trim().split('\n')
        if (lines.isEmpty()) {
            return emptySeries()
        }

        val klineList = mutableListOf<Kline>()

        // Skip header if it looks like one, very basic check
        val startIndex = if (lines[0].startsWith("timestamp", ignoreCase = true)) 1 else 0

        for (i in startIndex until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val parts = line.split(',')
            if (parts.size == 6) {
                try {
                    val timestamp = parts[0].toLong()
                    val open = Price(parts[1].toDouble())
                    val high = Price(parts[2].toDouble())
                    val low = Price(parts[3].toDouble())
                    val close = Price(parts[4].toDouble())
                    val volume = parts[5].toDouble()
                    klineList.add(Kline(timestamp, open, high, low, close, volume))
                } catch (e: NumberFormatException) {
                    // Log warning or handle error for malformed line
                    // For now, skip malformed lines
                    println("Warning: Skipping malformed CSV line: $line - ${e.message}")
                }
            } else {
                println("Warning: Skipping CSV line with incorrect number of parts: $line")
            }
        }

        if (klineList.isEmpty()) {
            return emptySeries()
        }

        return object : Indexed<Kline> {
            override val a: Int = klineList.size
            override fun b(index: Int): Kline = klineList[index]
        }
    }
}

/**
 * Placeholder for TrikeShed's emptySeries function.
 * This should be replaced by the actual TrikeShed implementation.
 */
private fun <T> emptySeries(): Indexed<T> {
    return object : Indexed<T> {
        override val a: Int = 0
        override fun b(index: Int): T {
            throw IndexOutOfBoundsException("Accessing empty series")
        }
    }
}
