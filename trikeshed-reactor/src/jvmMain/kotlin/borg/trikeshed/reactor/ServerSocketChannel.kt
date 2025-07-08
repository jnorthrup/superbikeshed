@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.ServerSocketChannel as JvmServerSocketChannel
import java.net.InetSocketAddress

actual class ServerSocketChannel actual constructor() : AcceptingChannel {
    internal val jvmChannel = JvmServerSocketChannel.open()
    
    actual override val isOpen: Boolean get() = jvmChannel.isOpen
    
    actual override suspend fun close() {
        jvmChannel.close()
    }
    
    actual override suspend fun accept(): SocketChannel? {
        val accepted = jvmChannel.accept()
        return if (accepted != null) {
            // This would need a constructor that takes an existing JVM channel
            // For now, return null as a placeholder
            null 
        } else null
    }
    
    actual suspend fun bind(port: Int) {
        jvmChannel.bind(InetSocketAddress(port))
    }
}