package borg.trikeshed.integration

import borg.trikeshed.lib.Either
import borg.trikeshed.lib.Indexed

// Platform-agnostic time function
expect fun getCurrentTimeMillis(): Long

// === SHARED INTEGRATION TYPEALIASES ===

typealias IntegrationUrl = String
typealias IntegrationPort = Int
typealias IntegrationDatabaseName = String

// === SHARED EVENT TYPEALIASES ===

typealias EventType = String
typealias EventData = Any
typealias EventId = String
typealias EventTimestamp = Long

// === SHARED RESULT TYPES ===

typealias IntegrationResult<S, E> = Either<E, S>

data class IntegrationError(val message: String)

// === UTILITY FUNCTIONS ===

/**
 * Converts a Map to a JSON string.
 * Note: This is a simplified implementation for integration purposes.
 */
internal fun Map<String, Any?>.toJson(): String {
    val entries = this.entries.joinToString(",") { (key, value) ->
        "\"$key\":${value.toJsonValue()}"
    }
    return "{$entries}"
}

private fun Any?.toJsonValue(): String = when (this) {
    is String -> "\"$this\""
    is Number, is Boolean -> this.toString()
    is Map<*, *> -> (this as Map<String, Any?>).toJson()
    is List<*> -> this.joinToString(",", "[", "]") { it.toJsonValue() }
    else -> if (this.toString().startsWith("borg.trikeshed.lib")) {
        // Handle Indexed types without type checking
        "[]" // Simplified handling for now
    } else {
        "null"
    }
}

// toByteArray extension defined in ReactorIntegration.kt 