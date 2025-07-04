package borg.trikeshed.isam

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*

/**
 * ISAM (Indexed Sequential Access Method) Cursor
 * 
 * High-performance file-backed cursor for efficient random access to large datasets.
 * Uses platform-optimized file I/O with zero-copy operations where possible.
 * 
 * Based on the proven columnar ISAM implementation with TrikeShed integration.
 */

/**
 * Handle for ISAM cursor operations
 */
typealias ISAMHandle = Join<Cursor, FileAccess>

/**
 * Driver interface for reading/writing typed data from/to byte buffers
 */
interface CellDriver<BufferType, ValueType> {
    fun read(buffer: BufferType): ValueType
    fun write(buffer: BufferType, value: ValueType)
    val fixedSize: Int
}

/**
 * Coordinate range for column data
 */
typealias CoordRange = Join<Int, Int>

/**
 * ISAM metadata containing schema information
 */
data class ISAMMetadata(
    val recordLength: Int,
    val columnCoords: Indexed<CoordRange>,
    val columnNames: Indexed<String>,
    val columnTypes: Indexed<IOMemento>,
    val rowCount: Int
)

/**
 * Platform-specific ISAM cursor implementation
 */
expect class ISAMCursor(fileAccess: FileAccess, metadata: ISAMMetadata) : Cursor {
    val recordLength: Int
    val columnCount: Int
    val rowCount: Int
    
    override val a: Int
    override val b: (Int) -> RowVec
}

/**
 * Open an ISAM cursor from file path
 */
expect fun openISAMCursor(path: String): ISAMHandle

/**
 * Write cursor data to ISAM format
 */
expect fun Cursor.writeISAM(
    pathname: String,
    defaultVarcharSize: Int = 128,
    varcharSizes: Map<Int, Int>? = null
)

/**
 * Common ISAM operations
 */

/**
 * Parse ISAM metadata from .meta file
 */
fun parseISAMMetadata(metaContent: String, fileSize: Long): ISAMMetadata {
    val lines = metaContent.lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
    
    require(lines.size >= 3) { "Invalid ISAM meta file: needs coords, names, types" }
    
    // Parse coordinates
    val coordPairs = lines[0].split("\\s+".toRegex())
        .mapNotNull { it.toIntOrNull() }
        .chunked(2) { (start, end) -> start j { end } }
    
    val recordLength = coordPairs.lastOrNull()?.b ?: 0
    require(recordLength > 0) { "Invalid record length: $recordLength" }
    
    // Parse names and types
    val names = lines[1].split("\\s+".toRegex())
    val typeNames = lines[2].split("\\s+".toRegex())
    
    require(coordPairs.size == names.size && names.size == typeNames.size) {
        "Metadata mismatch: coords=${coordPairs.size}, names=${names.size}, types=${typeNames.size}"
    }
    
    // Convert type names to IOMemento
    val types = typeNames.map { typeName ->
        when (typeName.uppercase()) {
            "INT", "IOINT" -> IOMemento.IoInt
            "STRING", "IOSTRING" -> IOMemento.IoString
            "FLOAT", "IOFLOAT" -> IOMemento.IoFloat
            "DOUBLE", "IODOUBLE" -> IOMemento.IoDouble
            "LOCALDATE", "IOLOCALDATE" -> IOMemento.IoLocalDate
            else -> IOMemento.IoString // Default fallback
        }
    }
    
    val rowCount = if (recordLength > 0) (fileSize / recordLength).toInt() else 0
    
    return ISAMMetadata(
        recordLength = recordLength,
        columnCoords = coordPairs.size j { i -> coordPairs[i] },
        columnNames = names.size j { i -> names[i] },
        columnTypes = types.size j { i -> types[i] },
        rowCount = rowCount
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
            IOMemento.IoInt -> 4
            IOMemento.IoFloat -> 4
            IOMemento.IoDouble -> 8
            IOMemento.IoLocalDate -> 8 // Store as epoch days (Long)
            IOMemento.IoString -> varcharSizes?.get(i) ?: defaultVarcharSize
        }
        
        val start = offset
        offset += size
        start j { offset }
    }
}

/**
 * Utility extensions
 */

/** Get column span (size) */
val CoordRange.span: Int get() = b - a

/** Create typed scalar for ISAM column */
fun createISAMScalar(type: IOMemento, name: String): Scalar = 
    Scalar(type, name)

/** Create simple cursor from ISAM data for testing */
fun createTestCursor(data: List<List<Any?>>, columnNames: List<String>): Cursor =
    cursorOf(data, columnNames)