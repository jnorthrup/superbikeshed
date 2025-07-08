@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Server socket channel - minimal TCP server
 */
expect class ServerSocketChannel() : AcceptingChannel {
    override val isOpen: Boolean
    override suspend fun close()
    override suspend fun accept(): SocketChannel?
    suspend fun bind(port: Int)
}