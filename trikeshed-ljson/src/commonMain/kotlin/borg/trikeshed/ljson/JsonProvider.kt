@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Thin JSON Provider API for TrikeShed
 * 
 * Minimal, efficient JSON operations that integrate with the TrikeShed ecosystem.
 * Focuses on essential functionality while maintaining compatibility with existing patterns.
 */

/**
 * JSON element types using TrikeShed foundations
 */
sealed class JsonElement {
    object Null : JsonElement()
    data class Bool(val value: Boolean) : JsonElement()
    data class Num(val value: Double) : JsonElement()
    data class Str(val value: String) : JsonElement()
    data class Arr(val elements: Indexed<JsonElement>) : JsonElement()
    data class Obj(val fields: Indexed<Join<String, JsonElement>>) : JsonElement()
}

/**
 * JSON parsing result
 */
typealias JsonResult<T> = Join<T?, String?> // Success value j error message

/**
 * Core JSON provider interface
 */
interface JsonProvider {
    /** Parse JSON string to element */
    fun parse(json: String): JsonResult<JsonElement>
    
    /** Serialize element to JSON string */
    fun stringify(element: JsonElement): String
    
    /** Parse directly to typed object */
    fun <T> decode(json: String, deserializer: DeserializationStrategy<T>): JsonResult<T>
    
    /** Serialize typed object to JSON */
    fun <T> encode(value: T, serializer: SerializationStrategy<T>): String
}

/**
 * Thin JSON provider implementation
 */
class ThinJsonProvider(
    internal val kotlinxJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }
) : JsonProvider {
    
    override fun parse(json: String): JsonResult<JsonElement> = try {
        val kotlinxElement = kotlinxJson.parseToJsonElement(json)
        val element = kotlinxElement.toTrikeShedJson()
        element j null
    } catch (e: Exception) {
        null j e.message
    }
    
    override fun stringify(element: JsonElement): String = 
        element.toKotlinxJson().toString()
    
    override fun <T> decode(json: String, deserializer: DeserializationStrategy<T>): JsonResult<T> = try {
        val value = kotlinxJson.decodeFromString(deserializer, json)
        value j null
    } catch (e: Exception) {
        null j e.message
    }
    
    override fun <T> encode(value: T, serializer: SerializationStrategy<T>): String =
        kotlinxJson.encodeToString(serializer, value)
}

/**
 * AutoProxy pattern for JSON operations
 */
interface JsonProxy {
    /** Get underlying provider */
    val provider: JsonProvider
    
    /** Delegate all operations through provider */
    fun parse(json: String): JsonResult<JsonElement> = provider.parse(json)
    fun stringify(element: JsonElement): String = provider.stringify(element)
    fun <T> decode(json: String, deserializer: DeserializationStrategy<T>): JsonResult<T> = 
        provider.decode(json, deserializer)
    fun <T> encode(value: T, serializer: SerializationStrategy<T>): String = 
        provider.encode(value, serializer)
}

/**
 * Concrete AutoProxy implementation
 */
class AutoJsonProxy(
    override val provider: JsonProvider = ThinJsonProvider()
) : JsonProxy

/**
 * Streaming JSON operations
 */
interface JsonStreaming {
    /** Parse JSON array as stream */
    suspend fun parseArrayStream(json: String): kotlinx.coroutines.flow.Flow<JsonElement>
    
    /** Parse large JSON object field by field */
    suspend fun parseObjectStream(json: String): kotlinx.coroutines.flow.Flow<Join<String, JsonElement>>
}

/**
 * JSON-Cursor integration
 */
object JsonCursor {
    
    /** Convert JSON array to cursor */
    fun fromJsonArray(array: JsonElement.Arr): Cursor {
        val rows = mutableListOf<List<Any?>>()
        
        for (i in 0 until array.elements.a) {
            val element = array.elements.b(i)
            when (element) {
                is JsonElement.Obj -> {
                    val row = mutableListOf<Any?>()
                    for (j in 0 until element.fields.a) {
                        val field = element.fields.b(j)
                        row.add(field.b.toNativeValue())
                    }
                    rows.add(row)
                }
                else -> {
                    // Single-column row
                    rows.add(listOf(element.toNativeValue()))
                }
            }
        }
        
        return if (rows.isNotEmpty()) {
            val columnCount = rows.maxOfOrNull { it.size } ?: 1
            val columnNames = (0 until columnCount).map { "col_$it" }
            cursorOf(rows, columnNames)
        } else {
            // Empty cursor
            0 j { _: Int -> 0 j { _: Int -> null j { Scalar(IOMemento.IoString) } } }
        }
    }
    
    /** Convert cursor to JSON array */
    fun toJsonArray(cursor: Cursor): JsonElement.Arr {
        val elements = mutableListOf<JsonElement>()
        
        for (i in 0 until cursor.a) {
            val row = cursor.at(i)
            val fields = mutableListOf<Join<String, JsonElement>>()
            
            for (j in 0 until row.a) {
                val cell = row.b(j)
                val columnName = cursor.columnNames.b(j)
                val value = cell.a.toJsonElement()
                fields.add(columnName j value)
            }
            
            val obj = JsonElement.Obj(fields.size j { k -> fields[k] })
            elements.add(obj)
        }
        
        return JsonElement.Arr(elements.size j { i -> elements[i] })
    }
}

/**
 * Conversion utilities
 */
internal fun kotlinx.serialization.json.JsonElement.toTrikeShedJson(): JsonElement = when (this) {
    is JsonNull -> JsonElement.Null
    is JsonPrimitive -> when {
        isString -> JsonElement.Str(content)
        this == JsonPrimitive(true) -> JsonElement.Bool(true)
        this == JsonPrimitive(false) -> JsonElement.Bool(false)
        else -> JsonElement.Num(content.toDoubleOrNull() ?: 0.0)
    }
    is JsonArray -> {
        val elements = size j { i -> this[i].toTrikeShedJson() }
        JsonElement.Arr(elements)
    }
    is JsonObject -> {
        val fields = mutableListOf<Join<String, JsonElement>>()
        forEach { (key, value) ->
            fields.add(key j value.toTrikeShedJson())
        }
        JsonElement.Obj(fields.size j { i -> fields[i] })
    }
}

internal fun JsonElement.toKotlinxJson(): kotlinx.serialization.json.JsonElement = when (this) {
    JsonElement.Null -> JsonNull
    is JsonElement.Bool -> JsonPrimitive(value)
    is JsonElement.Num -> JsonPrimitive(value)
    is JsonElement.Str -> JsonPrimitive(value)
    is JsonElement.Arr -> {
        val list = (0 until elements.a).map { elements.b(it).toKotlinxJson() }
        JsonArray(list)
    }
    is JsonElement.Obj -> {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (i in 0 until fields.a) {
            val field = fields.b(i)
            map[field.a] = field.b.toKotlinxJson()
        }
        JsonObject(map)
    }
}

internal fun JsonElement.toNativeValue(): Any? = when (this) {
    JsonElement.Null -> null
    is JsonElement.Bool -> value
    is JsonElement.Num -> value
    is JsonElement.Str -> value
    is JsonElement.Arr -> (0 until elements.a).map { elements.b(it).toNativeValue() }
    is JsonElement.Obj -> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until fields.a) {
            val field = fields.b(i)
            map[field.a] = field.b.toNativeValue()
        }
        map
    }
}

internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonElement.Null
    is Boolean -> JsonElement.Bool(this)
    is Number -> JsonElement.Num(this.toDouble())
    is String -> JsonElement.Str(this)
    is List<*> -> {
        val elements = size j { i -> this[i].toJsonElement() }
        JsonElement.Arr(elements)
    }
    is Map<*, *> -> {
        val fields = mutableListOf<Join<String, JsonElement>>()
        forEach { (key, value) ->
            fields.add((key?.toString() ?: "") j value.toJsonElement())
        }
        JsonElement.Obj(fields.size j { i -> fields[i] })
    }
    else -> JsonElement.Str(toString())
}

/**
 * Global JSON provider instance
 */
object Json : JsonProxy by AutoJsonProxy()

/**
 * Extension functions for convenience
 */
fun String.parseJson(): JsonResult<JsonElement> = Json.parse(this)
fun JsonElement.toJsonString(): String = Json.stringify(this)

inline fun <reified T> String.decodeJson(): JsonResult<T> = 
    Json.decode(this, serializer<T>())

inline fun <reified T> T.encodeJson(): String = 
    Json.encode(this, serializer<T>())