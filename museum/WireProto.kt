package borg.trikeshed.serialization

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * PROTOCOL BUFFERS SUPPORT - Integrated from kotlinx-serialization-wireproto
 *
 * This integrates Protocol Buffers (Protobuf) support into TrikeShed's serialization module.
 *
 * Features:
 * - Seamless integration with the kotlinx.serialization framework.
 * - High-performance, cross-platform binary serialization.
 * - Functions to encode/decode serializable objects to/from Protobuf byte arrays.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// WIRE PROTOCOL (PROTOBUF) INTEGRATION
// ═══════════════════════════════════════════════════════════════════════════════

object WireProto {

    /**
     * A ProtoBuf instance configured for common use cases.
     * `encodeDefaults = true` is often useful for ensuring consistent behavior across platforms.
     */
    val defaultProtoBuf = ProtoBuf { encodeDefaults = true }

    /**
     * Encodes a serializable object into a Protobuf byte array.
     *
     * @param T The type of the object to encode.
     * @param serializer The serializer for type T.
     * @param value The object to encode.
     * @param protoBufInstance An optional custom ProtoBuf instance to use for encoding.
     * @return A ByteArray representing the object in Protobuf format.
     */
    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
        protoBufInstance: ProtoBuf = defaultProtoBuf
    ): ByteArray {
        return protoBufInstance.encodeToByteArray(serializer, value)
    }

    /**
     * Decodes a Protobuf byte array into an object of type T.
     *
     * @param T The type of the object to decode.
     * @param serializer The serializer for type T.
     * @param bytes The ByteArray to decode.
     * @param protoBufInstance An optional custom ProtoBuf instance to use for decoding.
     * @return The decoded object of type T.
     */
    fun <T> decode(
        serializer: KSerializer<T>,
        bytes: ByteArray,
        protoBufInstance: ProtoBuf = defaultProtoBuf
    ): T {
        return protoBufInstance.decodeFromByteArray(serializer, bytes)
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// INLINE EXTENSIONS FOR CONVENIENCE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Inline extension function to encode any serializable object to a Protobuf ByteArray.
 *
 * Example: `val bytes = myObject.toProtoBuf()`
 */
@OptIn(InternalSerializationApi::class)
inline fun <reified T> T.toProtoBuf(protoBuf: ProtoBuf = WireProto.defaultProtoBuf): ByteArray {
    val serializer = T::class.serializer()
    return protoBuf.encodeToByteArray(serializer, this)
}

/**
 * Inline extension function to decode a Protobuf ByteArray into a specific type.
 * Note: The type must be specified explicitly at the call site.
 *
 * Example: `val myObject: MyType = bytes.fromProtoBuf()`
 */
@OptIn(InternalSerializationApi::class)
inline fun <reified T> ByteArray.fromProtoBuf(protoBuf: ProtoBuf = WireProto.defaultProtoBuf): T {
    val serializer = T::class.serializer()
    return protoBuf.decodeFromByteArray(serializer, this)
} 