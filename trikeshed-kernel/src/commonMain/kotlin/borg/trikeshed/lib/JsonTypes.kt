package borg.trikeshed.lib

import kotlin.jvm.JvmInline

// JSON Types Taxonomy - TrikeShed Style
@JvmInline value class JsonPrimitive(val value: String)
@JvmInline value class JsonObject(val value: String) 
@JvmInline value class JsonArray(val value: String)

// Legacy compatibility alias
typealias JsonImpl = JsonObject