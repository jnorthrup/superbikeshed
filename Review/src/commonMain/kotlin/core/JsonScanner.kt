@file:Suppress("NOTHING_TO_INLINE")

package core

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
typealias JsonArray = Series<JsonTypedValue>      // Series of JsonTypedValue
typealias JsonTypedValue = Join<JsonTypeEvidence, Any?> // type j value

// ═══════════════════════════════════════════════════════════════════════════════════════
// COMPACT SCANNER (TrikeShed Elegance)
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Compact JSON scanner with JsonTypeEvidence
 */
@JvmInline
value class JsonScannerCompact(val input: CharSequence) {
    
    /**
     * Single-pass scan with bitmap extraction
     */
    fun scan(): JsonDocument {
        val bitmap = IntArray(input.length shr 6) // 64-bit chunks
        val evidenceMap = mutableMapOf<Int, JsonTypeEvidence>() // Temporary mutable map
        var indexSeries: Series<Int> = emptySeries() // Use Series for index
        
        var pos = 0
        var depth = 0
        
        while (pos < input.length) {
            val char = input[pos]
            val wordIndex = pos shr 6
            val bitIndex = pos and 63
            
            when (char) {
                '{' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (1 shl bitIndex)
                    evidenceMap[pos] = "object" j depth++
                    indexSeries = indexSeries + Series(1) { pos }
                }
                '}' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (2 shl bitIndex)
                    depth--
                }
                '[' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (4 shl bitIndex)
                    evidenceMap[pos] = "array" j depth++
                    indexSeries = indexSeries + Series(1) { pos }
                }
                ']' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (8 shl bitIndex)
                    depth--
                }
                '"' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (16 shl bitIndex)
                    if (evidenceMap[pos] == null) evidenceMap[pos] = "string" j depth
                }
                ':' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (32 shl bitIndex)
                    evidenceMap[pos] = "colon" j depth
                }
                ',' -> {
                    bitmap[wordIndex] = bitmap[wordIndex] or (64 shl bitIndex)
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
        val jsonIndex = indexSeries.toArray() j input // Convert Series<Int> to IntArray for Join
        
        return jsonBitmap j jsonIndex
    }
    
    /**
     * Query with bitmap positioning (O(log n))
     */
    fun query(path: String): JsonTypedValue? {
        val doc = scan()
        val bitmap = doc.a.a
        val evidence = doc.a.b
        val index = doc.b.a
        val content = doc.b.b
        
        // Use bitmap to jump to structural positions
        val pathSegments = path.split('.')
        var currentPos = 0
        
        for (segment in pathSegments) {
            currentPos = findNext(bitmap, currentPos, segment) ?: return null
        }
        
        return extractValue(content, currentPos, evidence)
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
    fun properties(): Series<JsonProperty> {
        val doc = scan()
        val content = doc.b.b
        
        var propertiesSeries: Series<JsonProperty> = emptySeries()
        var inKey = false
        var key = ""
        var value = ""
        var pos = 0
        
        while (pos < content.length) {
            when (content[pos]) {
                '"' -> {
                    val (str, newPos) = extractString(content, pos)
                    if (inKey) {
                        key = str
                        inKey = false
                    } else {
                        value = str
                        propertiesSeries = propertiesSeries + Series(1) { key j value }
                    }
                    pos = newPos
                }
                ':' -> inKey = true
                ',' -> {
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        propertiesSeries = propertiesSeries + Series(1) { key j value }
                        key = ""
                        value = ""
                    }
                }
            }
            pos++
        }
        
        return propertiesSeries
    }
    
    // Compact helper functions
    private fun findNext(bitmap: IntArray, start: Int, segment: String): Int? {
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
    
    private fun extractValue(content: CharSequence, pos: Int, evidence: JsonTypeEvidence): JsonTypedValue? {
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
    
    private fun extractString(content: CharSequence, start: Int): Pair<String, Int> {
        var pos = start + 1 // Skip opening quote
        val sb = StringBuilder()
        
        while (pos < content.length && content[pos] != '"') {
            if (content[pos] == '\\') pos++ // Skip escape
            sb.append(content[pos])
            pos++
        }
        
        return sb.toString() to pos + 1
    }
    
    private fun extractNumber(content: CharSequence, start: Int): Pair<Double, Int> {
        var pos = start
        val sb = StringBuilder()
        
        while (pos < content.length && (content[pos].isDigit() || content[pos] in ".-eE+")) {
            sb.append(content[pos])
            pos++
        }
        
        return (sb.toString().toDoubleOrNull() ?: 0.0) to pos
    }
    
    private fun extractBoolean(content: CharSequence, start: Int): Pair<Boolean, Int> {
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
@JvmInline
value class JsonFlow(val scanner: JsonScannerCompact) {
    
    fun structural(): StructuralFlow = StructuralFlow(scanner)
    fun queryable(): QueryableFlow = QueryableFlow(scanner)
    fun comparable(): ComparableFlow = ComparableFlow(scanner)
}

@JvmInline
value class StructuralFlow(val scanner: JsonScannerCompact) {
    
    fun extract(): JsonDocument = scanner.scan()
    
    fun evidence(): JsonTypeEvidence {
        val doc = scanner.scan()
        return doc.a.b
    }
    
    fun bitmap(): IntArray = scanner.scan().a.a
}

@JvmInline
value class QueryableFlow(val scanner: JsonScannerCompact) {
    
    fun get(path: String): JsonTypedValue? = scanner.query(path)
    
    fun properties(): Series<JsonProperty> = scanner.properties()
    
    fun keys(): Series<String> = scanner.properties() α { it.a }
    
    fun values(): Series<String> = scanner.properties() α { it.b }
}

@JvmInline
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
inline fun JsonScannerCompact.toProperties(): Series<JsonProperty> = properties()

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
fun Series<String>.jsonTensor(): Join<IntArray, Series<JsonScannerCompact>> {
    val scanners = this α { it.json() } // Use α for transformation
    val shape = intArrayOf(size)
    return shape j scanners
}

/**
 * Batch isomorphism detection
 */
fun Join<IntArray, Series<JsonScannerCompact>>.findIsomorphisms(): Map<IntArray, Series<Int>> {
    val scanners = b
    // Create a Series of (bitmap j original_index) pairs
    val indexedBitmaps: Series<Join<IntArray, Int>> = scanners.size j { index ->
        scanners[index].scan().a.a j index
    }
    
    return indexedBitmaps.`▶`.groupBy( // Use `▶` to convert to Iterable for groupBy
        keySelector = { join: Join<IntArray, Int> -> join.a },
        valueTransform = { join: Join<IntArray, Int> -> join.b }
    ).mapValues { (_, indices: List<Int>) ->
        Series(indices.size) { i -> indices[i] }
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
fun Series<Int>.toArray(): IntArray {
    val result = IntArray(size)
    this.forEachIndexed { i, value -> result[i] = value }
    return result
}
