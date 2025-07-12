package borg.trikeshed.net.quic

/**
 * macOS ARM64 implementation of QuicConnection
 */
actual interface QuicConnection {
    actual suspend fun createStream(): QuicStream?
    actual suspend fun close()
}

class QuicConnectionImpl : QuicConnection {
    override suspend fun createStream(): QuicStream? {
        println("QuicConnection.createStream() not implemented for macosArm64")
        return null
    }
    
    override suspend fun close() {
        println("QuicConnection.close() not implemented for macosArm64")
    }
}