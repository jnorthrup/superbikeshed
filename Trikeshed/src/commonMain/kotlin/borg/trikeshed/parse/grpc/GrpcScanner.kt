@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.parse.grpc

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * TrikeShed gRPC Scanner - Protocol Buffers Wire Format Implementation
 * Compliant with CLAUDE.md type system and Kotlin 2.1.0
 *
 * Parses Protocol Buffers wire format using tensor-first columnar processing
 * Implements taxonomical typealiases for semantic clarity
 */

// Ontological Type Aliases - Protocol Buffers Wire Format
typealias WireFieldNumber = Int
typealias WireType = UByte
typealias WireTag = ULong  // (field_number << 3) | wire_type
typealias WirePosition = Int
typealias WireLength = Int
typealias WireVarint = ULong
typealias WireFixed32 = UInt
typealias WireFixed64 = ULong
typealias WireBytes = Indexed<Byte>

// Core Wire Format Processing Types
typealias WireFieldTag = Join<WireFieldNumber, WireType>
typealias WireFieldPosition = Join<WirePosition, WireLength>
typealias WireField = Join<WireFieldTag, WireFieldPosition>
typealias WireFieldIndexed = Indexed<WireField>

// Value Extraction Types
typealias WireValueBounds = Join<WirePosition, WirePosition> // start j end
typealias WireValueType = UByte
typealias WireValue = Join<WireValueType, WireValueBounds>
typealias WireValueIndexed = Indexed<WireValue>

// Message Structure Types
typealias WireMessageBounds = Join<WirePosition, WireLength>
typealias WireMessage = Join<WireFieldIndexed, WireMessageBounds>
typealias WireMessageIndexed = Indexed<WireMessage>

// Error Handling Types
@JvmInline
value class WireError(val message: String)
typealias WireResult<T> = Result<T>

/**
 * Protocol Buffers Wire Types
 */
object WireTypes {
    const val VARINT: WireType = 0u      // int32, int64, uint32, uint64, sint32, sint64, bool, enum
    const val FIXED64: WireType = 1u     // fixed64, sfixed64, double
    const val LENGTH_DELIMITED: WireType = 2u  // string, bytes, embedded messages, packed repeated fields
    const val START_GROUP: WireType = 3u  // deprecated
    const val END_GROUP: WireType = 4u    // deprecated
    const val FIXED32: WireType = 5u     // fixed32, sfixed32, float
}

/**
 * TrikeShed gRPC Scanner - Core Implementation
 * Uses Indexed<T> and Join<A,B> exclusively - no List<T> or Pair<A,B>
 */
object GrpcScanner {
    
    /**
     * Scan wire format bytes into field series using α transforms
     */
    fun scan(wireBytes: Indexed<Byte>): WireResult<WireFieldIndexed> {
        if (wireBytes.a == 0) return Result.success(emptyIndexed())
        
        return try {
            Result.success(parseWireFields(wireBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse wire fields from bytes
     */
    private fun parseWireFields(bytes: Indexed<Byte>): WireFieldIndexed {
        val fields = mutableListOf<WireField>()
        var pos = 0
        
        while (pos < bytes.a) {
            val tagStart = pos
            val (tag, newPos) = readVarint(bytes, pos)
            pos = newPos
            
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x7u).toUByte()
            
            val fieldLength = when (wireType) {
                WireTypes.VARINT -> {
                    val (_, endPos) = readVarint(bytes, pos)
                    endPos - pos
                }
                WireTypes.FIXED64 -> 8
                WireTypes.LENGTH_DELIMITED -> {
                    val (length, lengthEnd) = readVarint(bytes, pos)
                    pos = lengthEnd
                    length.toInt()
                }
                WireTypes.FIXED32 -> 4
                else -> throw IllegalArgumentException("Unknown wire type: $wireType")
            }
            
            val fieldTag = fieldNumber j wireType
            val fieldPosition = tagStart j (pos + fieldLength - tagStart)
            fields.add(fieldTag j fieldPosition)
            
            pos += fieldLength
        }
        
        val fieldArray = fields.toTypedArray()
        return fieldArray.size j fieldArray::get
    }
    
    /**
     * Read varint from bytes
     */
    internal fun readVarint(bytes: Indexed<Byte>, start: WirePosition): Join<WireVarint, WirePosition> {
        var result = 0uL
        var shift = 0
        var pos = start
        
        while (pos < bytes.a) {
            val b = bytes.b(pos).toUByte()
            result = result or ((b and 0x7Fu).toULong() shl shift)
            pos++
            
            if ((b and 0x80u) == 0u.toUByte()) {
                return result j pos
            }
            
            shift += 7
            if (shift >= 64) {
                throw IllegalArgumentException("Varint too long")
            }
        }
        
        throw IllegalArgumentException("Unexpected end of varint")
    }
    
    /**
     * Extract field values using α transforms
     */
    fun extractValues(fields: WireFieldIndexed, bytes: Indexed<Byte>): WireValueIndexed = 
        fields.α { field ->
            val (fieldTag, fieldPos) = field
            val (fieldNumber, wireType) = fieldTag
            val (start, length) = fieldPos
            
            // Calculate value position (skip tag)
            var valueStart = start
            val (_, tagEnd) = readVarint(bytes, start)
            valueStart = tagEnd
            
            // For length-delimited, skip length prefix
            if (wireType == WireTypes.LENGTH_DELIMITED) {
                val (_, lengthEnd) = readVarint(bytes, valueStart)
                valueStart = lengthEnd
            }
            
            val valueEnd = start + length
            wireType j (valueStart j valueEnd)
        }
    
    /**
     * Read string value from wire bytes
     */
    fun readString(bytes: Indexed<Byte>, bounds: WireValueBounds): String {
        val (start, end) = bounds
        val length = end - start
        val charArray = CharArray(length) { i ->
            bytes.b(start + i).toInt().toChar()
        }
        return charArray.concatToString()
    }
    
    /**
     * Read bytes value from wire bytes
     */
    fun readBytes(bytes: Indexed<Byte>, bounds: WireValueBounds): Indexed<Byte> {
        val (start, end) = bounds
        val length = end - start
        return length j { i -> bytes.b(start + i) }
    }
    
    /**
     * Read varint value
     */
    fun readVarintValue(bytes: Indexed<Byte>, bounds: WireValueBounds): WireVarint {
        val (start, _) = bounds
        val (value, _) = readVarint(bytes, start)
        return value
    }
    
    /**
     * Read fixed32 value
     */
    fun readFixed32(bytes: Indexed<Byte>, bounds: WireValueBounds): WireFixed32 {
        val (start, _) = bounds
        var result = 0u
        for (i in 0..3) {
            result = result or (bytes.b(start + i).toUInt() shl (i * 8))
        }
        return result
    }
    
    /**
     * Read fixed64 value
     */
    fun readFixed64(bytes: Indexed<Byte>, bounds: WireValueBounds): WireFixed64 {
        val (start, _) = bounds
        var result = 0uL
        for (i in 0..7) {
            result = result or (bytes.b(start + i).toULong() shl (i * 8))
        }
        return result
    }
    
    /**
     * Parse embedded message
     */
    fun parseEmbeddedMessage(bytes: Indexed<Byte>, bounds: WireValueBounds): WireResult<WireFieldIndexed> {
        val (start, end) = bounds
        val length = end - start
        val messageBytes = length j { i: Int -> bytes.b(start + i) }
        return scan(messageBytes)
    }
    
    /**
     * Group fields by field number using α transforms
     */
    fun groupByFieldNumber(fields: WireFieldIndexed): Indexed<Join<WireFieldNumber, WireFieldIndexed>> {
        val groups = mutableMapOf<WireFieldNumber, MutableList<WireField>>()
        
        for (i in 0 until fields.a) {
            val field = fields.b(i)
            val fieldNumber = field.a.a
            groups.getOrPut(fieldNumber) { mutableListOf() }.add(field)
        }
        
        val groupArray = groups.entries.map { (number, fieldList) ->
            val fieldArray = fieldList.toTypedArray()
            number j (fieldArray.size j { i: Int -> fieldArray[i] })
        }.toTypedArray()
        
        return groupArray.size j { i: Int -> groupArray[i] }
    }
    
    /**
     * Materialize fields to List using play operator
     */
    fun materializeFields(fields: WireFieldIndexed): List<WireField> = fields.play.toList()
    
    /**
     * Filter fields by wire type using α transform
     */
    fun filterByWireType(fields: WireFieldIndexed, targetType: WireType): WireFieldIndexed =
        fields.play.filter { it.a.b == targetType }.toIdx()
}

/**
 * Extension functions for convenient wire format processing
 */
fun Indexed<Byte>.scanWire(): WireResult<WireFieldIndexed> = GrpcScanner.scan(this)

fun WireFieldIndexed.extractValues(bytes: Indexed<Byte>): WireValueIndexed = 
    GrpcScanner.extractValues(this, bytes)

fun WireFieldIndexed.groupByFieldNumber(): Indexed<Join<WireFieldNumber, WireFieldIndexed>> =
    GrpcScanner.groupByFieldNumber(this)

fun WireFieldIndexed.filterByWireType(wireType: WireType): WireFieldIndexed =
    GrpcScanner.filterByWireType(this, wireType)

/**
 * Utility functions for Indexed operations
 */
private fun <T> Array<T>.toIdx(): Indexed<T> = size j ::get

private fun <T> List<T>.toIdx(): Indexed<T> = size j ::get

private fun <T> emptyIndexed(): Indexed<T> = 0 j { throw IndexOutOfBoundsException("Empty series") }

/**
 * Example usage demonstrating gRPC wire format parsing
 */
object GrpcScannerExample {
    fun demonstrateScanning() {
        // Example protobuf message: field 1 = "hello", field 2 = 42
        val wireBytes = byteArrayOf(
            0x0a, 0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f,  // field 1, string "hello"
            0x10, 0x2a                                    // field 2, varint 42
        )
        
        val indexedBytes = wireBytes.size j { i: Int -> wireBytes[i] }
        
        // Scan using α transforms and Join composition
        val scanResult = indexedBytes.scanWire()
        
        scanResult.fold(
            onSuccess = { fields ->
                // Extract values using α transforms
                val values = fields.extractValues(indexedBytes)
                val grouped = fields.groupByFieldNumber()
                
                // Use play operator only for final materialization
                println("Fields: ${fields.play.toList()}")
                println("Values: ${values.play.toList()}")
                println("Grouped: ${grouped.play.toList()}")
            },
            onFailure = { error ->
                println("Scan error: ${error.message}")
            }
        )
    }
}