package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

actual object JsonImpl {
    actual fun parse(input: String): Any? {
        val chars = input.toSeries()
        return try {
            val parsed = jsPath(chars, emptyList())
            reifyJson(parsed)
        } catch (e: Exception) {
            null
        }
    }
    
    actual fun stringify(obj: Any?, pretty: Boolean): String {
        return when (obj) {
            null -> "null"
            is Boolean -> obj.toString()
            is Number -> obj.toString()
            is String -> "\"${obj.replace("\"", "\\\"")}\""
            is List<*> -> {
                val items = obj.map { stringify(it, pretty) }
                if (pretty) "[\n  ${items.joinToString(",\n  ")}\n]"
                else "[${items.joinToString(",")}]"
            }
            is Map<*, *> -> {
                val pairs = obj.entries.map { (k, v) ->
                    "\"$k\": ${stringify(v, pretty)}"
                }
                if (pretty) "{\n  ${pairs.joinToString(",\n  ")}\n}"
                else "{${pairs.joinToString(",")}}"
            }
            else -> stringify(obj.toString(), pretty)
        }
    }
    
    private fun reifyJson(node: Any?): Any? = when (node) {
        is Series<*> -> node.`▶`.map { reifyJson(it) }
        is Join<*, *> -> mapOf(node.a.toString() to reifyJson(node.b))
        else -> node
    }
    
    // Simple JSON parser helper function
    private fun jsPath(chars: Series<Char>, path: List<String>): Any? {
        // Simplified JSON parsing - in real implementation this would be more robust
        val str = chars.▶.joinToString("")
        return simpleJsonParse(str)
    }
    
    private fun simpleJsonParse(json: String): Any? {
        val trimmed = json.trim()
        return when {
            trimmed == "null" -> null
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> 
                trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("[") && trimmed.endsWith("]") -> 
                parseJsonArray(trimmed)
            trimmed.startsWith("{") && trimmed.endsWith("}") -> 
                parseJsonObject(trimmed)
            else -> trimmed.toDoubleOrNull() ?: trimmed
        }
    }
    
    private fun parseJsonArray(json: String): List<Any?> {
        // Simplified array parsing
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) return emptyList()
        
        val items = mutableListOf<Any?>()
        var depth = 0
        var start = 0
        var inString = false
        
        for (i in content.indices) {
            when (content[i]) {
                '"' -> if (i == 0 || content[i-1] != '\\') inString = !inString
                '{', '[' -> if (!inString) depth++
                '}', ']' -> if (!inString) depth--
                ',' -> if (!inString && depth == 0) {
                    items.add(simpleJsonParse(content.substring(start, i)))
                    start = i + 1
                }
            }
        }
        if (start < content.length) {
            items.add(simpleJsonParse(content.substring(start)))
        }
        
        return items
    }
    
    private fun parseJsonObject(json: String): Map<String, Any?> {
        // Simplified object parsing
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) return emptyMap()
        
        val map = mutableMapOf<String, Any?>()
        var depth = 0
        var start = 0
        var inString = false
        
        for (i in content.indices) {
            when (content[i]) {
                '"' -> if (i == 0 || content[i-1] != '\\') inString = !inString
                '{', '[' -> if (!inString) depth++
                '}', ']' -> if (!inString) depth--
                ',' -> if (!inString && depth == 0) {
                    parsePair(content.substring(start, i))?.let { (k, v) -> map[k] = v }
                    start = i + 1
                }
            }
        }
        if (start < content.length) {
            parsePair(content.substring(start))?.let { (k, v) -> map[k] = v }
        }
        
        return map
    }
    
    private fun parsePair(pair: String): Pair<String, Any?>? {
        val colonIndex = pair.indexOf(':')
        if (colonIndex == -1) return null
        
        val key = simpleJsonParse(pair.substring(0, colonIndex).trim()) as? String ?: return null
        val value = simpleJsonParse(pair.substring(colonIndex + 1).trim())
        
        return key to value
    }
}