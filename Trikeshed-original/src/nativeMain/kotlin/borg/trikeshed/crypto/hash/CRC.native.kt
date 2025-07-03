@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * Native implementation of CRC16 hasher
 */
actual object CRC16Hasher {
    actual fun checksum(data: Indexed<Byte>): UShort {
        var crc = 0xFFFF
        for (i in 0 until data.a) {
            crc = crc xor (data.b(i).toInt() and 0xFF)
            for (j in 0..7) {
                if (crc and 1 != 0) {
                    crc = (crc shr 1) xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc.toUShort()
    }
}

/**
 * Native implementation of CRC32 hasher
 */
actual object CRC32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt {
        var crc = 0xFFFFFFFFL
        for (i in 0 until data.a) {
            var temp = (crc xor (data.b(i).toLong() and 0xFF)) and 0xFF
            for (j in 0..7) {
                if (temp and 1 != 0L) {
                    temp = (temp shr 1) xor 0xEDB88320L
                } else {
                    temp = temp shr 1
                }
            }
            crc = (crc shr 8) xor temp
        }
        return (crc xor 0xFFFFFFFFL).toUInt()
    }
}