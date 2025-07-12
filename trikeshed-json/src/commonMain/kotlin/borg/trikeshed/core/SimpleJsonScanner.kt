package borg.trikeshed.core

import borg.trikeshed.lib.*
import kotlin.jvm.*

/**
 * Simple JSON Scanner - TrikeShed Style without bitmap scanning
 * 
 * Lightweight JSON parsing with:
 * - Direct string-to-value parsing
 * - Join<A,B> pairwise structure  
 * - Minimal allocation patterns
 * - No bitmap overhead
 */

// ═══════════════════════════════════════════════════════════════════════════════════════
// SIMPLE TYPEALIAS DESIGN (TrikeShed Pattern without bitmap)
// ═══════════════════════════════════════════════════════════════════════════════════════

typealias SimpleJsonDocument = Join<Indexed<JsonProperty>, CharSequence>
typealias SimpleJsonValue = Join<String, Any?> // type j value

// ═══════════════════════════════════════════════════════════════════════════════════════
// SIMPLE SCANNER (TrikeShed Elegance - No Bitmap)
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Simple JSON scanner without bitmap overhead
 */
@kotlin.jvm.JvmInline
value class SimpleJsonScanner(val input: CharSequence) {
    
    /**
     * Direct parse without bitmap extraction
     */
    fun scan(): SimpleJsonDocument {
        val propertiesList = mutableListOf<JsonProperty>()
        
        var pos = 0
        var key = ""
        var value = ""
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
                char == ',' && !isKey -> {
                    isKey = true
                    key = ""
                    value = ""
                }
                char in '0'..'9' && !isKey -> {
                    val (num, newPos) = extractNumber(input, pos)
                    value = num.toString()
                    if (key.isNotEmpty()) {
                        propertiesList.add(key j value)
                    }
                    pos = newPos - 1
                    isKey = true
                }
                (char == 't' || char == 'f') && !isKey -> {
                    val (bool, newPos) = extractBoolean(input, pos)
                    value = bool.toString()
                    if (key.isNotEmpty()) {
                        propertiesList.add(key j value)
                    }
                    pos = newPos - 1
                    isKey = true
                }
                char == 'n' && !isKey -> {
                    if (input.substring(pos).startsWith("null")) {
                        value = "null"
                        if (key.isNotEmpty()) {
                            propertiesList.add(key j value)
                        }
                        pos += 3 // Skip "null"
                        isKey = true
                    }
                }
                else -> {
                    // Continue
                }
            }
            pos++
        }
        
        // Convert to Indexed
        val properties: Indexed<JsonProperty> = propertiesList.size j { i: Int -> propertiesList[i] }
        return properties j input
    }
    
    /**
     * Simple property lookup (linear search)
     */
    fun query(path: String): SimpleJsonValue? {
        val doc = scan()
        val properties = doc.component1()
        
        for (i in 0 until properties.size) {
            val prop = properties.component2()(i)
            if (prop.component1() == path) {
                return "string" j prop.component2()
            }
        }
        
        return null
    }
    
    /**
     * Extract all properties (same as bitmap version)
     */
    fun properties(): Indexed<JsonProperty> {
        return scan().component1()
    }
    
    /**
     * Simple structural comparison
     */
    infix fun isIsomorphicTo(other: SimpleJsonScanner): Boolean {
        val props1 = properties()
        val props2 = other.properties()
        
        if (props1.size != props2.size) return false
        
        for (i in 0 until props1.size) {
            if (props1.component2()(i).component1() != props2.component2()(i).component1()) return false
        }
        
        return true
    }
    
    /**
     * Simple fingerprinting (string hash)
     */
    fun fingerprint(): Long {
        val props = properties()
        var hash = 0L
        for (i in 0 until props.size) {
            hash = hash * 31 + props.component2()(i).component1().hashCode()
        }
        return hash
    }
    
    // Helper functions (same as bitmap version)
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
// SIMPLE DSL EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Simple scanner entry point
 */
inline fun String.simpleJson(): SimpleJsonScanner = SimpleJsonScanner(this)

/**
 * Direct query shorthand
 */
inline operator fun SimpleJsonScanner.get(path: String): SimpleJsonValue? = query(path)

/**
 * Property extraction shorthand
 */
inline fun SimpleJsonScanner.toProperties(): Indexed<JsonProperty> = properties()