package borg.trikeshed.net.quic

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.channels.Channel

actual class QuicConnectionImpl : QuicConnection {
    override suspend fun createStream(): QuicStream? {
        println("QuicConnection.createStream() not implemented for macosArm64")
        return null
    }

    override suspend fun close() {
        println("QuicConnection.close() not implemented for macosArm64")
    }
}

actual class QuicStreamImpl : QuicStream {
    override val internalReceiveChannel: Channel<Indexed<Byte>> = Channel(Channel.UNLIMITED)
    override val internalSendChannel: Channel<Indexed<Byte>> = Channel(Channel.UNLIMITED)

    override suspend fun writeBytes(bytes: Indexed<Byte>) {
        println("QuicStream.writeBytes() not implemented for macosArm64")
    }

    override fun remaining(): Int {
        println("QuicStream.remaining() not implemented for macosArm64")
        return 0
    }

    override fun array(): ByteArray {
        println("QuicStream.array() not implemented for macosArm64")
        return ByteArray(0)
    }
}
