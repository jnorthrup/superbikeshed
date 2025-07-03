package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.*

actual object CRC16Hasher {
    actual fun checksum(data: Indexed<Byte>): UShort {
        var crc: UShort = 0xFFFFu
        
        for (i in 0 until data.size) {
            crc = crc xor data[i].toUShort()
            for (j in 0 until 8) {
                crc = if ((crc and 0x0001u) != 0u.toUShort()) {
                    (crc.toUInt() shr 1).toUShort() xor 0xA001u
                } else {
                    (crc.toUInt() shr 1).toUShort()
                }
            }
        }
        
        return crc
    }
}

actual object CRC32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt {
        var crc: UInt = 0xFFFFFFFFu
        
        for (i in 0 until data.size) {
            crc = crc xor data[i].toUInt()
            for (j in 0 until 8) {
                crc = if ((crc and 1u) != 0u) {
                    (crc shr 1) xor 0xEDB88320u
                } else {
                    crc shr 1
                }
            }
        }
        
        return crc xor 0xFFFFFFFFu
    }
} 