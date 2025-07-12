package borg.trikeshed.net.quic

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.channels.Channel

expect interface QuicStream {
    val internalReceiveChannel: Channel<Indexed<Byte>>
    val internalSendChannel: Channel<Indexed<Byte>>
    suspend fun writeBytes(bytes: Indexed<Byte>)
    fun remaining(): Int
    fun array(): ByteArray
}
