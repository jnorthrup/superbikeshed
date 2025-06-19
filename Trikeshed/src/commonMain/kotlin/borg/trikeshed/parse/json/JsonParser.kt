package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// Lightning JSON Bridge - replaces broken JSON parser with SIMD implementation

typealias JsElement = Join<Twin<Int>, Series<Int>>
typealias JsIndex = Join<Twin<Int>, Series<Char>>
typealias JsContext = Join<JsElement, Series<Char>>

// Bridge to Lightning SIMD JSON
object JsonParser {
    fun parse(jsonString: String): Any {
        return LightningJson.parseToBitmap(jsonString)
    }
    
    fun stringify(value: Any): String {
        return when(value) {
            is String -> "\"$value\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> "\"$value\""
        }
    }
    
    fun extractValues(jsonString: String): Series<String> {
        return LightningJson.extractValues(jsonString)
    }
    
    fun findStructuralIndices(jsonString: String): Series<Int> {
        return LightningJson.findStructuralIndices(jsonString)
    }
}

// Lightning JSON is now the primary JSON implementation
val Any.lightningJson: JsonParser get() = JsonParser