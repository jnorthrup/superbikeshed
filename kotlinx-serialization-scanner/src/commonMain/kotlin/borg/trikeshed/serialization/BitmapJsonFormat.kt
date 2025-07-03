@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.serialization

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * High-performance JSON format using bitmap scanning
 * Drop-in replacement for kotlinx.serialization.json.Json with better performance
 */
class BitmapJsonFormat(private val configuration: JsonConfiguration) : StringFormat {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Decode JSON string using bitmap scanning
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val decoder = createDecoder(string)
        return decoder.decodeSerializableValue(deserializer)
    }

    /**
     * Encode to JSON string (uses kotlinx.serialization for now)
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        // For encoding, use standard kotlinx.serialization.json for now
        // Could be optimized with bitmap-based encoding in the future
        val json = Json {
            ignoreUnknownKeys = configuration.ignoreUnknownKeys
            isLenient = configuration.isLenient
            allowStructuredMapKeys = configuration.allowStructuredMapKeys
            useArrayPolymorphism = configuration.useArrayPolymorphism
            classDiscriminator = configuration.classDiscriminator
            explicitNulls = configuration.explicitNulls
        }
        return json.encodeToString(serializer, value)
    }

    private fun createDecoder(string: String): BitmapJsonDecoder {
        val (bitmap, indices) = scanJsonStructure(string)
        return BitmapJsonDecoder(serializersModule, string, bitmap, indices)
    }
}

/**
 * Convenient extension functions for common use cases
 */

// Decode extensions
inline fun <reified T> BitmapJsonFormat.decodeFromString(string: String): T =
    decodeFromString(serializersModule.serializer(), string)

inline fun <reified T> String.decodeBitmapJson(format: BitmapJsonFormat): T =
    format.decodeFromString<T>(this)

// Encode extensions
inline fun <reified T> BitmapJsonFormat.encodeToString(value: T): String =
    encodeToString(serializersModule.serializer(), value)

inline fun <reified T> T.encodeBitmapJson(format: BitmapJsonFormat): String =
    format.encodeToString(this)

/**
 * Streaming JSON processing for large documents
 */
class BitmapJsonStream(private val format: BitmapJsonFormat) {

    /**
     * Parse JSON array elements one by one using streaming
     */
    inline fun <reified T> parseArrayStream(jsonArray: String): Sequence<T> = sequence {
        val scanner = StreamingBitmapScanner()
        val indices = scanner.scanChunk(jsonArray).let { scanner.getStructuralIndices() }

        var arrayDepth = 0
        var elementStart = -1
        var i = 0

        while (i < indices.size) {
            val pos = indices[i]
            val char = jsonArray[pos]

            when (char) {
                '[' -> {
                    arrayDepth++
                    if (arrayDepth == 1 && elementStart == -1) {
                        elementStart = pos + 1
                    }
                }
                ']' -> {
                    arrayDepth--
                    if (arrayDepth == 0 && elementStart != -1) {
                        val elementJson = jsonArray.substring(elementStart, pos).trim()
                        if (elementJson.isNotEmpty() && elementJson != ",") {
                            yield(elementJson.decodeBitmapJson<T>(format))
                        }
                    }
                }
                ',' -> {
                    if (arrayDepth == 1 && elementStart != -1) {
                        val elementJson = jsonArray.substring(elementStart, pos).trim()
                        if (elementJson.isNotEmpty()) {
                            yield(elementJson.decodeBitmapJson<T>(format))
                        }
                        elementStart = pos + 1
                    }
                }
            }
            i++
        }
    }

    /**
     * Parse JSON object properties one by one
     */
    inline fun <reified T> parseObjectStream(jsonObject: String): Sequence<Pair<String, T>> = sequence {
        // Implementation for streaming object parsing
        // Placeholder for now
    }
}


/**
 * JSON validation using bitmap scanning
 */
object BitmapJsonValidator {

    /**
     * Fast JSON validation without full parsing
     */
    fun isValidJson(json: String): Boolean {
        return try {
            val (_, indices) = scanJsonStructure(json)
            validateStructuralIntegrity(json, indices)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate structural integrity of JSON using bitmap indices
     */
    private fun validateStructuralIntegrity(json: String, indices: JsonStructuralSeries): Boolean {
        val stack = mutableListOf<Char>()
        var quoteState = false
        var escapeNext = false

        for (i in 0 until indices.size) {
            val pos = indices[i]
            val char = json[pos]

            when {
                escapeNext -> escapeNext = false
                char == '\\' && quoteState -> escapeNext = true
                char == '"' -> quoteState = !quoteState
                !quoteState -> {
                    when (char) {
                        '{', '[' -> stack.add(char)
                        '}' -> {
                            if (stack.isEmpty() || stack.removeLastOrNull() != '{') return false
                        }
                        ']' -> {
                            if (stack.isEmpty() || stack.removeLastOrNull() != '[') return false
                        }
                    }
                }
            }
        }

        return stack.isEmpty() && !quoteState
    }

    /**
     * Get detailed validation errors
     */
    fun getValidationErrors(json: String): List<String> {
        val errors = mutableListOf<String>()

        try {
            val (_, indices) = scanJsonStructure(json)
            // Detailed validation logic would go here
        } catch (e: Exception) {
            errors.add("Scanning failed: ${e.message}")
        }

        return errors
    }
}