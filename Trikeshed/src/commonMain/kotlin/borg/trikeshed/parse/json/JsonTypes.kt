@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// TrikeShed JSON type system  
typealias JsonElement = Any?
// JSON objects can have duplicate keys per spec - Series2 handles this as Series<Join>
typealias JsonObject = Series2<String, JsonElement>
typealias JsonArray = Series<JsonElement>

// JSON parsing and serialization
expect object JsonImpl {
    fun parse(input: String): Any?
    fun stringify(obj: Any?, pretty: Boolean = false): String
}

// JSON path operations for extracting values
object JsonPath {
    fun extract(json: Any?, path: String): Any? {
        val parts = path.split('.')
        return parts.fold(json) { current, key ->
            when (current) {
                is Map<*, *> -> current[key]
                is List<*> -> key.toIntOrNull()?.let { current.getOrNull(it) }
                else -> null
            }
        }
    }
    
    fun extractString(json: Any?, path: String): String? {
        return extract(json, path)?.toString()
    }
    
    fun extractInt(json: Any?, path: String): Int? {
        return extract(json, path)?.toString()?.toIntOrNull()
    }
    
    fun extractDouble(json: Any?, path: String): Double? {
        return extract(json, path)?.toString()?.toDoubleOrNull()
    }
    
    fun extractBoolean(json: Any?, path: String): Boolean? {
        return extract(json, path) as? Boolean
    }
    
    fun extractArray(json: Any?, path: String): JsonArray? {
        val list = extract(json, path) as? List<*>
        return list?.let { it.size j { i -> it[i] } }
    }
    
    fun extractObject(json: Any?, path: String): JsonObject? {
        val map = extract(json, path) as? Map<String, *>
        return map?.let { m ->
            val entries = m.entries.toList()
            entries.size j { i -> entries[i].key j entries[i].value }
        }
    }
}

// JSON builder DSL
class JsonBuilder {
    private val pairs = mutableListOf<Join<String, JsonElement>>()
    
    fun put(key: String, value: Any?) {
        pairs.add(key j value)
    }
    
    fun putString(key: String, value: String) = put(key, value)
    fun putInt(key: String, value: Int) = put(key, value)
    fun putDouble(key: String, value: Double) = put(key, value)
    fun putBoolean(key: String, value: Boolean) = put(key, value)
    fun putArray(key: String, array: JsonArray) = put(key, array)
    fun putObject(key: String, obj: JsonObject) = put(key, obj)
    
    fun build(): JsonObject = pairs.size j { pairs[it] }
}

inline fun jsonObject(init: JsonBuilder.() -> Unit): JsonObject {
    val builder = JsonBuilder()
    builder.init()
    return builder.build()
}