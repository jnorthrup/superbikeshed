package borg.trikeshed.reactor

import java.nio.channels.SocketChannel

actual class ClientChannel(private val channel: SocketChannel) {
    actual fun isConnected(): Boolean = channel.isConnected
    actual fun close() = channel.close()
}