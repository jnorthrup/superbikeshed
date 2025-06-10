package borg.trikeshed.core.serialization

// import kotlinx.serialization.KSerializer // Removed
// import kotlinx.serialization.Serializable // Removed
// import kotlinx.serialization.descriptors.PrimitiveKind // Removed
// import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor // Removed
// import kotlinx.serialization.descriptors.SerialDescriptor // Removed
// import kotlinx.serialization.encoding.Decoder // Removed
// import kotlinx.serialization.encoding.Encoder // Removed
// import kotlinx.serialization.json.Json // Removed
// import kotlinx.serialization.encodeToString // Removed
import borg.trikeshed.core.Join
import borg.trikeshed.core.Series
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.SerializableJoin
import borg.trikeshed.core.SerializableTensorData
import borg.trikeshed.core.SerializableSeriesData
import borg.trikeshed.core.toSerializable
import borg.trikeshed.core.toTensor
import borg.trikeshed.core.toSeries

// val DefaultJson = Json { // Removed
//     prettyPrint = true
//     encodeDefaults = true
// }

// // Generic toJsonString for any @Serializable type // Removed
// inline fun <reified T> T.toJsonString(): String {
//     return DefaultJson.encodeToString(this)
// }

// // Specific toJsonString for Join<A, B> // Removed
// inline fun <reified A, reified B> Join<A, B>.toJsonString(): String {
//     if (this is SerializableJoin<A, B>) {
//         return DefaultJson.encodeToString(this as SerializableJoin<A, B>)
//     }
//     throw IllegalArgumentException("Instance of Join is not directly serializable: $this. Ensure it's a SerializableJoin.")
// }

// // toJsonString for Tensor<T> // Removed
// inline fun <reified T> Tensor<T>.toJsonString(): String {
//     return DefaultJson.encodeToString(this.toSerializable())
// }

// // fromJsonString for Tensor<T> // Removed
// inline fun <reified T> String.toTensor(): Tensor<T> {
//     val serializableData = DefaultJson.decodeFromString<SerializableTensorData<T>>(this)
//     return serializableData.toTensor()
// }

// // toJsonString for Series<T> // Removed
// inline fun <reified T> Series<T>.toJsonString(): String {
//     return DefaultJson.encodeToString(this.toSerializable())
// }

// // fromJsonString for Series<T> // Removed
// inline fun <reified T> String.toSeries(): Series<T> {
//     val serializableData = DefaultJson.decodeFromString<SerializableSeriesData<T>>(this)
//     return serializableData.toSeries()
// }

// // Example of a fromJsonString for a generic @Serializable type (if needed) // Removed
// inline fun <reified T> String.fromJsonTo(): T {
//     return DefaultJson.decodeFromString<T>(this)
// }
