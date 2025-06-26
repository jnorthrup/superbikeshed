package borg.trikeshed.wireproto
@file:OptIn(ExperimentalUnsignedTypes::class)


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*

/**
 * Minimal placeholder TrikeShed wire protocol for compilation
 */
class TrikeShedWireProto {
    fun serialize(data: Any): Indexed<Byte> {
        // Placeholder implementation
        return 0 j { throw NoSuchElementException() }
    }
    
    fun <T> deserialize(data: Indexed<Byte>): T {
        // Placeholder implementation
        throw NotImplementedError("Deserialization not implemented")
    }
}

// Minimal data structures
data class WireIoMemento(
    val id: String,
    val data: Indexed<Byte>
)

fun <T> T.toWireBytes(): Indexed<Byte> {
    // Placeholder implementation
    return 0 j { throw NoSuchElementException() }
}

fun <T> Indexed<Byte>.toIoMemento(): WireIoMemento {
    // Placeholder implementation
    return WireIoMemento("placeholder", this)
} 