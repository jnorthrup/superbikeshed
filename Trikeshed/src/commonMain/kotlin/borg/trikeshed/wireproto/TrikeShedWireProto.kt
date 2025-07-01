@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.wireproto

import borg.trikeshed.lib.*
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
     * Deserialize IoMemento from wire format
     */
    fun deserialize(data: UByteArray): borg.trikeshed.isam.meta.IOMemento {
        val message = deserializeMessage(data)
        require(message.messageType == "IoMemento") { "Expected IoMemento message" }
        val wireMemento = deserializeWireMemento(message.payload)
        return wireMemento.toIoMemento()
    }
    
    /**
     * Serialize Indexed<T> with type information
     */
    fun <T> serializeIndexed(series: Indexed<T>): UByteArray {
        val buffer = mutableListOf<UByte>()
        
        // Write size as varint
        buffer.addAll(encodeVarint(series.size))
        
        // Write each element based on type
        for (i in 0 until series.size) {
            val element = series[i]
            when (element) {
                is Byte -> {
                    buffer.add(0x01u) // Type marker for Byte
                    buffer.add(element.toUByte())
                }
                is Int -> {
                    buffer.add(0x02u) // Type marker for Int
                    buffer.addAll(encodeVarint(element))
                }
                is Long -> {
                    buffer.add(0x03u) // Type marker for Long
                    buffer.addAll(encodeVarlong(element))
                }
                is String -> {
                    buffer.add(0x04u) // Type marker for String
                    val bytes = element.encodeToByteArray()
                    buffer.addAll(encodeVarint(bytes.size))
                    buffer.addAll(bytes.map { it.toUByte() })
                }
                is Double -> {
                    buffer.add(0x05u) // Type marker for Double
                    buffer.addAll(element.toRawBits().toUByteArray())
                }
                else -> {
                    buffer.add(0xFFu) // Unknown type marker
                    // Could extend for more types
                }
            }
        }
        
        return buffer.toUByteArray()
    }
    
    // === INTERNAL SERIALIZATION HELPERS ===
    
    private fun serializeMessage(message: TrikeShedWireMessage): UByteArray {
        val buffer = mutableListOf<UByte>()
        
        // Version
        buffer.add(message.version.version)
        
        // Message type length and data
        val typeBytes = message.messageType.encodeToByteArray()
        buffer.addAll(encodeVarint(typeBytes.size))
        buffer.addAll(typeBytes.map { it.toUByte() })
        
        // Payload length and data
        buffer.addAll(encodeVarint(message.payload.size))
        buffer.addAll(message.payload.toList())
        
        // Checksum
        buffer.addAll(message.checksum.crc32.toUByteArray())
        
        return buffer.toUByteArray()
    }
    
    private fun deserializeMessage(data: UByteArray): TrikeShedWireMessage {
        var offset = 0
        
        // Version
        val version = WireVersion(data[offset++])
        
        // Message type
        val (typeLength, typeLengthBytes) = decodeVarint(data, offset)
        offset += typeLengthBytes
        val messageType = data.sliceArray(offset until offset + typeLength)
            .toByteArray().decodeToString()
        offset += typeLength
        
        // Payload
        val (payloadLength, payloadLengthBytes) = decodeVarint(data, offset)
        offset += payloadLengthBytes
        val payload = data.sliceArray(offset until offset + payloadLength)
        offset += payloadLength
        
        // Checksum
        val checksumBytes = data.sliceArray(offset until offset + 4)
        val checksum = WireChecksum(checksumBytes.toUInt())
        
        return TrikeShedWireMessage(version, messageType, payload, checksum)
    }
    
    private fun serializeWireMemento(memento: WireIoMemento): UByteArray {
        val buffer = mutableListOf<UByte>()
        
        // Serialize each field with presence flags
        var flags = 0u
        if (memento.name != null) flags = flags or 0x01u
        if (memento.type != null) flags = flags or 0x02u
        if (memento.width != null) flags = flags or 0x04u
        if (memento.nullable != null) flags = flags or 0x08u
        if (memento.encoding != null) flags = flags or 0x10u
        if (memento.format != null) flags = flags or 0x20u
        
        buffer.add(flags.toUByte())
        
        // Serialize present fields
        memento.name?.let {
            val bytes = it.encodeToByteArray()
            buffer.addAll(encodeVarint(bytes.size))
            buffer.addAll(bytes.map { b -> b.toUByte() })
        }
        
        memento.type?.let {
            val bytes = it.encodeToByteArray()
            buffer.addAll(encodeVarint(bytes.size))
            buffer.addAll(bytes.map { b -> b.toUByte() })
        }
        
        memento.width?.let {
            buffer.addAll(encodeVarint(it))
        }
        
        memento.nullable?.let {
            buffer.add(if (it) 1u else 0u)
        }
        
        memento.encoding?.let {
            val bytes = it.encodeToByteArray()
            buffer.addAll(encodeVarint(bytes.size))
            buffer.addAll(bytes.map { b -> b.toUByte() })
        }
        
        memento.format?.let {
            val bytes = it.encodeToByteArray()
            buffer.addAll(encodeVarint(bytes.size))
            buffer.addAll(bytes.map { b -> b.toUByte() })
        }
        
        return buffer.toUByteArray()
    }
    
    private fun deserializeWireMemento(data: UByteArray): WireIoMemento {
        var offset = 0
        
        val flags = data[offset++]
        
        var name: String? = null
        var type: String? = null
        var width: Int? = null
        var nullable: Boolean? = null
        var encoding: String? = null
        var format: String? = null
        
        if ((flags.toUInt() and 0x01u) != 0u) {
            val (length, lengthBytes) = decodeVarint(data, offset)
            offset += lengthBytes
            name = data.sliceArray(offset until offset + length).toByteArray().decodeToString()
            offset += length
        }
        
        if ((flags.toUInt() and 0x02u) != 0u) {
            val (length, lengthBytes) = decodeVarint(data, offset)
            offset += lengthBytes
            type = data.sliceArray(offset until offset + length).toByteArray().decodeToString()
            offset += length
        }
        
        if ((flags.toUInt() and 0x04u) != 0u) {
            val (value, bytes) = decodeVarint(data, offset)
            offset += bytes
            width = value
        }
        
        if ((flags.toUInt() and 0x08u) != 0u) {
            nullable = data[offset++] != 0u.toUByte()
        }
        
        if ((flags.toUInt() and 0x10u) != 0u) {
            val (length, lengthBytes) = decodeVarint(data, offset)
            offset += lengthBytes
            encoding = data.sliceArray(offset until offset + length).toByteArray().decodeToString()
            offset += length
        }
        
        if ((flags.toUInt() and 0x20u) != 0u) {
            val (length, lengthBytes) = decodeVarint(data, offset)
            offset += lengthBytes
            format = data.sliceArray(offset until offset + length).toByteArray().decodeToString()
            offset += length
        }
        
        return WireIoMemento(name, type, width, nullable, encoding, format)
    }
    
    // === VARINT ENCODING ===
    
    private fun encodeVarint(value: Int): List<UByte> {
        val result = mutableListOf<UByte>()
        var v = value
        while (v >= 0x80) {
            result.add(((v and 0x7F) or 0x80).toUByte())
            v = v ushr 7
        }
        result.add(v.toUByte())
        return result
    }
    
    private fun decodeVarint(data: UByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var bytesRead = 0
        
        while (true) {
            val byte = data[offset + bytesRead]
            result = result or ((byte.toInt() and 0x7F) shl shift)
            bytesRead++
            if ((byte.toInt() and 0x80) == 0) break
            shift += 7
        }
        
        return result to bytesRead
    }
    
    private fun encodeVarlong(value: Long): List<UByte> {
        val result = mutableListOf<UByte>()
        var v = value
        while (v >= 0x80) {
            result.add(((v and 0x7F) or 0x80).toUByte())
            v = v ushr 7
        }
        result.add(v.toUByte())
        return result
    }
    
    // === UTILITY EXTENSIONS ===
    
    private fun Long.toUByteArray(): UByteArray {
        return ubyteArrayOf(
            (this shr 56).toUByte(),
            (this shr 48).toUByte(),
            (this shr 40).toUByte(),
            (this shr 32).toUByte(),
            (this shr 24).toUByte(),
            (this shr 16).toUByte(),
            (this shr 8).toUByte(),
            this.toUByte()
        )
    }
    
    private fun UInt.toUByteArray(): UByteArray {
        return ubyteArrayOf(
            (this shr 24).toUByte(),
            (this shr 16).toUByte(),
            (this shr 8).toUByte(),
            this.toUByte()
        )
    }
    
    private fun UByteArray.toUInt(): UInt {
        require(size == 4) { "UInt requires 4 bytes" }
        return (this[0].toUInt() shl 24) or
               (this[1].toUInt() shl 16) or
               (this[2].toUInt() shl 8) or
               this[3].toUInt()
    }
}

// === EXTENSION FUNCTIONS FOR TRIKESHED TYPES ===

/**
 * Convert Indexed<Byte> to wire bytes
 */
fun Indexed<Byte>.toWireBytes(): UByteArray {
    val buffer = UByteArray(this.size) { i ->
        this[i].toUByte()
    }
    return buffer
}

/**
 * Convert UByteArray to Indexed<Byte>
 */
fun UByteArray.toBytesIndexed(): Indexed<Byte> {
    return this.size j { i: Int -> this[i].toByte() }
}

/**
 * Convert IoMemento to wire format
 */
fun borg.trikeshed.isam.meta.IOMemento.toWireBytes(): UByteArray {
    return TrikeShedWireSerializer.serialize(this)
}

/**
 * Create IoMemento from wire bytes
 */
fun UByteArray.toIoMemento(): borg.trikeshed.isam.meta.IOMemento {
    return TrikeShedWireSerializer.deserialize(this)
}

// === COMPATIBILITY WITH MINIMAL INTERFACE ===

/**
 * TrikeShed wire protocol main class for compatibility
 */
class TrikeShedWireProto {
    fun serialize(data: Any): Indexed<Byte> {
        return when (data) {
            is borg.trikeshed.isam.meta.IOMemento -> data.toWireBytes().toBytesIndexed()
            is Indexed<*> -> TrikeShedWireSerializer.serializeIndexed(data).toBytesIndexed()
            else -> emptyIndexed()
        }
    }
    
    fun <T> deserialize(data: Indexed<Byte>): T {
        val bytes = UByteArray(data.size) { i -> data[i].toUByte() }
        @Suppress("UNCHECKED_CAST")
        return bytes.toIoMemento() as T
    }
}