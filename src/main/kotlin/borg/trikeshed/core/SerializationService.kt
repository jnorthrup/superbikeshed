package borg.trikeshed.core

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

/**
 * Service for serializing and deserializing TrikeShed types to/from JSON.
 * This service provides a platform-agnostic way to convert Series and other
 * TrikeShed types to JSON and back.
 */
expect class SerializationService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<SerializationService>
    override val key: CoroutineContext.Key<*>

    /**
     * Serializes a Series to JSON.
     * @param series The Series to serialize
     * @return JSON string representation of the Series
     */
    fun <T> serializeSeries(series: Series<T>): String

    /**
     * Deserializes a JSON string into a Series.
     * @param json The JSON string to deserialize
     * @return Series reconstructed from the JSON
     */
    fun <T> deserializeSeries(json: String): Series<T>

    /**
     * Serializes a Join to JSON.
     * @param join The Join to serialize
     * @return JSON string representation of the Join
     */
    fun <A, B> serializeJoin(join: Join<A, B>): String

    /**
     * Deserializes a JSON string into a Join.
     * @param json The JSON string to deserialize
     * @return Join reconstructed from the JSON
     */
    fun <A, B> deserializeJoin(json: String): Join<A, B>
} 