@file:Suppress("ControlFlowWithEmptyBody")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// JSON Bridge - Simple implementation using existing parsers

typealias JsonBounds = Twin<Int>
typealias JsonCommaIndices = Indexed<Int>
typealias JsonStructuralIndices = Join<JsonBounds, JsonCommaIndices>
typealias JsonSegmentContent = Indexed<Char>
typealias JsonSegment = Join<JsonBounds, JsonSegmentContent>
typealias JsonParseContext = Join<JsonStructuralIndices, Indexed<Char>>

// JSON Implementation - Using existing TrikeShed parsers
object Json {
    fun parse(jsonString: String): Indexed<UByte> {
        // Use existing bitmap creation from JsonTensorFactory
        return createBitmapAsSeries(jsonString.encodeToByteArray().toUByteArray())
    }
    
    fun stringify(value: Any): String {
        // Simple JSON stringify - basic implementation
        return when (value) {
            is String -> "\"$value\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            null -> "null"
            is List<*> -> "[${value.joinToString(",") { stringify(it ?: "null") }}]"
            is Map<*, *> -> "{${value.entries.joinToString(",") { "\"${it.key}\":${stringify(it.value)}" }}}"
            else -> "\"$value\""
        }
    }
    
    fun extractValues(jsonString: String): Indexed<String> {
        // Use existing parser from JsonTensorFactory
        return parseJsonToTensor(jsonString)
    }
    
    fun findStructuralIndices(jsonString: String): Indexed<Int> {
        // Simple structural character detection
        val indices = mutableListOf<Int>()
        jsonString.forEachIndexed { index, char ->
            if (char in "{}[]:,") {
                indices.add(index)
            }
        }
        return indices.toSeries()
    }
    
    // Simple reify implementation
    fun reify(jsonString: String): Any? {
        return try {
            when {
                jsonString == "null" -> null
                jsonString == "true" -> true
                jsonString == "false" -> false
                jsonString.startsWith("\"") && jsonString.endsWith("\"") -> 
                    jsonString.substring(1, jsonString.length - 1)
                jsonString.toDoubleOrNull() != null -> jsonString.toDouble()
                else -> jsonString
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Simple index implementation
    fun index(jsonString: String): JsonStructuralIndices {
        val indices = findStructuralIndices(jsonString)
        val bounds = 2 j { i -> if (i == 0) 0 else jsonString.length }
        val commaIndices = indices.play.filter { jsonString[it] == ',' }.toSeries()
        return bounds j commaIndices
    }
    
    // Simple jsPath implementation
    fun jsPath(context: JsonParseContext, path: JsPath, reifyResult: Boolean = true): Any? {
        // Placeholder implementation
        return null
    }
}

// JSON extension functions
fun String.parseJson(): Indexed<UByte> = Json.parse(this)
fun Any.toJsonString(): String = Json.stringify(this)
fun String.reifyJson(): Any? = Json.reify(this)
fun String.indexJson(): JsonStructuralIndices = Json.index(this)