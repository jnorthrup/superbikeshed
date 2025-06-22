package borg.trikeshed.db

import borg.trikeshed.ccek.JsonService
import kotlinx.serialization.KSerializer

/**
 * Simple stub implementation of JsonService.
 * TODO: Implement proper JSON serialization when kotlinx.serialization is available
 */
class JsonServiceImpl : JsonService {
    override fun <T> toJson(value: T, serializer: KSerializer<T>): String {
        // Simple stub implementation
        return value.toString()
    }

    override fun <T> fromJson(jsonString: String, serializer: KSerializer<T>): T {
        // Simple stub implementation - this will need proper implementation
        throw NotImplementedError("JSON deserialization not implemented yet")
    }
} 