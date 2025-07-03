package borg.trikeshed.wireproto

import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*

// Placeholder serialization functions for tests
// These would need proper implementation with kotlinx-serialization

fun IOMemento.toWireBytes(): ByteArray {
    // Simplified implementation for testing
    val nameBytes = name?.toByteArray() ?: byteArrayOf()
    val typeBytes = type?.toByteArray() ?: byteArrayOf()
    
    return byteArrayOf(
        nameBytes.size.toByte(),
        typeBytes.size.toByte()
    ) + nameBytes + typeBytes
}

fun ByteArray.toIoMemento(): IOMemento {
    // Simplified deserialization
    val nameLen = this[0].toInt()
    val typeLen = this[1].toInt()
    
    val name = if (nameLen > 0) this.sliceArray(2 until 2 + nameLen).decodeToString() else null
    val type = if (typeLen > 0) this.sliceArray(2 + nameLen until 2 + nameLen + typeLen).decodeToString() else null
    
    return IOMemento.create(name, type, null, null)
}

fun <T> Indexed<T>.toWireBytes(): ByteArray {
    // Placeholder - would need proper serialization
    return byteArrayOf(0x00, 0x01, 0x02, 0x03)
}

fun <T> ByteArray.toSeries(): Indexed<T> {
    // Placeholder - would need proper deserialization
    return 0 j { throw NotImplementedError("Wire deserialization not implemented") }
}