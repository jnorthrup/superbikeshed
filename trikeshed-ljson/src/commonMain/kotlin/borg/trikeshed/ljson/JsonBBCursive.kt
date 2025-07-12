@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import borg.trikeshed.io.*
import com.v2superbikeshed.common.autovec.Vectorizable

/**
 * BBCursive JSON Parser for TrikeShed
 * 
 * High-performance JSON parser using BBCursive patterns with zero-copy operations.
 * Integrates with the TrikeShed type system and cursor ecosystem.
 */

/**
 * JSON parse result with position tracking
 */
data class JsonParseResult(
    val element: JsonElement?,
    val success: Boolean,
    val position: Int,
    val errorMessage: String? = null
)

/**
 * BBCursive-style parser operator
 */
fun interface JsonParser {
    fun parse(buffer: ByteIndexed, pos: Int): Join<JsonElement?, Int>? // result j new_position
}

/**
 * BBCursive JSON parser implementation
 */
object JsonBBCursive {
    
    // Character matchers
    @Vectorizable
    internal inline fun char(expected: Byte): JsonParser = JsonParser { buffer, pos ->
        if (pos < buffer.component1() && buffer.component2()(pos) == expected) {
            null j (pos + 1)
        } else null
    }
    
    @Vectorizable
    internal inline fun whitespace(): JsonParser = JsonParser { buffer, pos ->
        var p = pos
        while (p < buffer.component1()) {
            val c = buffer.component2()(p)
            if (c == ' '.code.toByte() || c == '\t'.code.toByte() || 
                c == '\n'.code.toByte() || c == '\r'.code.toByte()) {
                p++
            } else break
        }
        null j p
    }
    
    // String parser
    @Vectorizable
    internal fun string(): JsonParser = JsonParser { buffer, pos ->
        if (pos >= buffer.component1() || buffer.component2()(pos) != '"'.code.toByte()) return@JsonParser null
        
        var p = pos + 1
        val chars = mutableListOf<Byte>()
        
        while (p < buffer.component1()) {
            val c = buffer.component2()(p)
            when (c) {
                '"'.code.toByte() -> {
                    // End of string
                    val result = JsonElement.Str(String(chars.toByteArray(), Charsets.UTF_8))
                    return@JsonParser result j (p + 1)
                }
                '\\'.code.toByte() -> {
                    // Escape sequence
                    p++
                    if (p >= buffer.component1()) break
                    val escaped = buffer.component2()(p)
                    chars.add(when (escaped) {
                        '"'.code.toByte() -> '"'.code.toByte()
                        '\\'.code.toByte() -> '\\'.code.toByte()
                        '/'.code.toByte() -> '/'.code.toByte()
                        'b'.code.toByte() -> '\b'.code.toByte()
                        'f'.code.toByte() -> '\u000C'.code.toByte()
                        'n'.code.toByte() -> '\n'.code.toByte()
                        'r'.code.toByte() -> '\r'.code.toByte()
                        't'.code.toByte() -> '\t'.code.toByte()
                        'u'.code.toByte() -> {
                            // Unicode escape - simplified
                            '?'.code.toByte() // Placeholder
                        }
                        else -> escaped
                    })
                    p++
                }
                else -> {
                    chars.add(c)
                    p++
                }
            }
        }
        null // Unterminated string
    }
    
    // Number parser
    @Vectorizable
    internal fun number(): JsonParser = JsonParser { buffer, pos ->
        if (pos >= buffer.component1()) return@JsonParser null
        
        var p = pos
        val chars = mutableListOf<Byte>()
        
        // Optional minus
        if (p < buffer.component1() && buffer.component2()(p) == '-'.code.toByte()) {
            chars.add(buffer.component2()(p))
            p++
        }
        
        // Must have at least one digit
        if (p >= buffer.component1() || !isDigit(buffer.component2()(p))) return@JsonParser null
        
        // Integer part
        if (buffer.component2()(p) == '0'.code.toByte()) {
            chars.add(buffer.component2()(p))
            p++
        } else {
            while (p < buffer.component1() && isDigit(buffer.component2()(p))) {
                chars.add(buffer.component2()(p))
                p++
            }
        }
        
        // Fractional part
        if (p < buffer.component1() && buffer.component2()(p) == '.'.code.toByte()) {
            chars.add(buffer.component2()(p))
            p++
            if (p >= buffer.component1() || !isDigit(buffer.component2()(p))) return@JsonParser null
            while (p < buffer.component1() && isDigit(buffer.component2()(p))) {
                chars.add(buffer.component2()(p))
                p++
            }
        }
        
        // Exponent part
        if (p < buffer.component1() && (buffer.component2()(p) == 'e'.code.toByte() || buffer.component2()(p) == 'E'.code.toByte())) {
            chars.add(buffer.component2()(p))
            p++
            if (p < buffer.component1() && (buffer.component2()(p) == '+'.code.toByte() || buffer.component2()(p) == '-'.code.toByte())) {
                chars.add(buffer.component2()(p))
                p++
            }
            if (p >= buffer.component1() || !isDigit(buffer.component2()(p))) return@JsonParser null
            while (p < buffer.component1() && isDigit(buffer.component2()(p))) {
                chars.add(buffer.component2()(p))
                p++
            }
        }
        
        val numberStr = String(chars.toByteArray(), Charsets.UTF_8)
        val value = numberStr.toDoubleOrNull() ?: return@JsonParser null
        JsonElement.Num(value) j p
    }
    
    // Literal parsers
    @Vectorizable
    internal inline fun literal(text: String, element: JsonElement): JsonParser = JsonParser { buffer, pos ->
        val bytes = text.encodeToByteArray()
        if (pos + bytes.size <= buffer.component1()) {
            for (i in bytes.indices) {
                if (buffer.component2()(pos + i) != bytes[i]) return@JsonParser null
            }
            element j (pos + bytes.size)
        } else null
    }
    
    internal fun null_(): JsonParser = literal("null", JsonElement.Null)
    internal fun true_(): JsonParser = literal("true", JsonElement.Bool(true))
    internal fun false_(): JsonParser = literal("false", JsonElement.Bool(false))
    
    // Forward declarations for recursive parsers
    internal lateinit var valueParser: JsonParser
    
    // Array parser
    internal fun array(): JsonParser = JsonParser { buffer, pos ->
        var p = pos
        
        // Parse '['
        char('['.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
        
        // Skip whitespace
        whitespace().parse(buffer, p)?.let { p = it.component2() }
        
        // Check for empty array
        char(']'.code.toByte()).parse(buffer, p)?.let { 
            val emptyArray = JsonElement.Arr(0 j { _: Int -> JsonElement.Null })
            return@JsonParser emptyArray j it.component2()
        }
        
        // Parse elements
        val elements = mutableListOf<JsonElement>()
        
        // First element
        valueParser.parse(buffer, p)?.let { (element, newPos) ->
            element?.let { elements.add(it) }
            p = newPos
        } ?: return@JsonParser null
        
        // Remaining elements
        while (true) {
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            
            // Try comma
            char(','.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: break
            
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            
            // Parse next element
            valueParser.parse(buffer, p)?.let { (element, newPos) ->
                element?.let { elements.add(it) }
                p = newPos
            } ?: return@JsonParser null
        }
        
        whitespace().parse(buffer, p)?.let { p = it.component2() }
        char(']'.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
        
        val indexedElements = elements.size j { i: Int -> elements[i] }
        JsonElement.Arr(indexedElements) j p
    }
    
    // Object parser
    internal fun object_(): JsonParser = JsonParser { buffer, pos ->
        var p = pos
        
        // Parse '{'
        char('{'.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
        
        whitespace().parse(buffer, p)?.let { p = it.component2() }
        
        // Check for empty object
        char('}'.code.toByte()).parse(buffer, p)?.let {
            val emptyObj = JsonElement.Obj(0 j { _: Int -> "" j JsonElement.Null })
            return@JsonParser emptyObj j it.component2()
        }
        
        // Parse members
        val fields = mutableListOf<Join<String, JsonElement>>()
        
        // First member
        whitespace().parse(buffer, p)?.let { p = it.component2() }
        
        string().parse(buffer, p)?.let { (keyElement, newPos) ->
            val key = (keyElement as? JsonElement.Str)?.value ?: return@JsonParser null
            p = newPos
            
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            char(':'.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            
            valueParser.parse(buffer, p)?.let { (value, newPos2) ->
                value?.let { fields.add(key j it) }
                p = newPos2
            } ?: return@JsonParser null
        } ?: return@JsonParser null
        
        // Remaining members
        while (true) {
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            
            // Try comma
            char(','.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: break
            
            whitespace().parse(buffer, p)?.let { p = it.component2() }
            
            // Parse key
            string().parse(buffer, p)?.let { (keyElement, newPos) ->
                val key = (keyElement as? JsonElement.Str)?.value ?: return@JsonParser null
                p = newPos
                
                whitespace().parse(buffer, p)?.let { p = it.component2() }
                char(':'.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
                whitespace().parse(buffer, p)?.let { p = it.component2() }
                
                valueParser.parse(buffer, p)?.let { (value, newPos2) ->
                    value?.let { fields.add(key j it) }
                    p = newPos2
                } ?: return@JsonParser null
            } ?: return@JsonParser null
        }
        
        whitespace().parse(buffer, p)?.let { p = it.component2() }
        char('}'.code.toByte()).parse(buffer, p)?.let { p = it.component2() } ?: return@JsonParser null
        
        val indexedFields = fields.size j { i: Int -> fields[i] }
        JsonElement.Obj(indexedFields) j p
    }
    
    // Value parser (handles all JSON values)
    internal fun value(): JsonParser = JsonParser { buffer, pos ->
        whitespace().parse(buffer, pos)?.let { (_, p1) ->
            string().parse(buffer, p1)
                ?: number().parse(buffer, p1)
                ?: true_().parse(buffer, p1)
                ?: false_().parse(buffer, p1)
                ?: null_().parse(buffer, p1)
                ?: array().parse(buffer, p1)
                ?: object_().parse(buffer, p1)
        }
    }
    
    // Initialize recursive reference
    init {
        valueParser = value()
    }
    
    // Main parse function
    fun parse(json: String): JsonParseResult {
        val bytes = json.encodeToByteArray()
        val buffer = bytes.size j { i: Int -> bytes[i] }
        
        return try {
            val result = valueParser.parse(buffer, 0)
            if (result != null) {
                val (element, finalPos) = result
                whitespace().parse(buffer, finalPos)?.let { (_, endPos) ->
                    if (endPos == buffer.component1()) {
                        JsonParseResult(element, true, endPos)
                    } else {
                        JsonParseResult(null, false, endPos, "Unexpected characters after JSON")
                    }
                } ?: JsonParseResult(element, finalPos == buffer.component1(), finalPos)
            } else {
                JsonParseResult(null, false, 0, "Failed to parse JSON")
            }
        } catch (e: Exception) {
            JsonParseResult(null, false, 0, e.message)
        }
    }
    
    // Helper functions
    internal fun isDigit(b: Byte): Boolean = 
        b >= '0'.code.toByte() && b <= '9'.code.toByte()
}

/**
 * BBCursive JSON provider implementation
 */
class BBCursiveJsonProvider : JsonProvider {
    
    override fun parse(json: String): JsonResult<JsonElement> {
        val result = JsonBBCursive.parse(json)
        return if (result.success && result.element != null) {
            result.element j null
        } else {
            null j (result.errorMessage ?: "Parse failed")
        }
    }
    
    override fun stringify(element: JsonElement): String = 
        ThinJsonProvider().stringify(element) // Delegate to thin provider
    
    override fun <T> decode(json: String, deserializer: DeserializationStrategy<T>): JsonResult<T> =
        ThinJsonProvider().decode(json, deserializer) // Delegate to thin provider
    
    override fun <T> encode(value: T, serializer: SerializationStrategy<T>): String =
        ThinJsonProvider().encode(value, serializer) // Delegate to thin provider
}