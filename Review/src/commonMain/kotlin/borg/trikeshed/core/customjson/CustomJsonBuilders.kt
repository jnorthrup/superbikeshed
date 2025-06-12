package borg.trikeshed.core.customjson

// Helper to quote strings and handle nulls, numbers, booleans
internal fun Any?.toJsonValue(): String {
    return when (this) {
        null -> "null"
        is String -> "\"${this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\"" // Escape quotes and other common chars
        is Number, is Boolean -> this.toString()
        // For other types, consider if they have a specific toCustomJsonString() or default to escaped toString()
        else -> "\"${this.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
    }
}

// Helper to join a list of items into a JSON array string
internal fun <T> Iterable<T>.toJsonArrayString(transform: (T) -> String = { it.toJsonValue() }): String {
    return this.joinToString(separator = ",", prefix = "[", postfix = "]", transform = transform)
}

// Helper to join a map into a JSON object string
internal fun <K, V> Map<K, V>.toJsonObjectString(
    keyTransform: (K) -> String = { it.toString().toJsonValue() }, // Keys in JSON objects must be strings
    valueTransform: (V) -> String = { it.toJsonValue() }
): String {
    return this.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { entry ->
        // Ensure map keys are treated as strings and properly quoted if not already.
        val keyJson = if (entry.key is String) (entry.key as String).toJsonValue() else entry.key.toString().toJsonValue()
        "${keyJson}:${valueTransform(entry.value)}"
    }
}

// Overload for list of pairs to build a JSON object string, ensuring key order
internal fun <K, V> List<Pair<K, V>>.toJsonObjectStringFromPairs(
    keyTransform: (K) -> String = { it.toString().toJsonValue() }, // Default for simple keys
    valueTransform: (V) -> String = { it.toJsonValue() }
): String {
    return this.joinToString(separator = ",", prefix = "{", postfix = "}") { pair ->
        // Ensure pair first elements (keys) are treated as strings and properly quoted
        val keyJson = if (pair.first is String) (pair.first as String).toJsonValue() else pair.first.toString().toJsonValue()
        "${keyJson}:${valueTransform(pair.second)}"
    }
}

// --- Extension functions for specific types ---

// Assuming SerializableTensorData and SerializableSeriesData are accessible; adjust imports as necessary
// For example, if they are in borg.trikeshed.core:
import borg.trikeshed.core.SerializableTensorData
import borg.trikeshed.core.SerializableSeriesData
import borg.trikeshed.services.YourDataType // Assuming this path is correct
import borg.trikeshed.services.IncrementalDataResponse // Assuming this path is correct


fun <T> SerializableTensorData<T>.toCustomJsonString(): String {
    // IntArray's default toString isn't JSON, so map its elements
    val shapeJson = this.shape.map { it.toJsonValue() }.toJsonArrayString()
    val dataJson = this.data.toJsonArrayString() // Uses the helper for List<T>
    return "{\"shape\":${shapeJson},\"data\":${dataJson}}"
}

fun <T> SerializableSeriesData<T>.toCustomJsonString(): String {
    val dataJson = this.data.toJsonArrayString() // Uses the helper for List<T>
    return "{\"data\":${dataJson}}"
}

fun YourDataType.toCustomJsonString(): String {
    val properties = listOf(
        "id" to this.id,
        "value" to this.value,
        "timestamp" to this.timestamp
    )
    return properties.toJsonObjectStringFromPairs(
        keyTransform = { "\"${it}\"" } // Explicitly quote keys as strings
    )
}

fun IncrementalDataResponse.toCustomJsonString(): String {
    // this.newData is already SerializableSeriesData<YourDataType>
    val newDataJson = this.newData.toCustomJsonString() // This will call the extension for SerializableSeriesData

    val properties = listOf(
        // Key needs to be a JSON string literal, so add quotes.
        // Value for "newData" is already a JSON string, so it should not be re-escaped by toJsonValue.
        "\"newData\"" to newDataJson,
        "\"latestTimestamp\"" to this.latestTimestamp.toJsonValue()
    )
    // A specialized joinToString for pre-formatted keys and values might be cleaner here.
    // For now, using a simpler joinToString and ensuring keys are already string literals.
    return properties.joinToString(separator = ",", prefix = "{", postfix = "}") { pair ->
        "${pair.first}:${pair.second}" // Key is already quoted, value for newData is already JSON string.
    }
}
