@file:Suppress("ControlFlowWithEmptyBody")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// Lightning JSON Bridge - Complete implementation with full functionality

typealias JsonBounds = Twin<Int>
typealias JsonCommaIndices = Indexed<Int>
typealias JsonStructuralIndices = Join<JsonBounds, JsonCommaIndices>
typealias JsonSegmentContent = Indexed<Char>
typealias JsonSegment = Join<JsonBounds, JsonSegmentContent>
typealias JsonParseContext = Join<JsonStructuralIndices, Indexed<Char>>

// Lightning JSON Implementation - Complete
object Json {
    fun parse(jsonString: String): Indexed<UByte> {
        return LightningJson.parseToBitmap(jsonString)
    }
    
    fun stringify(value: Any): String {
        return LightningJson.stringify(value)
    }
    
    fun extractValues(jsonString: String): Indexed<String> {
        return LightningJson.extractValues(jsonString)
    }
    
    fun findStructuralIndices(jsonString: String): Indexed<Int> {
        return LightningJson.findStructuralIndices(jsonString)
    }
    
    // Complete reify implementation
    fun reify(jsonString: String): Any? {
        return LightningJson.reify(jsonString)
    }
    
    // Complete index implementation
    fun index(jsonString: String): JsonStructuralIndices {
        return LightningJson.index(jsonString)
    }
    
    // Complete jsPath implementation
    fun jsPath(context: JsonParseContext, path: JsPath, reifyResult: Boolean = true): Any? {
        return LightningJson.jsPath(context, path, reifyResult)
    }
}

// Lightning JSON is the performance implementation
fun String.parseJson(): Indexed<UByte> = Json.parse(this)
fun Any.toJsonString(): String = Json.stringify(this)
fun String.reifyJson(): Any? = Json.reify(this)
fun String.indexJson(): JsonStructuralIndices = Json.index(this)