@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SocketChannel

actual class ClientChannel(internal val socketChannel: SocketChannel) {
    actual fun isConnected(): Boolean = socketChannel.isConnected
    
    actual fun close() {
        socketChannel.close()
    }
}