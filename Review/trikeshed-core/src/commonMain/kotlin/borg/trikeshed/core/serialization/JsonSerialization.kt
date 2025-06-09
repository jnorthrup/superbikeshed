package borg.trikeshed.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import borg.trikeshed.core.Join
import borg.trikeshed.core.Series
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.SerializableJoin
import borg.trikeshed.core.SerializableTensorData
import borg.trikeshed.core.SerializableSeriesData
import borg.trikeshed.core.toSerializable
import borg.trikeshed.core.toTensor
import borg.trikeshed.core.toSeries

val DefaultJson = Json {
    prettyPrint = true
    encodeDefaults = true
    // Potentially add serializersModule if custom serializers for Join, Tensor, Series are directly registered
    // For now, relying on @Serializable on Join's implementor and wrapper classes for Tensor/Series
}

// Generic toJsonString for any @Serializable type
inline fun <reified T> T.toJsonString(): String {
    return DefaultJson.encodeToString(this)
}

// Specific toJsonString for Join<A, B>
// We need to ensure that A and B are serializable.
// This assumes Join itself is made serializable or has a serializable implementation like SerializableJoin.
inline fun <reified A, reified B> Join<A, B>.toJsonString(): String {
    // If Join is an interface, we might need to convert it to a concrete serializable class first,
    // or ensure polymorphic serialization is set up.
    // For now, assuming it's a SerializableJoin or similar.
    if (this is SerializableJoin<A, B>) {
        return DefaultJson.encodeToString(this as SerializableJoin<A, B>)
    }
    // Fallback or error if it's not a directly serializable Join type
    // This path should ideally not be taken if all Joins are SerializableJoins.
    throw IllegalArgumentException("Instance of Join is not directly serializable: $this. Ensure it's a SerializableJoin.")
}

// toJsonString for Tensor<T>
// Converts Tensor to SerializableTensorData then serializes.
inline fun <reified T> Tensor<T>.toJsonString(): String {
    return DefaultJson.encodeToString(this.toSerializable())
}

// fromJsonString for Tensor<T>
// Deserializes to SerializableTensorData then converts to Tensor.
inline fun <reified T> String.toTensor(): Tensor<T> {
    val serializableData = DefaultJson.decodeFromString<SerializableTensorData<T>>(this)
    return serializableData.toTensor()
}

// toJsonString for Series<T>
// Converts Series to SerializableSeriesData then serializes.
inline fun <reified T> Series<T>.toJsonString(): String {
    return DefaultJson.encodeToString(this.toSerializable())
}

// fromJsonString for Series<T>
// Deserializes to SerializableSeriesData then converts to Series.
inline fun <reified T> String.toSeries(): Series<T> {
    val serializableData = DefaultJson.decodeFromString<SerializableSeriesData<T>>(this)
    return serializableData.toSeries()
}

// Example of a fromJsonString for a generic @Serializable type (if needed)
// Be cautious with this generic approach for complex types without specific handling.
inline fun <reified T> String.fromJsonTo(): T {
    return DefaultJson.decodeFromString<T>(this)
}
