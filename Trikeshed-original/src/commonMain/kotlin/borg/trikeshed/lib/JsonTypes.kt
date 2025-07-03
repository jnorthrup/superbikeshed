package borg.trikeshed.lib

import kotlin.jvm.JvmInline
import borg.trikeshed.parse.json.JsonStructureType

// JSON Types Taxonomy - TrikeShed Style
@JvmInline value class JsonPrimitive(val value: String)
@JvmInline value class JsonObject(val value: String) 
@JvmInline value class JsonArray(val value: String)

// Structure type for bitmap positions
@JvmInline value class JsonBitmapStructure(val type: JsonStructureType)

typealias JsonBitmapIndexed = Indexed<JsonBitmapStructure>

typealias JsonImpl = JsonObject