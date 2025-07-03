package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.*

actual object Adler32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt = 0u
}