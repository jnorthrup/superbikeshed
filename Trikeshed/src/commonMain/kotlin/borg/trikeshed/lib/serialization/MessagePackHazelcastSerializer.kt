package borg.trikeshed.lib.serialization

import com.hazelcast.nio.serialization.ByteArraySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack // Assuming this is the correct import

// Define a placeholder for the actual MsgPack object if the import is different
// object MsgPack {
//     fun <T> encodeToByteArray(serializer: KSerializer<T>, value: T): ByteArray = TODO()
//     fun <T> decodeFromByteArray(serializer: KSerializer<T>, bytes: ByteArray): T = TODO()
//     val defaultSerializersModule: SerializersModule = SerializersModule { }
// }

@Serializable
data class TrikeshedMessage(
    val id: String,
    val timestamp: Long,
    val payload: String,
    val nestedData: Nested? = null
)

@Serializable
data class Nested(
    val valueA: Int,
    val valueB: Boolean
)

class TrikeshedMessageHazelcastSerializer : ByteArraySerializer<TrikeshedMessage> {
    override fun getTypeId(): Int {
        // This ID needs to be unique within the Hazelcast cluster's serialization configuration
        return 1001
    }

    // Configure kotlinx.serialization.MsgPack instance
    // This might involve setting up context or specific configurations if needed by the library.
    // For now, assume a default configuration or a simple setup.
    // The actual MsgPack object might need to be instantiated with specific SerializersModule
    // if dealing with polymorphism or context-sensitive serialization.
    private val msgPack = MsgPack {
        // If TrikeshedMessage or Nested were part of a polymorphic hierarchy,
        // you would configure it here. For example:
        // serializersModule = SerializersModule {
        //     polymorphic(Any::class) {
        //         subclass(TrikeshedMessage::class)
        //         subclass(Nested::class)
        //     }
        // }
        // For now, we assume direct serialization of concrete types is sufficient.
    }

    override fun write(message: TrikeshedMessage): ByteArray {
        return msgPack.encodeToByteArray(TrikeshedMessage.serializer(), message)
    }

    override fun read(buffer: ByteArray): TrikeshedMessage {
        return msgPack.decodeFromByteArray(TrikeshedMessage.serializer(), buffer)
    }

    override fun destroy() {
        // Release any resources if necessary
    }
}

// Example of a more generic Hazelcast serializer for any @Serializable KClass
// This would require passing the KClass or KSerializer to the constructor
// and might be more complex to register with Hazelcast if it expects parameterless constructors.

// class GenericMessagePackHazelcastSerializer<T : Any>(
//     private val kSerializer: KSerializer<T>,
//     private val typeId: Int
// ) : ByteArraySerializer<T> {
//
//     private val msgPack = MsgPack // Use a configured instance
//
//     override fun getTypeId(): Int = typeId
//
//     override fun write(obj: T): ByteArray {
//         return msgPack.encodeToByteArray(kSerializer, obj)
//     }
//
//     override fun read(buffer: ByteArray): T {
//         return msgPack.decodeFromByteArray(kSerializer, buffer)
//     }
//
//     override fun destroy() {}
// }
