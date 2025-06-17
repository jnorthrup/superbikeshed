package gk.kademlia.codec

import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack // Main MsgPack object
import gk.kademlia.agent.* // Imports KademliaEvent and its implementations (PingEvent, etc.)
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Assuming NUID and BigInt are either @Serializable or have serializers registered elsewhere
// or will be handled by @ContextualSerialization or similar mechanisms if direct serialization fails.

@OptIn(InternalSerializationApi::class) // For commonTypeSerializer an KClass.serializer()
class MessagePackKademliaCodec : Codec<KademliaEvent, ByteArray> {

    private val msgPack = MsgPack {
        serializersModule = SerializersModule {
            polymorphic(KademliaEvent::class) {
                subclass(PingEvent::class, PingEvent.serializer())
                subclass(PongEvent::class, PongEvent.serializer())
                subclass(JoinRequestEvent::class, JoinRequestEvent.serializer())
                subclass(JoinResponseEvent::class, JoinResponseEvent.serializer())
                // Add other KademliaEvent subtypes here as they are defined
                // e.g., subclass(LagdEvent::class, LagdEvent.serializer())
            }
            // If NUID or BigInt require custom serializers, they would be registered here, for example:
            // context(NUIDSerializer) // Assuming NUIDSerializer is a custom KSerializer for NUID
        }
        // Other MsgPack configurations if needed, e.g., ignoreUnknownKeys = true
        ignoreUnknownKeys = true
    }

    override fun send(event: KademliaEvent): ByteArray? {
        return try {
            // kotlinx.serialization can infer the serializer for sealed class subtypes
            // if the base class (KademliaEvent) is correctly configured with polymorphic serializers.
            msgPack.encodeToByteArray(KademliaEvent.serializer(), event)
        } catch (e: Exception) {
            // Log error or handle otherwise
            println("Serialization Error: ${'$'}{e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun recv(ser: ByteArray): KademliaEvent? {
        return try {
            msgPack.decodeFromByteArray(KademliaEvent.serializer(), ser)
        } catch (e: Exception) {
            // Log error or handle otherwise
            println("Deserialization Error: ${'$'}{e.message}")
            e.printStackTrace()
            null
        }
    }
}
