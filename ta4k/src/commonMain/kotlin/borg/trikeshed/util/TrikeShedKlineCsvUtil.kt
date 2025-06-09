package borg.trikeshed.util

import borg.trikeshed.core.CoreTensorCursorWithMeta
import borg.trikeshed.core.IOMemento
import borg.trikeshed.core.TensorCursor
import borg.trikeshed.core.TensorSeries
import borg.trikeshed.core.j
import borg.trikeshed.core.TypeMemento
import borg.trikeshed.core.Join
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.Series
import borg.trikeshed.core.toSeries
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.α // For alpha transform
import borg.trikeshed.core.get // For Series element access
import borg.trikeshed.core.size // For Series.size
import borg.trikeshed.core.isEmpty // For Series.isEmpty
import borg.trikeshed.core.filter // For Series.filter
import java.io.Reader
// Remove unused list imports if they become unused
// import kotlin.collections.MutableList
// import kotlin.collections.List

// Assuming Tensor an interface like this for context:
// interface Tensor<T> { val list: List<T>; val size: Int; val totalSize: Int /* Added based on usage */ }
// And TensorSeries is a concrete implementation.
// If totalSize is not part of the actual Tensor interface you are using, this will cause a compile error.
// This change is made based on the explicit request to use 'totalSize'.

val klineCursorMeta: Tensor<Join<String, TypeMemento>> = TensorSeries(11) { index ->
    when (index) {
        0 -> "Open_time" j IOMemento.IoLong
        1 -> "Open" j IOMemento.IoDouble
        2 -> "High" j IOMemento.IoDouble
        3 -> "Low" j IOMemento.IoDouble
        4 -> "Close" j IOMemento.IoDouble
        5 -> "Volume" j IOMemento.IoDouble
        6 -> "Close_time" j IOMemento.IoLong
        7 -> "Quote_asset_volume" j IOMemento.IoDouble
        8 -> "Number_of_trades" j IOMemento.IoInt
        9 -> "Taker_buy_base_asset_volume" j IOMemento.IoDouble
        10 -> "Taker_buy_quote_asset_volume" j IOMemento.IoDouble
        else -> throw IndexOutOfBoundsException("Invalid index for klineCursorMeta: $index")
    }
}

fun parseKlineCsv(reader: Reader): CoreTensorCursorWithMeta<Double> {
    println("parseKlineCsv called")

    val allLines = reader.buffered().readLines()
    if (allLines.isEmpty()) {
        println("CSV file is empty.")
        val emptyData = TensorCursor(0, klineCursorMeta.totalSize) { _, _ -> 0.0 }
        return emptyData j klineCursorMeta
    }

    val linesSeries = allLines.drop(1).toSeries() // Skip header line and convert to Series

    val parsedRowsSeries = linesSeries.α { line ->
        val fields = line.split(',')
        // klineCursorMeta.totalSize is 11 based on its definition
        if (fields.size < klineCursorMeta.totalSize) {
            println("Skipping malformed line (not enough fields): $line")
            emptySeries<Double>()
        } else {
            try {
                val rowDataArray = doubleArrayOf(
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
                rowDataArray.toSeries()
            } catch (e: NumberFormatException) {
                println("Error parsing line (number format): $line. Error: ${e.message}")
                emptySeries<Double>()
            } catch (e: IndexOutOfBoundsException) { // Should be less likely if fields.size check is robust
                println("Error parsing line (index out of bounds, possibly after split): $line. Error: ${e.message}")
                emptySeries<Double>()
            }
        }
    }.filter { it.isNotEmpty() } // Filter out rows that resulted in emptySeries due to parsing errors

    if (parsedRowsSeries.isEmpty()) {
        println("No valid data rows found after parsing.")
        val emptyData = TensorCursor(0, klineCursorMeta.totalSize) { _, _ -> 0.0 }
        return emptyData j klineCursorMeta
    } else {
        val numRows = parsedRowsSeries.size
        val numCols = klineCursorMeta.totalSize // All inner series should have this size

        val dataTensor = TensorCursor(numRows, numCols) { rowIndex, colIndex ->
            // It's assumed that after filtering, all series in parsedRowsSeries are valid
            // and have numCols elements. A more robust solution might check parsedRowsSeries[rowIndex].size.
            parsedRowsSeries[rowIndex][colIndex]
        }
        return dataTensor j klineCursorMeta
    }
}
