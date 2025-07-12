package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel

/**
 * JVM implementation of QuicStream
 */
actual interface QuicStream {
    actual val internalReceiveChannel: Channel<Indexed<Byte>>
    actual val internalSendChannel: Channel<Indexed<Byte>>
    actual suspend fun writeBytes(bytes: Indexed<Byte>)
    actual fun remaining(): Int
    actual fun array(): ByteArray
}

class QuicStreamImpl : QuicStream {
    override val internalReceiveChannel: Channel<Indexed<Byte>> = Channel()
    override val internalSendChannel: Channel<Indexed<Byte>> = Channel()
    
    override suspend fun writeBytes(bytes: Indexed<Byte>) {
        println("QuicStream.writeBytes() not implemented for JVM")
    }
    
    override fun remaining(): Int = 0
    
    override fun array(): ByteArray = ByteArray(0)
}