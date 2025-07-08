package borg.trikeshed.lib.json

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join

sealed class JsonValue {
    data class JsonString(val value: String) : JsonValue()
    data class JsonNumber(val value: Double) : JsonValue()
    data class JsonBoolean(val value: Boolean) : JsonValue()
    object JsonNull : JsonValue()
    data class JsonArray(val elements: Indexed<JsonValue>) : JsonValue()
    data class JsonObject(val members: Indexed<Join<String, JsonValue>>) : JsonValue()
}
