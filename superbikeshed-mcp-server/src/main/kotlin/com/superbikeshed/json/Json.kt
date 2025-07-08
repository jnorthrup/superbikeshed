package com.superbikeshed.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

interface JsonSerializer {
    fun <T> serialize(obj: T): String
    fun <T> deserialize(json: String, clazz: Class<T>): T
}

@Serializable
data class JsonMessage(
    val type: String,
    val data: JsonElement,
    val timestamp: Long = System.currentTimeMillis()
)

class TrikeshedJsonSerializer : JsonSerializer {
    internal val json = Json { ignoreUnknownKeys = true }
    
    override fun <T> serialize(obj: T): String {
        return json.encodeToString(JsonMessage.serializer(), JsonMessage("data", JsonPrimitive(obj.toString())))
    }
    
    override fun <T> deserialize(json: String, clazz: Class<T>): T {
        val message = this.json.decodeFromString(JsonMessage.serializer(), json)
        return clazz.cast(message.data.jsonPrimitive.content)
    }
} 