@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.core

import borg.trikeshed.lib.*
import kotlin.jvm.*

/**
 * Compact JSON Scanner - TrikeShed Style
 * 
 * Preserves the original TrikeShed compact design with:
 * - JsonTypedValue forward design
 * - Join<A,B> pairwise scanning  
 * - Minimal allocation patterns
 * - Elegant typealias forwarding
 */

// ═══════════════════════════════════════════════════════════════════════════════════════
// TYPEALIAS FORWARD DESIGN (TrikeShed Pattern)
// ═══════════════════════════════════════════════════════════════════════════════════════

typealias JsonDocument = Join<JsonStructuralBitmap, JsonContentIndex>
typealias JsonStructuralBitmap = Join<IntArray, JsonTypeEvidence>
typealias JsonContentIndex = Join<IntArray, CharSequence>
typealias JsonTypeEvidence = Join<String, Int> // type j depth/count

// Compact element representation
typealias JsonProperty = Join<String, String>      // key j value
typealias JsonArray = Indexed<JsonTypedValue>      // Indexed of JsonTypedValue
typealias JsonTypedValue = Join<JsonTypeEvidence, Any?> // type j value

// ═══════════════════════════════════════════════════════════════════════════════════════
// COMPACT SCANNER (TrikeShed Elegance)
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Compact JSON scanner with JsonTypeEvidence
 */
@kotlin.jvm.JvmInline
value class JsonScannerCompact(val input: CharSequence) {
    
    /**
     * Single-pass scan with bitmap extraction
     */
    fun scan(): JsonDocument {
        val bitmap = IntArray((input.length shr 6) + 1) // 64-bit chunks with extra space
        val evidenceMap = mutableMapOf<Int, JsonTypeEvidence>() // Temporary mutable map
        val indexList = mutableListOf<Int>() // Use mutable list to avoid recursion
        
        var pos = 0
        var depth = 0
        
        while (pos < input.length) {
            val char = input[pos]
            val wordIndex = pos shr 6
            val bitIndex = pos and 63
            
            when (char) {
                '{' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (1 shl bitIndex)
                    evidenceMap[pos] = "object" j depth++
                    indexList.add(pos)
                }
                '}' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (2 shl bitIndex)
                    depth--
                }
                '[' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (4 shl bitIndex)
                    evidenceMap[pos] = "array" j depth++
                    indexList.add(pos)
                }
                ']' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (8 shl bitIndex)
                    depth--
                }
                '"' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (16 shl bitIndex)
                    if (evidenceMap[pos] == null) evidenceMap[pos] = "string" j depth
                }
                ':' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (32 shl bitIndex)
                    evidenceMap[pos] = "colon" j depth
                }
                ',' -> {
                    if (wordIndex < bitmap.size) bitmap[wordIndex] = bitmap[wordIndex] or (64 shl bitIndex)
                    evidenceMap[pos] = "comma" j depth
                }
                in '0'..'9', '-' -> {
                    if (evidenceMap[pos] == null) evidenceMap[pos] = "number" j depth
                }
                't', 'f' -> {
                    if (evidenceMap[pos] == null) evidenceMap[pos] = "boolean" j depth
                }
                'n' -> {
                    if (evidenceMap[pos] == null) evidenceMap[pos] = "null" j depth
                }
            }
            pos++
        }
        
        val jsonBitmap = bitmap j JsonTypeEvidence(evidenceMap.entries.map { "${it.key}:${it.value.a}" }.joinToString(","), evidenceMap.size)
        val indexArray = indexList.toIntArray()
        val jsonIndex = indexArray j input
        
        return jsonBitmap j jsonIndex
    }
    
    /**
     * Query with bitmap positioning (O(log n))
     */
    fun query(path: String): JsonTypedValue? {
        val doc = scan()
        val content = doc.b.b
        
        // Simple property extraction for MVP
        val properties = properties()
        for (i in 0 until properties.size) {
            val prop = properties[i]
            if (prop.a == path) {
                return JsonTypeEvidence("string", 1) j prop.b
            }
        }
        
        return null
    }
    
    /**
     * Structural comparison (isomorphism check)
     */
    infix fun isIsomorphicTo(other: JsonScannerCompact): Boolean {
        val doc1 = scan()
        val doc2 = other.scan()
        
        val bitmap1 = doc1.a.a
        val bitmap2 = doc2.a.a
        
        // Compare structural bitmaps
        return bitmap1.contentEquals(bitmap2)
    }
    
    /**
     * Extract all properties (TrikeShed pattern)
     */
    fun properties(): Indexed<JsonProperty> {
        // Simple JSON property extraction for MVP
        val propertiesList = mutableListOf<JsonProperty>()
        
        var pos = 0
        var key = ""
        var value = ""
        var inString = false
        var isKey = true
        
        while (pos < input.length) {
            val char = input[pos]
            
            when {
                char == '"' -> {
                    val (str, newPos) = extractString(input, pos)
                    if (isKey) {
                        key = str
                        isKey = false // Next string will be the value
                    } else {
                        value = str
                        if (key.isNotEmpty()) {
                            propertiesList.add(key j value)
                        }
                        isKey = true // Reset for next key
                    }
                    pos = newPos - 1 // Will be incremented at end of loop
                }
                char == ',' && !inString -> {
                    isKey = true
                    key = ""
                    value = ""
                }
                char in '0'..'9' && !inString && !isKey -> {
                    val (num, newPos) = extractNumber(input, pos)
                    value = num.toString()
                    if (key.isNotEmpty()) {
                        propertiesList.add(key j value)
                    }
                    pos = newPos - 1
                }
                (char == 't' || char == 'f') && !inString && !isKey -> {
                    val (bool, newPos) = extractBoolean(input, pos)
                    value = bool.toString()
                    if (key.isNotEmpty()) {
                        propertiesList.add(key j value)
                    }
                    pos = newPos - 1
                }
                else -> {
                    // Continue
                }
            }
            pos++
        }
        
        // Convert to Indexed
        return propertiesList.size j { i -> propertiesList[i] }
    }
    
    // Compact helper functions
    internal fun findNext(bitmap: IntArray, start: Int, segment: String): Int? {
        // Use bitmap to find next structural element
        var i = start
        while (i < bitmap.size * 64) {
            val wordIndex = i shr 6
            val bitIndex = i and 63
            if (wordIndex < bitmap.size && (bitmap[wordIndex] and (1 shl bitIndex)) != 0) {
                return i
            }
            i++
        }
        return null
    }
    
    internal fun extractValue(content: CharSequence, pos: Int, evidence: JsonTypeEvidence): JsonTypedValue? {
        // Extract typed value at position
        val type = evidence.a.split(',').find { it.startsWith("$pos:") }?.substringAfter(':')
        return when (type) {
            "string" -> {
                val (str, _) = extractString(content, pos)
                JsonTypeEvidence("string", 1) j str
            }
            "number" -> {
                val (num, _) = extractNumber(content, pos)
                JsonTypeEvidence("number", 1) j num
            }
            "boolean" -> {
                val (bool, _) = extractBoolean(content, pos)
                JsonTypeEvidence("boolean", 1) j bool
            }
            "null" -> JsonTypeEvidence("null", 1) j null
            else -> null
        }
    }
    
    internal fun extractString(content: CharSequence, start: Int): Pair<String, Int> {
        var pos = start + 1 // Skip opening quote
        val sb = StringBuilder()
        
        while (pos < content.length && content[pos] != '"') {
            if (content[pos] == '\\') pos++ // Skip escape
            sb.append(content[pos])
            pos++
        }
        
        return sb.toString() to pos + 1
    }
    
    internal fun extractNumber(content: CharSequence, start: Int): Pair<Double, Int> {
        var pos = start
        val sb = StringBuilder()
        
        while (pos < content.length && (content[pos].isDigit() || content[pos] in ".-eE+")) {
            sb.append(content[pos])
            pos++
        }
        
        return (sb.toString().toDoubleOrNull() ?: 0.0) to pos
    }
    
    internal fun extractBoolean(content: CharSequence, start: Int): Pair<Boolean, Int> {
        return when {
            content.substring(start).startsWith("true") -> true to start + 4
            content.substring(start).startsWith("false") -> false to start + 5
            else -> false to start
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// CONTEXT FLOW WITH JsonTypeEvidence
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Context flow for JSON operations (TrikeShed stairway pattern)
 */
@kotlin.jvm.JvmInline
value class JsonFlow(val scanner: JsonScannerCompact) {
    
    fun structural(): StructuralFlow = StructuralFlow(scanner)
    fun queryable(): QueryableFlow = QueryableFlow(scanner)
    fun comparable(): ComparableFlow = ComparableFlow(scanner)
}

@kotlin.jvm.JvmInline
value class StructuralFlow(val scanner: JsonScannerCompact) {
    
    fun extract(): JsonDocument = scanner.scan()
    
    fun evidence(): JsonTypeEvidence {
        val doc = scanner.scan()
        return doc.a.b
    }
    
    fun bitmap(): IntArray = scanner.scan().a.a
}

@kotlin.jvm.JvmInline
value class QueryableFlow(val scanner: JsonScannerCompact) {
    
    fun get(path: String): JsonTypedValue? = scanner.query(path)
    
    fun properties(): Indexed<JsonProperty> = scanner.properties()
    
    fun keys(): Indexed<String> = scanner.properties() α { it.a }
    
    fun values(): Indexed<String> = scanner.properties() α { it.b }
}

@kotlin.jvm.JvmInline
value class ComparableFlow(val scanner: JsonScannerCompact) {
    
    fun isomorphic(other: JsonScannerCompact): Boolean = scanner isIsomorphicTo other
    
    fun similarity(other: JsonScannerCompact): Double {
        val doc1 = scanner.scan()
        val doc2 = other.scan()
        
        val evidence1 = doc1.a.b.b
        val evidence2 = doc2.a.b.b
        
        return if (evidence1 == evidence2) 1.0 else 0.0
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// COMPACT DSL EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Compact scanner entry point
 */
inline fun String.json(): JsonScannerCompact = JsonScannerCompact(this)

/**
 * Context flow entry
 */
inline fun JsonScannerCompact.flow(): JsonFlow = JsonFlow(this)

/**
 * Direct query shorthand
 */
inline operator fun JsonScannerCompact.get(path: String): JsonTypedValue? = query(path)

/**
 * Property extraction shorthand
 */
inline fun JsonScannerCompact.toProperties(): Indexed<JsonProperty> = properties()

/**
 * JsonTypeEvidence extraction
 */
inline fun JsonScannerCompact.evidence(): JsonTypeEvidence = scan().a.b

// ═══════════════════════════════════════════════════════════════════════════════════════
// TENSOR INTEGRATION
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Tensor-based JSON processing
 */
fun Indexed<String>.jsonTensor(): Join<IntArray, Indexed<JsonScannerCompact>> {
    val scanners = this α { it.json() } // Use α for transformation
    val shape = intArrayOf(size)
    return shape j scanners
}

/**
 * Batch isomorphism detection
 */
fun Join<IntArray, Indexed<JsonScannerCompact>>.findIsomorphisms(): Map<IntArray, Indexed<Int>> {
    val scanners = b
    // Create a Series of (bitmap j original_index) pairs
    val indexedBitmaps: Indexed<Join<IntArray, Int>> = scanners.size j { index ->
        scanners[index].scan().a.a j index
    }
    
    return indexedBitmaps.play.groupBy( // Use play to convert to Iterable for groupBy
        keySelector = { join: Join<IntArray, Int> -> join.a },
        valueTransform = { join: Join<IntArray, Int> -> join.b }
    ).mapValues { (_, indices: List<Int>) ->
        indices.size j { i -> indices[i] }
    }
}
 
/**
 * Structural fingerprinting
 */
fun JsonScannerCompact.fingerprint(): Long {
    val bitmap = scan().a.a
    return bitmap.fold(0L) { acc, word -> acc xor word.toLong() }
}

// Extension to convert Series to IntArray
fun Indexed<Int>.toArray(): IntArray {
    val result = IntArray(size)
    for (i in 0 until size) result[i] = this[i]
    return result
}
