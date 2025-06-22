@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*
// Either type removed - using Result instead
import kotlin.jvm.JvmInline
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.α

/**
 * TrikeShed JSON Scanner - Production Implementation
 * Compliant with CLAUDE.md type system and Kotlin 2.1.0
 * 
 * Uses tensor-first columnar processing with zero-cost abstractions
 * Implements taxonomical typealiases for semantic clarity
 */

// Ontological Type Aliases - Permanent Definitions
typealias JsonChar = Char
typealias JsonPosition = Int
typealias JsonOffset = Int
typealias JsonLength = Int
typealias JsonDepth = Int
typealias JsonStringValue = String
typealias JsonNumberValue = Double
typealias JsonBooleanValue = Boolean
typealias JsonNullValue = Nothing?

// Core JSON Processing Types
typealias JsonCharSeries = Indexed<JsonChar>
typealias JsonTokenType = UByte
typealias JsonTokenPosition = Join<JsonPosition, JsonLength>
typealias JsonToken = Join<JsonTokenType, JsonTokenPosition>
typealias JsonTokenSeries = Indexed<JsonToken>

// Structural Analysis Types  
typealias JsonStructuralChar = Join<JsonChar, JsonPosition>
typealias JsonStructuralSeries = Indexed<JsonStructuralChar>
typealias JsonNestingLevel = Join<JsonDepth, JsonPosition>
typealias JsonNestingSeries = Indexed<JsonNestingLevel>

// Value Extraction Types
typealias JsonValueBounds = Join<JsonPosition, JsonPosition> // start j end
typealias JsonValueType = UByte
typealias JsonValue = Join<JsonValueType, JsonValueBounds>
typealias JsonValueSeries = Indexed<JsonValue>

// Error Handling Types
@JvmInline
value class JsonError(val message: String)
typealias JsonResult<T> = Result<T>

/**
 * JSON Token Types - Encoded as UByte for performance
 */
object JsonTokenTypes {
    const val LBRACE: JsonTokenType = 1u        // {
    const val RBRACE: JsonTokenType = 2u        // }
    const val LBRACKET: JsonTokenType = 3u      // [
    const val RBRACKET: JsonTokenType = 4u      // ]
    const val COLON: JsonTokenType = 5u         // :
    const val COMMA: JsonTokenType = 6u         // ,
    const val STRING: JsonTokenType = 7u        // "..."
    const val NUMBER: JsonTokenType = 8u        // 123, 123.45
    const val TRUE: JsonTokenType = 9u          // true
    const val FALSE: JsonTokenType = 10u        // false
    const val NULL: JsonTokenType = 11u         // null
    const val WHITESPACE: JsonTokenType = 12u   // \t\n\r space
}

/**
 * JSON Value Types - For value extraction
 */
object JsonValueTypes {
    const val OBJECT: JsonValueType = 1u
    const val ARRAY: JsonValueType = 2u
    const val STRING: JsonValueType = 3u
    const val NUMBER: JsonValueType = 4u
    const val BOOLEAN: JsonValueType = 5u
    const val NULL: JsonValueType = 6u
}

/**
 * TrikeShed JSON Scanner - Core Implementation
 * Uses Series<T> and Join<A,B> exclusively - no List<T> or Pair<A,B>
 */
object TrikeShedJsonScanner {
    
    /**
     * Scan JSON string into token series using α transforms
     */
    fun scan(jsonString: JsonStringValue): JsonResult<JsonTokenSeries> {
        if (jsonString.isEmpty()) return Result.success(emptySeries())
        
        val chars = jsonString.toList().toSeries()
        return Result.success(tokenize(chars))
    }
    
    /**
     * Tokenize character series using α transform - the ONLY transformation operator
     */
    private fun tokenize(chars: JsonCharSeries): JsonTokenSeries {
        val tokens = mutableListOf<JsonToken>()
        var pos = 0

        while (pos < chars.size) {
            val token = scanNextToken(chars, pos)
            tokens.add(token)
            pos += token.b.b.coerceAtLeast(1) // Advance by token length, ensuring progress
        }

        val tokenArray = tokens.toTypedArray()
        return tokenArray.size j tokenArray::get
    }

    private inline fun scanNextToken(chars: JsonCharSeries, pos: JsonPosition): JsonToken {
        val char = chars[pos]
        return when {
            char.isWhitespace() -> scanWhitespace(chars, pos)
            char == '{' -> JsonTokenTypes.LBRACE j (pos j 1)
            char == '}' -> JsonTokenTypes.RBRACE j (pos j 1)
            char == '[' -> JsonTokenTypes.LBRACKET j (pos j 1)
            char == ']' -> JsonTokenTypes.RBRACKET j (pos j 1)
            char == ':' -> JsonTokenTypes.COLON j (pos j 1)
            char == ',' -> JsonTokenTypes.COMMA j (pos j 1)
            char == '"' -> scanString(chars, pos)
            char.isDigit() || char == '-' -> scanNumber(chars, pos)
            char == 't' -> scanLiteral(chars, pos, "true", JsonTokenTypes.TRUE)
            char == 'f' -> scanLiteral(chars, pos, "false", JsonTokenTypes.FALSE)
            char == 'n' -> scanLiteral(chars, pos, "null", JsonTokenTypes.NULL)
            else -> JsonTokenTypes.WHITESPACE j (pos j 1) // Skip unknown chars
        }
    }
    
    /**
     * Scan whitespace using Join composition
     */
    private fun scanWhitespace(chars: JsonCharSeries, start: JsonPosition): JsonToken {
        var pos = start
        while (pos < chars.size && chars[pos].isWhitespace()) {
            pos++
        }
        return JsonTokenTypes.WHITESPACE j (start j (pos - start))
    }
    
    /**
     * Scan JSON string with proper escape handling
     */
    private fun scanString(chars: JsonCharSeries, start: JsonPosition): JsonToken {
        var pos = start + 1 // Skip opening quote
        var escaped = false
        
        while (pos < chars.size) {
            val char = chars[pos]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                pos++ // Include closing quote
                break
            }
            pos++
        }
        
        return JsonTokenTypes.STRING j (start j (pos - start))
    }
    
    /**
     * Scan JSON number (integer or decimal)
     */
    private fun scanNumber(chars: JsonCharSeries, start: JsonPosition): JsonToken {
        var pos = start
        
        // Handle negative sign
        if (pos < chars.size && chars[pos] == '-') pos++
        
        // Scan integer part
        while (pos < chars.size && chars[pos].isDigit()) pos++
        
        // Scan decimal part
        if (pos < chars.size && chars[pos] == '.') {
            pos++
            while (pos < chars.size && chars[pos].isDigit()) pos++
        }
        
        // Scan exponent part
        if (pos < chars.size && (chars[pos] == 'e' || chars[pos] == 'E')) {
            pos++
            if (pos < chars.size && (chars[pos] == '+' || chars[pos] == '-')) pos++
            while (pos < chars.size && chars[pos].isDigit()) pos++
        }
        
        return JsonTokenTypes.NUMBER j (start j (pos - start))
    }
    
    /**
     * Scan JSON literal (true, false, null)
     */
    private fun scanLiteral(
        chars: JsonCharSeries, 
        start: JsonPosition, 
        literal: String, 
        tokenType: JsonTokenType
    ): JsonToken {
        val end = start + literal.length
        return if (end <= chars.size && 
                   literal.withIndex().all { (i, c) -> chars[start + i] == c }) {
            tokenType j (start j literal.length)
        } else {
            JsonTokenTypes.WHITESPACE j (start j 1) // Invalid literal, skip
        }
    }
    
    /**
     * Extract structural characters using α transform
     */
    fun extractStructuralChars(tokens: JsonTokenSeries): JsonStructuralSeries {
        return tokens.α { token ->
            val (type, bounds) = token
            val char = when (type) {
                JsonTokenTypes.LBRACE -> '{'
                JsonTokenTypes.RBRACE -> '}'
                JsonTokenTypes.LBRACKET -> '['
                JsonTokenTypes.RBRACKET -> ']'
                JsonTokenTypes.COLON -> ':'
                JsonTokenTypes.COMMA -> ','
                else -> ' ' // Non-structural
            }
            char j bounds.a
        }
    }
    
    /**
     * Analyze nesting levels using α transforms
     */
    fun analyzeNesting(structuralChars: JsonStructuralSeries): JsonNestingSeries {
        var depth = 0
        return structuralChars.α { (char, pos) ->
            when (char) {
                '{', '[' -> {
                    val currentDepth = depth
                    depth++
                    currentDepth j pos
                }
                '}', ']' -> {
                    depth = maxOf(0, depth - 1)
                    depth j pos
                }
                else -> depth j pos
            }
        }
    }
    
    /**
     * Extract values from token series using α transforms
     */
    fun extractValues(tokens: JsonTokenSeries, jsonString: JsonStringValue): JsonValueSeries {
        return tokens.α { token ->
            val (type, bounds) = token
            val valueType = when (type) {
                JsonTokenTypes.LBRACE -> JsonValueTypes.OBJECT
                JsonTokenTypes.LBRACKET -> JsonValueTypes.ARRAY
                JsonTokenTypes.STRING -> JsonValueTypes.STRING
                JsonTokenTypes.NUMBER -> JsonValueTypes.NUMBER
                JsonTokenTypes.TRUE, JsonTokenTypes.FALSE -> JsonValueTypes.BOOLEAN
                JsonTokenTypes.NULL -> JsonValueTypes.NULL
                else -> JsonValueTypes.NULL
            }
            val startPos = bounds.a
            val endPos = startPos + bounds.b
            valueType j (startPos j endPos)
        }
    }
    
    /**
     * Get string value from bounds using zero-cost abstraction
     */
    inline fun getStringValue(
        jsonString: JsonStringValue, 
        bounds: JsonValueBounds
    ): JsonStringValue {
        val (start, end) = bounds
        return jsonString.substring(start, minOf(end, jsonString.length))
    }
    
    /**
     * Parse number value with proper error handling
     */
    fun parseNumberValue(jsonString: JsonStringValue, bounds: JsonValueBounds): JsonResult<JsonNumberValue> {
        return try {
            val str = getStringValue(jsonString, bounds)
            Result.success(str.toDouble())
        } catch (e: NumberFormatException) {
            Result.failure(Exception("Invalid number format"))
        }
    }
    
    /**
     * Parse boolean value
     */
    fun parseBooleanValue(jsonString: JsonStringValue, bounds: JsonValueBounds): JsonBooleanValue {
        val str = getStringValue(jsonString, bounds)
        return str == "true"
    }
    
    /**
     * Materialize tokens to List using play operator - gateway to AbstractList
     */
    fun materializeTokens(tokens: JsonTokenSeries): List<JsonToken> {
        return tokens.play.toList()
    }
    
    /**
     * Filter tokens by type using α transform
     */
    fun filterTokensByType(tokens: JsonTokenSeries, targetType: JsonTokenType): JsonTokenSeries {
        return tokens.play.filter { it.a == targetType }.toSeries()
    }
}

/**
 * Extension functions for convenient JSON processing
 */
fun JsonStringValue.scanJson(): JsonResult<JsonTokenSeries> = 
    TrikeShedJsonScanner.scan(this)

fun JsonTokenSeries.extractStructural(): JsonStructuralSeries = 
    TrikeShedJsonScanner.extractStructuralChars(this)

fun JsonStructuralSeries.analyzeNesting(): JsonNestingSeries = 
    TrikeShedJsonScanner.analyzeNesting(this)

fun JsonTokenSeries.extractValues(jsonString: JsonStringValue): JsonValueSeries = 
    TrikeShedJsonScanner.extractValues(this, jsonString)

/**
 * Utility functions for Series operations
 */
private fun <T> Array<T>.toSeries(): Indexed<T> = size j ::get
private fun <T> List<T>.toSeries(): Indexed<T> = size j ::get
private fun <T> emptySeries(): Indexed<T> = 0 j { throw IndexOutOfBoundsException("Empty series") }

/**
 * Example usage demonstrating TrikeShed patterns
 */
object JsonScannerExample {
    fun demonstrateScanning() {
        val jsonText = """{"name":"TrikeShed","version":1.0,"active":true}"""
        
        // Scan using α transforms and Join composition
        val scanResult = jsonText.scanJson()
        
        scanResult.fold(
            onSuccess = { tokens ->
                // Extract structural information using α transforms
                val structural = tokens.extractStructural()
                val nesting = structural.analyzeNesting()
                val values = tokens.extractValues(jsonText)
                
                // Use play operator only for final materialization
                println("Tokens: ${tokens.play.toList()}")
                println("Nesting levels: ${nesting.play.toList()}")
                println("Values: ${values.play.toList()}")
            },
            onFailure = { error ->
                println("Scan error: ${error.message}")
            }
        )
    }
}