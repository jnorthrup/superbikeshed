package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.*

actual object Adler32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt {
        var a: UInt = 1u
        var b: UInt = 0u
        
        for (i in 0 until data.size) {
            val byte = data[i].toUInt()
            a = (a + byte) % 65521u
            b = (b + a) % 65521u
        }
        
        return (b shl 16) or a
    }
} 