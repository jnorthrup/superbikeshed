@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.wireproto

import borg.trikeshed.lib.*
import borg.trikeshed.lib.bridge.toSeries
import kotlin.jvm.JvmInline

/**
 * TrikeShed native wire protocol - focused on IoMemento and actual use cases
 */

// === SIMPLE WIRE PROTOCOL TYPES ===

@JvmInline
value class WireVersion(val version: UByte)

@JvmInline
value class WireChecksum(val crc32: UInt)

@JvmInline
value class WirePayloadLength(val bytes: Int)

// === CORE SERIALIZABLE TYPES ===

/**
 * Wire-serializable IoMemento - the actual TrikeShed use case
 */
data class WireIoMemento(
    val name: String?,
    val type: String?, 
    val width: Int?,
    val nullable: Boolean?,
    val encoding: String?,
    val format: String?
) {
    fun toIoMemento(): borg.trikeshed.isam.meta.IOMemento {
        return borg.trikeshed.isam.meta.IOMemento.create(name, type, width, nullable).apply {
            encoding = this@WireIoMemento.encoding
            format = this@WireIoMemento.format
        }
    }
    
    companion object {
        fun fromIoMemento(memento: borg.trikeshed.isam.meta.IOMemento): WireIoMemento {
            return WireIoMemento(
                name = memento.name,
                type = memento.type,
                width = memento.width,
                nullable = memento.nullable,
                encoding = memento.encoding,
                format = memento.format
            )
        }
    }
}

/**
 * Simple wire message container
 */
data class TrikeShedWireMessage(
    val version: WireVersion,
    val messageType: String,
    val payload: UByteArray,
    val checksum: WireChecksum
) {
    companion object {
        val CURRENT_VERSION = WireVersion(1u)
        
        fun create(messageType: String, payload: UByteArray): TrikeShedWireMessage {
            val checksum = calculateCrc32(payload)
            return TrikeShedWireMessage(
                version = CURRENT_VERSION,
                messageType = messageType,
                payload = payload,
                checksum = WireChecksum(checksum)
            )
        }
        
        private fun calculateCrc32(data: UByteArray): UInt {
            // Simple CRC32 implementation
            var crc = 0xFFFFFFFFu
            for (byte in data) {
                crc = crc xor byte.toUInt()
                repeat(8) {
                    crc = if ((crc and 1u) != 0u) {
                        (crc shr 1) xor 0xEDB88320u
                    } else {
                        crc shr 1
                    }
                }
            }
            return crc xor 0xFFFFFFFFu
        }
    }
}

// === TRIKESHED WIRE SERIALIZER ===

/**
 * Simple, practical TrikeShed wire protocol serializer
 */
object TrikeShedWireSerializer {
    
    /**
     * Serialize IoMemento to wire format
     */
    fun serialize(memento: borg.trikeshed.isam.meta.IOMemento): UByteArray {
        val wireMemento = WireIoMemento.fromIoMemento(memento)
        val payload = serializeWireMemento(wireMemento)
        val message = TrikeShedWireMessage.create("IoMemento", payload)
        return serializeMessage(message)
    }
    
    /**
     * Deserialize wire format to IoMemento
     */
    fun deserialize(data: UByteArray): borg.trikeshed.isam.meta.IOMemento {
        val message = deserializeMessage(data)
        require(message.messageType == "IoMemento") { "Expected IoMemento message" }
        
        val wireMemento = deserializeWireMemento(message.payload)
        return wireMemento.toIoMemento()
    }
    
    /**
     * Serialize Series<T> to wire format with type information
     */
    inline fun <reified T> serializeSeries(indexed: Indexed<T>): UByteArray {
        val payload = buildWirePayload {
            writeString(T::class.simpleName ?: "Unknown")
            writeVarInt(indexed.size)
            
            // Serialize elements
            for (i in 0 until indexed.size) {
                writeElement(indexed[i])
            }
        }
        
        val message = TrikeShedWireMessage.create("Series", payload)
        return serializeMessage(message)
    }
    
    /**
     * Serialize Series<Int> to wire format with optional optimal packing
     */
    fun serializeIntSeries(indexed: Indexed<Int>, useOptimalPacking: Boolean = true): UByteArray {
        return serializeSeries(indexed)
    }
    
    /**
     * Deserialize wire format to Series<T>
     */
    inline fun <reified T> deserializeSeries(data: UByteArray): Indexed<T> {
        val message = deserializeMessage(data)
        require(message.messageType == "Series") { "Expected Series message" }
        
        val reader = WireReader(message.payload)
        val typeName = reader.readString()
        val size = reader.readVarInt()
        
        val elements = mutableListOf<T>()
        repeat(size) {
            elements.add(reader.readElement<T>())
        }
        
        return size j { i -> elements[i] }
    }
    
    // === INTERNAL SERIALIZATION ===
    
    private fun serializeWireMemento(memento: WireIoMemento): UByteArray {
        return buildWirePayload {
            writeOptionalString(memento.name)
            writeOptionalString(memento.type)
            writeOptionalInt(memento.width)
            writeOptionalBoolean(memento.nullable)
            writeOptionalString(memento.encoding)
            writeOptionalString(memento.format)
        }
    }
    
    private fun deserializeWireMemento(data: UByteArray): WireIoMemento {
        val reader = WireReader(data)
        return WireIoMemento(
            name = reader.readOptionalString(),
            type = reader.readOptionalString(),
            width = reader.readOptionalInt(),
            nullable = reader.readOptionalBoolean(),
            encoding = reader.readOptionalString(),
            format = reader.readOptionalString()
        )
    }
    
    fun serializeMessage(message: TrikeShedWireMessage): UByteArray {
        return buildWirePayload {
            writeByte(message.version.version)
            writeString(message.messageType)
            writeVarInt(message.payload.size)
            writeByteArray(message.payload.toByteArray())
            writeFixed32(message.checksum.crc32.toInt())
        }
    }
    
    fun deserializeMessage(data: UByteArray): TrikeShedWireMessage {
        val reader = WireReader(data)
        val version = WireVersion(reader.readByte())
        val messageType = reader.readString()
        val payloadLength = reader.readVarInt()
        val payload = reader.readByteArray(payloadLength).toUByteArray()
        val checksum = WireChecksum(reader.readFixed32().toUInt())
        
        return TrikeShedWireMessage(version, messageType, payload, checksum)
    }
}

// === WIRE PAYLOAD BUILDER ===

class WirePayloadBuilder {
    private val buffer = mutableListOf<UByte>()
    
    fun writeByte(value: UByte) {
        buffer.add(value)
    }
    
    fun writeVarInt(value: Int) {
        var v = value
        while (v >= 0x80) {
            buffer.add(((v and 0x7F) or 0x80).toUByte())
            v = v ushr 7
        }
        buffer.add(v.toUByte())
    }
    
    fun writeFixed32(value: Int) {
        repeat(4) { i ->
            buffer.add(((value shr (i * 8)) and 0xFF).toUByte())
        }
    }
    
    fun writeString(str: String) {
        val bytes = str.encodeToByteArray()
        writeVarInt(bytes.size)
        bytes.forEach { buffer.add(it.toUByte()) }
    }
    
    fun writeByteArray(bytes: ByteArray) {
        bytes.forEach { buffer.add(it.toUByte()) }
    }
    
    fun writeOptionalString(str: String?) {
        if (str != null) {
            writeByte(1u)
            writeString(str)
        } else {
            writeByte(0u)
        }
    }
    
    fun writeOptionalInt(value: Int?) {
        if (value != null) {
            writeByte(1u)
            writeVarInt(value)
        } else {
            writeByte(0u)
        }
    }
    
    fun writeOptionalBoolean(value: Boolean?) {
        if (value != null) {
            writeByte(1u)
            writeByte(if (value) 1u else 0u)
        } else {
            writeByte(0u)
        }
    }
    
    inline fun <reified T> writeElement(element: T) {
        when (element) {
            is String -> {
                writeByte(1u) // String type marker
                writeString(element)
            }
            is Int -> {
                writeByte(2u) // Int type marker
                writeVarInt(element)
            }
            is Long -> {
                writeByte(3u) // Long type marker
                writeFixed32((element and 0xFFFFFFFF).toInt())
                writeFixed32((element shr 32).toInt())
            }
            is Boolean -> {
                writeByte(4u) // Boolean type marker
                writeByte(if (element) 1u else 0u)
            }
            is Double -> {
                writeByte(5u) // Double type marker
                val bits = element.toBits()
                writeFixed32((bits and 0xFFFFFFFF).toInt())
                writeFixed32((bits shr 32).toInt())
            }
            else -> {
                writeByte(255u) // Generic type marker
                writeString(element.toString())
            }
        }
    }
    
    fun build(): UByteArray = buffer.toUByteArray()
}

inline fun buildWirePayload(block: WirePayloadBuilder.() -> Unit): UByteArray {
    val builder = WirePayloadBuilder()
    builder.block()
    return builder.build()
}

// === WIRE READER ===

class WireReader(private val data: UByteArray) {
    private var position = 0
    
    fun readByte(): UByte {
        require(position < data.size) { "Unexpected end of data" }
        return data[position++]
    }
    
    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (position < data.size) {
            val byte = data[position++].toInt()
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return result
    }
    
    fun readFixed32(): Int {
        require(position + 4 <= data.size) { "Not enough data for fixed32" }
        var result = 0
        repeat(4) { i ->
            result = result or (data[position++].toInt() shl (i * 8))
        }
        return result
    }
    
    fun readString(): String {
        val length = readVarInt()
        return readByteArray(length).decodeToString()
    }
    
    fun readByteArray(length: Int): ByteArray {
        require(position + length <= data.size) { "Not enough data for byte array" }
        val result = ByteArray(length)
        repeat(length) { i ->
            result[i] = data[position++].toByte()
        }
        return result
    }
    
    fun readOptionalString(): String? {
        return if (readByte() == 1.toUByte()) readString() else null
    }
    
    fun readOptionalInt(): Int? {
        return if (readByte() == 1.toUByte()) readVarInt() else null
    }
    
    fun readOptionalBoolean(): Boolean? {
        return if (readByte() == 1.toUByte()) readByte() == 1.toUByte() else null
    }
    
    inline fun <reified T> readElement(): T {
        val typeMarker = readByte()
        return when (typeMarker.toInt()) {
            1 -> readString() as T
            2 -> readVarInt() as T
            3 -> {
                val low = readFixed32().toLong() and 0xFFFFFFFF
                val high = readFixed32().toLong() shl 32
                (low or high) as T
            }
            4 -> (readByte() == 1.toUByte()) as T
            5 -> {
                val low = readFixed32().toLong() and 0xFFFFFFFF
                val high = readFixed32().toLong() shl 32
                Double.fromBits(low or high) as T
            }
            255 -> readString() as T
            else -> throw IllegalArgumentException("Unknown type marker: $typeMarker")
        }
    }
}

// === CONVENIENCE EXTENSIONS ===

/**
 * Serialize IoMemento to wire bytes
 */
fun borg.trikeshed.isam.meta.IOMemento.toWireBytes(): UByteArray =
    TrikeShedWireSerializer.serialize(this)

/**
 * Deserialize wire bytes to IoMemento  
 */
fun UByteArray.toIoMemento(): borg.trikeshed.isam.meta.IOMemento =
    TrikeShedWireSerializer.deserialize(this)

/**
 * Serialize Series<T> to wire bytes
 */
inline fun <reified T> Indexed<T>.toWireBytes(): UByteArray =
    TrikeShedWireSerializer.serializeSeries(this)

/**
 * Deserialize wire bytes to Series<T>
 */
inline fun <reified T> UByteArray.toSeries(): Indexed<T> =
    TrikeShedWireSerializer.deserializeSeries(this)

expect fun pack(data: ByteArray): ByteArray