package borg.trikeshed.parse.json

import borg.trikeshed.parse.json.JsonParser
import borg.trikeshed.lib.toIndexed

object JsonImpl {
    /**
     * Parse a JSON string into a Kotlin object using the TrikeShed parser.
     * If asAutoBean is true and the result is a Map, returns an AutoProxy.MapBackedProxy.
     */
    fun parse(input: String, asAutoBean: Boolean = false): Any? = try {
        val result = JsonParser.reify(input.toIndexed())
        if (asAutoBean && result is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            AutoProxy.MapBackedProxy<Any>(result as Map<String, Any?>)
        } else result
    } catch (e: Throwable) {
        null
    }

    /**
     * Stringify a Kotlin object to a JSON string using the TrikeShed implementation.
     * If pretty is true, output is pretty-printed (not implemented, just standard for now).
     */
    fun stringify(obj: Any?, pretty: Boolean = false): String = try {
        // Use LightningJson or fallback to a simple implementation
        // (LightningJson.stringify is similar to the one in JsonTensorFactory)
        when (obj) {
            null -> "null"
            is String -> '"' + obj.replace("\"", "\\\"") + '"'
            is Boolean, is Number -> obj.toString()
            is Map<*, *> -> obj.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                '"' + k.toString().replace("\"", "\\\"") + '"' + ":" + stringify(v)
            }
            is List<*> -> obj.joinToString(prefix = "[", postfix = "]") { stringify(it) }
            is Array<*> -> obj.joinToString(prefix = "[", postfix = "]") { stringify(it) }
            else -> '"' + obj.toString().replace("\"", "\\\"") + '"'
        }
    } catch (e: Throwable) {
        "null"
    }
}

/**
 * Canonical TrikeShed JSON entry point for all serialization/deserialization.
 * Use TrikeShedJson.parse(jsonString) and TrikeShedJson.stringify(obj) everywhere in TrikeShed code.
 */
object TrikeShedJson {
    /**
     * Parse a JSON string into a Kotlin object (Map/List/primitive/null).
     * If asAutoBean is true and the result is a Map, returns an AutoProxy.MapBackedProxy.
     */
    fun parse(input: String, asAutoBean: Boolean = false): Any? = JsonImpl.parse(input, asAutoBean)

    /** Stringify a Kotlin object to a JSON string. */
    fun stringify(obj: Any?, pretty: Boolean = false): String = JsonImpl.stringify(obj, pretty)
}

/**
 * Minimal multiplatform AutoProxy for TrikeShed: creates a LinkedHashMap-backed proxy that veils TrikeShedJson serialization.
 * Usage:
 *   val person = AutoProxy.createProxy<PersonProxy>()
 *   person.name = "Alice"
 *   val json = person.toJson()
 *   val restored = AutoProxy.fromJson<PersonProxy>(json)
 */
object AutoProxy {
    inline fun <reified T : Any> createProxy(): T = MapBackedProxy<T>()

    inline fun <reified T : Any> fromJson(json: String): T {
        val map = TrikeShedJson.parse(json) as? Map<String, Any?> ?: emptyMap()
        return MapBackedProxy<T>(map)
    }

    // LinkedHashMap-backed proxy implementation for all platforms
    class MapBackedProxy<T : Any>(private val backing: MutableMap<String, Any?> = LinkedHashMap()) : kotlin.reflect.KProperty1<T, Any?>, kotlin.reflect.KProperty0<Any?> {
        constructor(initial: Map<String, Any?>) : this(LinkedHashMap(initial))
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Any? = backing[property.name]
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Any?) { backing[property.name] = value }
        fun toJson(): String = TrikeShedJson.stringify(backing)
    }
} 