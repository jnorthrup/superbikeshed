@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.isam

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.cursorOf
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.IOMemento
import borg.trikeshed.lib.ColumnTypeMemento
import borg.trikeshed.lib.j

/**
 * Simple ISAM Cursor - File-backed cursor implementation
 * 
 * Simple file-backed cursor for efficient access to large datasets.
 * Focus on simplicity over complex columnar operations.
 */

/**
 * Coordinate range for ISAM column layout
 */
typealias CoordRange = Join<Int, Int>

/**
 * Simple ISAM metadata
 */
data class ISAMMetadata(
    val columnNames: List<String>,
    val columnTypes: List<IOMemento>,
    val rowCount: Int
)

/**
 * Simple ISAM cursor implementation
 */
expect class ISAMCursor(path: String, metadata: ISAMMetadata) : Cursor

/**
 * Open an ISAM cursor from file path
 */
expect fun openISAMCursor(path: String): ISAMCursor

/**
 * Write cursor data to ISAM format
 */
expect fun Cursor.writeISAM(pathname: String, defaultVarcharSize: Int = 255, varcharSizes: Map<Int, Int>? = null)

/**
 * Simple ISAM operations
 */

/**
 * Parse simple ISAM metadata
 */
fun parseISAMMetadata(metaContent: String): ISAMMetadata {
    val lines = metaContent.lines().filter { it.isNotBlank() && !it.startsWith("#") }
    require(lines.size >= 2) { "Invalid ISAM meta file: needs names, types" }
    
    val names = lines[0].split("\\s+".toRegex())
    val typeNames = lines[1].split("\\s+".toRegex())
    
    require(names.size == typeNames.size) {
        "Metadata mismatch: names=${names.size}, types=${typeNames.size}"
    }
    val types = typeNames.map { typeName ->
        when (typeName.uppercase()) {
            "INT" -> IOMemento.IoInt
            "STRING" -> IOMemento.IoString
            "FLOAT" -> IOMemento.IoFloat
            "DOUBLE" -> IOMemento.IoDouble
            else -> IOMemento.IoString
        }
    }
    
    return ISAMMetadata(
        columnNames = names,
        columnTypes = types,
        rowCount = 0 // Will be determined from file
    )
}

/**
 * Generate ISAM metadata content for writing
 */
fun generateISAMMetaContent(
    coords: Indexed<CoordRange>,
    names: Indexed<String>,
    types: Indexed<IOMemento>
): String {
    val coordsStr = (0 until coords.a).joinToString(" ") { i ->
        val coord = coords.b(i)
        "${coord.a} ${coord.b}"
    }
    
    val namesStr = (0 until names.a).joinToString(" ") { i ->
        names.b(i).replace(' ', '_')
    }
    
    val typesStr = (0 until types.a).joinToString(" ") { i ->
        types.b(i).toString()
    }
    
    return listOf(
        "# format: coords WS .. EOL names WS .. EOL TypeMemento WS ..",
        "# last coord pair's second value is the record length",
        coordsStr,
        namesStr,
        typesStr
    ).joinToString("\n") + "\n"
}

/**
 * Calculate column coordinates for network serialization
 */
fun calculateNetworkCoords(
    types: Indexed<IOMemento>,
    defaultVarcharSize: Int = 128,
    varcharSizes: Map<Int, Int>? = null
): Indexed<CoordRange> {
    var offset = 0
    
    return types.a j { i ->
        val type = types.b(i)
        val size = when (type) {
            IOMemento.IoBoolean -> 1
            IOMemento.IoByte -> 1
            IOMemento.IoShort -> 2
            IOMemento.IoInt -> 4
            IOMemento.IoLong -> 8
            IOMemento.IoFloat -> 4
            IOMemento.IoDouble -> 8
            IOMemento.IoChar -> 2
            IOMemento.IoString -> varcharSizes?.get(i) ?: defaultVarcharSize
            IOMemento.IoVarchar -> varcharSizes?.get(i) ?: defaultVarcharSize
            IOMemento.IoLocalDate -> 8
            IOMemento.IoLocalDateTime -> 16
            IOMemento.IoInstant -> 12
            IOMemento.IoNothing -> 0
        }
        
        val start = offset
        offset += size
        start j offset
    }
}

/**
 * Utility extensions
 */

/** Get column span (size) */
val CoordRange.span: Int get() = b - a

/** Create typed scalar for ISAM column */
fun createISAMScalar(type: IOMemento, name: String): ColumnTypeMemento = 
    type j name

/** Create simple cursor from ISAM data for testing */
fun createTestCursor(data: List<List<Any?>>, columnNames: List<String>): Cursor =
    cursorOf(data, columnNames)