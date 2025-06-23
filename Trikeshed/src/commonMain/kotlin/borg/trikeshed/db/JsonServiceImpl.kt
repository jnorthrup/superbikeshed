package borg.trikeshed.db

import borg.trikeshed.ccek.JsonService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Full implementation of JsonService using kotlinx.serialization
 */
class JsonServiceImpl : JsonService {
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun <T> toJson(value: T, serializer: KSerializer<T>): String {
        return json.encodeToString(serializer, value)
    }

    override fun <T> fromJson(jsonString: String, serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, jsonString)
    }
} 