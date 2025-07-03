@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.Indexed

/**
 * Native implementation of Adler32 hasher
 */
actual object Adler32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt {
        var s1 = 1L
        var s2 = 0L
        
        for (i in 0 until data.a) {
            s1 = (s1 + (data.b(i).toInt() and 0xFF)) % 65521
            s2 = (s2 + s1) % 65521
        }
        
        return ((s2 shl 16) or s1).toUInt()
    }
}