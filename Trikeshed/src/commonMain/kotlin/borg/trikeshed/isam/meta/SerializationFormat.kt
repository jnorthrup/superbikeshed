package borg.trikeshed.isam.meta

import borg.trikeshed.lib.*

// Inline value classes for serialization format and memento type
@JvmInline value class SerializationFormat(val value: String)
@JvmInline value class MementoType(val value: IOMemento)

// Type aliases for dispatch tables
typealias FormatMementoDispatch = DoubleDispatchTable<SerializationFormat, MementoType, CodecFunction>

// Codec function type for encoding/decoding
typealias CodecFunction = Pair<(Any?) -> ByteArray, (ByteArray) -> Any?>

object SerializationFormatDispatch {
    // Helper function to get encoder for a format and memento
    fun getEncoder(format: String, memento: IOMemento): (Any?) -> ByteArray =
        memento.createEncoder(0)

    // Helper function to get decoder for a format and memento  
    fun getDecoder(format: String, memento: IOMemento): (ByteArray) -> Any? =
        memento.createDecoder(0)
}