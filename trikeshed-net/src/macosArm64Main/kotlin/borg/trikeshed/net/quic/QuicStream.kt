package borg.trikeshed.net.quic

import borg.trikeshed.lib.Indexed

/**
 * macOS ARM64 implementation of QuicStream
 */
actual interface QuicStream {
    actual suspend fun write(data: Indexed<Byte>): Boolean
    actual suspend fun read(): Indexed<Byte>?
    actual suspend fun close()
}

class QuicStreamImpl : QuicStream {
    override suspend fun write(data: Indexed<Byte>): Boolean {
        println("QuicStream.write() not implemented for macosArm64")
        return false
    }
    
    override suspend fun read(): Indexed<Byte>? {
        println("QuicStream.read() not implemented for macosArm64")
        return null
    }
    
    override suspend fun close() {
        println("QuicStream.close() not implemented for macosArm64")
    }
}