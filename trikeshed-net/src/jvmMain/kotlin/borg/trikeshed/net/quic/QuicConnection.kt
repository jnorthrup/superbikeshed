package borg.trikeshed.net.quic

/**
 * JVM implementation of QuicConnection
 */
actual interface QuicConnection {
    actual suspend fun createStream(): QuicStream?
    actual suspend fun close()
}

class QuicConnectionImpl : QuicConnection {
    override suspend fun createStream(): QuicStream? {
        println("QuicConnection.createStream() not implemented for JVM")
        return null
    }
    
    override suspend fun close() {
        println("QuicConnection.close() not implemented for JVM")
    }
}