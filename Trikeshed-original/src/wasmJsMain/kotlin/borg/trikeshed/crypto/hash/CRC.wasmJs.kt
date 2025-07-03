package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.*

/**
 * WasmJs implementations of CRC hashers
 * Simplified stub implementations for compilation
 */

actual object CRC16Hasher {
    actual fun checksum(data: Indexed<Byte>): UShort = 0u
}

actual object CRC32Hasher {
    actual fun checksum(data: Indexed<Byte>): UInt = 0u
}