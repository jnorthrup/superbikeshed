@file:Suppress("ControlFlowWithEmptyBody")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// Lightning JSON Bridge - type aliases and modern JSON parsing

typealias JsonBounds = Twin<Int>
typealias JsonCommaIndices = Series<Int>
typealias JsonStructuralIndices = Join<JsonBounds, JsonCommaIndices>
typealias JsonSegmentContent = Series<Char>
typealias JsonSegment = Join<JsonBounds, JsonSegmentContent>
typealias JsonParseContext = Join<JsonStructuralIndices, Series<Char>>

// Lightning JSON Implementation
object Json {
    fun parse(jsonString: String): Series<UByte> {
        return LightningJson.parseToBitmap(jsonString)
    }
    
    fun stringify(value: Any): String {
        return JsonParser.stringify(value)
    }
    
    fun extractValues(jsonString: String): Series<String> {
        return LightningJson.extractValues(jsonString)
    }
    
    fun findStructuralIndices(jsonString: String): Series<Int> {
        return LightningJson.findStructuralIndices(jsonString)
    }
}

// Lightning JSON is the performance implementation
fun String.parseJson(): Series<UByte> = Json.parse(this)
fun Any.toJsonString(): String = Json.stringify(this)