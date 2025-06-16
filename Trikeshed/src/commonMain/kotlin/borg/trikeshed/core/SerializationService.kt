package borg.trikeshed.core

import borg.trikeshed.lib.Series
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

/**
 * Service for handling serialization and deserialization of TrikeShed data structures
 */
actual class SerializationService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key
    
    companion object Key : CoroutineContext.Key<SerializationService>
    
    fun <T> serialize(data: T): String {
        // Platform-specific serialization implementation
        return Json.encodeToString(data)
    }
    
    inline fun <reified T> deserialize(json: String): T {
        // Platform-specific deserialization implementation
        return Json.decodeFromString(json)
    }
}
