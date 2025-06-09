package borg.trikeshed.cursor

import borg.trikeshed.isam.RecordMeta // Assuming RecordMeta's first param is String for name
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`↺`
import borg.trikeshed.lib.ColumnName
import borg.trikeshed.lib.ItemCount

/**
 * Converts a list of CSV strings into a `Cursor` of strings.
 *
 * @param lineList The list of CSV strings, where the first line contains the headers.
 * @return A `Cursor` representing the parsed CSV data.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun simpleCsvCursor(lineList: List<String>): Cursor {
    if (lineList.isEmpty()) {
        // Or return an empty Cursor, depending on desired behavior for empty input
        throw IllegalArgumentException("Input lineList cannot be empty.")
    }
    // Take the first line as headers and split by ',', trim, and wrap in ColumnName
    val headerNames: List<ColumnName> = lineList[0].split(",").map { ColumnName(it.trim()) }
    // When creating RecordMeta, use .name from ColumnName
    val hdrMeta = headerNames.map { RecordMeta(it.name, IOMemento.IoString) }

    // Count of fields, wrapped in ItemCount
    val fieldCount = ItemCount(headerNames.size)

    val lines = lineList.drop(1)
    val lineItemCount = ItemCount(lines.size)
    val lineSegments = arrayOfNulls<UShortArray>(lineItemCount.count)

    return lineItemCount.count j { y -> // Use .count for the Series size
        val line = lines[y]
        // Lazily create line segments
        val lineSegs = lineSegments[y] ?: UShortArray(fieldCount.count).also { proto -> // Use .count
            lineSegments[y] = proto
            var f = 0
            // Find comma positions, store them as UShort character indices
            for ((charIndex, c) in line.withIndex()) {
                if (c == ',') {
                    if (f < proto.size) { // Ensure we don't write out of bounds for malformed CSV
                        proto[f++] = charIndex.toUShort()
                    } else {
                        // Potentially log a warning for malformed CSV (more commas than headers)
                        // For now, we'll stop collecting comma indices if it exceeds header count
                        break
                    }
                }
            }
        }

        fieldCount.count j { x: Int -> // Use .count for the Series size
            // Determine start and end indices for the current field's substring
            val start: Int
            val end: Int

            if (x == 0) { // First field
                start = 0
                end = if (fieldCount.count == 1) line.length else if (lineSegs.isNotEmpty() && x < lineSegs.size && lineSegs[x] > 0u) lineSegs[x].toInt() else line.length
            } else { // Subsequent fields
                // Ensure lineSegs[x-1] is valid before accessing
                if (x -1 >= lineSegs.size || lineSegs[x-1] == 0.toUShort() && x-1 > 0) { // check if previous comma was not found
                     // This case handles if a previous field was empty and no comma was stored for it,
                     // or if line is malformed. Default to empty string for this field.
                    start = if (x < lineSegs.size && lineSegs[x-1] > 0u) lineSegs[x-1].toInt() + 1 else line.length
                    end = start
                } else {
                    start = lineSegs[x - 1].toInt() + 1
                    end = if (x == fieldCount.count - 1) { // Last field
                        line.length
                    } else {
                        if (x < lineSegs.size && lineSegs[x] > 0u) lineSegs[x].toInt() else line.length
                    }
                }
            }
            // Ensure start and end are within line bounds and start <= end
            val safeStart = start.coerceIn(0, line.length)
            val safeEnd = end.coerceIn(safeStart, line.length)

            line.substring(safeStart, safeEnd) j hdrMeta[x].`↺`
        }
    }
}