package trikeshed.lib

/**
 * TrikeShed platform stub types
 * In production, these would come from the actual TrikeShed library
 */

// Core collection types
typealias Indexed<T> = List<T>
typealias Series<T> = Sequence<T>
typealias Cursor<T> = Iterator<T>

// Join types
data class Join<A, B>(val l: A, val r: B)
data class Twin<T>(val l: T, val r: T)

// Infix operators
infix fun <A, B> A.j(that: B): Join<A, B> = this j that

// Binary buffer for serialization
class BinaryBuffer(
    var data: ByteArray = ByteArray(256),
    var position: Int = 0
) {
    fun ensureCapacity(needed: Int) {
        if (position + needed > data.size) {
            data = data.copyOf((data.size * 2).coerceAtLeast(position + needed))
        }
    }
    
    fun writeByte(value: Byte) {
        ensureCapacity(1)
        data[position++] = value
    }
    
    fun readByte(): Byte = data[position++]
    
    fun writeVarInt(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            writeByte(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        writeByte(v.toByte())
    }
    
    fun readVarInt(): Int {
        var value = 0
        var shift = 0
        var b: Byte
        do {
            b = readByte()
            value = value or ((b.toInt() and 0x7F) shl shift)
            shift += 7
        } while (b.toInt() and 0x80 != 0)
        return value
    }
    
    fun writeFloat(value: Float) {
        writeVarInt(value.toBits())
    }
    
    fun readFloat(): Float = Float.fromBits(readVarInt())
    
    fun writeString(value: String) {
        val bytes = value.toByteArray()
        writeVarInt(bytes.size)
        ensureCapacity(bytes.size)
        bytes.copyInto(data, position)
        position += bytes.size
    }
    
    fun readString(): String {
        val length = readVarInt()
        val str = String(data.sliceArray(position until position + length))
        position += length
        return str
    }
    
    fun toByteArray(): ByteArray = data.copyOf(position)
}