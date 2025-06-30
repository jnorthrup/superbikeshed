package borg.trikeshed.zlib

import borg.trikeshed.lib.Indexed

actual object Zlib {
    actual fun compress(input: Indexed<Byte>): Indexed<Byte> {
        throw NotImplementedError("compress() is not yet implemented for JVM.")
    }

    actual fun decompress(input: Indexed<Byte>): Indexed<Byte> {
        throw NotImplementedError("decompress() is not yet implemented for JVM.")
    }

    actual fun gzipCompress(input: Indexed<Byte>): Indexed<Byte> {
        throw NotImplementedError("gzipCompress() is not yet implemented for JVM.")
    }

    actual fun gzipDecompress(input: Indexed<Byte>): Indexed<Byte> {
        throw NotImplementedError("gzipDecompress() is not yet implemented for JVM.")
    }

    actual fun createZranIndex(gzipStream: Indexed<Byte>): Zlib.ZranIndex {
        throw NotImplementedError("createZranIndex() is not yet implemented for JVM.")
    }

    actual class ZranIndex : Zlib.ZranIndex {
        override fun decompressSegment(offset: Long, length: Int): Indexed<Byte> {
            throw NotImplementedError("decompressSegment() is not yet implemented for JVM.")
        }
    }
}
