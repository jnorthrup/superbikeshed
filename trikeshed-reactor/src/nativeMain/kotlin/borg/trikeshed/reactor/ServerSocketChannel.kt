@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

actual class ServerSocketChannel actual constructor() : AcceptingChannel {
    internal var bound = false
    
    actual override val isOpen: Boolean get() = bound
    
    actual override suspend fun close() {
        bound = false
    }
    
    actual override suspend fun accept(): SocketChannel? {
        return null // Placeholder
    }
    
    actual suspend fun bind(port: Int) {
        bound = true
    }
}