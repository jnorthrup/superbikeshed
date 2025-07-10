@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

actual class ServerSocketChannel actual constructor() : AcceptingChannel {
    actual override val isOpen: Boolean get() = false // Not supported in browser
    
    actual override suspend fun close() {}
    
    actual override suspend fun accept(): SocketChannel? = null // Not supported
    
    actual suspend fun bind(port: Int) {
        throw UnsupportedOperationException("Server sockets not supported in browser")
    }
}