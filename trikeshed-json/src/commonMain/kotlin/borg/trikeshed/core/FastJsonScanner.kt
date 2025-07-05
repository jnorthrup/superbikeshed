@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.core

import borg.trikeshed.lib.*

/**
 * Fast JSON Scanner with LinkedHashMap for O(1) queries
 * Combines bitmap scanning with efficient property lookup
 */
class FastJsonScanner(private val input: CharSequence) {
    
    private val propertyMap = LinkedHashMap<String, String>()
    private val properties = mutableListOf<JsonProperty>()
    private var scanned = false
    
    /**
     * Single-pass scan with property extraction
     */
    fun scan() {
        if (scanned) return
        
        var pos = 0
        var key = ""
        var isKey = true
        
        while (pos < input.length) {
            val char = input[pos]
            
            when {
                char == '"' -> {
                    val (str, newPos) = extractString(input, pos)
                    if (isKey) {
                        key = str
                        isKey = false
                    } else {
                        val property = key j str
                        properties.add(property)
                        propertyMap[key] = str
                        isKey = true
                    }
                    pos = newPos - 1
                }
                char == ',' -> {
                    isKey = true
                    key = ""
                }
                char in '0'..'9' && !isKey -> {
                    val (num, newPos) = extractNumber(input, pos)
                    val value = num.toString()
                    val property = key j value
                    properties.add(property)
                    propertyMap[key] = value
                    pos = newPos - 1
                    isKey = true
                }
                (char == 't' || char == 'f') && !isKey -> {
                    val (bool, newPos) = extractBoolean(input, pos)
                    val value = bool.toString()
                    val property = key j value
                    properties.add(property)
                    propertyMap[key] = value
                    pos = newPos - 1
                    isKey = true
                }
                char == 'n' && !isKey && pos + 3 < input.length && 
                    input.substring(pos, pos + 4) == "null" -> {
                    val property = key j "null"
                    properties.add(property)
                    propertyMap[key] = "null"
                    pos += 3
                    isKey = true
                }
            }
            pos++
        }
        
        scanned = true
    }
    
    /**
     * O(1) property lookup
     */
    fun query(key: String): Join<String, Any?>? {
        if (!scanned) scan()
        return propertyMap[key]?.let { key j it }
    }
    
    /**
     * Get all properties
     */
    fun properties(): Indexed<JsonProperty> {
        if (!scanned) scan()
        return properties.size j properties::get
    }
    
    /**
     * Fast fingerprinting using hash
     */
    fun fingerprint(): String {
        if (!scanned) scan()
        return propertyMap.hashCode().toString(16)
    }
    
    private fun extractString(content: CharSequence, start: Int): Pair<String, Int> {
        var pos = start + 1
        val sb = StringBuilder()
        
        while (pos < content.length && content[pos] != '"') {
            if (content[pos] == '\\') pos++
            sb.append(content[pos])
            pos++
        }
        
        return sb.toString() to (pos + 1)
    }
    
    private fun extractNumber(content: CharSequence, start: Int): Pair<Double, Int> {
        var pos = start
        val sb = StringBuilder()
        
        while (pos < content.length && (content[pos].isDigit() || content[pos] in ".-+eE")) {
            sb.append(content[pos])
            pos++
        }
        
        return sb.toString().toDouble() to pos
    }
    
    private fun extractBoolean(content: CharSequence, start: Int): Pair<Boolean, Int> {
        return if (content.substring(start).startsWith("true")) {
            true to (start + 4)
        } else {
            false to (start + 5)
        }
    }
}

// Extension function
fun CharSequence.fastJson() = FastJsonScanner(this)