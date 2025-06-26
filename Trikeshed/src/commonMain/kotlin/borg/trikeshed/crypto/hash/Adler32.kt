package borg.trikeshed.crypto.hash
@file:OptIn(ExperimentalUnsignedTypes::class)


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * Adler32 Checksum Algorithm Implementation
 * 
 * Provides multiplatform Adler32 checksum following the CCEK pattern.
 * Adler32 is a fast checksum algorithm, commonly used in zlib.
 */
expect object Adler32Hasher {
    /**
     * Computes the Adler32 checksum of the input data.
     * @param data The input byte array.
     * @return A 32-bit checksum value.
     */
    fun checksum(data: Indexed<Byte>): UInt
}

/**
 * Extension function for easy Adler32 checksum of any object
 */
val Any?.adler32Checksum: UInt
    get() {
        val s = this?.toString() ?: "null"
        val ba = s.encodeToByteArray()
        return Adler32Hasher.checksum(ba.size j { ba[it] })
    }

/**
 * Extension function for Adler32 checksum as hex string
 */
val Any?.adler32Hex: String get() = this.adler32Checksum.toString(16).padStart(8, '0') 