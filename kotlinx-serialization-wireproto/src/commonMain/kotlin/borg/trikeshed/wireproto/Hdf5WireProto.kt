@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.wireproto

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * HDF5-compatible TrikeShed wire protocol serialization
 * Combines HDF5's scientific data format design with TrikeShed's high-performance type system
 */

// === HDF5-STYLE HIERARCHICAL METADATA ===

enum class Hdf5ObjectType(val id: UByte) {
    GROUP(1u),          // HDF5 Group (container)
    DATASET(2u),        // HDF5 Dataset (data array)
    DATATYPE(3u),       // HDF5 Datatype definition
    DATASPACE(4u),      // HDF5 Dataspace (shape/dimensions)
    ATTRIBUTE(5u),      // HDF5 Attribute (metadata)
    LINK(6u)            // HDF5 Link (reference)
}

enum class Hdf5DataClass(val id: UByte) {
    INTEGER(0u),        // Signed/unsigned integers
    FLOAT(1u),          // Floating point numbers
    TIME(2u),           // Date/time values
    STRING(3u),         // Character strings
    BITFIELD(4u),       // Bit fields
    OPAQUE(5u),         // Opaque byte sequences
    COMPOUND(6u),       // Compound/struct types
    REFERENCE(7u),      // Object/region references
    ENUM(8u),           // Enumerated types
    VLEN(9u),           // Variable-length sequences
    ARRAY(10u)          // Array types
}

enum class CompressionType(val id: UByte) {
    NONE(0u),
    GZIP(1u),
    LZ4(2u),
    ZSTD(3u),
    BZIP2(4u),
    TRIKESHED_NATIVE(255u)  // TrikeShed's native compression
}

// === HDF5-COMPATIBLE METADATA STRUCTURES ===

@JvmInline
value class Hdf5ObjectName(val path: String)

@JvmInline
value class ChunkSize(val bytes: Int)

@JvmInline
value class CompressionLevel(val level: UByte)

/**
 * HDF5-style dataspace describing tensor dimensions and layout
 */
data class Hdf5Dataspace(
    val rank: Int,
    val dimensions: IntArray,
    val maxDimensions: IntArray? = null,  // For extensible datasets
    val chunkDimensions: IntArray? = null
) {
    companion object {
        fun fromTensor(tensor: Tensor<*>): Hdf5Dataspace {
            return Hdf5Dataspace(
                rank = tensor.rank,
                dimensions = tensor.shape,
                maxDimensions = null,
                chunkDimensions = calculateOptimalChunking(tensor.shape)
            )
        }
        
        fun fromSeries(series: Series<*>): Hdf5Dataspace {
            return Hdf5Dataspace(
                rank = 1,
                dimensions = intArrayOf(series.size),
                maxDimensions = intArrayOf(-1), // Unlimited dimension
                chunkDimensions = intArrayOf(minOf(series.size, 1024))
            )
        }
        
        private fun calculateOptimalChunking(shape: IntArray): IntArray {
            // Calculate optimal chunk size (aim for 64KB chunks)
            val targetBytes = 64 * 1024
            val elementSize = 8 // Assume 8 bytes per element
            val targetElements = targetBytes / elementSize
            
            val chunks = IntArray(shape.size)
            var remainingElements = targetElements
            
            for (i in shape.indices.reversed()) {
                chunks[i] = minOf(shape[i], maxOf(1, remainingElements))
                remainingElements /= chunks[i]
            }
            
            return chunks
        }
    }
}

/**
 * HDF5-style datatype description
 */
data class Hdf5Datatype(
    val dataClass: Hdf5DataClass,
    val size: Int,
    val byteOrder: ByteOrder,
    val precision: Int? = null,
    val offset: Int = 0
) {
    enum class ByteOrder(val id: UByte) {
        LITTLE_ENDIAN(0u),
        BIG_ENDIAN(1u),
        NATIVE(2u)
    }
    
    companion object {
        fun fromKotlinType(type: String): Hdf5Datatype {
            return when (type) {
                "Byte", "kotlin.Byte" -> Hdf5Datatype(Hdf5DataClass.INTEGER, 1, ByteOrder.NATIVE)
                "Short", "kotlin.Short" -> Hdf5Datatype(Hdf5DataClass.INTEGER, 2, ByteOrder.NATIVE)
                "Int", "kotlin.Int" -> Hdf5Datatype(Hdf5DataClass.INTEGER, 4, ByteOrder.NATIVE)
                "Long", "kotlin.Long" -> Hdf5Datatype(Hdf5DataClass.INTEGER, 8, ByteOrder.NATIVE)
                "Float", "kotlin.Float" -> Hdf5Datatype(Hdf5DataClass.FLOAT, 4, ByteOrder.NATIVE, 24)
                "Double", "kotlin.Double" -> Hdf5Datatype(Hdf5DataClass.FLOAT, 8, ByteOrder.NATIVE, 53)
                "String", "kotlin.String" -> Hdf5Datatype(Hdf5DataClass.STRING, -1, ByteOrder.NATIVE)
                "Boolean", "kotlin.Boolean" -> Hdf5Datatype(Hdf5DataClass.BITFIELD, 1, ByteOrder.NATIVE)
                else -> Hdf5Datatype(Hdf5DataClass.OPAQUE, -1, ByteOrder.NATIVE)
            }
        }
    }
}

/**
 * Enhanced IoMemento with HDF5-style hierarchical metadata
 */
data class Hdf5WireMemento(
    val objectName: Hdf5ObjectName,
    val objectType: Hdf5ObjectType,
    val datatype: Hdf5Datatype?,
    val dataspace: Hdf5Dataspace?,
    val attributes: Map<String, Any>,
    val compressionType: CompressionType = CompressionType.NONE,
    val compressionLevel: CompressionLevel = CompressionLevel(6u),
    val chunkSize: ChunkSize? = null,
    val parent: Hdf5ObjectName? = null,
    val children: List<Hdf5ObjectName> = emptyList(),
    
    // Original TrikeShed fields
    val name: String?,
    val type: String?,
    val width: Int?,
    val nullable: Boolean?,
    val encoding: String?,
    val format: String?,
    val mementoType: MementoType,
    val serializedData: UByteArray?
) {
    companion object {
        fun fromIoMemento(
            memento: borg.trikeshed.isam.meta.IOMemento,
            objectPath: String = "/",
            mementoType: MementoType = MementoType.DATA
        ): Hdf5WireMemento {
            val datatype = memento.type?.let { Hdf5Datatype.fromKotlinType(it) }
            val dataspace = memento.width?.let { 
                Hdf5Dataspace(rank = 1, dimensions = intArrayOf(it))
            }
            
            return Hdf5WireMemento(
                objectName = Hdf5ObjectName(objectPath),
                objectType = Hdf5ObjectType.DATASET,
                datatype = datatype,
                dataspace = dataspace,
                attributes = mapOf(
                    "nullable" to (memento.nullable ?: true),
                    "encoding" to (memento.encoding ?: "utf-8"),
                    "format" to (memento.format ?: "native")
                ),
                compressionType = CompressionType.GZIP,
                compressionLevel = CompressionLevel(6u),
                chunkSize = null,
                parent = if (objectPath.contains('/')) Hdf5ObjectName(objectPath.substringBeforeLast('/')) else null,
                children = emptyList(),
                name = memento.name,
                type = memento.type,
                width = memento.width,
                nullable = memento.nullable,
                encoding = memento.encoding,
                format = memento.format,
                mementoType = mementoType,
                serializedData = null
            )
        }
        
        fun createGroup(path: String, children: List<String> = emptyList()): Hdf5WireMemento {
            return Hdf5WireMemento(
                objectName = Hdf5ObjectName(path),
                objectType = Hdf5ObjectType.GROUP,
                datatype = null,
                dataspace = null,
                attributes = mapOf("created" to System.currentTimeMillis()),
                children = children.map { Hdf5ObjectName(it) },
                parent = if (path.contains('/')) Hdf5ObjectName(path.substringBeforeLast('/')) else null,
                name = path.substringAfterLast('/'),
                type = "group",
                width = null,
                nullable = false,
                encoding = null,
                format = "hdf5",
                mementoType = MementoType.METADATA,
                serializedData = null
            )
        }
        
        fun fromTensor(tensor: Tensor<*>, path: String, elementType: String): Hdf5WireMemento {
            return Hdf5WireMemento(
                objectName = Hdf5ObjectName(path),
                objectType = Hdf5ObjectType.DATASET,
                datatype = Hdf5Datatype.fromKotlinType(elementType),
                dataspace = Hdf5Dataspace.fromTensor(tensor),
                attributes = mapOf(
                    "rank" to tensor.rank,
                    "shape" to tensor.shape.toList(),
                    "total_size" to tensor.totalSize
                ),
                compressionType = CompressionType.LZ4,
                compressionLevel = CompressionLevel(4u),
                chunkSize = ChunkSize(64 * 1024),
                parent = if (path.contains('/')) Hdf5ObjectName(path.substringBeforeLast('/')) else null,
                children = emptyList(),
                name = path.substringAfterLast('/'),
                type = "tensor",
                width = tensor.totalSize,
                nullable = false,
                encoding = "binary",
                format = "hdf5_tensor",
                mementoType = MementoType.DATA,
                serializedData = null
            )
        }
        
        fun fromSeries(series: Series<*>, path: String, elementType: String): Hdf5WireMemento {
            return Hdf5WireMemento(
                objectName = Hdf5ObjectName(path),
                objectType = Hdf5ObjectType.DATASET,
                datatype = Hdf5Datatype.fromKotlinType(elementType),
                dataspace = Hdf5Dataspace.fromSeries(series),
                attributes = mapOf(
                    "size" to series.size,
                    "element_type" to elementType
                ),
                compressionType = CompressionType.ZSTD,
                compressionLevel = CompressionLevel(3u),
                chunkSize = ChunkSize(32 * 1024),
                parent = if (path.contains('/')) Hdf5ObjectName(path.substringBeforeLast('/')) else null,
                children = emptyList(),
                name = path.substringAfterLast('/'),
                type = "series",
                width = series.size,
                nullable = false,
                encoding = "binary",
                format = "hdf5_series",
                mementoType = MementoType.DATA,
                serializedData = null
            )
        }
    }
}

// === HDF5 WIRE PROTOCOL SERIALIZER ===

/**
 * HDF5-compatible wire protocol serializer for TrikeShed
 * Implements HDF5's hierarchical format with TrikeShed optimizations
 */
object Hdf5WireSerializer {
    
    // HDF5 file signature
    private val HDF5_SIGNATURE = ubyteArrayOf(0x89u, 0x48u, 0x44u, 0x46u, 0x0Du, 0x0Au, 0x1Au, 0x0Au)
    
    /**
     * Serialize TrikeShed data structures to HDF5-compatible format
     */
    fun <T> serializeToHdf5(data: T, metadata: Hdf5WireMemento): UByteArray {
        return when (data) {
            is Tensor<*> -> serializeTensor(data, metadata)
            is Series<*> -> serializeSeries(data, metadata)
            is borg.trikeshed.isam.meta.IOMemento -> serializeIoMemento(data, metadata)
            else -> serializeGeneric(data, metadata)
        }
    }
    
    /**
     * Deserialize HDF5-compatible format back to TrikeShed structures
     */
    inline fun <reified T> deserializeFromHdf5(data: UByteArray): Pair<T, Hdf5WireMemento> {
        val reader = Hdf5Reader(data)
        val metadata = reader.readMetadata()
        val payload = reader.readPayload<T>()
        return payload to metadata
    }
    
    /**
     * Serialize Tensor<T> with HDF5 multidimensional array format
     */
    private fun serializeTensor(tensor: Tensor<*>, metadata: Hdf5WireMemento): UByteArray {
        return buildHdf5Message {
            writeHdf5Header()
            writeDataspace(metadata.dataspace!!)
            writeDatatype(metadata.datatype!!)
            writeCompressionInfo(metadata.compressionType, metadata.compressionLevel)
            
            // Serialize tensor data with optimal chunking
            val chunks = metadata.dataspace.chunkDimensions ?: tensor.shape
            writeTensorData(tensor, chunks, metadata.compressionType)
        }
    }
    
    /**
     * Serialize Series<T> with HDF5 1D array format
     */
    private fun serializeSeries(series: Series<*>, metadata: Hdf5WireMemento): UByteArray {
        return buildHdf5Message {
            writeHdf5Header()
            writeDataspace(metadata.dataspace!!)
            writeDatatype(metadata.datatype!!)
            writeCompressionInfo(metadata.compressionType, metadata.compressionLevel)
            
            // Serialize series data with chunking for large datasets
            val chunkSize = metadata.chunkSize?.bytes ?: (32 * 1024)
            writeSeriesData(series, chunkSize, metadata.compressionType)
        }
    }
    
    /**
     * Serialize IoMemento with full HDF5 metadata
     */
    private fun serializeIoMemento(memento: borg.trikeshed.isam.meta.IOMemento, metadata: Hdf5WireMemento): UByteArray {
        return buildHdf5Message {
            writeHdf5Header()
            writeAttributes(metadata.attributes)
            writeIoMementoData(memento)
        }
    }
    
    /**
     * Generic serialization for other data types
     */
    private fun serializeGeneric(data: Any, metadata: Hdf5WireMemento): UByteArray {
        return buildHdf5Message {
            writeHdf5Header()
            writeGenericData(data, metadata)
        }
    }
}

// === HDF5 MESSAGE BUILDER ===

/**
 * HDF5-compatible message builder with chunking and compression support
 */
class Hdf5MessageBuilder {
    private val buffer = mutableListOf<UByte>()
    
    fun writeHdf5Header() {
        // Write HDF5 signature
        Hdf5WireSerializer.HDF5_SIGNATURE.forEach { buffer.add(it) }
        
        // Write version info
        buffer.add(2u) // Superblock version
        buffer.add(0u) // Free space version
        buffer.add(0u) // Root group version
        buffer.add(0u) // Reserved
    }
    
    fun writeDataspace(dataspace: Hdf5Dataspace) {
        buffer.add(Hdf5ObjectType.DATASPACE.id)
        writeVarInt(dataspace.rank)
        dataspace.dimensions.forEach { writeFixed32(it) }
        dataspace.maxDimensions?.forEach { writeFixed32(it) }
        dataspace.chunkDimensions?.forEach { writeFixed32(it) }
    }
    
    fun writeDatatype(datatype: Hdf5Datatype) {
        buffer.add(Hdf5ObjectType.DATATYPE.id)
        buffer.add(datatype.dataClass.id)
        writeFixed32(datatype.size)
        buffer.add(datatype.byteOrder.id)
        writeFixed32(datatype.precision ?: 0)
        writeFixed32(datatype.offset)
    }
    
    fun writeCompressionInfo(type: CompressionType, level: CompressionLevel) {
        buffer.add(type.id)
        buffer.add(level.level)
    }
    
    fun writeAttributes(attributes: Map<String, Any>) {
        writeVarInt(attributes.size)
        attributes.forEach { (key, value) ->
            writeString(key)
            writeAttributeValue(value)
        }
    }
    
    fun writeTensorData(tensor: Tensor<*>, chunkDims: IntArray, compression: CompressionType) {
        // Implementation would serialize tensor data with chunking
        // For now, placeholder
        writeString("TENSOR_DATA_PLACEHOLDER")
    }
    
    fun writeSeriesData(series: Series<*>, chunkSize: Int, compression: CompressionType) {
        // Implementation would serialize series data with chunking
        // For now, placeholder
        writeString("SERIES_DATA_PLACEHOLDER")
    }
    
    fun writeIoMementoData(memento: borg.trikeshed.isam.meta.IOMemento) {
        writeOptionalString(memento.name)
        writeOptionalString(memento.type)
        writeOptionalInt(memento.width)
        writeOptionalBoolean(memento.nullable)
        writeOptionalString(memento.encoding)
        writeOptionalString(memento.format)
    }
    
    fun writeGenericData(data: Any, metadata: Hdf5WireMemento) {
        writeString(data.toString()) // Simplified for now
    }
    
    private fun writeAttributeValue(value: Any) {
        when (value) {
            is String -> {
                buffer.add(Hdf5DataClass.STRING.id)
                writeString(value)
            }
            is Int -> {
                buffer.add(Hdf5DataClass.INTEGER.id)
                writeFixed32(value)
            }
            is Long -> {
                buffer.add(Hdf5DataClass.INTEGER.id)
                writeFixed64(value)
            }
            is Boolean -> {
                buffer.add(Hdf5DataClass.BITFIELD.id)
                buffer.add(if (value) 1u else 0u)
            }
            is List<*> -> {
                buffer.add(Hdf5DataClass.ARRAY.id)
                writeVarInt(value.size)
                value.forEach { writeAttributeValue(it!!) }
            }
            else -> {
                buffer.add(Hdf5DataClass.OPAQUE.id)
                writeString(value.toString())
            }
        }
    }
    
    // Utility methods from WireMessageBuilder
    private fun writeVarInt(value: Int) {
        var v = value
        while (v >= 0x80) {
            buffer.add(((v and 0x7F) or 0x80).toUByte())
            v = v ushr 7
        }
        buffer.add(v.toUByte())
    }
    
    private fun writeFixed64(value: Long) {
        repeat(8) { i ->
            buffer.add(((value shr (i * 8)) and 0xFF).toUByte())
        }
    }
    
    private fun writeFixed32(value: Int) {
        repeat(4) { i ->
            buffer.add(((value shr (i * 8)) and 0xFF).toUByte())
        }
    }
    
    private fun writeString(str: String) {
        val bytes = str.encodeToByteArray()
        writeVarInt(bytes.size)
        bytes.forEach { buffer.add(it.toUByte()) }
    }
    
    private fun writeOptionalString(str: String?) {
        if (str != null) {
            buffer.add(1u) // Present marker
            writeString(str)
        } else {
            buffer.add(0u) // Null marker
        }
    }
    
    private fun writeOptionalInt(value: Int?) {
        if (value != null) {
            buffer.add(1u)
            writeFixed32(value)
        } else {
            buffer.add(0u)
        }
    }
    
    private fun writeOptionalBoolean(value: Boolean?) {
        if (value != null) {
            buffer.add(1u)
            buffer.add(if (value) 1u else 0u)
        } else {
            buffer.add(0u)
        }
    }
    
    fun build(): UByteArray = buffer.toUByteArray()
}

inline fun buildHdf5Message(block: Hdf5MessageBuilder.() -> Unit): UByteArray {
    val builder = Hdf5MessageBuilder()
    builder.block()
    return builder.build()
}

// === HDF5 READER ===

/**
 * HDF5-compatible data reader for deserializing TrikeShed structures
 */
class Hdf5Reader(private val data: UByteArray) {
    private var position = 0
    
    fun readMetadata(): Hdf5WireMemento {
        // Verify HDF5 signature
        val signature = data.sliceArray(0..7)
        require(signature.contentEquals(Hdf5WireSerializer.HDF5_SIGNATURE)) {
            "Invalid HDF5 signature"
        }
        position = 8
        
        // Read version info
        val versions = data.sliceArray(8..11)
        position = 12
        
        // Read metadata structures
        return readHdf5Metadata()
    }
    
    inline fun <reified T> readPayload(): T {
        // Implementation would read and deserialize payload data
        throw NotImplementedError("HDF5 payload reading not implemented yet")
    }
    
    private fun readHdf5Metadata(): Hdf5WireMemento {
        // Implementation would parse HDF5 metadata structures
        throw NotImplementedError("HDF5 metadata reading not implemented yet")
    }
}