package borg.trikeshed.isam.meta

import kotlin.jvm.JvmInline

/**
 * ISAM data type definitions for storage and retrieval operations.
 * These types represent primitive data types with storage metadata.
 */

// Primitive IO types
@JvmInline
value class IoByte(val value: Byte) {
    companion object {
        const val SIZE = 1
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoByte = IoByte(bytes[offset])
    }
    fun toBytes(): ByteArray = byteArrayOf(value)
}

@JvmInline
value class IoShort(val value: Short) {
    companion object {
        const val SIZE = 2
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoShort {
            return IoShort((bytes[offset].toInt() shl 8 or (bytes[offset + 1].toInt() and 0xFF)).toShort())
        }
    }
    fun toBytes(): ByteArray = byteArrayOf((value.toInt() shr 8).toByte(), value.toByte())
}

@JvmInline
value class IoInt(val value: Int) {
    companion object {
        const val SIZE = 4
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoInt {
            return IoInt(
                (bytes[offset].toInt() shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            )
        }
    }
    fun toBytes(): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )
}

@JvmInline
value class IoLong(val value: Long) {
    companion object {
        const val SIZE = 8
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoLong {
            var result = 0L
            for (i in 0 until 8) {
                result = (result shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
            return IoLong(result)
        }
    }
    fun toBytes(): ByteArray {
        val bytes = ByteArray(8)
        var v = value
        for (i in 7 downTo 0) {
            bytes[i] = v.toByte()
            v = v shr 8
        }
        return bytes
    }
}

@JvmInline
value class IoFloat(val value: Float) {
    companion object {
        const val SIZE = 4
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoFloat {
            val intBits = IoInt.fromBytes(bytes, offset).value
            return IoFloat(Float.fromBits(intBits))
        }
    }
    fun toBytes(): ByteArray = IoInt(value.toBits()).toBytes()
}

@JvmInline
value class IoDouble(val value: Double) {
    companion object {
        const val SIZE = 8
        fun fromBytes(bytes: ByteArray, offset: Int = 0): IoDouble {
            val longBits = IoLong.fromBytes(bytes, offset).value
            return IoDouble(Double.fromBits(longBits))
        }
    }
    fun toBytes(): ByteArray = IoLong(value.toBits()).toBytes()
}

// String types with variable length
@JvmInline
value class IoString(val value: String) {
    companion object {
        fun fromBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): IoString {
            return IoString(bytes.decodeToString(offset, offset + length))
        }
    }
    fun toBytes(): ByteArray = value.encodeToByteArray()
}

// Nullable variants
typealias IoByteNullable = IoByte?
typealias IoShortNullable = IoShort?
typealias IoIntNullable = IoInt?
typealias IoLongNullable = IoLong?
typealias IoFloatNullable = IoFloat?
typealias IoDoubleNullable = IoDouble?
typealias IoStringNullable = IoString?