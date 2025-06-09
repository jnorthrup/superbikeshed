package borg.trikeshed.net.quic.utils

/**
 * Writes a UShort to a ByteArray as 2 bytes in big-endian order.
 */
fun UShort.writeShort(): ByteArray = byteArrayOf((this.toInt() shr 8).toByte(), this.toInt().toByte())

/**
 * Prepends the size of the ByteArray (as a UShort) to the ByteArray itself.
 * The size is written as 2 bytes in big-endian order.
 * Useful for length-prefixed lists in TLS.
 */
fun ByteArray.writeShortLengthPrefixed(): ByteArray = this.size.toUShort().writeShort() + this

/**
 * Prepends the size of the ByteArray (as a UByte) to the ByteArray itself.
 * The size is written as 1 byte.
 * Useful for length-prefixed lists in TLS where length fits in one byte.
 */
fun ByteArray.writeByteLengthPrefixed(): ByteArray {
    require(this.size <= UByte.MAX_VALUE.toInt()) { "ByteArray size ${this.size} exceeds UByte.MAX_VALUE" }
    return byteArrayOf(this.size.toUByte().toByte()) + this
}

/**
 * Reads a UShort from the first 2 bytes of a ByteArray in big-endian order.
 * @throws IllegalArgumentException if ByteArray is too short.
 */
fun ByteArray.readShort(offset: Int = 0): UShort {
    require(offset + 2 <= this.size) { "Not enough bytes to read a UShort from offset $offset" }
    return ((this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)).toUShort()
}

/**
 * Reads a UInt from the first 4 bytes of a ByteArray in big-endian order.
 * @throws IllegalArgumentException if ByteArray is too short.
 */
fun ByteArray.readUInt(offset: Int = 0): UInt {
    require(offset + 4 <= this.size) { "Not enough bytes to read a UInt from offset $offset" }
    var value = 0u
    value = value or ((this[offset].toUInt() and 0xFFu) shl 24)
    value = value or ((this[offset + 1].toUInt() and 0xFFu) shl 16)
    value = value or ((this[offset + 2].toUInt() and 0xFFu) shl 8)
    value = value or (this[offset + 3].toUInt() and 0xFFu)
    return value
}

/**
 * Writes a UInt to a ByteArray as 4 bytes in big-endian order.
 */
fun UInt.writeUInt(): ByteArray {
    return byteArrayOf(
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte()
    )
}

/**
 * Writes an Int to a ByteArray as 3 bytes (24-bit unsigned integer) in big-endian order.
 * Useful for TLS lengths (e.g., for handshake message payloads, certificate data lengths).
 * @throws IllegalArgumentException if the value is out of range for a 24-bit unsigned integer.
 */
fun Int.toUInt24Bytes(): ByteArray {
    require(this >= 0 && this < (1 shl 24)) { "Value $this out of range for 24-bit unsigned integer (0 to ${ (1 shl 24) -1 })" }
    return byteArrayOf(
        (this shr 16).toByte(), // Most significant byte of the 24 bits
        (this shr 8).toByte(),  // Middle byte
        this.toByte()           // Least significant byte
    )
}
