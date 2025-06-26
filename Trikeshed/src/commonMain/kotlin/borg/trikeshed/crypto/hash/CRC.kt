package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * CRC16 Checksum Algorithm Implementation
 * 
 * Provides multiplatform CRC16 checksum following the CCEK pattern.
 * CRC16 is commonly used in communication protocols and data integrity checks.
 */
expect object CRC16Hasher {
    /**
     * Computes the CRC16 checksum of the input data.
     * @param data The input byte array.
     * @return A 16-bit checksum value.
     */
    fun checksum(data: Indexed<Byte>): UShort
}

/**
 * CRC32 Checksum Algorithm Implementation
 * 
 * Provides multiplatform CRC32 checksum following the CCEK pattern.
 * CRC32 is widely used in file formats and data integrity checks.
 */
expect object CRC32Hasher {
    /**
     * Computes the CRC32 checksum of the input data.
     * @param data The input byte array.
     * @return A 32-bit checksum value.
     */
    fun checksum(data: Indexed<Byte>): UInt
}

/**
 * Extension function for easy CRC16 checksum of any object
 */
val Any?.crc16Checksum: UShort
    get() {
        val s = this?.toString() ?: "null"
        val ba = s.encodeToByteArray()
        return CRC16Hasher.checksum(ba.size j { ba[it] })
    }

/**
 * Extension function for CRC16 checksum as hex string
 */
val Any?.crc16Hex: String get() = this.crc16Checksum.toString(16).padStart(4, '0')

/**
 * Extension function for easy CRC32 checksum of any object
 */
val Any?.crc32Checksum: UInt
    get() {
        val s = this?.toString() ?: "null"
        val ba = s.encodeToByteArray()
        return CRC32Hasher.checksum(ba.size j { ba[it] })
    }

/**
 * Extension function for CRC32 checksum as hex string
 */
val Any?.crc32Hex: String get() = this.crc32Checksum.toString(16).padStart(8, '0') 