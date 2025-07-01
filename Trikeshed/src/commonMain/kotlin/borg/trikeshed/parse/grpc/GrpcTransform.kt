@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.parse.grpc

import borg.trikeshed.lib.*
import borg.trikeshed.wireproto.WireIoMemento
import kotlin.jvm.JvmInline

/**
 * TrikeShed gRPC Transform - Wire Protocol Transformation
 * Transforms between Protocol Buffers wire format and TrikeShed types
 */

// Transform Type Aliases
typealias ProtoMessage = Indexed<ProtoField>
typealias ProtoField = Join<WireFieldNumber, ProtoValue>
typealias ProtoValue = Join<ProtoType, Any?>
typealias ProtoType = UByte

/**
 * Proto value types
 */
object ProtoTypes {
    const val INT32: ProtoType = 1u
    const val INT64: ProtoType = 2u
    const val UINT32: ProtoType = 3u
    const val UINT64: ProtoType = 4u
    const val SINT32: ProtoType = 5u
    const val SINT64: ProtoType = 6u
    const val BOOL: ProtoType = 7u
    const val FIXED32: ProtoType = 8u
    const val FIXED64: ProtoType = 9u
    const val SFIXED32: ProtoType = 10u
    const val SFIXED64: ProtoType = 11u
    const val FLOAT: ProtoType = 12u
    const val DOUBLE: ProtoType = 13u
    const val STRING: ProtoType = 14u
    const val BYTES: ProtoType = 15u
    const val MESSAGE: ProtoType = 16u
    const val ENUM: ProtoType = 17u
}

/**
 * gRPC Transform - Bidirectional transformation
 */
object GrpcTransform {
    
    /**
     * Transform wire bytes to ProtoMessage
     */
    fun decode(wireBytes: Indexed<Byte>): WireResult<ProtoMessage> {
        return wireBytes.scanWire().fold(
            onSuccess = { fields ->
                fields.parse(wireBytes).map { graph ->
                    transformGraphToMessage(graph, fields, wireBytes)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    /**
     * Transform ProtoMessage to wire bytes
     */
    fun encode(message: ProtoMessage): Indexed<Byte> {
        val bytes = mutableListOf<Byte>()
        
        for (i in 0 until message.a) {
            val (fieldNumber, protoValue) = message.b(i)
            val (protoType, value) = protoValue
            
            encodeField(fieldNumber, protoType, value, bytes)
        }
        
        return bytes.size j { bytes[it] }
    }
    
    /**
     * Transform message graph to ProtoMessage
     */
    private fun transformGraphToMessage(
        graph: MessageGraph,
        fields: WireFieldIndexed,
        bytes: Indexed<Byte>
    ): ProtoMessage {
        val protoFields = mutableListOf<ProtoField>()
        val values = fields.extractValues(bytes)
        
        // Process each field
        for (i in 0 until fields.a) {
            val field = fields.b(i)
            val value = values.b(i)
            val (fieldTag, _) = field
            val (fieldNumber, wireType) = fieldTag
            val (valueType, valueBounds) = value
            
            val protoValue = extractProtoValue(wireType, valueBounds, bytes)
            protoValue?.let {
                protoFields.add(fieldNumber j it)
            }
        }
        
        val fieldArray = protoFields.toTypedArray()
        return fieldArray.size j fieldArray::get
    }
    
    /**
     * Extract proto value from wire bytes
     */
    private fun extractProtoValue(
        wireType: WireType,
        bounds: WireValueBounds,
        bytes: Indexed<Byte>
    ): ProtoValue? {
        return when (wireType) {
            WireTypes.VARINT -> {
                val value = GrpcScanner.readVarintValue(bytes, bounds)
                ProtoTypes.INT64 j value.toLong()
            }
            WireTypes.FIXED32 -> {
                val value = GrpcScanner.readFixed32(bytes, bounds)
                ProtoTypes.FIXED32 j value.toInt()
            }
            WireTypes.FIXED64 -> {
                val value = GrpcScanner.readFixed64(bytes, bounds)
                ProtoTypes.FIXED64 j value.toLong()
            }
            WireTypes.LENGTH_DELIMITED -> {
                // Try string first
                try {
                    val str = GrpcScanner.readString(bytes, bounds)
                    ProtoTypes.STRING j str
                } catch (e: Exception) {
                    // Fall back to bytes
                    val data = GrpcScanner.readBytes(bytes, bounds)
                    ProtoTypes.BYTES j data
                }
            }
            else -> null
        }
    }
    
    /**
     * Encode field to wire format
     */
    private fun encodeField(
        fieldNumber: WireFieldNumber,
        protoType: ProtoType,
        value: Any?,
        bytes: MutableList<Byte>
    ) {
        val wireType = protoTypeToWireType(protoType)
        val tag = (fieldNumber.toULong() shl 3) or wireType.toULong()
        
        // Write tag
        encodeVarint(tag, bytes)
        
        // Write value
        when (protoType) {
            ProtoTypes.INT32, ProtoTypes.INT64, ProtoTypes.UINT32, ProtoTypes.UINT64,
            ProtoTypes.SINT32, ProtoTypes.SINT64, ProtoTypes.BOOL, ProtoTypes.ENUM -> {
                val longValue = when (value) {
                    is Int -> value.toLong()
                    is Long -> value
                    is Boolean -> if (value) 1L else 0L
                    else -> 0L
                }
                encodeVarint(longValue.toULong(), bytes)
            }
            ProtoTypes.FIXED32, ProtoTypes.SFIXED32, ProtoTypes.FLOAT -> {
                val intValue = when (value) {
                    is Int -> value
                    is Float -> value.toBits()
                    else -> 0
                }
                encodeFixed32(intValue.toUInt(), bytes)
            }
            ProtoTypes.FIXED64, ProtoTypes.SFIXED64, ProtoTypes.DOUBLE -> {
                val longValue = when (value) {
                    is Long -> value
                    is Double -> value.toBits()
                    else -> 0L
                }
                encodeFixed64(longValue.toULong(), bytes)
            }
            ProtoTypes.STRING -> {
                val str = value as? String ?: ""
                val strBytes = str.encodeToByteArray()
                encodeVarint(strBytes.size.toULong(), bytes)
                bytes.addAll(strBytes.toList())
            }
            ProtoTypes.BYTES -> {
                val data = value as? Indexed<Byte> ?: emptyIndexed()
                encodeVarint(data.a.toULong(), bytes)
                for (i in 0 until data.a) {
                    bytes.add(data.b(i))
                }
            }
            ProtoTypes.MESSAGE -> {
                val msg = value as? ProtoMessage ?: emptyIndexed()
                val msgBytes = encode(msg)
                encodeVarint(msgBytes.a.toULong(), bytes)
                for (i in 0 until msgBytes.a) {
                    bytes.add(msgBytes.b(i))
                }
            }
        }
    }
    
    /**
     * Map proto type to wire type
     */
    private fun protoTypeToWireType(protoType: ProtoType): WireType = when (protoType) {
        ProtoTypes.INT32, ProtoTypes.INT64, ProtoTypes.UINT32, ProtoTypes.UINT64,
        ProtoTypes.SINT32, ProtoTypes.SINT64, ProtoTypes.BOOL, ProtoTypes.ENUM -> WireTypes.VARINT
        ProtoTypes.FIXED32, ProtoTypes.SFIXED32, ProtoTypes.FLOAT -> WireTypes.FIXED32
        ProtoTypes.FIXED64, ProtoTypes.SFIXED64, ProtoTypes.DOUBLE -> WireTypes.FIXED64
        ProtoTypes.STRING, ProtoTypes.BYTES, ProtoTypes.MESSAGE -> WireTypes.LENGTH_DELIMITED
        else -> throw IllegalArgumentException("Unknown proto type: $protoType")
    }
    
    /**
     * Encode varint
     */
    private fun encodeVarint(value: ULong, bytes: MutableList<Byte>) {
        var v = value
        while (v >= 0x80u) {
            bytes.add(((v and 0x7Fu) or 0x80u).toByte())
            v = v shr 7
        }
        bytes.add(v.toByte())
    }
    
    /**
     * Encode fixed32
     */
    private fun encodeFixed32(value: UInt, bytes: MutableList<Byte>) {
        bytes.add((value and 0xFFu).toByte())
        bytes.add(((value shr 8) and 0xFFu).toByte())
        bytes.add(((value shr 16) and 0xFFu).toByte())
        bytes.add(((value shr 24) and 0xFFu).toByte())
    }
    
    /**
     * Encode fixed64
     */
    private fun encodeFixed64(value: ULong, bytes: MutableList<Byte>) {
        for (i in 0..7) {
            bytes.add(((value shr (i * 8)) and 0xFFu).toByte())
        }
    }
    
    /**
     * Create WireIoMemento from ProtoMessage
     */
    fun toIoMemento(message: ProtoMessage, id: String): WireIoMemento {
        val wireBytes = encode(message)
        return WireIoMemento(id, wireBytes)
    }
    
    /**
     * Create ProtoMessage from WireIoMemento
     */
    fun fromIoMemento(memento: WireIoMemento): WireResult<ProtoMessage> {
        return decode(memento.data)
    }
}

/**
 * Builder for creating ProtoMessages
 */
class ProtoMessageBuilder {
    private val fields = mutableListOf<ProtoField>()
    
    fun addInt32(fieldNumber: WireFieldNumber, value: Int): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.INT32 j value))
        return this
    }
    
    fun addInt64(fieldNumber: WireFieldNumber, value: Long): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.INT64 j value))
        return this
    }
    
    fun addString(fieldNumber: WireFieldNumber, value: String): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.STRING j value))
        return this
    }
    
    fun addBytes(fieldNumber: WireFieldNumber, value: Indexed<Byte>): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.BYTES j value))
        return this
    }
    
    fun addBool(fieldNumber: WireFieldNumber, value: Boolean): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.BOOL j value))
        return this
    }
    
    fun addFloat(fieldNumber: WireFieldNumber, value: Float): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.FLOAT j value))
        return this
    }
    
    fun addDouble(fieldNumber: WireFieldNumber, value: Double): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.DOUBLE j value))
        return this
    }
    
    fun addMessage(fieldNumber: WireFieldNumber, value: ProtoMessage): ProtoMessageBuilder {
        fields.add(fieldNumber j (ProtoTypes.MESSAGE j value))
        return this
    }
    
    fun build(): ProtoMessage {
        val fieldArray = fields.toTypedArray()
        return fieldArray.size j fieldArray::get
    }
}

/**
 * Extension functions
 */
fun ProtoMessage.encode(): Indexed<Byte> = GrpcTransform.encode(this)

fun Indexed<Byte>.decodeProto(): WireResult<ProtoMessage> = GrpcTransform.decode(this)

fun ProtoMessage.toIoMemento(id: String): WireIoMemento = GrpcTransform.toIoMemento(this, id)

/**
 * Example usage
 */
object GrpcTransformExample {
    fun demonstrateTransform() {
        // Build a message
        val message = ProtoMessageBuilder()
            .addString(1, "Alice")
            .addInt32(2, 25)
            .addBool(3, true)
            .build()
        
        // Encode to wire format
        val wireBytes = message.encode()
        println("Encoded bytes: ${wireBytes.a} bytes")
        
        // Decode back
        val decodeResult = wireBytes.decodeProto()
        decodeResult.fold(
            onSuccess = { decoded ->
                println("Decoded ${decoded.a} fields")
                for (i in 0 until decoded.a) {
                    val (fieldNum, protoValue) = decoded.b(i)
                    val (protoType, value) = protoValue
                    println("Field $fieldNum: $value (type: $protoType)")
                }
            },
            onFailure = { error ->
                println("Decode error: ${error.message}")
            }
        )
        
        // Create IoMemento
        val memento = message.toIoMemento("user-123")
        println("IoMemento id: ${memento.id}, data size: ${memento.data.a}")
    }
}