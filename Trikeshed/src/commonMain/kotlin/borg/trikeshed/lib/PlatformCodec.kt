package borg.trikeshed.lib

enum class Endianness {
    LITTLE,
    BIG
}

enum class PrimitiveSize {
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE
}

class PlatformCodec(private val endianness: Endianness) {
    fun <T> encode(value: T, size: PrimitiveSize): ByteArray {
        // Implementation would depend on platform
        return ByteArray(0)
    }
    
    fun <T> decode(bytes: ByteArray, size: PrimitiveSize): T {
        // Implementation would depend on platform
        throw NotImplementedError()
    }
} 