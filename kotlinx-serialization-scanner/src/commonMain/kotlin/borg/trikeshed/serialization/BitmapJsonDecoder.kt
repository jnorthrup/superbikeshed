@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.serialization

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * High-performance JSON decoder using TrikeShed's lightning bitmap scanning
 * Integrates with kotlinx-serialization for type-safe deserialization
 */
@kotlin.jvm.JvmInline
value class JsonBitmapPosition(val value: Int)

@kotlin.jvm.JvmInline  
value class JsonStructuralMask(val bits: Int)  // Changed to 32-bit for deterministic packing

@kotlin.jvm.JvmInline
value class JsonBitmapWord(val data: Int)  // Changed to 32-bit for deterministic packing

typealias JsonBitmapArray = MetaSeries<Int, JsonBitmapWord>
typealias JsonStructuralIndex = Int
typealias JsonStructuralSeries = MetaSeries<Int, JsonStructuralIndex>

/**
 * Core bitmap-based JSON decoder that implements kotlinx-serialization Decoder interface
 */
class BitmapJsonDecoder(
    override val serializersModule: SerializersModule,
    private val input: String,
    private val bitmapArray: JsonBitmapArray, // Currently unused, but kept for future SIMD/bitmap ops
    private val structuralIndices: JsonStructuralSeries,
    private var currentIndex: Int = 0
) : AbstractDecoder() {
    
    // Track element index for composite decoding
    var elementIndex: Int = 0

    // === Helper to get string for primitive values ===
    private fun getPrimitiveValueString(): String {
        // Assumes currentIndex points to the structural token *before* the primitive value (e.g., ':', '[', or ',')
        val valueStartIndex = structuralIndices.b(currentIndex) + 1

        // The primitive value ends just before the next structural token
        val valueEndIndex = if (currentIndex + 1 < structuralIndices.size) {
            structuralIndices.b(currentIndex + 1)
        } else {
            input.length // Primitive is the last thing in the input
        }

        if (valueStartIndex >= input.length || valueStartIndex > valueEndIndex) {
             // Handles cases like `[,]` or `{"key":}` or `[1,]` where the last element is missing
            throw SerializationException("Missing or empty primitive value after token at ${structuralIndices.b(currentIndex)} between $valueStartIndex and $valueEndIndex")
        }
        return input.substring(valueStartIndex, valueEndIndex).trim()
    }
    
    // === Core Decoding Interface ===
    
    override fun decodeBoolean(): Boolean {
        val s = getPrimitiveValueString()
        currentIndex++ // Advance past the structural token that contained the primitive value
        return when (s) {
            "true" -> true
            "false" -> false
            else -> throw SerializationException("Expected boolean value, got: '$s'")
        }
    }
    
    override fun decodeByte(): Byte {
        val s = getPrimitiveValueString()
        currentIndex++
        try {
            return s.toByte()
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid byte literal: '$s'", e)
        }
    }
    override fun decodeShort(): Short {
        val s = getPrimitiveValueString()
        currentIndex++
        try {
            return s.toShort()
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid short literal: '$s'", e)
        }
    }
    override fun decodeInt(): Int {
        val s = getPrimitiveValueString()
        currentIndex++
        try {
            return s.toInt()
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid int literal: '$s'", e)
        }
    }
    override fun decodeLong(): Long {
        val s = getPrimitiveValueString()
        currentIndex++
        try {
            return s.toLong()
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid long literal: '$s'", e)
        }
    }
    override fun decodeFloat(): Float {
        val s = getPrimitiveValueString()
        currentIndex++
        // Kotlin's String.toFloat() can be quite lenient.
        // JSON spec for numbers is stricter but often parsers are lenient too.
        // For stricter parsing, one might need a custom number parser.
        try {
            return s.toFloat()
        } catch (e: NumberFormatException) { // Though toFloat() might not throw this as often as toInt()
            throw SerializationException("Invalid float literal: '$s'", e)
        }
    }
    override fun decodeDouble(): Double {
        val s = getPrimitiveValueString()
        currentIndex++
        try {
            return s.toDouble()
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid double literal: '$s'", e)
        }
    }
    override fun decodeChar(): Char { // JSON doesn't have a separate char type; usually treated as string of length 1
        val s = decodeString()
        if (s.length != 1) throw SerializationException("Expected single char, got string: \"$s\"")
        return s.single()
    }
    
    override fun decodeString(): String {
        val openQuoteInputIndex = structuralIndices.b(currentIndex)
        if (input[openQuoteInputIndex] != '"') {
            throw SerializationException("Expected string starting with '\"' at $openQuoteInputIndex, found ${input[openQuoteInputIndex]}")
        }

        if (currentIndex + 1 >= structuralIndices.size || input[structuralIndices.b(currentIndex + 1)] != '"') {
            throw SerializationException("Missing closing quote in structuralIndices for string starting at $openQuoteInputIndex")
        }
        val closeQuoteInputIndex = structuralIndices.b(currentIndex + 1)

        val valueStartInInput = openQuoteInputIndex + 1
        val valueEndInInput = closeQuoteInputIndex // exclusive end for subSequence/substring

        if (valueStartInInput > valueEndInInput) { // Empty string ""
            currentIndex += 2
            return ""
        }

        var hasEscapes = false
        for (i in valueStartInInput until valueEndInInput) {
            if (input[i] == '\\') {
                hasEscapes = true
                break
            }
        }

        val result: String
        if (!hasEscapes) {
            result = input.substring(valueStartInInput, valueEndInInput)
        } else {
            result = unescapeStringAlreadyKnownBounds(input, valueStartInInput, valueEndInInput)
        }

        currentIndex += 2 // Advance past open and close quote structural indices.
        return result
    }

    // This function is only called if escapes ARE present in the slice [valueStart, valueEnd).
    private fun unescapeStringAlreadyKnownBounds(originalInput: String, valueStart: Int, valueEnd: Int): String {
        val sb = StringBuilder(valueEnd - valueStart) // Pre-size StringBuilder
        var currentPosInSlice = valueStart

        while (currentPosInSlice < valueEnd) {
            val char = originalInput[currentPosInSlice]
            if (char == '\\') {
                currentPosInSlice++ // Consume backslash
                if (currentPosInSlice >= valueEnd) throw SerializationException("Unterminated escape sequence at end of string slice, started at $valueStart")

                val escapedChar = originalInput[currentPosInSlice]
                when (escapedChar) {
                    '"', '\\', '/' -> sb.append(escapedChar)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c') // Form feed
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (currentPosInSlice + 4 >= valueEnd) { // Check boundary within the slice for the 4 hex digits
                            throw SerializationException("Incomplete unicode escape sequence: \\u${originalInput.substring(currentPosInSlice + 1, kotlin.math.min(currentPosInSlice + 1 + 4, valueEnd))}")
                        }
                        val hexCode = originalInput.substring(currentPosInSlice + 1, currentPosInSlice + 5)
                        try {
                            sb.append(hexCode.toInt(16).toChar())
                        } catch (e: NumberFormatException) {
                            throw SerializationException("Invalid unicode escape sequence: \\u$hexCode")
                        }
                        currentPosInSlice += 4 // Advance past the 4 hex digits
                    }
                    else -> throw SerializationException("Invalid escape character: '\\$escapedChar'")
                }
            } else {
                // Regular character (cannot be '"' within the slice if valueEnd is the closing quote's index)
                sb.append(char)
            }
            currentPosInSlice++
        }
        return sb.toString()
    }
    
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        // Enums are typically encoded as strings
        val value = decodeString()
        val enumIndex = enumDescriptor.getElementIndex(value)
        if (enumIndex == CompositeDecoder.UNKNOWN_NAME) {
            throw SerializationException("Enum ${enumDescriptor.serialName} does not contain element '$value'")
        }
        return enumIndex
    }
    
    override fun decodeNotNullMark(): Boolean {
        // If the current token is 'null', it's a null value. Otherwise, it's not null.
        // This assumes currentIndex points to the start of the value (or token before primitive).
        // For primitives, getPrimitiveValueString() would read "null".
        // For strings, input[structuralIndices.b(currentIndex)] would be '"'.
        // For objects/arrays, it would be '{' or '['.
        // This is tricky because "null" is a primitive value.

        // A robust way: try to read "null" as a primitive. If it matches, it's null.
        // Peek ahead without advancing currentIndex yet.
        val valueStartIndex = structuralIndices.b(currentIndex) + 1
        val valueEndIndex = if (currentIndex + 1 < structuralIndices.size) structuralIndices.b(currentIndex + 1) else input.length
        if (valueStartIndex < valueEndIndex && input.substring(valueStartIndex, valueEndIndex).trim() == "null") {
            return false // It is null
        }
        return true // It is not null
    }

    override fun decodeNull(): Nothing? {
        val s = getPrimitiveValueString()
        if (s != "null") throw SerializationException("Expected 'null' literal, got: '$s'")
        currentIndex++ // Advance past the structural token that contained "null"
        return null
    }
    
    // === Composite Decoding ===
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val currentTokenPos = structuralIndices.b(currentIndex)
        val startChar = input[currentTokenPos]

        val expectedChar = when (descriptor.kind) {
            StructureKind.LIST -> '['
            StructureKind.MAP, StructureKind.CLASS, StructureKind.OBJECT -> '{'
            else -> null // Or throw exception for unsupported kinds
        }

        if (expectedChar != null && startChar != expectedChar) {
            throw SerializationException("Expected '$expectedChar' for ${descriptor.kind} at $currentTokenPos, got '$startChar'")
        }

        advanceToNextStructural() // Consume the opening bracket/brace structural token itself

        // Return a new decoder instance for the substructure, starting at the advanced index.
        // The new decoder will have its own elementIndex starting from 0.
        val childDecoder = BitmapJsonDecoder(serializersModule, input, bitmapArray, structuralIndices, currentIndex)
        childDecoder.elementIndex = 0
        return childDecoder
    }
    
    override fun endStructure(descriptor: SerialDescriptor) {
        // This is called on the child decoder instance after all its elements are decoded.
        // currentIndex should now point to the closing bracket/brace of the structure.
        val currentTokenPos = structuralIndices.b(currentIndex)
        val endChar = input[currentTokenPos]

        val expectedChar = when (descriptor.kind) {
            StructureKind.LIST -> ']'
            StructureKind.MAP, StructureKind.CLASS, StructureKind.OBJECT -> '}'
            else -> null // Or throw exception
        }

        if (expectedChar != null && endChar != expectedChar) {
            throw SerializationException("Expected '$expectedChar' to end ${descriptor.kind} at $currentTokenPos, got '$endChar'")
        }
        
        advanceToNextStructural() // Consume the closing bracket/brace structural token.
                                  // The parent decoder will resume from this new currentIndex.
    }
    
    // decodeSequentially is true by default in AbstractDecoder. Good.
    // override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // `elementIndex` (from AbstractDecoder) is the index of the *next* element to be decoded.
        // `currentIndex` (our internal state) points to the structural token that *starts* or *precedes* this next element.

        val currentTokenAtIndex = structuralIndices.b(currentIndex)
        val char = input[currentTokenAtIndex]

        // First, check for end of structure markers ']' or '}'
        if (char == ']' || char == '}') {
            return CompositeDecoder.DECODE_DONE
        }

        // Delimiter handling based on elementIndex (0 for first, >0 for subsequent)
        // and descriptor kind (list vs map/object)
        when (descriptor.kind) {
            StructureKind.LIST -> {
                if (elementIndex > 0) { // If not the first element, expect a comma
                    if (char == ',') {
                        advanceToNextStructural() // Consume comma
                        // After comma, check if immediately followed by list terminator (e.g. trailing comma)
                        if (currentIndex < structuralIndices.size && input[structuralIndices.b(currentIndex)] == ']') {
                            return CompositeDecoder.DECODE_DONE // Lenient: allow trailing comma
                        }
                    } else {
                        // No comma and not ']', error for strict JSON
                        throw SerializationException("Expected ',' or ']' in list at $currentTokenAtIndex, found '$char'")
                    }
                }
                // If first element (elementIndex == 0), or after consuming a comma,
                // currentIndex now points to the start of the element itself (or token before primitive).
            }
            StructureKind.MAP, StructureKind.CLASS, StructureKind.OBJECT -> {
                if (elementIndex > 0) { // Not the first key-value pair
                    if (elementIndex % 2 == 0) { // Expecting a key (after a previous key-value pair)
                        if (char == ',') {
                            advanceToNextStructural() // Consume comma
                            // After comma, check if immediately followed by object terminator (e.g. trailing comma)
                             if (currentIndex < structuralIndices.size && input[structuralIndices.b(currentIndex)] == '}') {
                                return CompositeDecoder.DECODE_DONE // Lenient: allow trailing comma
                            }
                        } else {
                             // No comma and not '}', error for strict JSON
                            throw SerializationException("Expected ',' or '}' in object at $currentTokenAtIndex, found '$char'")
                        }
                    } else { // Expecting a value (after a key)
                        if (char == ':') {
                            advanceToNextStructural() // Consume colon
                        } else {
                            throw SerializationException("Expected ':' after map key at $currentTokenAtIndex, found '$char'")
                        }
                    }
                } else { // First element (elementIndex == 0), must be a key.
                    // No comma or colon to consume yet. currentIndex points to the start of the key (e.g. opening quote).
                }
            }
            else -> { /* No special delimiter handling for other kinds */ }
        }

        // If, after consuming delimiters, we are at the end of input or structure unexpectedly
        if (currentIndex >= structuralIndices.size || input[structuralIndices.b(currentIndex)] == ']' || input[structuralIndices.b(currentIndex)] == '}') {
            // This could happen if input ends abruptly after a comma/colon, or with trailing comma + end
            return CompositeDecoder.DECODE_DONE
        }

        return elementIndex++ // Return the element index to be decoded and increment it.
    }
    
    // === Bitmap Navigation Helpers (internal) ===

    // Removed getCurrentStructuralValue() as its logic is now split into getPrimitiveValueString() and direct handling for strings.
    // Removed extractValue() as it's replaced by more specific extraction.
    // Renamed extractStringValue to extractAndUnescapeString and modified it.
    // Removed extractPrimitiveValue as getPrimitiveValueString() covers it.

    private fun advanceToNextStructural() {
        currentIndex++
        if (currentIndex >= structuralIndices.size) {
            // This check can be useful for debugging, but might be too strict if input can end without closing structures.
            // Consider if an exception here is always appropriate.
            // For now, allow advancing past the end, subsequent checks will handle it.
        }
    }
}

/**
 * Factory for creating bitmap-based JSON decoders
 */
object BitmapJsonDecoderFactory {
    fun createDecoder(input: String): BitmapJsonDecoder {
        val (bitmapArray, structuralIndices) = scanJsonStructure(input)
        return BitmapJsonDecoder(
            serializersModule = EmptySerializersModule(),
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
    val decoder = BitmapJsonDecoderFactory.createDecoder(this)
    return decoder.decodeSerializableValue(serializer<T>())
}