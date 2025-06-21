@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.serialization

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * High-performance JSON decoder using TrikeShed's lightning bitmap scanning
 * Integrates with kotlinx-serialization for type-safe deserialization
 */
@JvmInline
value class JsonBitmapPosition(val value: Int)

@JvmInline  
value class JsonStructuralMask(val bits: Int)  // Changed to 32-bit for deterministic packing

@JvmInline
value class JsonBitmapWord(val data: Int)  // Changed to 32-bit for deterministic packing

typealias JsonBitmapArray = MetaSeries<Int, JsonBitmapWord>
typealias JsonStructuralIndex = Int
typealias JsonStructuralSeries = MetaSeries<Int, JsonStructuralIndex>

/**
 * Core bitmap-based JSON decoder that implements kotlinx-serialization Decoder interface
 */
class BitmapJsonDecoder(
    private val json: JsonSerializersModule = EmptySerializersModule(),
    private val input: String,
    private val bitmapArray: JsonBitmapArray,
    private val structuralIndices: JsonStructuralSeries,
    private var currentIndex: Int = 0
) : AbstractDecoder() {
    
    override val serializersModule: SerializersModule = json
    
    private var elementIndex = 0
    
    // === Core Decoding Interface ===
    
    override fun decodeBoolean(): Boolean {
        val value = getCurrentStructuralValue()
        advanceToNextStructural()
        return when (value) {
            "true" -> true
            "false" -> false
            else -> throw SerializationException("Expected boolean value, got: $value")
        }
    }
    
    override fun decodeByte(): Byte = getCurrentStructuralValue().toByte().also { advanceToNextStructural() }
    override fun decodeShort(): Short = getCurrentStructuralValue().toShort().also { advanceToNextStructural() }
    override fun decodeInt(): Int = getCurrentStructuralValue().toInt().also { advanceToNextStructural() }
    override fun decodeLong(): Long = getCurrentStructuralValue().toLong().also { advanceToNextStructural() }
    override fun decodeFloat(): Float = getCurrentStructuralValue().toFloat().also { advanceToNextStructural() }
    override fun decodeDouble(): Double = getCurrentStructuralValue().toDouble().also { advanceToNextStructural() }
    override fun decodeChar(): Char = getCurrentStructuralValue().single().also { advanceToNextStructural() }
    
    override fun decodeString(): String {
        val value = getCurrentStructuralValue()
        advanceToNextStructural()
        return when {
            value.startsWith('"') && value.endsWith('"') -> value.substring(1, value.length - 1)
            else -> value
        }
    }
    
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        return enumDescriptor.getElementIndex(value)
    }
    
    override fun decodeNull(): Nothing? {
        val value = getCurrentStructuralValue()
        if (value != "null") throw SerializationException("Expected null, got: $value")
        advanceToNextStructural()
        return null
    }
    
    // === Composite Decoding ===
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val startChar = peekCurrentStructuralChar()
        when (descriptor.kind) {
            StructureKind.LIST -> {
                if (startChar != '[') throw SerializationException("Expected '[' for list, got: $startChar")
                advanceToNextStructural() // consume '['
            }
            StructureKind.MAP, StructureKind.CLASS, StructureKind.OBJECT -> {
                if (startChar != '{') throw SerializationException("Expected '{' for object, got: $startChar")
                advanceToNextStructural() // consume '{'
            }
        }
        return BitmapJsonDecoder(json, input, bitmapArray, structuralIndices, currentIndex)
    }
    
    override fun endStructure(descriptor: SerialDescriptor) {
        val endChar = peekCurrentStructuralChar()
        when (descriptor.kind) {
            StructureKind.LIST -> {
                if (endChar == ']') advanceToNextStructural()
            }
            StructureKind.MAP, StructureKind.CLASS, StructureKind.OBJECT -> {
                if (endChar == '}') advanceToNextStructural()
            }
        }
    }
    
    override fun decodeSequentially(): Boolean = true
    
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (currentIndex >= structuralIndices.size) return CompositeDecoder.DECODE_DONE
        
        val char = peekCurrentStructuralChar()
        when {
            char == '}' || char == ']' -> return CompositeDecoder.DECODE_DONE
            char == ',' -> {
                advanceToNextStructural() // consume ','
                return elementIndex++
            }
            elementIndex == 0 -> return elementIndex++
            else -> return CompositeDecoder.DECODE_DONE
        }
    }
    
    // === Bitmap Navigation Helpers ===
    
    private fun getCurrentStructuralValue(): String {
        if (currentIndex >= structuralIndices.size) 
            throw SerializationException("Unexpected end of JSON input")
        
        val startIdx = structuralIndices[currentIndex]
        val endIdx = if (currentIndex + 1 < structuralIndices.size) 
            structuralIndices[currentIndex + 1] else input.length
        
        return extractValue(startIdx, endIdx)
    }
    
    private fun peekCurrentStructuralChar(): Char {
        if (currentIndex >= structuralIndices.size) return '\u0000'
        return input[structuralIndices[currentIndex]]
    }
    
    private fun advanceToNextStructural() {
        currentIndex++
    }
    
    private fun extractValue(start: Int, end: Int): String {
        val char = input[start]
        return when (char) {
            '"' -> extractStringValue(start)
            '[', '{', ']', '}', ',' -> char.toString()
            else -> extractPrimitiveValue(start, end)
        }
    }
    
    private fun extractStringValue(start: Int): String {
        var pos = start + 1 // skip opening quote
        val sb = StringBuilder()
        
        while (pos < input.length) {
            val char = input[pos]
            when (char) {
                '"' -> return "\"${sb}\"" // include quotes for consistency
                '\\' -> {
                    pos++
                    if (pos < input.length) {
                        when (input[pos]) {
                            '"', '\\', '/' -> sb.append(input[pos])
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                // Unicode escape sequence
                                val unicode = input.substring(pos + 1, pos + 5).toInt(16)
                                sb.append(unicode.toChar())
                                pos += 4
                            }
                            else -> sb.append(input[pos])
                        }
                    }
                }
                else -> sb.append(char)
            }
            pos++
        }
        throw SerializationException("Unterminated string")
    }
    
    private fun extractPrimitiveValue(start: Int, end: Int): String {
        var actualEnd = start
        while (actualEnd < end && actualEnd < input.length) {
            val char = input[actualEnd]
            if (char in " \t\n\r,]}") break
            actualEnd++
        }
        return input.substring(start, actualEnd)
    }
}

/**
 * Factory for creating bitmap-based JSON decoders
 */
object BitmapJsonFormat {
    fun createDecoder(input: String): BitmapJsonDecoder {
        val (bitmapArray, structuralIndices) = scanJsonStructure(input)
        return BitmapJsonDecoder(
            input = input,
            bitmapArray = bitmapArray, 
            structuralIndices = structuralIndices
        )
    }
}

/**
 * High-performance JSON structure scanning using bitmap techniques
 * This is the core engine that creates structural indices for fast navigation
 */
expect fun scanJsonStructure(input: String): Pair<JsonBitmapArray, JsonStructuralSeries>

/**
 * Extension function for easy deserialization with bitmap scanning
 */
inline fun <reified T> String.decodeBitmapJson(): T {
    val decoder = BitmapJsonFormat.createDecoder(this)
    return decoder.decodeSerializableValue(serializer<T>())
}