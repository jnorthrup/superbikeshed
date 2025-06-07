package borg.trikeshed.util

import borg.trikeshed.core.CoreTensorCursorWithMeta
import borg.trikeshed.core.IOMemento
import borg.trikeshed.core.TensorCursor
import borg.trikeshed.core.TensorSeries
import borg.trikeshed.core.j
import borg.trikeshed.core.TypeMemento
import borg.trikeshed.core.Join
import borg.trikeshed.core.Tensor
import java.io.Reader
import kotlin.collections.MutableList
import kotlin.collections.List

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
    val parsedRowsData: MutableList<List<Double>> = mutableListOf()

    reader.buffered().useLines { lines ->
        lines.drop(1) // Skip header line
            .forEach { line ->
                val fields = line.split(',')
                if (fields.size < 11) {
                    println("Skipping malformed line: $line")
                    return@forEach // Continue to next line
                }
                try {
                    val rowData = listOf<Double>(
                        fields[0].toLong().toDouble(),      // Open_time
                        fields[1].toDouble(),               // Open
                        fields[2].toDouble(),               // High
                        fields[3].toDouble(),               // Low
                        fields[4].toDouble(),               // Close
                        fields[5].toDouble(),               // Volume
                        fields[6].toLong().toDouble(),      // Close_time
                        fields[7].toDouble(),               // Quote_asset_volume
                        fields[8].toInt().toDouble(),       // Number_of_trades
                        fields[9].toDouble(),               // Taker_buy_base_asset_volume
                        fields[10].toDouble()               // Taker_buy_quote_asset_volume
                    )
                    parsedRowsData.add(rowData)
                } catch (e: NumberFormatException) {
                    println("Error parsing line: $line. Error: ${e.message}")
                } catch (e: IndexOutOfBoundsException) {
                    println("Error parsing line (not enough fields after split): $line. Error: ${e.message}")
                }
            }
    }

    if (parsedRowsData.isEmpty()) {
        // Changed klineCursorMeta.size to klineCursorMeta.totalSize
        val emptyData = TensorCursor(0, klineCursorMeta.totalSize) { _, _ -> 0.0 }
        return emptyData j klineCursorMeta
    } else {
        val numRows = parsedRowsData.size
        // Changed klineCursorMeta.size to klineCursorMeta.totalSize
        val numCols = klineCursorMeta.totalSize
        val dataTensor = TensorCursor(numRows, numCols) { rowIndex, colIndex ->
            parsedRowsData[rowIndex][colIndex]
        }
        return dataTensor j klineCursorMeta
    }
}
