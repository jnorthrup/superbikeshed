@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.isam

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.min

/**
 * JVM implementation of ISAMCursor using FileChannel for high-performance I/O
 */
actual class ISAMCursor actual constructor(
    internal val fileAccess: FileAccess,
    internal val metadata: ISAMMetadata
) : Cursor {
    
    actual val recordLength: Int = metadata.recordLength
    actual val columnCount: Int = metadata.columnCoords.a  
    actual val rowCount: Int = metadata.rowCount
    
    // Cursor interface implementation
    actual override val a: Int = rowCount
    actual override val b: (Int) -> RowVec = { rowIndex -> getRow(rowIndex) }
    
    internal fun getRow(rowIndex: Int): RowVec {
        require(rowIndex >= 0 && rowIndex < rowCount) { 
            "Row index $rowIndex out of bounds [0, $rowCount)" 
        }
        
        // Read the entire row from file
        val position = rowIndex.toLong() * recordLength
        val rowData = fileAccess.readAt(position, recordLength)
        
        // Create RowVec with column accessors
        return columnCount j { colIndex ->
            val coord = metadata.columnCoords.b(colIndex)
            val start = coord.a
            val end = min(coord.b, rowData.size)
            
            // Extract column data
            val columnData = if (start < rowData.size && end > start) {
                rowData.sliceArray(start until end)
            } else {
                ByteArray(0) // Empty data for invalid ranges
            }
            
            // Parse value based on type
            val type = metadata.columnTypes.b(colIndex)
            val value = parseValue(columnData, type)
            val columnName = metadata.columnNames.b(colIndex)
            
            // Return value with metadata
            value j { createISAMScalar(type, columnName) }
        }
    }
    
    internal fun parseValue(data: ByteArray, type: IOMemento): Any? {
        if (data.isEmpty()) return null
        
        return try {
            when (type) {
                IOMemento.IoInt -> {
                    if (data.size >= 4) {
                        ByteBuffer.wrap(data).int
                    } else null
                }
                IOMemento.IoFloat -> {
                    if (data.size >= 4) {
                        ByteBuffer.wrap(data).float
                    } else null
                }
                IOMemento.IoDouble -> {
                    if (data.size >= 8) {
                        ByteBuffer.wrap(data).double
                    } else null
                }
                IOMemento.IoString -> {
                    val nullIndex = data.indexOf(0)
                    val endIndex = if (nullIndex >= 0) nullIndex else data.size
                    String(data, 0, endIndex, Charsets.UTF_8)
                }
                IOMemento.IoLocalDate -> {
                    if (data.size >= 8) {
                        val epochDays = ByteBuffer.wrap(data).long
                        kotlinx.datetime.LocalDate.fromEpochDays(epochDays.toInt())
                    } else null
                }
            }
        } catch (e: Exception) {
            null // Return null for parse errors
        }
    }
}

/**
 * Open ISAM cursor from file path
 */
actual fun openISAMCursor(path: String): ISAMHandle {
    val metaPath = "$path.meta"
    
    // Read metadata
    val metaContent = Files.readString(Paths.get(metaPath))
    val fileAccess = openFileForReading(path)
    val metadata = parseISAMMetadata(metaContent, fileAccess.size)
    
    // Create cursor
    val cursor = ISAMCursor(fileAccess, metadata)
    
    return cursor j fileAccess
}

/**
 * Write cursor to ISAM format
 */
actual fun Cursor.writeISAM(
    pathname: String,
    defaultVarcharSize: Int,
    varcharSizes: Map<Int, Int>?
) {
    if (a == 0) {
        // Create empty files for empty cursor
        Files.createFile(Paths.get(pathname))
        Files.createFile(Paths.get("$pathname.meta"))
        return
    }
    
    // Get cursor metadata
    val firstRow = at(0)
    val columnCount = firstRow.a
    
    // Extract column information
    val columnTypes = columnCount j { i ->
        firstRow.b(i).b().typeMemento
    }
    
    val columnNames = columnCount j { i ->
        firstRow.b(i).b().columnName ?: "col_$i"
    }
    
    // Calculate coordinates
    val coords = calculateNetworkCoords(columnTypes, defaultVarcharSize, varcharSizes)
    val recordLength = coords.b(coords.a - 1).b
    
    // Generate metadata
    val metaContent = generateISAMMetaContent(coords, columnNames, columnTypes)
    Files.writeString(Paths.get("$pathname.meta"), metaContent)
    
    // Write data file
    val fileAccess = openFileForWriting(pathname)
    
    try {
        for (rowIndex in 0 until a) {
            val row = at(rowIndex)
            val rowBuffer = ByteArray(recordLength)
            
            for (colIndex in 0 until columnCount) {
                val coord = coords.b(colIndex)
                val type = columnTypes.b(colIndex)
                val value = row.b(colIndex).a
                
                val columnData = serializeValue(value, type, coord.span)
                val start = coord.a
                val copyLength = min(columnData.size, coord.span)
                
                // Copy column data to row buffer
                columnData.copyInto(rowBuffer, start, 0, copyLength)
            }
            
            // Write row to file
            val position = rowIndex.toLong() * recordLength
            fileAccess.writeAt(position, rowBuffer)
        }
    } finally {
        fileAccess.close()
    }
}

internal fun serializeValue(value: Any?, type: IOMemento, maxSize: Int): ByteArray {
    return when (type) {
        IOMemento.IoInt -> {
            val buffer = ByteBuffer.allocate(4)
            buffer.putInt((value as? Int) ?: 0)
            buffer.array()
        }
        IOMemento.IoFloat -> {
            val buffer = ByteBuffer.allocate(4)
            buffer.putFloat((value as? Float) ?: 0f)
            buffer.array()
        }
        IOMemento.IoDouble -> {
            val buffer = ByteBuffer.allocate(8)
            buffer.putDouble((value as? Double) ?: 0.0)
            buffer.array()
        }
        IOMemento.IoString -> {
            val str = value?.toString() ?: ""
            val bytes = str.toByteArray(Charsets.UTF_8)
            val result = ByteArray(maxSize)
            val copyLength = min(bytes.size, maxSize - 1) // Leave space for null terminator
            bytes.copyInto(result, 0, 0, copyLength)
            result
        }
        IOMemento.IoLocalDate -> {
            val buffer = ByteBuffer.allocate(8)
            val epochDays = when (value) {
                is kotlinx.datetime.LocalDate -> value.toEpochDays().toLong()
                else -> 0L
            }
            buffer.putLong(epochDays)
            buffer.array()
        }
    }
}