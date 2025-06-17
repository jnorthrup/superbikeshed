package borg.trikeshed.lib

enum class SerializationFormat {
    JSON,
    CBOR,
    PROTOBUF,
    MESSAGE_PACK
}

enum class MementoType {
    DATA,
    METADATA,
    INDEX,
    CACHE
}

class WireProto(private val format: SerializationFormat) {
    fun <T> serialize(value: T, type: MementoType): ByteArray {
        // Implementation would depend on format
        return ByteArray(0)
    }
    
    fun <T> deserialize(bytes: ByteArray, type: MementoType): T {
        // Implementation would depend on format
        throw NotImplementedError()
    }
} 