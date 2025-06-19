package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

/**
 * Creates a lazy, tensor-native view of a pre-computed JSON bitmap using TrikeShed's Series<T>.
 *
 * @param input The raw UByteArray of JSON data.
 * @return A Series<UByte> where each element is a 4-bit pixel from the bitmap.
 *         The accessor function performs the necessary bit-shifting to read from the
 *         underlying ULongArray on demand.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun createBitmapAsSeries(input: UByteArray): Series<UByte> {
    // 1. Eagerly create the bitmap using the hyper-optimized `actual` implementation.
    val bitmapArray = JsonBitmapSimd.createBitmap(input)
    val inputSize = input.size

    // 2. Return a lazy Series view over the materialized array.
    return inputSize j { i ->
        val ulongIndex = i / 16
        val bitPosition = (i % 16) * 4
        
        // The accessor's logic is to simply read the pre-computed pixel.
        ((bitmapArray[ulongIndex] shr bitPosition) and 0b1111uL).toUByte()
    }
}

/**
 * Lightning-fast JSON parser using SIMD bitmap and Series<T> for TrikeShed integration.
 * Complete implementation with full functionality matching the original JsonParser.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object LightningJson {
    
    /**
     * Parse JSON string to structural bitmap using lightning-fast SIMD processing.
     */
    fun parseToBitmap(jsonString: String): Series<UByte> {
        val jsonBytes = jsonString.encodeToByteArray().toUByteArray()
        return createBitmapAsSeries(jsonBytes)
    }
    
    /**
     * Find all structural indices (opening/closing braces, brackets, commas) in JSON.
     */
    fun findStructuralIndices(jsonString: String): Series<Int> {
        val bitmap = parseToBitmap(jsonString)
        val indices = mutableListOf<Int>()
        
        bitmap.play.forEachIndexed { index, pixel ->
            val jsState = pixel.toInt() and 0b11
            if (jsState != JsonBitmapProcessor.JsStateEvent.Unchanged.ordinal) {
                indices.add(index)
            }
        }
        
        return indices.toSeries()
    }
    
    /**
     * Extract JSON values using Series<T> operations - pure TrikeShed style.
     */
    fun extractValues(jsonString: String): Series<String> {
        val structuralIndices = findStructuralIndices(jsonString)
        val jsonChars = jsonString.toSeries()
        
        // Simple value extraction between structural characters
        val values = mutableListOf<String>()
        var i = 0
        
        structuralIndices.play.zipWithNext().forEach { (start, end) ->
            val segment = jsonChars[start + 1 until end]
            val value = segment.play.joinToString("").trim()
            if (value.isNotEmpty() && value != ":") {
                values.add(value)
            }
        }
        
        return values.toSeries()
    }
    
    /**
     * Complete index implementation - indexes JSON structure and returns structural indices.
     */
    fun index(jsonString: String): JsonStructuralIndices {
        val chars = jsonString.toSeries()
        var depth = 0
        var openIdx = -1
        var closeIdx = -1
        val commaIdxs = mutableListOf<Int>()
        var insideQuote = false
        var escapeNextChar = false
        
        chars.play.forEachIndexed { i, char ->
            when {
                insideQuote -> when {
                    escapeNextChar -> escapeNextChar = false
                    char == '\\' -> escapeNextChar = true
                    char == '"' -> insideQuote = false
                }
                else -> when (char) {
                    '{', '[' -> {
                        depth++
                        if (depth == 1) openIdx = i
                    }
                    '}', ']' -> {
                        depth--
                        if (depth == 0) {
                            closeIdx = i
                            return@forEachIndexed
                        }
                    }
                    ',' -> if (depth == 1) {
                        commaIdxs.add(i)
                    }
                    '"' -> insideQuote = true
                }
            }
        }
        
        return (openIdx j closeIdx) j commaIdxs.toSeries()
    }
    
    /**
     * Complete reify implementation - converts JSON string to Kotlin objects.
     */
    fun reify(jsonString: String): Any? {
        val chars = jsonString.trim().toSeries()
        if (chars.size == 0) return null
        
        return when (chars[0]) {
            '{', '[' -> {
                val index = index(jsonString)
                val (openIdx, closeIdx) = index.a
                val commaIdxs = index.b
                
                val isObj = chars[0] == '{'
                
                if (commaIdxs.size == 0) {
                    // Empty object or array
                    return if (isObj) emptyMap<String, Any?>() else emptyList<Any?>()
                }
                
                // Combine indices for segment extraction
                val allIndices = mutableListOf<Int>()
                allIndices.add(openIdx)
                commaIdxs.play.forEach { allIndices.add(it) }
                allIndices.add(closeIdx)
                
                val segments = allIndices.zipWithNext().map { (start, end) ->
                    val segment = chars[start + 1 until end]
                    val trimmed = segment.play.joinToString("").trim()
                    if (isObj) {
                        parseKeyValuePair(trimmed)
                    } else {
                        reify(trimmed)
                    }
                }
                
                if (isObj) {
                    segments.associate { pair ->
                        val (key, value) = pair as Pair<String, Any?>
                        key to value
                    }
                } else {
                    segments
                }
            }
            '"' -> {
                // Parse string
                val content = chars.play.drop(1).takeWhile { it != '"' }.joinToString("")
                content
            }
            't' -> true
            'f' -> false
            'n' -> null
            else -> {
                // Parse number
                val content = chars.play.joinToString("")
                content.toDoubleOrNull() ?: content.toLongOrNull() ?: content
            }
        }
    }
    
    /**
     * Complete jsPath implementation - path-based JSON traversal.
     */
    fun jsPath(context: JsonParseContext, path: JsPath, reifyResult: Boolean = true): Any? {
        if (path.size == 0) {
            return if (reifyResult) reify(context.b.play.joinToString("")) else context
        }
        
        val (pathHead, pathTail) = path.first() j path.drop(1)
        
        return pathHead.fold(
            // String branch - object key lookup
            { key ->
                val (element, src) = context
                val segments = getSegments(element, src)
                
                for (segment in segments) {
                    val keyValue = parseKeyValuePair(segment.play.joinToString(""))
                    val (segmentKey, _) = keyValue
                    if (segmentKey == key) {
                        val newContext = segment j src
                        return jsPath(newContext, pathTail, reifyResult)
                    }
                }
                null
            },
            // Int branch - array index lookup
            { index ->
                val (element, src) = context
                val segments = getSegments(element, src)
                
                if (index < segments.size) {
                    val segment = segments[index]
                    val newContext = segment j src
                    return jsPath(newContext, pathTail, reifyResult)
                }
                null
            }
        )
    }
    
    /**
     * Complete stringify implementation - converts Kotlin objects to JSON string.
     */
    fun stringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escapeString(value)}\""
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Map<*, *> -> {
                val pairs = value.map { (k, v) ->
                    "\"${k}\":${stringify(v)}"
                }
                "{${pairs.joinToString(",")}}"
            }
            is List<*> -> {
                val elements = value.map { stringify(it) }
                "[${elements.joinToString(",")}]"
            }
            is Array<*> -> {
                val elements = value.map { stringify(it) }
                "[${elements.joinToString(",")}]"
            }
            else -> "\"${value.toString()}\""
        }
    }
    
    // Helper functions
    
    private fun parseKeyValuePair(json: String): Pair<String, Any?> {
        val colonIndex = json.indexOf(':')
        if (colonIndex == -1) throw IllegalArgumentException("Invalid key-value pair: $json")
        
        val key = json.substring(0, colonIndex).trim().removeSurrounding("\"")
        val value = json.substring(colonIndex + 1).trim()
        
        return key to reify(value)
    }
    
    private fun getSegments(element: JsonStructuralIndices, src: Series<Char>): Series<JsonSegment> {
        val (openIdx, closeIdx) = element.a
        val commaIdxs = element.b
        
        val allIndices = mutableListOf<Int>()
        allIndices.add(openIdx)
        commaIdxs.play.forEach { allIndices.add(it) }
        allIndices.add(closeIdx)
        
        return allIndices.zipWithNext().map { (start, end) ->
            (start j end) j src[start + 1 until end]
        }.toSeries()
    }
    
    private fun escapeString(str: String): String {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t")
    }
}

// Extension functions for convenience
fun String.toSeries(): Series<Char> = length j { index -> this[index] }
fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }